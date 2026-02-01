//! AirPlay device discovery using mDNS/Bonjour
//!
//! Discovers AirPlay 2 devices on the local network by browsing for
//! the _airplay._tcp service type.

use mdns_sd::{ServiceDaemon, ServiceEvent};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::Arc;
use std::time::Duration;
use flume::RecvTimeoutError;

use crate::AirPlayBridge;

/// AirPlay service type for mDNS discovery
const AIRPLAY_SERVICE_TYPE: &str = "_airplay._tcp.local.";

/// Represents a discovered AirPlay device
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AirPlayDevice {
    /// Unique device identifier
    pub id: String,
    /// Human-readable device name
    pub name: String,
    /// Device IP address
    pub address: String,
    /// AirPlay port (usually 7000)
    pub port: u16,
    /// Device model (e.g., "AudioAccessory5,1" for HomePod)
    pub model: Option<String>,
    /// Device features flags
    pub features: Option<String>,
    /// Whether device supports AirPlay 2
    pub supports_airplay2: bool,
    /// Device flags
    pub flags: Option<String>,
}

impl AirPlayDevice {
    /// Create a new AirPlayDevice from mDNS service info
    fn from_service_info(
        name: String,
        addresses: Vec<IpAddr>,
        port: u16,
        properties: HashMap<String, String>,
    ) -> Option<Self> {
        let address = addresses.first()?.to_string();

        // Extract device ID from properties or generate from name
        let id = properties
            .get("deviceid")
            .or_else(|| properties.get("id"))
            .cloned()
            .unwrap_or_else(|| {
                // Generate ID from name hash
                format!("{:x}", simple_hash(&name))
            });

        let model = properties.get("model").cloned();
        let features = properties.get("features").cloned();
        let flags = properties.get("flags").cloned();

        // Check if device supports AirPlay 2 based on features
        // AirPlay 2 devices typically have specific feature bits set
        let supports_airplay2 = features
            .as_ref()
            .map(|f| {
                // Parse hex features and check for AirPlay 2 bits
                // Bit 48 (0x1000000000000) indicates AirPlay 2 support
                if let Some(hex) = f.strip_prefix("0x") {
                    u64::from_str_radix(hex, 16)
                        .map(|v| (v & 0x1000000000000) != 0)
                        .unwrap_or(false)
                } else {
                    // Try parsing as decimal
                    f.parse::<u64>()
                        .map(|v| (v & 0x1000000000000) != 0)
                        .unwrap_or(false)
                }
            })
            .unwrap_or(false);

        Some(Self {
            id,
            name,
            address,
            port,
            model,
            features,
            supports_airplay2,
            flags,
        })
    }
}

/// Simple hash for generating device IDs
fn simple_hash(input: &str) -> u64 {
    let mut hash: u64 = 0;
    for byte in input.bytes() {
        hash = hash.wrapping_mul(31).wrapping_add(byte as u64);
    }
    hash
}

/// Discover AirPlay devices on the network
pub async fn discover_devices(bridge: Arc<AirPlayBridge>) {
    log::info!("Starting mDNS discovery for AirPlay devices");

    // Create mDNS daemon
    let mdns = match ServiceDaemon::new() {
        Ok(m) => m,
        Err(e) => {
            log::error!("Failed to create mDNS daemon: {}", e);
            return;
        }
    };

    // Browse for AirPlay services
    let receiver = match mdns.browse(AIRPLAY_SERVICE_TYPE) {
        Ok(r) => r,
        Err(e) => {
            log::error!("Failed to browse for AirPlay services: {}", e);
            return;
        }
    };

    log::info!("Browsing for {} services", AIRPLAY_SERVICE_TYPE);

    // Process events in a loop
    loop {
        // Check if we should stop
        {
            let running = bridge.discovery_running.read().unwrap();
            if !*running {
                log::info!("Discovery stopped by user");
                break;
            }
        }

        // Use tokio's spawn_blocking for the synchronous recv with timeout
        let recv_result = tokio::task::spawn_blocking({
            let receiver = receiver.clone();
            move || {
                receiver.recv_timeout(Duration::from_millis(500))
            }
        }).await;

        match recv_result {
            Ok(Ok(event)) => {
                handle_service_event(&bridge, event);
            }
            Ok(Err(RecvTimeoutError::Timeout)) => {
                // Timeout, continue loop
            }
            Ok(Err(e)) => {
                log::warn!("Error receiving mDNS event: {}", e);
                break;
            }
            Err(e) => {
                log::error!("Task join error: {}", e);
                break;
            }
        }
    }

    // Stop browsing
    if let Err(e) = mdns.stop_browse(AIRPLAY_SERVICE_TYPE) {
        log::warn!("Failed to stop mDNS browse: {}", e);
    }

    log::info!("mDNS discovery stopped");
}

/// Handle mDNS service events
fn handle_service_event(bridge: &Arc<AirPlayBridge>, event: ServiceEvent) {
    match event {
        ServiceEvent::ServiceResolved(info) => {
            log::info!("Discovered AirPlay device: {}", info.get_fullname());

            // Extract properties
            let properties: HashMap<String, String> = info
                .get_properties()
                .iter()
                .filter_map(|p| {
                    Some((p.key().to_string(), p.val_str().to_string()))
                })
                .collect();

            // Create device info
            if let Some(device) = AirPlayDevice::from_service_info(
                info.get_fullname().to_string(),
                info.get_addresses().iter().cloned().collect(),
                info.get_port(),
                properties,
            ) {
                log::info!(
                    "Found AirPlay device: {} at {}:{} (AirPlay 2: {})",
                    device.name,
                    device.address,
                    device.port,
                    device.supports_airplay2
                );

                // Add to device list
                if let Ok(mut devices) = bridge.devices.write() {
                    devices.insert(device.id.clone(), device);
                }
            }
        }
        ServiceEvent::ServiceRemoved(_, fullname) => {
            log::info!("AirPlay device removed: {}", fullname);

            // Remove from device list - compare by name (fullname from mDNS)
            if let Ok(mut devices) = bridge.devices.write() {
                // Find and remove the device with matching name
                let key_to_remove: Option<String> = devices
                    .iter()
                    .find(|(_, d)| d.name == fullname)
                    .map(|(k, _)| k.clone());

                if let Some(key) = key_to_remove {
                    devices.remove(&key);
                    log::info!("Removed device with id: {}", key);
                }
            }
        }
        ServiceEvent::SearchStarted(_) => {
            log::debug!("mDNS search started");
        }
        ServiceEvent::SearchStopped(_) => {
            log::debug!("mDNS search stopped");
        }
        _ => {}
    }
}
