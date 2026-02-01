//! AirPlay 2 Bridge for OuterTune
//!
//! This library provides JNI bindings for AirPlay 2 audio streaming functionality.
//! It handles device discovery via mDNS, HomeKit pairing, and audio streaming.

use jni::JNIEnv;
use jni::objects::{JClass, JString, JByteArray};
use jni::sys::{jboolean, jint, JNI_TRUE, JNI_FALSE};
use std::sync::{Arc, RwLock, OnceLock};
use std::collections::HashMap;
use tokio::sync::mpsc;

mod discovery;
mod airplay;
mod audio;

use discovery::AirPlayDevice;
use airplay::AudioCommand;

/// Global state for the AirPlay bridge
static BRIDGE: OnceLock<Arc<AirPlayBridge>> = OnceLock::new();

/// Main bridge struct that manages AirPlay connections
pub struct AirPlayBridge {
    /// Discovered AirPlay devices
    pub devices: RwLock<HashMap<String, AirPlayDevice>>,
    /// Active session info
    pub session_info: RwLock<Option<SessionInfo>>,
    /// Tokio runtime for async operations
    pub runtime: tokio::runtime::Runtime,
    /// Flag indicating if discovery is running
    pub discovery_running: RwLock<bool>,
}

/// Info about the active session
pub struct SessionInfo {
    pub device_id: String,
    pub device_name: String,
    /// Channel to send audio commands to the session
    pub audio_tx: mpsc::Sender<AudioCommand>,
}

impl AirPlayBridge {
    fn new() -> Self {
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .expect("Failed to create Tokio runtime");

        Self {
            devices: RwLock::new(HashMap::new()),
            session_info: RwLock::new(None),
            runtime,
            discovery_running: RwLock::new(false),
        }
    }

    pub fn get() -> Arc<AirPlayBridge> {
        BRIDGE.get_or_init(|| Arc::new(AirPlayBridge::new())).clone()
    }

    pub fn is_connected(&self) -> bool {
        self.session_info.read().map(|s| s.is_some()).unwrap_or(false)
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

    log::info!("Initializing AirPlay Bridge");

    // Initialize the bridge
    let _ = AirPlayBridge::get();

    JNI_TRUE
}

/// Start device discovery
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeStartDiscovery(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    log::info!("Starting AirPlay device discovery");

    let bridge = AirPlayBridge::get();

    // Check if already running
    {
        let running = bridge.discovery_running.read().unwrap();
        if *running {
            log::warn!("Discovery already running");
            return JNI_TRUE;
        }
    }

    // Set running flag
    {
        let mut running = bridge.discovery_running.write().unwrap();
        *running = true;
    }

    let bridge_clone = bridge.clone();
    bridge.runtime.spawn(async move {
        discovery::discover_devices(bridge_clone).await;
    });

    JNI_TRUE
}

/// Stop device discovery
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeStopDiscovery(
    _env: JNIEnv,
    _class: JClass,
) {
    log::info!("Stopping AirPlay device discovery");

    let bridge = AirPlayBridge::get();
    if let Ok(mut running) = bridge.discovery_running.write() {
        *running = false;
    };
}

/// Get discovered devices as JSON array
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeGetDevices<'a>(
    env: JNIEnv<'a>,
    _class: JClass,
) -> JString<'a> {
    let bridge = AirPlayBridge::get();

    let json = {
        let devices = bridge.devices.read().unwrap();
        let device_list: Vec<&AirPlayDevice> = devices.values().collect();
        serde_json::to_string(&device_list).unwrap_or_else(|_| "[]".to_string())
    };

    env.new_string(&json).unwrap()
}

