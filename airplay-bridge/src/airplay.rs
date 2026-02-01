//! AirPlay 2 session management
//!
//! Handles RTSP communication, HomeKit pairing, and audio streaming setup
//! for AirPlay 2 devices.

use anyhow::{anyhow, Result};
use chacha20poly1305::{
    aead::{Aead, KeyInit},
    ChaCha20Poly1305, Nonce,
};
use hkdf::Hkdf;
use sha2::Sha256;
use std::sync::Arc;
use std::sync::atomic::{AtomicU16, AtomicU32, AtomicU64, Ordering};
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio::sync::mpsc;
use x25519_dalek::{EphemeralSecret, PublicKey};

use crate::audio::{AudioEncoder, ensure_44100, AIRPLAY_SAMPLE_RATE};
use crate::discovery::AirPlayDevice;
use crate::AirPlayBridge;

/// Commands that can be sent to the audio session
#[derive(Debug)]
pub enum AudioCommand {
    SendAudio {
        data: Vec<u8>,
        sample_rate: u32,
        channels: u32,
    },
    SetVolume(f32),
    Disconnect,
}

/// Manages an active AirPlay connection
pub struct AirPlaySession {
    /// Target device
    device: AirPlayDevice,
    /// TCP connection to device
    connection: TcpStream,
    /// RTSP CSeq counter
    cseq: AtomicU32,
    /// RTP sequence number
    rtp_seq: AtomicU16,
    /// RTP timestamp
    rtp_timestamp: AtomicU32,
    /// Audio encoder
    audio_encoder: AudioEncoder,
    /// Audio data socket
    audio_socket: Option<tokio::net::UdpSocket>,
    /// Server audio port (parsed from SETUP response)
    server_audio_port: u16,
    /// Current volume (0.0 - 1.0)
    volume: f32,
    /// Whether encryption is enabled
    encryption_enabled: bool,
    /// Encryption key (derived from pairing)
    encryption_key: Option<[u8; 32]>,
    /// Encryption nonce counter
    nonce_counter: AtomicU64,
    /// Whether this is the first RTP packet (for marker bit)
    first_packet: bool,
}

impl AirPlaySession {
    /// Create a new AirPlay session
    pub async fn new(device: AirPlayDevice) -> Result<Self> {
        log::info!("Connecting to {} at {}:{}",
            device.name, device.address, device.port);

        // Establish TCP connection with timeout
        let addr = format!("{}:{}", device.address, device.port);
        let connection = tokio::time::timeout(
            std::time::Duration::from_secs(10),
            TcpStream::connect(&addr)
        ).await
            .map_err(|_| anyhow!("Connection timeout"))?
            .map_err(|e| anyhow!("Connection failed: {}", e))?;

        log::info!("TCP connection established");

        Ok(Self {
            device,
            connection,
            cseq: AtomicU32::new(0),
            rtp_seq: AtomicU16::new(0),
            rtp_timestamp: AtomicU32::new(0),
            audio_encoder: AudioEncoder::new(),
            audio_socket: None,
            server_audio_port: 6000,
            volume: 1.0,
            encryption_enabled: false,
            encryption_key: None,
            nonce_counter: AtomicU64::new(0),
            first_packet: true,
        })
    }

    /// Perform the initial handshake and setup
    pub async fn setup(&mut self) -> Result<()> {
        // Perform RTSP handshake
        self.rtsp_options().await?;

        // For AirPlay 2, we need to do HomeKit pairing
        if self.device.supports_airplay2 {
            match self.homekit_pair().await {
                Ok(_) => log::info!("HomeKit pairing successful"),
                Err(e) => {
                    log::warn!("HomeKit pairing failed: {}. Trying without encryption.", e);
                    // Continue without encryption for devices that allow it
                }
            }
        }

        // Setup audio streaming
        self.rtsp_setup().await?;

        log::info!("AirPlay session established");
        Ok(())
    }

