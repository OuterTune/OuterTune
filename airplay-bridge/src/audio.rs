//! Audio encoding for AirPlay
//!
//! Handles encoding PCM audio to ALAC (Apple Lossless) format
//! for streaming to AirPlay devices.

use anyhow::{anyhow, Result};

/// Audio encoder for AirPlay streaming
pub struct AudioEncoder {
    /// Sample rate (default 44100)
    sample_rate: u32,
    /// Number of channels (default 2 for stereo)
    channels: u32,
    /// Bits per sample (default 16)
    bits_per_sample: u32,
    /// Frame counter for RTP timestamps
    frame_count: u64,
}

impl AudioEncoder {
    /// Create a new audio encoder with default settings
    pub fn new() -> Self {
        Self {
            sample_rate: 44100,
            channels: 2,
            bits_per_sample: 16,
            frame_count: 0,
        }
    }

    /// Encode PCM audio data to ALAC format
    ///
    /// # Arguments
    /// * `pcm_data` - Raw PCM audio data (16-bit signed, interleaved stereo)
    /// * `sample_rate` - Sample rate in Hz
    /// * `channels` - Number of audio channels
    ///
    /// # Returns
    /// ALAC encoded audio data
    pub fn encode_alac(&self, pcm_data: &[u8], sample_rate: u32, channels: u32) -> Result<Vec<u8>> {
        // ALAC frame format for AirPlay:
        // - 352 samples per frame (standard for AirPlay)
        // - 16-bit samples
        // - Stereo (2 channels)

        const SAMPLES_PER_FRAME: usize = 352;
        let bytes_per_sample = 2; // 16-bit
        let bytes_per_frame = SAMPLES_PER_FRAME * channels as usize * bytes_per_sample;

        if pcm_data.len() < bytes_per_frame {
            return Err(anyhow!("Not enough PCM data for ALAC frame"));
        }

        // For simplicity, we'll use uncompressed ALAC frames
        // Real implementation would use proper ALAC compression
        let mut alac_frame = Vec::with_capacity(bytes_per_frame + 4);

        // ALAC frame header (simplified)
        // Byte 0: Channel config and sample size
        // Bits 7-5: unused (0)
        // Bit 4: "has size" (0 = use default 352 samples)
        // Bit 3: "not compressed" (1 = uncompressed)
        // Bits 2-0: unused (0)
        alac_frame.push(0x08); // Uncompressed flag

        // For uncompressed frames, include the raw PCM data
        // with some minimal framing

        // Add raw PCM data (up to one frame worth)
        let data_to_encode = &pcm_data[..bytes_per_frame.min(pcm_data.len())];

        // Swap bytes for network order (big-endian) if needed
        for chunk in data_to_encode.chunks(2) {
            if chunk.len() == 2 {
                // Convert little-endian PCM to big-endian for ALAC
                alac_frame.push(chunk[1]);
                alac_frame.push(chunk[0]);
            }
        }

        Ok(alac_frame)
    }

    /// Encode PCM to AAC format (alternative codec)
    pub fn encode_aac(&self, pcm_data: &[u8], sample_rate: u32, channels: u32) -> Result<Vec<u8>> {
        // AAC encoding would require an external library like FDK-AAC
        // For now, return an error indicating it's not implemented
        Err(anyhow!("AAC encoding not yet implemented"))
    }

    /// Get the number of samples in a standard AirPlay frame
    pub fn samples_per_frame(&self) -> usize {
        352
    }

    /// Get the byte size of one audio frame
    pub fn frame_size(&self) -> usize {
        self.samples_per_frame() * self.channels as usize * (self.bits_per_sample / 8) as usize
    }

    /// Calculate RTP timestamp for current frame
    pub fn next_timestamp(&mut self) -> u32 {
        let timestamp = self.frame_count * self.samples_per_frame() as u64;
        self.frame_count += 1;
        (timestamp & 0xFFFFFFFF) as u32
    }

    /// Reset the encoder state
    pub fn reset(&mut self) {
        self.frame_count = 0;
    }
}

impl Default for AudioEncoder {
    fn default() -> Self {
        Self::new()
    }
}

/// Convert PCM sample rate if needed
pub fn resample_pcm(
    input: &[u8],
    input_rate: u32,
    output_rate: u32,
    channels: u32,
) -> Vec<u8> {
    if input_rate == output_rate {
        return input.to_vec();
    }

    // Simple linear interpolation resampling
    // For production, use a proper resampling library like rubato

    let bytes_per_sample = 2 * channels as usize;
    let input_samples = input.len() / bytes_per_sample;
    let output_samples = (input_samples as u64 * output_rate as u64 / input_rate as u64) as usize;
    let mut output = vec![0u8; output_samples * bytes_per_sample];

    let ratio = input_rate as f64 / output_rate as f64;

    for i in 0..output_samples {
        let src_pos = (i as f64 * ratio) as usize;
        let src_idx = src_pos * bytes_per_sample;
        let dst_idx = i * bytes_per_sample;

        if src_idx + bytes_per_sample <= input.len() && dst_idx + bytes_per_sample <= output.len() {
            output[dst_idx..dst_idx + bytes_per_sample]
                .copy_from_slice(&input[src_idx..src_idx + bytes_per_sample]);
        }
    }

    output
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encoder_creation() {
        let encoder = AudioEncoder::new();
        assert_eq!(encoder.sample_rate, 44100);
        assert_eq!(encoder.channels, 2);
        assert_eq!(encoder.bits_per_sample, 16);
    }

    #[test]
    fn test_frame_size() {
        let encoder = AudioEncoder::new();
        // 352 samples * 2 channels * 2 bytes = 1408 bytes
        assert_eq!(encoder.frame_size(), 1408);
    }

    #[test]
    fn test_encode_alac() {
        let encoder = AudioEncoder::new();
        let pcm_data = vec![0u8; encoder.frame_size()];
        let result = encoder.encode_alac(&pcm_data, 44100, 2);
        assert!(result.is_ok());
    }
}
