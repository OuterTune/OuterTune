//! Audio encoding for AirPlay
//!
//! Handles encoding PCM audio to ALAC (Apple Lossless) format
//! for streaming to AirPlay devices.

use alac_encoder::{AlacEncoder, FormatDescription};
use anyhow::{anyhow, Result};
use std::sync::Mutex;

/// Standard AirPlay frame size (352 samples)
pub const SAMPLES_PER_FRAME: usize = 352;

/// Standard AirPlay sample rate
pub const AIRPLAY_SAMPLE_RATE: u32 = 44100;

/// Audio encoder for AirPlay streaming using ALAC
pub struct AudioEncoder {
    /// Sample rate (default 44100)
    sample_rate: u32,
    /// Number of channels (default 2 for stereo)
    channels: u32,
    /// Bits per sample (default 16)
    bits_per_sample: u32,
    /// Frame counter for RTP timestamps
    frame_count: u64,
    /// ALAC encoder instance (wrapped in Mutex for interior mutability)
    alac: Mutex<AlacEncoder>,
    /// PCM input format description (for encode() calls)
    pcm_format: FormatDescription,
    /// Output buffer for encoded data
    output_buffer: Vec<u8>,
}

impl AudioEncoder {
    /// Create a new audio encoder with default settings (44100Hz, stereo, 16-bit)
    pub fn new() -> Self {
        Self::with_config(44100, 2, 16)
    }

    /// Create a new audio encoder with specified configuration
    pub fn with_config(sample_rate: u32, channels: u32, bits_per_sample: u32) -> Self {
        // Create ALAC output format description for the encoder
        // AlacEncoder::new() expects the OUTPUT format (AppleLossless)
        // alac(sample_rate, frames_per_packet, channels)
        let alac_format = FormatDescription::alac(
            sample_rate as f64,
            SAMPLES_PER_FRAME as u32,
            channels,
        );

        // Create PCM input format description for encode() calls
        let pcm_format = FormatDescription::pcm::<i16>(sample_rate as f64, channels);

        let alac = AlacEncoder::new(&alac_format);

        Self {
            sample_rate,
            channels,
            bits_per_sample,
            frame_count: 0,
            alac: Mutex::new(alac),
            pcm_format,
            output_buffer: Vec::with_capacity(SAMPLES_PER_FRAME * channels as usize * 2 + 64),
        }
    }

    /// Encode PCM audio data to ALAC format
    ///
    /// # Arguments
    /// * `pcm_data` - Raw PCM audio data (16-bit signed, little-endian, interleaved stereo)
    /// * `_sample_rate` - Sample rate in Hz (should match encoder config)
    /// * `channels` - Number of audio channels
    ///
    /// # Returns
    /// ALAC encoded audio data ready for RTP transmission
    pub fn encode_alac(&mut self, pcm_data: &[u8], _sample_rate: u32, channels: u32) -> Result<Vec<u8>> {
        let bytes_per_sample = 2; // 16-bit
        let bytes_per_frame = SAMPLES_PER_FRAME * channels as usize * bytes_per_sample;

        if pcm_data.len() < bytes_per_frame {
            return Err(anyhow!(
                "Not enough PCM data for ALAC frame: got {} bytes, need {}",
                pcm_data.len(),
                bytes_per_frame
            ));
        }

        // Take exactly one frame worth of data
        let frame_data = &pcm_data[..bytes_per_frame];

        // Encode using ALAC encoder
        // The API is: encode(input_format, input_data_bytes, output_buffer) -> encoded_length
        let mut alac = self.alac.lock().map_err(|e| anyhow!("Failed to lock encoder: {}", e))?;

        // Prepare output buffer - ALAC output is at most input size + overhead
        self.output_buffer.clear();
        self.output_buffer.resize(bytes_per_frame + 64, 0);

        let encoded_len = alac.encode(&self.pcm_format, frame_data, &mut self.output_buffer);
        self.output_buffer.truncate(encoded_len);

        Ok(self.output_buffer.clone())
    }