    /// Run the session, processing commands from the channel
    pub async fn run(&mut self, mut cmd_rx: mpsc::Receiver<AudioCommand>) -> Result<()> {
        log::info!("Session running, waiting for commands");

        while let Some(cmd) = cmd_rx.recv().await {
            match cmd {
                AudioCommand::SendAudio { data, sample_rate, channels } => {
                    if let Err(e) = self.send_audio_internal(&data, sample_rate, channels).await {
                        log::warn!("Failed to send audio: {}", e);
                    }
                }
                AudioCommand::SetVolume(vol) => {
                    if let Err(e) = self.set_volume_internal(vol).await {
                        log::warn!("Failed to set volume: {}", e);
                    }
                }
                AudioCommand::Disconnect => {
                    log::info!("Disconnect command received");
                    break;
                }
            }
        }

        // Send TEARDOWN
        let _ = self.rtsp_teardown().await;

        log::info!("Session ended");
        Ok(())
    }

    /// Get next CSeq value
    fn next_cseq(&self) -> u32 {
        self.cseq.fetch_add(1, Ordering::SeqCst) + 1
    }

    /// Get next RTP sequence number
    fn next_rtp_seq(&self) -> u16 {
        self.rtp_seq.fetch_add(1, Ordering::SeqCst)
    }

    /// Update RTP timestamp
    fn advance_rtp_timestamp(&self, samples: u32) {
        self.rtp_timestamp.fetch_add(samples, Ordering::SeqCst);
    }

    /// Send RTSP OPTIONS request
    async fn rtsp_options(&mut self) -> Result<()> {
        let cseq = self.next_cseq();

        let request = format!(
            "OPTIONS * RTSP/1.0\r\n\
             CSeq: {}\r\n\
             User-Agent: OuterTune/1.0\r\n\
             \r\n",
            cseq
        );

        self.send_rtsp(&request).await?;
        let response = self.recv_rtsp_response().await?;

        if !response.status_line.contains("200") {
            return Err(anyhow!("OPTIONS failed: {}", response.status_line));
        }

        log::debug!("OPTIONS successful");
        Ok(())
    }

    /// Perform HomeKit transient pairing using Curve25519 key exchange
    async fn homekit_pair(&mut self) -> Result<()> {
        log::info!("Starting HomeKit pairing");

        // Generate our ephemeral key pair
        let our_secret = EphemeralSecret::random_from_rng(rand::thread_rng());
        let our_public = PublicKey::from(&our_secret);

        // Step 1: Start pairing
        let cseq = self.next_cseq();
        let request = format!(
            "POST /pair-setup RTSP/1.0\r\n\
             CSeq: {}\r\n\
             User-Agent: OuterTune/1.0\r\n\
             Content-Type: application/octet-stream\r\n\
             Content-Length: {}\r\n\
             \r\n",
            cseq,
            our_public.as_bytes().len()
        );

        self.send_rtsp(&request).await?;
        self.connection.write_all(our_public.as_bytes()).await?;

        let response = self.recv_rtsp_response().await?;

        if response.status_line.contains("200") {
            // Try to get server's public key from response body
            if response.body.len() >= 32 {
                let server_public_bytes: [u8; 32] = response.body[..32]
                    .try_into()
                    .map_err(|_| anyhow!("Invalid server public key"))?;
                let server_public = PublicKey::from(server_public_bytes);

                // Compute shared secret
                let shared_secret = our_secret.diffie_hellman(&server_public);

                // Derive encryption key using HKDF
                let hk = Hkdf::<Sha256>::new(None, shared_secret.as_bytes());
                let mut encryption_key = [0u8; 32];
                hk.expand(b"AirPlay-Audio-Key", &mut encryption_key)
                    .map_err(|_| anyhow!("HKDF expansion failed"))?;

                self.encryption_key = Some(encryption_key);
                self.encryption_enabled = true;
                log::info!("Encryption key derived successfully");
            }
        } else if response.status_line.contains("403") {
            // Device requires PIN pairing - try transient mode
            log::warn!("Device requires PIN. Trying transient pairing...");

            let cseq = self.next_cseq();
            let request = format!(
                "POST /pair-pin-start RTSP/1.0\r\n\
                 CSeq: {}\r\n\
                 User-Agent: OuterTune/1.0\r\n\
                 Content-Length: 0\r\n\
                 \r\n",
                cseq
            );

            self.send_rtsp(&request).await?;
            let response = self.recv_rtsp_response().await?;

            if !response.status_line.contains("200") {
                return Err(anyhow!("Transient pairing not supported"));
            }
        } else {
            log::warn!("Pairing response: {}. Continuing without encryption.", response.status_line);
        }

        Ok(())
    }

