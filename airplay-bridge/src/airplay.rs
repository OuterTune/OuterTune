//! AirPlay 2 session management
//!
//! Handles RTSP communication, HomeKit pairing, and audio streaming setup
//! for AirPlay 2 devices.

use anyhow::{anyhow, Result};
use base64::{Engine as _, engine::general_purpose::STANDARD as BASE64};
use chacha20poly1305::{
    aead::{Aead, KeyInit},
    ChaCha20Poly1305, Nonce,
};
use hkdf::Hkdf;
use sha2::Sha256;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicU16, AtomicU32, AtomicU64, Ordering};
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::{TcpStream, UdpSocket};
use tokio::sync::mpsc;
use x25519_dalek::{EphemeralSecret, PublicKey};

use crate::audio::{AudioEncoder, ensure_44100, AIRPLAY_SAMPLE_RATE};
use crate::discovery::AirPlayDevice;
use crate::AirPlayBridge;
use crate::{send_log_to_kotlin, LOG_LEVEL_INFO, LOG_LEVEL_WARN, LOG_LEVEL_ERROR};

const RTSP_TAG: &str = "RTSP";

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
    /// Our local IP address (for SDP)
    local_address: String,
    /// RTSP CSeq counter
    cseq: AtomicU32,
    /// RTP sequence number
    rtp_seq: AtomicU16,
    /// RTP timestamp
    rtp_timestamp: AtomicU32,
    /// RTP SSRC (randomly generated per session)
    ssrc: u32,
    /// Client instance ID (for Apple-Challenge)
    client_instance: String,
    /// Audio encoder
    audio_encoder: AudioEncoder,
    /// Audio data socket
    audio_socket: Option<UdpSocket>,
    /// Control socket (for retransmit requests)
    control_socket: Option<UdpSocket>,
    /// Server audio port (parsed from SETUP response)
    server_audio_port: u16,
    /// Session ID from SETUP response
    session_id: String,
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
    /// Last time we sent a keepalive
    last_keepalive: std::time::Instant,
    /// Flag to signal timing handler to stop
    timing_stop_flag: Option<Arc<AtomicBool>>,
}

impl AirPlaySession {
    /// Create a new AirPlay session
    pub async fn new(device: AirPlayDevice) -> Result<Self> {
        let msg = format!("Connecting to {} at {}:{}", device.name, device.address, device.port);
        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &msg);

        // Establish TCP connection with timeout
        let addr = format!("{}:{}", device.address, device.port);
        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Opening TCP connection to {}", addr));

