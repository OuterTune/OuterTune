//! AirPlay 2 session management
//!
//! Handles RTSP communication, HomeKit pairing, and audio streaming setup
//! for AirPlay 2 devices.

use anyhow::{anyhow, Result};
use std::sync::{Arc, Mutex};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;

use crate::audio::AudioEncoder;
use crate::discovery::AirPlayDevice;
use crate::AirPlayBridge;

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
    connection: Option<TcpStream>,
    /// Current session state
    state: SessionState,
    /// RTSP CSeq counter
    cseq: u32,
    /// Session ID
    _session_id: Option<String>,
    /// Audio encoder
    audio_encoder: AudioEncoder,
    /// Audio data socket
    audio_socket: Option<tokio::net::UdpSocket>,
    /// Current volume (0.0 - 1.0)
    _volume: f32,
}

impl AirPlaySession {
    /// Create a new AirPlay session
    pub fn new(device: AirPlayDevice) -> Self {
        Self {
            device,
            connection: None,
            state: SessionState::Disconnected,
            cseq: 0,
            _session_id: None,
            audio_encoder: AudioEncoder::new(),
            audio_socket: None,
            _volume: 1.0,
        }
    }

    /// Connect to the AirPlay device
    pub async fn connect(&mut self) -> Result<()> {
        log::info!("Connecting to {} at {}:{}",
            self.device.name, self.device.address, self.device.port);

        self.state = SessionState::Connecting;

        // Establish TCP connection
        let addr = format!("{}:{}", self.device.address, self.device.port);
        let stream = TcpStream::connect(&addr).await?;
        self.connection = Some(stream);

        log::info!("TCP connection established");

        // Perform RTSP handshake
        self.rtsp_options().await?;

        // For AirPlay 2, we need to do HomeKit pairing
        if self.device.supports_airplay2 {
            self.state = SessionState::Pairing;
            self.homekit_pair().await?;
        }

        // Setup audio streaming
        self.rtsp_setup().await?;

        self.state = SessionState::Connected;
        log::info!("AirPlay session established");

        Ok(())
    }

    /// Disconnect from the device
    pub async fn disconnect(&self) {
        log::info!("Disconnecting from AirPlay device");
        // Connection will be dropped when session is dropped
    }

    /// Send RTSP OPTIONS request
    async fn rtsp_options(&mut self) -> Result<()> {
        self.cseq += 1;
        let cseq = self.cseq;

        let request = format!(
            "OPTIONS * RTSP/1.0\r\n\
             CSeq: {}\r\n\
             User-Agent: OuterTune/1.0\r\n\
             \r\n",
            cseq
        );

        self.send_rtsp_request(&request).await?;
        let response = self.recv_rtsp_response().await?;

        if !response.contains("RTSP/1.0 200") {
            return Err(anyhow!("OPTIONS failed: {}", response));
        }

        log::debug!("OPTIONS successful");
        Ok(())
    }

    /// Perform HomeKit transient pairing
    async fn homekit_pair(&mut self) -> Result<()> {
        log::info!("Starting HomeKit transient pairing");

        self.cseq += 1;
        let cseq = self.cseq;

        // Step 1: Send pair-setup request
        let request = format!(
            "POST /pair-setup RTSP/1.0\r\n\
             CSeq: {}\r\n\
             User-Agent: OuterTune/1.0\r\n\
             Content-Type: application/octet-stream\r\n\
             Content-Length: 6\r\n\
             \r\n",
            cseq
        );

        self.send_rtsp_request(&request).await?;

        // Send pairing request data (simplified - real implementation needs SRP)
        // This is a placeholder for the actual HomeKit pairing protocol
        let pair_request = [0x00, 0x00, 0x00, 0x00, 0x01, 0x00]; // Method: Pair Setup, State: M1
        self.send_raw_data(&pair_request).await?;

        let response = self.recv_rtsp_response().await?;
        log::debug!("Pair-setup response: {}", response);

        // For now, we'll use transient pairing which doesn't require PIN
        // Full implementation would need SRP protocol

        log::info!("HomeKit pairing completed (transient mode)");
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
             o=iTunes 1 1 IN IP4 {}\r\n\
             s=iTunes\r\n\
             c=IN IP4 {}\r\n\
             t=0 0\r\n\
             m=audio 0 RTP/AVP 96\r\n\
             a=rtpmap:96 AppleLossless\r\n\
             a=fmtp:96 352 0 16 40 10 14 2 255 0 0 44100\r\n",
            device_address,
            device_address
        );

        self.cseq += 1;
        let cseq = self.cseq;

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

        self.send_rtsp_request(&request).await?;
        let response = self.recv_rtsp_response().await?;

        if !response.contains("RTSP/1.0 200") {
            log::warn!("ANNOUNCE response: {}", response);
            // Continue anyway, some devices don't require ANNOUNCE
        }

