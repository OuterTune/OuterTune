//! AirPlay 2 Bridge for OuterTune
//!
//! This library provides JNI bindings for AirPlay 2 audio streaming functionality.
//! It handles device discovery via mDNS, HomeKit pairing, and audio streaming.

use jni::JNIEnv;
use jni::objects::{JClass, JString, JByteArray};
use jni::sys::{jboolean, jint, JNI_TRUE, JNI_FALSE};
use std::sync::{Arc, Mutex, OnceLock};
use std::collections::HashMap;

mod discovery;
mod airplay;
mod audio;

use discovery::AirPlayDevice;
use airplay::AirPlaySession;

/// Global state for the AirPlay bridge
static BRIDGE: OnceLock<Arc<Mutex<AirPlayBridge>>> = OnceLock::new();

/// Main bridge struct that manages AirPlay connections
pub struct AirPlayBridge {
    /// Discovered AirPlay devices
    pub devices: HashMap<String, AirPlayDevice>,
    /// Active session (if any)
    pub active_session: Option<AirPlaySession>,
    /// Tokio runtime for async operations
    pub runtime: tokio::runtime::Runtime,
    /// Flag indicating if discovery is running
    pub discovery_running: bool,
}

impl AirPlayBridge {
    fn new() -> Self {
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .expect("Failed to create Tokio runtime");

        Self {
            devices: HashMap::new(),
            active_session: None,
            runtime,
            discovery_running: false,
        }
    }

    pub fn get() -> Arc<Mutex<AirPlayBridge>> {
        BRIDGE.get_or_init(|| Arc::new(Mutex::new(AirPlayBridge::new()))).clone()
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

    let should_start = {
        let mut guard = match bridge.lock() {
            Ok(g) => g,
            Err(_) => return JNI_FALSE,
        };

        if guard.discovery_running {
            log::warn!("Discovery already running");
            return JNI_TRUE;
        }

        guard.discovery_running = true;
        true
    };

    if should_start {
        let bridge_clone = bridge.clone();

        // Get runtime handle before spawning
        if let Ok(guard) = bridge.lock() {
            guard.runtime.spawn(async move {
                discovery::discover_devices(bridge_clone).await;
            });
        }
    }

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
    if let Ok(mut guard) = bridge.lock() {
        guard.discovery_running = false;
    };
}

/// Get discovered devices as JSON array
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeGetDevices<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass,
) -> JString<'a> {
    let bridge = AirPlayBridge::get();

    let json = {
        let guard = match bridge.lock() {
            Ok(g) => g,
            Err(_) => return env.new_string("[]").unwrap(),
        };

        let devices: Vec<&AirPlayDevice> = guard.devices.values().collect();
        serde_json::to_string(&devices).unwrap_or_else(|_| "[]".to_string())
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
    let bridge_clone = bridge.clone();

    // Get device and runtime handle
    let (device, runtime_handle) = {
        let guard = match bridge.lock() {
            Ok(g) => g,
            Err(_) => return JNI_FALSE,
        };

        let device = match guard.devices.get(&device_id) {
            Some(d) => d.clone(),
            None => {
                log::error!("Device not found: {}", device_id);
                return JNI_FALSE;
            }
        };

        (device, guard.runtime.handle().clone())
    };

    // Run connection outside the lock
    let result = runtime_handle.block_on(async {
        airplay::connect_to_device(&device, bridge_clone).await
    });

    match result {
        Ok(_) => JNI_TRUE,
        Err(e) => {
            log::error!("Failed to connect: {}", e);
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

    let (session, runtime_handle) = {
        let mut guard = match bridge.lock() {
            Ok(g) => g,
            Err(_) => return,
        };

        let session = guard.active_session.take();
        let handle = guard.runtime.handle().clone();
        (session, handle)
    };

    if let Some(session) = session {
        runtime_handle.block_on(async {
            session.disconnect().await;
        });
    };
}

/// Check if connected to an AirPlay device
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeIsConnected(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let bridge = AirPlayBridge::get();
    let is_connected = {
        if let Ok(guard) = bridge.lock() {
            guard.active_session.is_some()
        } else {
            false
        }
    };

    if is_connected { JNI_TRUE } else { JNI_FALSE }
}

/// Send audio data to AirPlay device
/// audio_data: PCM audio samples (16-bit signed, stereo, 44100Hz)
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
        Err(_) => return JNI_FALSE,
    };

    let bridge = AirPlayBridge::get();

    let result = {
        let guard = match bridge.lock() {
            Ok(g) => g,
            Err(_) => return JNI_FALSE,
        };

        if let Some(ref session) = guard.active_session {
            let handle = guard.runtime.handle().clone();
            let session_ref = session;

            // We need to clone or handle this differently
            // For now, just return success as a placeholder
            Ok(())
        } else {
            Err(anyhow::anyhow!("No active session"))
        }
    };

    match result {
        Ok(_) => JNI_TRUE,
        Err(e) => {
            log::error!("Failed to send audio: {}", e);
            JNI_FALSE
        }
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

    let result = {
        let guard = match bridge.lock() {
            Ok(g) => g,
            Err(_) => return JNI_FALSE,
        };

        if guard.active_session.is_some() {
            // Volume setting placeholder
            Ok(())
        } else {
            Err(anyhow::anyhow!("No active session"))
        }
    };

    match result {
        Ok(_) => JNI_TRUE,
        Err(e) => {
            log::error!("Failed to set volume: {}", e);
            JNI_FALSE
        }
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

    let (session, runtime_handle) = {
        let mut guard = match bridge.lock() {
            Ok(g) => g,
            Err(_) => return,
        };

        guard.discovery_running = false;
        let session = guard.active_session.take();
        let handle = guard.runtime.handle().clone();
        (session, handle)
    };

    if let Some(session) = session {
        runtime_handle.block_on(async {
            session.disconnect().await;
        });
    };
}