/// Connect to an AirPlay device
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeConnect(
    mut env: JNIEnv,
    _class: JClass,
    device_id: JString,
) -> jboolean {
    let device_id: String = match env.get_string(&device_id) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };

    log::info!("Connecting to AirPlay device: {}", device_id);

    let bridge = AirPlayBridge::get();

    // Get device info
    let device = {
        let devices = bridge.devices.read().unwrap();
        match devices.get(&device_id) {
            Some(d) => d.clone(),
            None => {
                log::error!("Device not found: {}", device_id);
                return JNI_FALSE;
            }
        }
    };

    // Create channel for audio commands
    let (audio_tx, audio_rx) = mpsc::channel::<AudioCommand>(100);

    // Store session info
    {
        let mut session_info = bridge.session_info.write().unwrap();
        *session_info = Some(SessionInfo {
            device_id: device.id.clone(),
            device_name: device.name.clone(),
            audio_tx,
        });
    }

    // Start session in background
    let device_clone = device.clone();
    let bridge_clone = bridge.clone();

    let result = bridge.runtime.block_on(async {
        airplay::start_session(device_clone, audio_rx, bridge_clone).await
    });

    match result {
        Ok(_) => {
            log::info!("Connected to {}", device.name);
            JNI_TRUE
        }
        Err(e) => {
            log::error!("Failed to connect: {}", e);
            // Clear session info on failure
            if let Ok(mut session_info) = bridge.session_info.write() {
                *session_info = None;
            }
            JNI_FALSE
        }
    }
}

/// Disconnect from current AirPlay device
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeDisconnect(
    _env: JNIEnv,
    _class: JClass,
) {
    log::info!("Disconnecting from AirPlay device");

    let bridge = AirPlayBridge::get();

    // Send disconnect command and clear session
    if let Ok(mut session_info) = bridge.session_info.write() {
        if let Some(info) = session_info.take() {
            let _ = info.audio_tx.try_send(AudioCommand::Disconnect);
        }
    };
}

/// Check if connected to an AirPlay device
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeIsConnected(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let bridge = AirPlayBridge::get();
    if bridge.is_connected() { JNI_TRUE } else { JNI_FALSE }
}

/// Send audio data to AirPlay device
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

    // Get the audio sender channel with proper error handling
    let session_info = match bridge.session_info.read() {
        Ok(info) => info,
        Err(e) => {
            log::error!("Failed to read session info: {}", e);
            return JNI_FALSE;
        }
    };

    if let Some(ref info) = *session_info {
        let cmd = AudioCommand::SendAudio {
            data,
            sample_rate: sample_rate as u32,
            channels: channels as u32,
        };

        match info.audio_tx.try_send(cmd) {
            Ok(_) => JNI_TRUE,
            Err(e) => {
                log::warn!("Failed to send audio command: {}", e);
                JNI_FALSE
            }
        }
    } else {
        log::warn!("No active session for audio");
        JNI_FALSE
    }
}

/// Set volume on AirPlay device (0-100)
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeSetVolume(
    _env: JNIEnv,
    _class: JClass,
    volume: jint,
) -> jboolean {
    let volume_float = (volume as f32) / 100.0;
    log::debug!("Setting AirPlay volume to {}", volume_float);

    let bridge = AirPlayBridge::get();

    // Get the audio sender channel with proper error handling
    let session_info = match bridge.session_info.read() {
        Ok(info) => info,
        Err(e) => {
            log::error!("Failed to read session info: {}", e);
            return JNI_FALSE;
        }
    };

    if let Some(ref info) = *session_info {
        let cmd = AudioCommand::SetVolume(volume_float);

        match info.audio_tx.try_send(cmd) {
            Ok(_) => JNI_TRUE,
            Err(e) => {
                log::warn!("Failed to send volume command: {}", e);
                JNI_FALSE
            }
        }
    } else {
        log::warn!("No active session for volume");
        JNI_FALSE
    }
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

    // Disconnect session
    if let Ok(mut session_info) = bridge.session_info.write() {
        if let Some(info) = session_info.take() {
            let _ = info.audio_tx.try_send(AudioCommand::Disconnect);
        }
    };
}
