//! AirPlay 2 Bridge for OuterTune
//!
//! This library provides JNI bindings for AirPlay 2 audio streaming functionality.
//! Supports multi-device streaming to multiple AirPlay speakers simultaneously.

use jni::JNIEnv;
use jni::objects::{JClass, JString, JByteArray, JObject, GlobalRef};
use jni::sys::{jboolean, jint, JNI_TRUE, JNI_FALSE};
use jni::JavaVM;
use std::sync::{Arc, RwLock, OnceLock, Mutex};
use std::collections::HashMap;
use tokio::sync::mpsc;

mod discovery;
mod airplay;
mod audio;

pub use discovery::AirPlayDevice;
use airplay::AudioCommand;

/// Global JVM reference for callbacks from async contexts
static JAVA_VM: OnceLock<JavaVM> = OnceLock::new();

/// Global log callback reference
static LOG_CALLBACK: OnceLock<Mutex<Option<GlobalRef>>> = OnceLock::new();

/// Log levels matching Kotlin LogCallback
pub const LOG_LEVEL_DEBUG: i32 = 0;
pub const LOG_LEVEL_INFO: i32 = 1;
pub const LOG_LEVEL_WARN: i32 = 2;
pub const LOG_LEVEL_ERROR: i32 = 3;

/// Send a log message to the Kotlin callback (safe to call from any thread)
pub fn send_log_to_kotlin(level: i32, tag: &str, message: &str) {
    // Always log to Android logcat first
    match level {
        LOG_LEVEL_DEBUG => log::debug!("[{}] {}", tag, message),
        LOG_LEVEL_INFO => log::info!("[{}] {}", tag, message),
        LOG_LEVEL_WARN => log::warn!("[{}] {}", tag, message),
        LOG_LEVEL_ERROR => log::error!("[{}] {}", tag, message),
        _ => log::info!("[{}] {}", tag, message),
    }

    // Skip Kotlin callback from Tokio threads to avoid potential JNI issues
    // The logs will still appear in logcat
    let jvm = match JAVA_VM.get() {
        Some(vm) => vm,
        None => return,
    };

    // Check if we're on the main thread or a JNI-attached thread
    // If attach fails or we're in a weird state, just skip the callback
    let mut env = match jvm.attach_current_thread_as_daemon() {
        Ok(env) => env,
        Err(_) => return, // Silently skip - logs still go to logcat
    };

    // Now safely access the callback while attached to JVM
    let callback_lock = match LOG_CALLBACK.get() {
        Some(lock) => lock,
        None => return,
    };

    // Try to get the lock, but don't block - skip if locked (prevents deadlock)
    let callback_guard = match callback_lock.try_lock() {
        Ok(guard) => guard,
        Err(_) => return, // Skip if we can't get lock immediately
    };

    let callback = match callback_guard.as_ref() {
        Some(cb) => cb,
        None => return,
    };

    // Create Java strings - skip on failure
    let j_tag = match env.new_string(tag) {
        Ok(s) => s,
        Err(_) => return,
    };
    let j_message = match env.new_string(message) {
        Ok(s) => s,
        Err(_) => return,
    };

    // Call the callback: onLog(level, tag, message)
    // Use invoke to properly handle the interface method
    let _ = env.call_method(
        callback.as_obj(),
        "onLog",
        "(ILjava/lang/String;Ljava/lang/String;)V",
        &[
            jni::objects::JValue::Int(level),
            jni::objects::JValue::Object(&j_tag.into()),
            jni::objects::JValue::Object(&j_message.into()),
        ],
    );
}

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
    env: JNIEnv,
    _class: JClass,
) -> jboolean {
    // Initialize Android logger
    #[cfg(target_os = "android")]
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("AirPlayBridge"),
    );

    // Store JVM reference for callbacks
    if JAVA_VM.get().is_none() {
        if let Ok(jvm) = env.get_java_vm() {
            let _ = JAVA_VM.set(jvm);
            log::info!("JVM reference stored for callbacks");
        }
    }

    // Initialize log callback storage
    let _ = LOG_CALLBACK.get_or_init(|| Mutex::new(None));

    log::info!("Initializing AirPlay Bridge (multi-device support)");

    // Initialize the bridge
    let _ = AirPlayBridge::get();

    JNI_TRUE
}

/// Set the log callback for receiving logs from native code
#[no_mangle]
pub extern "system" fn Java_com_dd3boh_outertune_playback_AirPlayBridge_nativeSetLogCallback(
    env: JNIEnv,
    _class: JClass,
    callback: JObject,
) {
    // Create a global reference to the callback so it survives across JNI calls
    let global_ref = match env.new_global_ref(callback) {
        Ok(r) => r,
        Err(e) => {
            log::error!("Failed to create global ref for log callback: {}", e);
            return;
        }
    };

    // Store the callback
    if let Some(lock) = LOG_CALLBACK.get() {
        if let Ok(mut guard) = lock.lock() {
            *guard = Some(global_ref);
            log::info!("Log callback registered");
        }
    }
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

    // Start session in background (non-blocking)
    // IMPORTANT: Session is only added to map AFTER setup succeeds
    // This prevents audio from being sent before the connection is ready
    let device_clone = device.clone();
    let bridge_clone = bridge.clone();
    let device_id_clone = device_id.clone();
    let device_name_clone = device_name.clone();

    bridge.runtime.spawn(async move {
        match airplay::start_session(device_clone, audio_rx, bridge_clone.clone()).await {
            Ok(_) => {
                log::info!("Session setup complete for device: {}", device_id_clone);

                // NOW add to sessions map - connection is ready for audio
                match bridge_clone.sessions.write() {
                    Ok(mut sessions) => {
                        sessions.insert(device_id_clone.clone(), SessionInfo {
                            device_id: device_id_clone.clone(),
                            device_name: device_name_clone,
                            audio_tx,
                        });
                        log::info!("Session registered and ready for audio: {}", device_id_clone);
                    }
                    Err(e) => {
                        log::error!("Failed to register session: {}", e);
                    }
                }
            }
            Err(e) => {
                log::error!("Failed to start session for {}: {}", device_id_clone, e);
                // No need to remove from sessions - it was never added
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
    mut env: JNIEnv<'a>,
    _class: JClass,
) -> JString<'a> {
    let bridge = AirPlayBridge::get();

    let json = {
        let device_ids = bridge.connected_device_ids();
        serde_json::to_string(&device_ids).unwrap_or_else(|_| "[]".to_string())
    };

    // Return the JSON string
    match env.new_string(&json) {
        Ok(s) => s,
        Err(e) => {
            // If we can't create a string, throw a Java exception and return empty
            log::error!("JNI new_string failed: {}", e);
            let _ = env.throw_new("java/lang/RuntimeException", "Failed to create JNI string");
            // Return a default - the exception will be thrown when control returns to Java
            unsafe { JString::from_raw(std::ptr::null_mut()) }
        }
    }
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

/// Counter for logging audio sends periodically
static AUDIO_SEND_COUNTER: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);

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

    // Log periodically to show audio is flowing
    let count = AUDIO_SEND_COUNTER.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
    if count == 0 || count % 500 == 0 {
        log::info!("nativeSendAudio: frame #{}, {} sessions, {} bytes",
            count, sessions.len(), data.len());
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
                // Log channel full errors - indicates audio is being sent faster than processed
                log::warn!("Failed to queue audio for {}: {} (channel may be full)", device_id, e);
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