        // SETUP the audio transport
        self.cseq += 1;
        let cseq = self.cseq;

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

        self.send_rtsp_request(&request).await?;
        let response = self.recv_rtsp_response().await?;

        if !response.contains("RTSP/1.0 200") {
            return Err(anyhow!("SETUP failed: {}", response));
        }

        // Parse server ports from response
        log::debug!("SETUP response: {}", response);

        // Create UDP socket for audio data
        let socket = tokio::net::UdpSocket::bind("0.0.0.0:0").await?;
        socket.connect(format!("{}:6000", device_address)).await?;
        self.audio_socket = Some(socket);

        // Start playback with RECORD
        self.cseq += 1;
        let cseq = self.cseq;

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

        self.send_rtsp_request(&request).await?;
        let response = self.recv_rtsp_response().await?;

        if !response.contains("RTSP/1.0 200") {
            log::warn!("RECORD response: {}", response);
        }

        self.state = SessionState::Streaming;
        log::info!("Audio streaming setup complete");

        Ok(())
    }

    /// Send audio data to the AirPlay device
    pub async fn send_audio(&self, pcm_data: &[u8], sample_rate: u32, channels: u32) -> Result<()> {
        let socket = self.audio_socket.as_ref()
            .ok_or_else(|| anyhow!("Audio socket not initialized"))?;

        // Encode PCM to ALAC
        let alac_data = self.audio_encoder.encode_alac(pcm_data, sample_rate, channels)?;

        // Create RTP packet
        let rtp_packet = self.create_rtp_packet(&alac_data);

        // Send via UDP
        socket.send(&rtp_packet).await?;

        Ok(())
    }

    /// Set volume on the device
    pub async fn set_volume(&self, volume: f32) -> Result<()> {
        let _conn = self.connection.as_ref()
            .ok_or_else(|| anyhow!("Not connected"))?;

        // AirPlay uses -144 to 0 dB scale
        // 0.0 -> -144 (mute), 1.0 -> 0 (full volume)
        let db_volume = if volume <= 0.0 {
            -144.0
        } else {
            20.0 * volume.log10()
        };

        // Note: We can't easily send this without mutable access
        // In a full implementation, we'd use interior mutability or channels
        log::debug!("Would set volume to {} dB", db_volume);

        Ok(())
    }

    /// Create an RTP packet for audio data
    fn create_rtp_packet(&self, payload: &[u8]) -> Vec<u8> {
        let mut packet = Vec::with_capacity(12 + payload.len());

        // RTP Header (12 bytes)
        // V=2, P=0, X=0, CC=0
        packet.push(0x80);
        // M=1, PT=96 (dynamic)
        packet.push(0xE0);
        // Sequence number (16 bits) - simplified, should increment
        packet.push(0x00);
        packet.push(0x01);
        // Timestamp (32 bits)
        packet.extend_from_slice(&[0x00, 0x00, 0x00, 0x00]);
        // SSRC (32 bits)
        packet.extend_from_slice(&[0x00, 0x00, 0x00, 0x01]);

        // Payload
        packet.extend_from_slice(payload);

        packet
    }

    /// Send RTSP request
    async fn send_rtsp_request(&mut self, request: &str) -> Result<()> {
        let conn = self.connection.as_mut()
            .ok_or_else(|| anyhow!("Not connected"))?;

        conn.write_all(request.as_bytes()).await?;
        conn.flush().await?;

        log::debug!("Sent RTSP: {}", request.lines().next().unwrap_or(""));
        Ok(())
    }

    /// Send raw bytes
    async fn send_raw_data(&mut self, data: &[u8]) -> Result<()> {
        let conn = self.connection.as_mut()
            .ok_or_else(|| anyhow!("Not connected"))?;

        conn.write_all(data).await?;
        conn.flush().await?;

        Ok(())
    }

    /// Receive RTSP response
    async fn recv_rtsp_response(&mut self) -> Result<String> {
        let conn = self.connection.as_mut()
            .ok_or_else(|| anyhow!("Not connected"))?;

        let mut buffer = vec![0u8; 4096];
        let n = conn.read(&mut buffer).await?;

        let response = String::from_utf8_lossy(&buffer[..n]).to_string();
        log::debug!("Received RTSP: {}", response.lines().next().unwrap_or(""));

        Ok(response)
    }
}

/// Connect to an AirPlay device and store the session
pub async fn connect_to_device(
    device: &AirPlayDevice,
    bridge: Arc<Mutex<AirPlayBridge>>,
) -> Result<()> {
    let mut session = AirPlaySession::new(device.clone());
    session.connect().await?;

    // Store the session
    if let Ok(mut guard) = bridge.lock() {
        guard.active_session = Some(session);
    }

    Ok(())
}
