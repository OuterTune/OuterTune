# AirPlay 2 Bridge for OuterTune

This module provides native AirPlay 2 audio streaming support for OuterTune, allowing you to stream music directly to AirPlay-compatible devices like HomePod, Apple TV, and Ubiquiti PowerAmp.

## Features

- **Device Discovery**: Automatically discovers AirPlay devices on your local network via mDNS
- **AirPlay 2 Support**: Supports both AirPlay 1 and AirPlay 2 devices with HomeKit pairing
- **Audio Streaming**: Streams audio encoded in ALAC format for lossless quality
- **Volume Control**: Adjust volume on the connected AirPlay device

## Building

### Prerequisites

1. **Rust toolchain** (1.70+)
   ```bash
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
   ```

2. **Android NDK** (r21+)
   - Install via Android Studio SDK Manager
   - Or download from [Android NDK Downloads](https://developer.android.com/ndk/downloads)

3. **Rust Android targets**
   ```bash
   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
   ```

4. **cargo-ndk**
   ```bash
   cargo install cargo-ndk
   ```

### Build Steps

1. Set up environment variables:
   ```bash
   export ANDROID_HOME=/path/to/android/sdk
   export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/25.2.9519653  # or your NDK version
   ```

2. Run the build script:
   ```bash
   cd airplay-bridge
   ./build-android.sh
   ```

3. The script will:
   - Build the Rust library for all Android architectures (arm64-v8a, armeabi-v7a, x86_64, x86)
   - Copy the `.so` files to `app/src/main/jniLibs/`

4. Build OuterTune as usual:
   ```bash
   ./gradlew assembleCoreDebug
   ```

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    OuterTune (Kotlin)                   │
│                                                         │
│  ┌─────────────────┐     ┌─────────────────────────┐   │
│  │   ExoPlayer     │────▶│    AirPlayBridge.kt     │   │
│  │  (Audio PCM)    │     │    (JNI Interface)      │   │
│  └─────────────────┘     └───────────┬─────────────┘   │
│                                      │                  │
│                          ┌───────────▼─────────────┐   │
│                          │  libairplay_bridge.so   │   │
│                          │  (Rust Native Library)  │   │
│                          │                         │   │
│                          │  ├── discovery.rs       │   │
│                          │  │   (mDNS/Bonjour)     │   │
│                          │  ├── airplay.rs         │   │
│                          │  │   (RTSP/HomeKit)     │   │
│                          │  └── audio.rs           │   │
│                          │      (ALAC Encoding)    │   │
│                          └───────────┬─────────────┘   │
│                                      │                  │
└──────────────────────────────────────┼──────────────────┘
                                       │
                            ┌──────────▼──────────┐
                            │   AirPlay Device    │
                            │ (HomePod, PowerAmp) │
                            └─────────────────────┘
```

## Usage in Code

```kotlin
// Start device discovery
AirPlayBridge.startDiscovery()

// Get discovered devices
val devices = AirPlayBridge.devices.value

// Connect to a device
AirPlayBridge.connect(device)

// Send audio (from ExoPlayer AudioProcessor)
AirPlayBridge.sendAudio(pcmData, 44100, 2)

// Disconnect
AirPlayBridge.disconnect()
```

## Limitations

- **Experimental**: This is an early implementation of AirPlay 2
- **HomeKit Pairing**: Some devices may require PIN pairing (not yet fully implemented)
- **No Multi-room**: Synchronized multi-room playback is not yet supported

## License

GPL-3.0 - Same as OuterTune
