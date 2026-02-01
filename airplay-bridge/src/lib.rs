//! AirPlay 2 Bridge for OuterTune
//!
//! This library provides JNI bindings for AirPlay 2 audio streaming functionality.
//! Supports multi-device streaming to multiple AirPlay speakers simultaneously.

use jni::JNIEnv;
use jni::objects::{JClass, JString, JByteArray};
use jni::sys::{jboolean, jint, JNI_TRUE, JNI_FALSE};
use std::sync::{Arc, RwLock, OnceLock};
use std::collections::HashMap;
use tokio::sync::mpsc;

mod discovery;
mod airplay;
mod audio;

pub use discovery::AirPlayDevice;
use airplay::AudioCommand;

/// Global state for the AirPlay bridge
static BRIDGE: OnceLock<Arc<AirPlayBridge>> = OnceLock::new();

/// Main bridge struct that manages AirPlay connections
pub struct AirPlayBridge {
    /// Discovered AirPlay devices
    pub devices: RwLock<HashMap<String, AirPlayDevice>>,
    /// Active sessions (device_id -> session info) - supports multiple concurrent connections
    pub sessions: RwLock<HashMap<String, SessionInfo>>,
    /// Tokio runtime for async operations
    pub runtime: tokio::runtime::Runtime,
    /// Flag indicating if discovery is running
    pub discovery_running: RwLock<bool>,
}

/// Info about an active session
pub struct SessionInfo {
    pub device_id: String,
    pub device_name: String,
    /// Channel to send audio commands to the session
    pub audio_tx: mpsc::Sender<AudioCommand>,
}

impl AirPlayBridge {
    fn new() -> Self {
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(4)
            .enable_all()
            .build()
            .expect("Failed to create Tokio runtime");

        Self {
            devices: RwLock::new(HashMap::new()),
            sessions: RwLock::new(HashMap::new()),
            runtime,
            discovery_running: RwLock::new(false),
        }
    }

    pub fn get() -> Arc<AirPlayBridge> {
        BRIDGE.get_or_init(|| Arc::new(AirPlayBridge::new())).clone()
    }

    pub fn is_connected(&self) -> bool {
        self.sessions.read().map(|s| !s.is_empty()).unwrap_or(false)
    }

    pub fn connected_device_ids(&self) -> Vec<String> {
        self.sessions.read()
            .map(|s| s.keys().cloned().collect())
            .unwrap_or_default()
    }
}

/// Initialize the AirPlay bridge (called from Android)
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    // Initialize Android logger
    #[cfg(target_os = "android")]
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("AirPlayBridge"),
    );

    log::info!("Initializing AirPlay Bridge (multi-device support)");

    // Initialize the bridge
    let _ = AirPlayBridge::get();

    JNI_TRUE
}

/// Connect to an AirPlay device with device info passed from Kotlin
/// (Discovery is handled by Android NSD, so we receive device info directly)
/// Returns immediately and connects in background
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeConnectWithInfo(
    mut env: JNIEnv,
    _class: JClass,
    device_id: JString,
    device_name: JString,
    address: JString,
    port: jint,
    supports_airplay2: jboolean,
) -> jboolean {
    let device_id: String = match env.get_string(&device_id) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };
    let device_name: String = match env.get_string(&device_name) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };
    let address: String = match env.get_string(&address) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };

    log::info!("Connecting to AirPlay device: {} at {}:{}", device_name, address, port);

    let bridge = AirPlayBridge::get();

    // Check if already connected to this device
    {
        if let Ok(sessions) = bridge.sessions.read() {
            if sessions.contains_key(&device_id) {
                log::warn!("Already connected to device: {}", device_id);
                return JNI_TRUE;
            }
        }
    }

    // Create device from passed parameters
    let device = AirPlayDevice {
        id: device_id.clone(),
        name: device_name.clone(),
        address,
        port: port as u16,
        model: None,
        features: None,
        supports_airplay2: supports_airplay2 == JNI_TRUE,
        flags: None,
    };

    // Create channel for audio commands
    let (audio_tx, audio_rx) = mpsc::channel::<AudioCommand>(100);

    // Store session info immediately
    {
        match bridge.sessions.write() {
            Ok(mut sessions) => {
                sessions.insert(device_id.clone(), SessionInfo {
                    device_id: device.id.clone(),
                    device_name: device.name.clone(),
                    audio_tx,
                });
            }
            Err(e) => {
                log::error!("Failed to acquire sessions lock: {}", e);
                return JNI_FALSE;
            }
        }
    }

    // Start session in background (non-blocking)
    let device_clone = device.clone();
    let bridge_clone = bridge.clone();
    let device_id_clone = device_id.clone();

    bridge.runtime.spawn(async move {
        match airplay::start_session(device_clone, audio_rx, bridge_clone.clone()).await {
            Ok(_) => {
                log::info!("Session started for device: {}", device_id_clone);
            }
            Err(e) => {
                log::error!("Failed to start session for {}: {}", device_id_clone, e);
                // Remove from sessions on failure
                if let Ok(mut sessions) = bridge_clone.sessions.write() {
                    sessions.remove(&device_id_clone);
                }
            }
        }
    });

    JNI_TRUE
}