        let connection = tokio::time::timeout(
            std::time::Duration::from_secs(10),
            TcpStream::connect(&addr)
        ).await
            .map_err(|_| {
                send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, "TCP connection timeout (10s)");
                anyhow!("Connection timeout")
            })?
            .map_err(|e| {
                send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, &format!("TCP connection failed: {}", e));
                anyhow!("Connection failed: {}", e)
            })?;

        // Get our local IP from the connection
        let local_addr = connection.local_addr()
            .map(|a| a.ip().to_string())
            .unwrap_or_else(|_| "0.0.0.0".to_string());
        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("TCP connected, local IP: {}", local_addr));

        // Generate random SSRC and client instance for this session
        let ssrc: u32 = rand::random();
        let client_instance = format!("{:016X}", rand::random::<u64>());
        log::debug!("Generated SSRC: {:#010x}, Client-Instance: {}", ssrc, client_instance);

        Ok(Self {
            device,
            connection,
            local_address: local_addr,
            cseq: AtomicU32::new(0),
            rtp_seq: AtomicU16::new(0),
            rtp_timestamp: AtomicU32::new(0),
            ssrc,
            client_instance,
            audio_encoder: AudioEncoder::new(),
            audio_socket: None,
            control_socket: None,
            server_audio_port: 6000,
            session_id: String::from("1"),
            volume: 1.0,
            encryption_enabled: false,
            encryption_key: None,
            nonce_counter: AtomicU64::new(0),
            first_packet: true,
            last_keepalive: std::time::Instant::now(),
            timing_stop_flag: None,
        })
    }

    /// Perform the initial handshake and setup
    pub async fn setup(&mut self) -> Result<()> {
        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Starting RTSP handshake...");

        // Perform RTSP handshake
        self.rtsp_options().await?;

        // For AirPlay 2 devices, try HomeKit pairing (but don't fail if it doesn't work)
        // Third-party speakers like Ubiquiti don't require this
        if self.device.supports_airplay2 {
            match self.homekit_pair().await {
                Ok(_) => {
                    send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "HomeKit pairing successful");
                }
                Err(e) => {
                    send_log_to_kotlin(LOG_LEVEL_WARN, RTSP_TAG, &format!("HomeKit pairing skipped: {}", e));
                    // This is fine for third-party AirPlay speakers
                }
            }
        } else {
            send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "AirPlay 1 device - skipping HomeKit pairing");
        }

        // Setup audio streaming
        self.rtsp_setup().await?;

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Session established successfully!");
        Ok(())
    }

    /// Run the session, processing commands from the channel
    pub async fn run(&mut self, mut cmd_rx: mpsc::Receiver<AudioCommand>) -> Result<()> {
        log::info!("Session running, waiting for commands");

        // Keepalive interval (15 seconds)
        const KEEPALIVE_INTERVAL: std::time::Duration = std::time::Duration::from_secs(15);

        loop {
            // Use timeout to allow periodic keepalive checks
            match tokio::time::timeout(std::time::Duration::from_secs(1), cmd_rx.recv()).await {
                Ok(Some(cmd)) => {
                    match cmd {
                        AudioCommand::SendAudio { data, sample_rate, channels } => {
                            if let Err(e) = self.send_audio_internal(&data, sample_rate, channels).await {
                                log::warn!("Failed to send audio: {}", e);
                            }
                            // Reset keepalive timer on audio activity
                            self.last_keepalive = std::time::Instant::now();
                        }
                        AudioCommand::SetVolume(vol) => {
                            if let Err(e) = self.set_volume_internal(vol).await {
                                log::warn!("Failed to set volume: {}", e);
                            }
                            self.last_keepalive = std::time::Instant::now();
                        }
                        AudioCommand::Disconnect => {
                            log::info!("Disconnect command received");
                            break;
                        }
                    }
                }
                Ok(None) => {
                    // Channel closed
                    log::info!("Command channel closed");
                    break;
                }
                Err(_) => {
                    // Timeout - check if we need to send keepalive
                    if self.last_keepalive.elapsed() >= KEEPALIVE_INTERVAL {
                        if let Err(e) = self.send_keepalive().await {
                            log::warn!("Keepalive failed: {}", e);
                            // Don't break on keepalive failure, connection might recover
                        }
                        self.last_keepalive = std::time::Instant::now();
                    }
                }
            }
        }

        // Signal timing handler to stop
        if let Some(ref stop_flag) = self.timing_stop_flag {
            stop_flag.store(true, Ordering::Relaxed);
            send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Signaling timing handler to stop");
        }

        // Send TEARDOWN
        let _ = self.rtsp_teardown().await;

        log::info!("Session ended");
        Ok(())
    }

    /// Send a keepalive OPTIONS request to maintain the connection
    async fn send_keepalive(&mut self) -> Result<()> {
        log::debug!("Sending keepalive");

        let cseq = self.next_cseq();
        let request = format!(
            "OPTIONS * RTSP/1.0\r\n\
CSeq: {}\r\n\
User-Agent: iTunes/12.0 (Macintosh)\r\n\
Session: {}\r\n\
\r\n",
            cseq,
            self.session_id
        );

        self.send_rtsp(&request).await?;

        // Try to read response with timeout
        match tokio::time::timeout(
            std::time::Duration::from_secs(5),
            self.recv_rtsp_response()
        ).await {
            Ok(Ok(response)) => {
                if response.status_line.contains("200") {
                    log::debug!("Keepalive successful");
                } else {
                    log::warn!("Keepalive response: {}", response.status_line);
                }
            }
            Ok(Err(e)) => {
                log::warn!("Keepalive read error: {}", e);
            }
            Err(_) => {
                log::warn!("Keepalive response timeout");
            }
        }

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

    /// Send RTSP OPTIONS request with Apple-Challenge for authentication
    async fn rtsp_options(&mut self) -> Result<()> {
        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Sending OPTIONS request...");

        let cseq = self.next_cseq();

        // Generate Apple-Challenge (16 random bytes, base64 encoded)
        let challenge_bytes: [u8; 16] = rand::random();
        let apple_challenge = BASE64.encode(challenge_bytes);

        let request = format!(
            "OPTIONS * RTSP/1.0\r\n\
CSeq: {}\r\n\
User-Agent: iTunes/12.0 (Macintosh)\r\n\
Client-Instance: {}\r\n\
DACP-ID: {}\r\n\
Active-Remote: {}\r\n\
Apple-Challenge: {}\r\n\
\r\n",
            cseq,
            self.client_instance,
            self.client_instance,
            self.ssrc,
            apple_challenge
        );

        self.send_rtsp(&request).await?;

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Waiting for OPTIONS response...");
        let response = match tokio::time::timeout(
            std::time::Duration::from_secs(5),
            self.recv_rtsp_response()
        ).await {
            Ok(Ok(r)) => r,
            Ok(Err(e)) => {
                send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, &format!("OPTIONS read error: {}", e));
                return Err(anyhow!("OPTIONS read error: {}", e));
            }
            Err(_) => {
                send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, "OPTIONS timeout (5s) - speaker not responding");
                return Err(anyhow!("OPTIONS timeout"));
            }
        };

        if !response.status_line.contains("200") {
            send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, &format!("OPTIONS failed: {}", response.status_line));
            return Err(anyhow!("OPTIONS failed: {}", response.status_line));
        }

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "OPTIONS successful");
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
        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Setting up audio stream...");

        let device_address = self.device.address.clone();
        let device_port = self.device.port;

        // Bind UDP sockets first to get our local ports
        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Binding UDP sockets...");
        let audio_socket = tokio::net::UdpSocket::bind("0.0.0.0:0").await?;
        let audio_port = audio_socket.local_addr()?.port();

        let control_socket = tokio::net::UdpSocket::bind("0.0.0.0:0").await?;
        let control_port = control_socket.local_addr()?.port();

        let timing_socket = tokio::net::UdpSocket::bind("0.0.0.0:0").await?;
        let timing_port = timing_socket.local_addr()?.port();

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Local ports - audio:{}, ctrl:{}, timing:{}", audio_port, control_port, timing_port));

        // ANNOUNCE the audio format (AirPlay 1 style - ALAC)
        // Use OUR local IP in the SDP (not the device's IP)
        // fmtp params: frameLength maxBitRate bitDepth sampleRate ...
        let sdp = format!(
            "v=0\r\n\
o=iTunes 1 1 IN IP4 {}\r\n\
s=iTunes\r\n\
c=IN IP4 {}\r\n\
t=0 0\r\n\
m=audio 0 RTP/AVP 96\r\n\
a=rtpmap:96 AppleLossless\r\n\
a=fmtp:96 352 0 16 40 10 14 2 255 0 0 44100\r\n",
            self.local_address,
            self.local_address
        );

        let cseq = self.next_cseq();

        let request = format!(
            "ANNOUNCE rtsp://{}:{}/{} RTSP/1.0\r\n\
CSeq: {}\r\n\
Content-Type: application/sdp\r\n\
Content-Length: {}\r\n\
User-Agent: iTunes/12.0 (Macintosh)\r\n\
Client-Instance: {}\r\n\
\r\n\
{}",
            device_address,
            device_port,
            self.ssrc,
            cseq,
            sdp.len(),
            self.client_instance,
            sdp
        );

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Sending ANNOUNCE (SDP audio format)...");
        self.send_rtsp(&request).await?;

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Waiting for ANNOUNCE response...");
        let response = match tokio::time::timeout(
            std::time::Duration::from_secs(5),
            self.recv_rtsp_response()
        ).await {
            Ok(Ok(r)) => r,
            Ok(Err(e)) => {
                send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, &format!("ANNOUNCE read error: {}", e));
                return Err(anyhow!("ANNOUNCE read error: {}", e));
            }
            Err(_) => {
                send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, "ANNOUNCE timeout (5s)");
                return Err(anyhow!("ANNOUNCE timeout"));
            }
        };

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("ANNOUNCE response: {}", response.status_line));

        if response.status_line.contains("401") || response.status_line.contains("403") {
            send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, &format!("Auth required: {}", response.status_line));
            return Err(anyhow!("Device requires authentication ({})", response.status_line));
        }

        if !response.status_line.contains("200") {
            send_log_to_kotlin(LOG_LEVEL_WARN, RTSP_TAG, &format!("ANNOUNCE returned {}, trying SETUP anyway", response.status_line));
        }

        // SETUP the audio transport
        let cseq = self.next_cseq();

        let request = format!(
            "SETUP rtsp://{}:{}/{} RTSP/1.0\r\n\
CSeq: {}\r\n\
Transport: RTP/AVP/UDP;unicast;interleaved=0-1;mode=record;control_port={};timing_port={}\r\n\
User-Agent: iTunes/12.0 (Macintosh)\r\n\
Client-Instance: {}\r\n\
\r\n",
            device_address,
            device_port,
            self.ssrc,
            cseq,
            control_port,
            timing_port,
            self.client_instance
        );

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Sending SETUP (transport config)...");
        self.send_rtsp(&request).await?;

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Waiting for SETUP response...");
        let response = match tokio::time::timeout(
            std::time::Duration::from_secs(5),
            self.recv_rtsp_response()
        ).await {
            Ok(Ok(r)) => r,
            Ok(Err(e)) => {
                send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, &format!("SETUP read error: {}", e));
                return Err(anyhow!("SETUP read error: {}", e));
            }
            Err(_) => {
                send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, "SETUP timeout (5s)");
                return Err(anyhow!("SETUP timeout"));
            }
        };

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("SETUP response: {}", response.status_line));

        if !response.status_line.contains("200") {
            send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, &format!("SETUP failed: {}", response.status_line));
            return Err(anyhow!("SETUP failed: {}", response.status_line));
        }

        // Parse server port from response
        self.server_audio_port = self.parse_server_port(&response.headers).unwrap_or(6000);
        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Server audio port: {}", self.server_audio_port));

        // Parse Session ID from response (for subsequent requests)
        if let Some(session_id) = self.parse_session_id(&response.headers) {
            send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Session ID: {}", session_id));
            self.session_id = session_id;
        }

        // Connect UDP socket to server
        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Connecting UDP to {}:{}", device_address, self.server_audio_port));
        audio_socket.connect(format!("{}:{}", device_address, self.server_audio_port)).await?;
        self.audio_socket = Some(audio_socket);

        // Parse server timing port from Transport header and start timing sync handler
        let server_timing_port = self.parse_timing_port(&response.headers);

        // Wrap timing socket in Arc for sharing with timing handler
        let timing_socket = Arc::new(timing_socket);

        if let Some(tp) = server_timing_port {
            send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Server timing port: {}", tp));
            // Create stop flag for timing handler
            let stop_flag = Arc::new(AtomicBool::new(false));
            self.timing_stop_flag = Some(Arc::clone(&stop_flag));

            // Spawn timing sync handler with shared socket reference
            let timing_socket_clone = Arc::clone(&timing_socket);
            let device_addr = device_address.clone();
            tokio::spawn(async move {
                Self::run_timing_handler(timing_socket_clone, device_addr, tp, stop_flag).await;
            });
            send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Timing sync handler started");
        } else {
            send_log_to_kotlin(LOG_LEVEL_WARN, RTSP_TAG, "No server timing port - timing sync disabled");
        }

        // Store control socket (timing socket is managed by the handler now)
        self.control_socket = Some(control_socket);

        // Start playback with RECORD
        let cseq = self.next_cseq();
        let seq = self.rtp_seq.load(Ordering::SeqCst);
        let rtptime = self.rtp_timestamp.load(Ordering::SeqCst);

        // RTP-Info header should include URL for some receivers
        let request = format!(
            "RECORD rtsp://{}:{}/{} RTSP/1.0\r\n\
CSeq: {}\r\n\
Range: npt=0-\r\n\
RTP-Info: url=rtsp://{}:{}/{};seq={};rtptime={}\r\n\
User-Agent: iTunes/12.0 (Macintosh)\r\n\
Session: {}\r\n\
\r\n",
            device_address,
            device_port,
            self.ssrc,
            cseq,
            device_address,
            device_port,
            self.ssrc,
            seq,
            rtptime,
            self.session_id
        );

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Sending RECORD (start playback)...");
        self.send_rtsp(&request).await?;

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Waiting for RECORD response...");
        let response = match tokio::time::timeout(
            std::time::Duration::from_secs(5),
            self.recv_rtsp_response()
        ).await {
            Ok(Ok(r)) => r,
            Ok(Err(e)) => {
                send_log_to_kotlin(LOG_LEVEL_WARN, RTSP_TAG, &format!("RECORD read error (may be OK): {}", e));
                // Some devices don't respond to RECORD, which is fine
                return Ok(());
            }
            Err(_) => {
                send_log_to_kotlin(LOG_LEVEL_WARN, RTSP_TAG, "RECORD timeout (may be OK for some devices)");
                // Timeout is OK for some devices
                return Ok(());
            }
        };

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("RECORD response: {}", response.status_line));

        if !response.status_line.contains("200") {
            send_log_to_kotlin(LOG_LEVEL_WARN, RTSP_TAG, &format!("RECORD returned: {} (continuing)", response.status_line));
        }

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, "Audio streaming setup complete!");
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

    /// Parse Session ID from SETUP response header
    fn parse_session_id(&self, headers: &str) -> Option<String> {
        for line in headers.lines() {
            let lower = line.to_lowercase();
            if lower.starts_with("session:") {
                // Session header format: "Session: <id>[;timeout=<seconds>]"
                let value = line.split(':').nth(1)?.trim();
                // Extract just the ID part (before semicolon if present)
                let id = value.split(';').next()?.trim();
                if !id.is_empty() {
                    return Some(id.to_string());
                }
            }
        }
        None
    }

    /// Parse timing_port from Transport header
    fn parse_timing_port(&self, headers: &str) -> Option<u16> {
        for line in headers.lines() {
            let lower = line.to_lowercase();
            if lower.starts_with("transport:") {
                // Look for timing_port=XXXX
                for part in line.split(';') {
                    let part = part.trim();
                    if let Some(port_str) = part.strip_prefix("timing_port=")
                        .or_else(|| part.strip_prefix("Timing_port="))
                    {
                        return port_str.trim().parse().ok();
                    }
                }
            }
        }
        None
    }

    /// Handle timing sync requests from AirPlay receiver
    /// The receiver sends timing queries to synchronize clocks (NTP-like protocol)
    async fn run_timing_handler(socket: Arc<UdpSocket>, device_addr: String, device_port: u16, stop_flag: Arc<AtomicBool>) {
        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Timing handler listening for {}:{}", device_addr, device_port));

        let mut buf = [0u8; 32];
        let mut timing_requests_handled = 0u64;

        loop {
            // Check if we should stop
            if stop_flag.load(Ordering::Relaxed) {
                send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Timing handler stopping for {}", device_addr));
                break;
            }

            match tokio::time::timeout(
                std::time::Duration::from_secs(5),  // Shorter timeout to check stop flag more often
                socket.recv_from(&mut buf)
            ).await {
                Ok(Ok((len, src))) => {
                    if len >= 8 {
                        // Check if this is a timing request (payload type 0xD2 = 210 = 82 | 0x80)
                        if buf[1] == 0xD2 || buf[1] == 0x52 {
                            // Build timing response
                            let now = Self::get_ntp_time();

                            let mut response = [0u8; 32];
                            response[0] = 0x80; // RTP v2
                            response[1] = 0xD3; // Timing response (83 | 0x80)
                            response[2] = buf[2]; // Copy sequence number
                            response[3] = buf[3];
                            response[4..8].copy_from_slice(&[0, 0, 0, 0]); // Padding

                            // Reference time (copy from request bytes 24-31)
                            if len >= 32 {
                                response[8..16].copy_from_slice(&buf[24..32]);
                            }

                            // Receive time (when we got the request)
                            response[16..24].copy_from_slice(&now);

                            // Send time (now, same as receive for simplicity)
                            response[24..32].copy_from_slice(&now);

                            if let Err(e) = socket.send_to(&response, src).await {
                                log::warn!("Failed to send timing response: {}", e);
                            } else {
                                timing_requests_handled += 1;
                                if timing_requests_handled == 1 || timing_requests_handled % 100 == 0 {
                                    send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG,
                                        &format!("Timing sync #{} for {}", timing_requests_handled, device_addr));
                                }
                            }
                        }
                    }
                }
                Ok(Err(e)) => {
                    send_log_to_kotlin(LOG_LEVEL_WARN, RTSP_TAG, &format!("Timing socket error: {}", e));
                    // Don't break on error - continue trying
                }
                Err(_) => {
                    // Timeout - continue to check stop flag
                }
            }
        }

        send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Timing handler exited for {}", device_addr));
    }

    /// Get current time as NTP timestamp (8 bytes: 4 bytes seconds + 4 bytes fraction)
    fn get_ntp_time() -> [u8; 8] {
        use std::time::{SystemTime, UNIX_EPOCH};

        // NTP epoch is 1900-01-01, Unix epoch is 1970-01-01
        // Difference is 70 years = 2208988800 seconds
        const NTP_UNIX_OFFSET: u64 = 2208988800;

        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default();

        let secs = now.as_secs() + NTP_UNIX_OFFSET;
        let frac = ((now.subsec_nanos() as u64) << 32) / 1_000_000_000;

        let mut result = [0u8; 8];
        result[0..4].copy_from_slice(&(secs as u32).to_be_bytes());
        result[4..8].copy_from_slice(&(frac as u32).to_be_bytes());
        result
    }

    /// Send RTSP TEARDOWN
    async fn rtsp_teardown(&mut self) -> Result<()> {
        let cseq = self.next_cseq();
        let device_address = &self.device.address;
        let device_port = self.device.port;

        let request = format!(
            "TEARDOWN rtsp://{}:{}/{} RTSP/1.0\r\n\
CSeq: {}\r\n\
User-Agent: iTunes/12.0 (Macintosh)\r\n\
Session: {}\r\n\
\r\n",
            device_address,
            device_port,
            self.ssrc,
            cseq,
            self.session_id
        );

        let _ = self.send_rtsp(&request).await;
        Ok(())
    }

    /// Send audio data to the AirPlay device
    async fn send_audio_internal(&mut self, pcm_data: &[u8], sample_rate: u32, channels: u32) -> Result<()> {
        if self.audio_socket.is_none() {
            log::warn!("Audio socket not initialized for device: {}", self.device.name);
            return Err(anyhow!("Audio socket not initialized"));
        }

        // Log first packet and then every 100th packet
        let seq = self.rtp_seq.load(Ordering::SeqCst);
        if seq == 0 || seq % 100 == 0 {
            log::info!("Sending audio to {} - seq: {}, pcm_len: {}, rate: {}",
                self.device.name, seq, pcm_data.len(), sample_rate);
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

            // Log first packet sent
            if self.first_packet {
                log::info!("First audio packet sent to {} (port {}), rtp_len: {}",
                    self.device.name, self.server_audio_port, rtp_packet.len());
            }

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

        // SSRC (32 bits, big-endian) - randomly generated per session
        packet.push((self.ssrc >> 24) as u8);
        packet.push((self.ssrc >> 16) as u8);
        packet.push((self.ssrc >> 8) as u8);
        packet.push((self.ssrc & 0xFF) as u8);

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
    let device_id = device.id.clone();
    let device_name = device.name.clone();

    send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Starting session for {}", device_name));

    let mut session = match AirPlaySession::new(device).await {
        Ok(s) => s,
        Err(e) => {
            send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, &format!("Session create failed: {}", e));
            return Err(e);
        }
    };

    if let Err(e) = session.setup().await {
        send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, &format!("Session setup failed: {}", e));
        return Err(e);
    }

    send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Session ready for {}", device_name));

    // Spawn the session runner
    let bridge_clone = bridge.clone();
    let device_name_clone = device_name.clone();
    tokio::spawn(async move {
        if let Err(e) = session.run(cmd_rx).await {
            send_log_to_kotlin(LOG_LEVEL_ERROR, RTSP_TAG, &format!("Session error for {}: {}", device_name_clone, e));
        }

        // Remove session from sessions map when done
        if let Ok(mut sessions) = bridge_clone.sessions.write() {
            sessions.remove(&device_id);
            send_log_to_kotlin(LOG_LEVEL_INFO, RTSP_TAG, &format!("Session ended for {}", device_name_clone));
        }
    });

    Ok(())
}