    /// Encode PCM to raw format for uncompressed transmission (fallback)
    ///
    /// This creates an uncompressed ALAC frame which some devices accept
    /// when they have issues with compressed ALAC.
    pub fn encode_raw(&self, pcm_data: &[u8], channels: u32) -> Result<Vec<u8>> {
        let bytes_per_sample = 2;
        let bytes_per_frame = SAMPLES_PER_FRAME * channels as usize * bytes_per_sample;

        if pcm_data.len() < bytes_per_frame {
            return Err(anyhow!("Not enough PCM data for frame"));
        }

        let frame_data = &pcm_data[..bytes_per_frame];

        // Create uncompressed ALAC frame
        // Header byte: 0x20 = escape code for uncompressed frame
        // Then 16 bits for element instance tag (0), then 32 bits unused
        // Followed by big-endian PCM samples
        let mut output = Vec::with_capacity(bytes_per_frame + 7);

        // ALAC uncompressed frame header (7 bytes)
        output.push(0x20); // Escape code for uncompressed
        output.extend_from_slice(&[0x00, 0x00]); // Element instance tag
        output.extend_from_slice(&[0x00, 0x00, 0x00, 0x00]); // Unused

        // Convert PCM samples to big-endian
        for chunk in frame_data.chunks_exact(2) {
            // Swap byte order: little-endian to big-endian
            output.push(chunk[1]);
            output.push(chunk[0]);
        }

        Ok(output)
    }

    /// Get the number of samples in a standard AirPlay frame
    pub fn samples_per_frame(&self) -> usize {
        SAMPLES_PER_FRAME
    }

    /// Get the byte size of one audio frame (PCM input)
    pub fn frame_size(&self) -> usize {
        SAMPLES_PER_FRAME * self.channels as usize * (self.bits_per_sample / 8) as usize
    }

    /// Calculate and return the next RTP timestamp, incrementing frame count
    pub fn next_timestamp(&mut self) -> u32 {
        let timestamp = self.frame_count * SAMPLES_PER_FRAME as u64;
        self.frame_count += 1;
        (timestamp & 0xFFFFFFFF) as u32
    }

    /// Get current timestamp without incrementing
    pub fn current_timestamp(&self) -> u32 {
        let timestamp = self.frame_count * SAMPLES_PER_FRAME as u64;
        (timestamp & 0xFFFFFFFF) as u32
    }

    /// Reset the encoder state
    pub fn reset(&mut self) {
        self.frame_count = 0;
        self.output_buffer.clear();
    }

    /// Get the sample rate
    pub fn sample_rate(&self) -> u32 {
        self.sample_rate
    }

    /// Get the channel count
    pub fn channels(&self) -> u32 {
        self.channels
    }
}

impl Default for AudioEncoder {
    fn default() -> Self {
        Self::new()
    }
}

/// Convert PCM sample rate if needed using linear interpolation
///
/// For production use, consider using a proper resampling library like rubato.
pub fn resample_pcm(
    input: &[u8],
    input_rate: u32,
    output_rate: u32,
    channels: u32,
) -> Vec<u8> {
    if input_rate == output_rate {
        return input.to_vec();
    }

    let bytes_per_sample = 2 * channels as usize;
    let input_samples = input.len() / bytes_per_sample;

    if input_samples == 0 {
        return Vec::new();
    }

    let output_samples = ((input_samples as u64 * output_rate as u64) / input_rate as u64) as usize;
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

/// Resample audio to 44100 Hz if needed (standard AirPlay sample rate)
pub fn ensure_44100(input: &[u8], input_rate: u32, channels: u32) -> Vec<u8> {
    resample_pcm(input, input_rate, AIRPLAY_SAMPLE_RATE, channels)
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
        let mut encoder = AudioEncoder::new();
        let pcm_data = vec![0u8; encoder.frame_size()];
        let result = encoder.encode_alac(&pcm_data, 44100, 2);
        assert!(result.is_ok());
        // Encoded data should not be empty
        assert!(!result.unwrap().is_empty());
    }

    #[test]
    fn test_encode_raw() {
        let encoder = AudioEncoder::new();
        let pcm_data = vec![0u8; encoder.frame_size()];
        let result = encoder.encode_raw(&pcm_data, 2);
        assert!(result.is_ok());
        // Raw frame should be input size + 7 byte header
        assert_eq!(result.unwrap().len(), encoder.frame_size() + 7);
    }

    #[test]
    fn test_timestamp_increment() {
        let mut encoder = AudioEncoder::new();
        assert_eq!(encoder.next_timestamp(), 0);
        assert_eq!(encoder.next_timestamp(), 352);
        assert_eq!(encoder.next_timestamp(), 704);
    }

    #[test]
    fn test_resample_same_rate() {
        let input = vec![1u8, 2, 3, 4];
        let output = resample_pcm(&input, 44100, 44100, 1);
        assert_eq!(input, output);
    }
}