/// Disconnect from a specific AirPlay device
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeDisconnectDevice(
    mut env: JNIEnv,
    _class: JClass,
    device_id: JString,
) {
    let device_id: String = match env.get_string(&device_id) {
        Ok(s) => s.into(),
        Err(_) => return,
    };

    log::info!("Disconnecting from AirPlay device: {}", device_id);

    let bridge = AirPlayBridge::get();

    // Remove session and send disconnect
    if let Ok(mut sessions) = bridge.sessions.write() {
        if let Some(info) = sessions.remove(&device_id) {
            let _ = info.audio_tx.try_send(AudioCommand::Disconnect);
            log::info!("Disconnected from: {}", device_id);
        }
    };
}

/// Disconnect from all AirPlay devices
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeDisconnect(
    _env: JNIEnv,
    _class: JClass,
) {
    log::info!("Disconnecting from all AirPlay devices");

    let bridge = AirPlayBridge::get();

    // Remove all sessions and send disconnect
    if let Ok(mut sessions) = bridge.sessions.write() {
        for (device_id, info) in sessions.drain() {
            let _ = info.audio_tx.try_send(AudioCommand::Disconnect);
            log::info!("Disconnected from: {}", device_id);
        }
    };
}

/// Get list of connected device IDs as JSON array
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeGetConnectedDevices<'a>(
    env: JNIEnv<'a>,
    _class: JClass,
) -> JString<'a> {
    let bridge = AirPlayBridge::get();

    let json = {
        let device_ids = bridge.connected_device_ids();
        serde_json::to_string(&device_ids).unwrap_or_else(|_| "[]".to_string())
    };

    env.new_string(&json).unwrap()
}

/// Check if connected to any AirPlay device
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeIsConnected(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let bridge = AirPlayBridge::get();
    if bridge.is_connected() { JNI_TRUE } else { JNI_FALSE }
}

/// Check if connected to a specific device
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeIsDeviceConnected(
    mut env: JNIEnv,
    _class: JClass,
    device_id: JString,
) -> jboolean {
    let device_id: String = match env.get_string(&device_id) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };

    let bridge = AirPlayBridge::get();

    if let Ok(sessions) = bridge.sessions.read() {
        if sessions.contains_key(&device_id) {
            return JNI_TRUE;
        }
    }

    JNI_FALSE
}

/// Send audio data to ALL connected AirPlay devices
/// audio_data: PCM audio samples (16-bit signed, stereo)
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeSendAudio(
    env: JNIEnv,
    _class: JClass,
    audio_data: JByteArray,
    sample_rate: jint,
    channels: jint,
) -> jboolean {
    let data = match env.convert_byte_array(&audio_data) {
        Ok(d) => d,
        Err(e) => {
            log::error!("Failed to convert audio data: {}", e);
            return JNI_FALSE;
        }
    };

    let bridge = AirPlayBridge::get();

    // Send audio to all connected sessions
    let sessions = match bridge.sessions.read() {
        Ok(s) => s,
        Err(e) => {
            log::error!("Failed to read sessions: {}", e);
            return JNI_FALSE;
        }
    };

    if sessions.is_empty() {
        return JNI_FALSE;
    }

    let mut sent_any = false;
    for (device_id, info) in sessions.iter() {
        let cmd = AudioCommand::SendAudio {
            data: data.clone(),
            sample_rate: sample_rate as u32,
            channels: channels as u32,
        };

        match info.audio_tx.try_send(cmd) {
            Ok(_) => {
                sent_any = true;
            }
            Err(e) => {
                log::warn!("Failed to send audio to {}: {}", device_id, e);
            }
        }
    }

    if sent_any { JNI_TRUE } else { JNI_FALSE }
}

/// Set volume on ALL connected AirPlay devices (0-100)
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeSetVolume(
    _env: JNIEnv,
    _class: JClass,
    volume: jint,
) -> jboolean {
    let volume_float = (volume as f32) / 100.0;
    log::debug!("Setting AirPlay volume to {} on all devices", volume_float);

    let bridge = AirPlayBridge::get();

    let sessions = match bridge.sessions.read() {
        Ok(s) => s,
        Err(e) => {
            log::error!("Failed to read sessions: {}", e);
            return JNI_FALSE;
        }
    };

    if sessions.is_empty() {
        return JNI_FALSE;
    }

    let mut sent_any = false;
    for (device_id, info) in sessions.iter() {
        let cmd = AudioCommand::SetVolume(volume_float);

        match info.audio_tx.try_send(cmd) {
            Ok(_) => {
                sent_any = true;
            }
            Err(e) => {
                log::warn!("Failed to set volume on {}: {}", device_id, e);
            }
        }
    }

    if sent_any { JNI_TRUE } else { JNI_FALSE }
}

/// Cleanup and release resources
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
) {
    log::info!("Destroying AirPlay Bridge");

    let bridge = AirPlayBridge::get();

    // Stop discovery
    if let Ok(mut running) = bridge.discovery_running.write() {
        *running = false;
    };

    // Disconnect all sessions
    if let Ok(mut sessions) = bridge.sessions.write() {
        for (device_id, info) in sessions.drain() {
            let _ = info.audio_tx.try_send(AudioCommand::Disconnect);
            log::info!("Cleaned up session for: {}", device_id);
        }
    };
}
