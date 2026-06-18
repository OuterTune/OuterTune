# Prebuilt binaries

This directory contains prebuilt binaries that are bundled into the `full` build
flavor instead of being built from source as part of this repository.

## `ffMetadataEx-release.aar`

A prebuilt build of **ffMetadataEx**, the FFmpeg-based metadata extractor and
audio decoder module used by the `full` flavor.

- **License:** GPLv3
- **Source:** https://github.com/OuterTune/ffMetadataEx
- **Source revision used by this project:** `5374cd8b5eaf603c09ea8199aaf11311a3e60254`

The corresponding source code is available at the repository above. To rebuild
the AAR yourself, follow the build instructions in the ffMetadataEx README.

### Bundled components

The AAR embeds the following third-party components:

- **FFmpeg** — LGPLv2.1
  - Source (build scripts and FFmpeg sources): https://github.com/mikooomich/ffmpeg-android-maker
- **nextlib** (audio decoders) — see https://github.com/anilbeesetti/nextlib

The FFmpeg native libraries (`libav*.so`, `libsw*.so`) are dynamically linked,
so they can be replaced as required by the LGPL.
