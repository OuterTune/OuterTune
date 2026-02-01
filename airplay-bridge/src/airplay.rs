//! AirPlay 2 session management
//!
//! Handles RTSP communication, HomeKit pairing, and audio streaming setup
//! for AirPlay 2 devices.

use anyhow::{anyhow, Result};
use std::sync::Arc;
use std::sync::atomic::{AtomicU16, AtomicU32, Ordering};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;
use tokio::sync::mpsc;

use crate::audio::AudioEncoder;
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

/// AirPlay session state
#[derive(Debug, Clone, PartialEq)]
pub enum SessionState {
    Disconnected,
    Connecting,
    Pairing,
    Connected,
    Streaming,
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
}

impl AirPlaySession {
    /// Create a new AirPlay session
    pub async fn new(device: AirPlayDevice) -> Result<Self> {
        log::info!("Connecting to {} at {}:{}",
            device.name, device.address, device.port);

        // Establish TCP connection
        let addr = format!("{}:{}", device.address, device.port);
        let connection = TcpStream::connect(&addr).await?;

        log::info!("TCP connection established");

        Ok(Self {
            device,
            connection,
            cseq: AtomicU32::new(0),
            rtp_seq: AtomicU16::new(0),
            rtp_timestamp: AtomicU32::new(0),
            audio_encoder: AudioEncoder::new(),
            audio_socket: None,
            server_audio_port: 6000, // Default, will be updated from SETUP
            volume: 1.0,
        })
    }

    /// Perform the initial handshake and setup
    pub async fn setup(&mut self) -> Result<()> {
        // Perform RTSP handshake
        self.rtsp_options().await?;

        // For AirPlay 2, we need to do HomeKit pairing
        if self.device.supports_airplay2 {
            self.homekit_pair().await?;
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
        let response = self.recv_rtsp().await?;

        if !response.contains("RTSP/1.0 200") {
            return Err(anyhow!("OPTIONS failed: {}", response));
        }

        log::debug!("OPTIONS successful");
        Ok(())
    }

    /// Perform HomeKit transient pairing
    /// Note: This is a simplified implementation for transient pairing
    /// Full AirPlay 2 would need SRP protocol
    async fn homekit_pair(&mut self) -> Result<()> {
        log::info!("Starting HomeKit pairing (transient mode)");

        // For transient pairing, we use pair-pin-start
        // This allows temporary pairing without persistent credentials
        let cseq = self.next_cseq();

        // Request transient pairing
        let request = format!(
            "POST /pair-pin-start RTSP/1.0\r\n\
             CSeq: {}\r\n\
             User-Agent: OuterTune/1.0\r\n\
             Content-Length: 0\r\n\
             \r\n",
            cseq
        );

        self.send_rtsp(&request).await?;
        let response = self.recv_rtsp().await?;

        // 200 OK means transient pairing is allowed
        // 403 means device requires persistent pairing (PIN)
        if response.contains("RTSP/1.0 200") {
            log::info!("Transient pairing accepted");
        } else if response.contains("RTSP/1.0 403") {
            log::warn!("Device requires PIN pairing - attempting without encryption");
            // Some devices will still work with unencrypted audio
        } else {
            log::warn!("Pairing response: {}", response.lines().next().unwrap_or(""));
            // Continue anyway - some devices don't require explicit pairing
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
        let response = self.recv_rtsp().await?;

        if !response.contains("RTSP/1.0 200") {
            log::warn!("ANNOUNCE response: {}", response.lines().next().unwrap_or(""));
            // Continue anyway, some devices don't require ANNOUNCE
        }

        // SETUP the audio transport
        let cseq = self.next_cseq();

        let request = format!(
            "SETUP rtsp://{}:{}/audio RTSP/1.0\r\n\
             CSeq: {}\r\n\
             User-Agent: OuterTune/1.0\r\n\
             Transport: RTP/AVP/UDP;unicast;mode=record;control_port=6001;timing_port=6002\r\n\
             \r\n",
            device_address,
            device_port,
            cseq
        );

        self.send_rtsp(&request).await?;
        let response = self.recv_rtsp().await?;

        if !response.contains("RTSP/1.0 200") {
            return Err(anyhow!("SETUP failed: {}", response.lines().next().unwrap_or("")));
        }

        // Parse server port from response
        self.server_audio_port = self.parse_server_port(&response).unwrap_or(6000);
        log::info!("Server audio port: {}", self.server_audio_port);

        // Create UDP socket for audio data
        let socket = tokio::net::UdpSocket::bind("0.0.0.0:0").await?;
        socket.connect(format!("{}:{}", device_address, self.server_audio_port)).await?;
        self.audio_socket = Some(socket);

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
        let response = self.recv_rtsp().await?;

        if !response.contains("RTSP/1.0 200") {
            log::warn!("RECORD response: {}", response.lines().next().unwrap_or(""));
        }

        log::info!("Audio streaming setup complete");
        Ok(())
    }

    /// Parse server_port from Transport header
    fn parse_server_port(&self, response: &str) -> Option<u16> {
        for line in response.lines() {
            if line.to_lowercase().starts_with("transport:") {
                // Look for server_port=XXXX
                for part in line.split(';') {
                    let part = part.trim();
                    if part.starts_with("server_port=") {
                        if let Some(port_str) = part.strip_prefix("server_port=") {
                            // May be port or port-port range
                            let port = port_str.split('-').next()?;
                            return port.parse().ok();
                        }
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

        // Process audio in frames
        let frame_size = 352 * channels as usize * 2; // 352 samples * channels * 2 bytes

        for chunk in pcm_data.chunks(frame_size) {
            let frame_data = if chunk.len() < frame_size {
                // Pad short chunk
                let mut padded = vec![0u8; frame_size];
                padded[..chunk.len()].copy_from_slice(chunk);
                padded
            } else {
                chunk.to_vec()
            };

            // Encode PCM to ALAC
            let alac_data = self.audio_encoder.encode_alac(&frame_data, sample_rate, channels)?;

            // Create RTP packet
            let seq = self.next_rtp_seq();
            let timestamp = self.rtp_timestamp.load(Ordering::SeqCst);
            let rtp_packet = self.create_rtp_packet(seq, timestamp, &alac_data);

            // Advance timestamp by samples per frame
            self.advance_rtp_timestamp(352);

            // Send via UDP - unwrap is safe because we checked is_none above
            let socket = self.audio_socket.as_ref().unwrap();
            socket.send(&rtp_packet).await?;
        }

        Ok(())
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
        let response = self.recv_rtsp().await?;

        if !response.contains("RTSP/1.0 200") {
            log::warn!("Volume set response: {}", response.lines().next().unwrap_or(""));
        }

        Ok(())
    }

    /// Create an RTP packet for audio data
    fn create_rtp_packet(&self, seq: u16, timestamp: u32, payload: &[u8]) -> Vec<u8> {
        let mut packet = Vec::with_capacity(12 + payload.len());

        // RTP Header (12 bytes)
        // V=2, P=0, X=0, CC=0
        packet.push(0x80);
        // M=1, PT=96 (dynamic)
        packet.push(0xE0);

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

    /// Receive RTSP response
    async fn recv_rtsp(&mut self) -> Result<String> {
        let mut buffer = vec![0u8; 4096];
        let n = self.connection.read(&mut buffer).await?;

        let response = String::from_utf8_lossy(&buffer[..n]).to_string();
        log::debug!("Received RTSP: {}", response.lines().next().unwrap_or(""));

        Ok(response)
    }
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