    /// Setup audio streaming via RTSP SETUP
    async fn rtsp_setup(&mut self) -> Result<()> {
        log::info!("Setting up audio stream");

        let device_address = self.device.address.clone();
        let device_port = self.device.port;

        // ANNOUNCE the audio format
        let sdp = format!(
            "v=0\r\n\
             o=OuterTune 1 1 IN IP4 {}\r\n\
             s=OuterTune\r\n\
             c=IN IP4 {}\r\n\
             t=0 0\r\n\
             m=audio 0 RTP/AVP 96\r\n\
             a=rtpmap:96 AppleLossless\r\n\
             a=fmtp:96 352 0 16 40 10 14 2 255 0 0 44100\r\n",
            device_address,
            device_address
        );

        let cseq = self.next_cseq();

        let request = format!(
            "ANNOUNCE rtsp://{}:{}/audio RTSP/1.0\r\n\
             CSeq: {}\r\n\
             User-Agent: OuterTune/1.0\r\n\
             Content-Type: application/sdp\r\n\
             Content-Length: {}\r\n\
             \r\n\
             {}",
            device_address,
            device_port,
            cseq,
            sdp.len(),
            sdp
        );

        self.send_rtsp(&request).await?;
        let response = self.recv_rtsp_response().await?;

        if !response.status_line.contains("200") {
            log::warn!("ANNOUNCE response: {}", response.status_line);
            // Continue anyway, some devices don't require ANNOUNCE
        }

        // SETUP the audio transport
        let cseq = self.next_cseq();

        // Bind a local UDP socket first to get the client port
        let client_socket = tokio::net::UdpSocket::bind("0.0.0.0:0").await?;
        let client_port = client_socket.local_addr()?.port();

        let request = format!(
            "SETUP rtsp://{}:{}/audio RTSP/1.0\r\n\
             CSeq: {}\r\n\
             User-Agent: OuterTune/1.0\r\n\
             Transport: RTP/AVP/UDP;unicast;mode=record;client_port={};control_port={};timing_port={}\r\n\
             \r\n",
            device_address,
            device_port,
            cseq,
            client_port,
            client_port + 1,
            client_port + 2
        );

        self.send_rtsp(&request).await?;
        let response = self.recv_rtsp_response().await?;

        if !response.status_line.contains("200") {
            return Err(anyhow!("SETUP failed: {}", response.status_line));
        }

        // Parse server port from response
        self.server_audio_port = self.parse_server_port(&response.headers).unwrap_or(6000);
        log::info!("Server audio port: {}", self.server_audio_port);

        // Connect UDP socket to server
        client_socket.connect(format!("{}:{}", device_address, self.server_audio_port)).await?;
        self.audio_socket = Some(client_socket);

        // Start playback with RECORD
        let cseq = self.next_cseq();

        let request = format!(
            "RECORD rtsp://{}:{}/audio RTSP/1.0\r\n\
             CSeq: {}\r\n\
             User-Agent: OuterTune/1.0\r\n\
             Range: npt=0-\r\n\
             RTP-Info: seq=0;rtptime=0\r\n\
             \r\n",
            device_address,
            device_port,
            cseq
        );

        self.send_rtsp(&request).await?;
        let response = self.recv_rtsp_response().await?;

        if !response.status_line.contains("200") {
            log::warn!("RECORD response: {}", response.status_line);
        }

        log::info!("Audio streaming setup complete");
        Ok(())
    }

    /// Parse server_port from Transport header
    fn parse_server_port(&self, headers: &str) -> Option<u16> {
        for line in headers.lines() {
            let lower = line.to_lowercase();
            if lower.starts_with("transport:") {
                // Look for server_port=XXXX
                for part in line.split(';') {
                    let part = part.trim();
                    if let Some(port_str) = part.strip_prefix("server_port=")
                        .or_else(|| part.strip_prefix("Server_port="))
                    {
                        // May be port or port-port range
                        let port = port_str.split('-').next()?;
                        return port.trim().parse().ok();
                    }
                }
            }
        }
        None
    }

    /// Send RTSP TEARDOWN
    async fn rtsp_teardown(&mut self) -> Result<()> {
        let cseq = self.next_cseq();
        let device_address = &self.device.address;
        let device_port = self.device.port;

        let request = format!(
            "TEARDOWN rtsp://{}:{}/audio RTSP/1.0\r\n\
             CSeq: {}\r\n\
             User-Agent: OuterTune/1.0\r\n\
             \r\n",
            device_address,
            device_port,
            cseq
        );

        let _ = self.send_rtsp(&request).await;
        Ok(())
    }

    /// Send audio data to the AirPlay device
    async fn send_audio_internal(&mut self, pcm_data: &[u8], sample_rate: u32, channels: u32) -> Result<()> {
        if self.audio_socket.is_none() {
            return Err(anyhow!("Audio socket not initialized"));
        }

        // Resample to 44100 Hz if needed
        let resampled = if sample_rate != AIRPLAY_SAMPLE_RATE {
            ensure_44100(pcm_data, sample_rate, channels)
        } else {
            pcm_data.to_vec()
        };

        // Process audio in frames
        let frame_size = 352 * channels as usize * 2; // 352 samples * channels * 2 bytes

        for chunk in resampled.chunks(frame_size) {
            let frame_data = if chunk.len() < frame_size {
                // Pad short chunk with silence
                let mut padded = vec![0u8; frame_size];
                padded[..chunk.len()].copy_from_slice(chunk);
                padded
            } else {
                chunk.to_vec()
            };

            // Encode PCM to ALAC
            let alac_data = self.audio_encoder.encode_alac(&frame_data, AIRPLAY_SAMPLE_RATE, channels)?;

            // Encrypt if encryption is enabled
            let payload = if self.encryption_enabled {
                self.encrypt_payload(&alac_data)?
            } else {
                alac_data
            };

            // Create RTP packet
            let seq = self.next_rtp_seq();
            let timestamp = self.rtp_timestamp.load(Ordering::SeqCst);
            let rtp_packet = self.create_rtp_packet(seq, timestamp, &payload);

            // Advance timestamp by samples per frame
            self.advance_rtp_timestamp(352);

            // Send via UDP
            let socket = self.audio_socket.as_ref().unwrap();
            socket.send(&rtp_packet).await?;

            // After first packet, clear first_packet flag
            self.first_packet = false;
        }

        Ok(())
    }

    /// Encrypt payload using ChaCha20-Poly1305
    fn encrypt_payload(&self, plaintext: &[u8]) -> Result<Vec<u8>> {
        let key = self.encryption_key.ok_or_else(|| anyhow!("No encryption key"))?;
        let cipher = ChaCha20Poly1305::new_from_slice(&key)
            .map_err(|e| anyhow!("Failed to create cipher: {}", e))?;

        // Create nonce from counter
        let counter = self.nonce_counter.fetch_add(1, Ordering::SeqCst);
        let mut nonce_bytes = [0u8; 12];
        nonce_bytes[4..12].copy_from_slice(&counter.to_le_bytes());
        let nonce = Nonce::from_slice(&nonce_bytes);

        let ciphertext = cipher.encrypt(nonce, plaintext)
            .map_err(|e| anyhow!("Encryption failed: {}", e))?;

        // Prepend nonce to ciphertext
        let mut result = Vec::with_capacity(12 + ciphertext.len());
        result.extend_from_slice(&nonce_bytes);
        result.extend_from_slice(&ciphertext);

        Ok(result)
    }

    /// Set volume on the device
    async fn set_volume_internal(&mut self, volume: f32) -> Result<()> {
        self.volume = volume;

        // AirPlay uses -144 to 0 dB scale
        // 0.0 -> -144 (mute), 1.0 -> 0 (full volume)
        let db_volume = if volume <= 0.0 {
            -144.0
        } else {
            (20.0 * volume.log10()).max(-144.0)
        };

        let cseq = self.next_cseq();
        let device_address = &self.device.address;
        let device_port = self.device.port;

        let body = format!("volume: {:.6}\r\n", db_volume);

        let request = format!(
            "SET_PARAMETER rtsp://{}:{}/audio RTSP/1.0\r\n\
             CSeq: {}\r\n\
             User-Agent: OuterTune/1.0\r\n\
             Content-Type: text/parameters\r\n\
             Content-Length: {}\r\n\
             \r\n\
             {}",
            device_address,
            device_port,
            cseq,
            body.len(),
            body
        );

        self.send_rtsp(&request).await?;
        let response = self.recv_rtsp_response().await?;

        if !response.status_line.contains("200") {
            log::warn!("Volume set response: {}", response.status_line);
        }

        Ok(())
    }

    /// Create an RTP packet for audio data
    fn create_rtp_packet(&self, seq: u16, timestamp: u32, payload: &[u8]) -> Vec<u8> {
        let mut packet = Vec::with_capacity(12 + payload.len());

        // RTP Header (12 bytes)
        // V=2, P=0, X=0, CC=0
        packet.push(0x80);

        // M bit (marker) + PT=96 (dynamic)
        // Set marker bit only on first packet after start/resume
        let marker = if self.first_packet { 0x80 } else { 0x00 };
        packet.push(marker | 96);

        // Sequence number (16 bits, big-endian)
        packet.push((seq >> 8) as u8);
        packet.push((seq & 0xFF) as u8);

        // Timestamp (32 bits, big-endian)
        packet.push((timestamp >> 24) as u8);
        packet.push((timestamp >> 16) as u8);
        packet.push((timestamp >> 8) as u8);
        packet.push((timestamp & 0xFF) as u8);

        // SSRC (32 bits) - use a fixed value
        packet.extend_from_slice(&[0x00, 0x00, 0x00, 0x01]);

        // Payload
        packet.extend_from_slice(payload);

        packet
    }

    /// Send RTSP request
    async fn send_rtsp(&mut self, request: &str) -> Result<()> {
        self.connection.write_all(request.as_bytes()).await?;
        self.connection.flush().await?;

        log::debug!("Sent RTSP: {}", request.lines().next().unwrap_or(""));
        Ok(())
    }

    /// Receive and parse RTSP response properly
    async fn recv_rtsp_response(&mut self) -> Result<RtspResponse> {
        let mut reader = BufReader::new(&mut self.connection);
        let mut headers = String::new();
        let mut status_line = String::new();

        // Read status line
        reader.read_line(&mut status_line).await?;
        let status_line = status_line.trim().to_string();

        // Read headers until empty line
        loop {
            let mut line = String::new();
            let bytes_read = reader.read_line(&mut line).await?;
            if bytes_read == 0 || line.trim().is_empty() {
                break;
            }
            headers.push_str(&line);
        }

        // Check for Content-Length and read body if present
        let mut body = Vec::new();
        if let Some(content_length) = Self::parse_content_length(&headers) {
            if content_length > 0 {
                body.resize(content_length, 0);
                reader.read_exact(&mut body).await?;
            }
        }

        log::debug!("Received RTSP: {}", status_line);

        Ok(RtspResponse {
            status_line,
            headers,
            body,
        })
    }

    /// Parse Content-Length header
    fn parse_content_length(headers: &str) -> Option<usize> {
        for line in headers.lines() {
            let lower = line.to_lowercase();
            if lower.starts_with("content-length:") {
                let value = line.split(':').nth(1)?.trim();
                return value.parse().ok();
            }
        }
        None
    }
}

/// Parsed RTSP response
struct RtspResponse {
    status_line: String,
    headers: String,
    body: Vec<u8>,
}

/// Start an AirPlay session with the given device
pub async fn start_session(
    device: AirPlayDevice,
    cmd_rx: mpsc::Receiver<AudioCommand>,
    bridge: Arc<AirPlayBridge>,
) -> Result<()> {
    let mut session = AirPlaySession::new(device).await?;
    session.setup().await?;

    // Spawn the session runner
    let bridge_clone = bridge.clone();
    tokio::spawn(async move {
        if let Err(e) = session.run(cmd_rx).await {
            log::error!("Session error: {}", e);
        }

        // Clear session info when done
        if let Ok(mut session_info) = bridge_clone.session_info.write() {
            *session_info = None;
        }
    });

    Ok(())
}
