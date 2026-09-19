# PicQuery Mod

[中文](README_zh.md)

An offline Android application for natural-language photo and video search. It uses a single MobileCLIP2-S2 image/text encoder pair, ObjectBox HNSW vector indexes, MediaStore discovery, and an FFmpeg software-decoding fallback for video frames.

## Requirements

- Android Studio or JDK 17
- Android SDK 36
- The MobileCLIP2-S2 ONNX model pair, obtained separately

The build expects these files:

```text
local-models/mobileclip2-s2-onnx/text_model.onnx
local-models/mobileclip2-s2-onnx/vision_model.onnx
```

The model binaries are deliberately excluded from Git because each exceeds GitHub's regular file-size limit. Download or regenerate them only when building locally.

Expected SHA-256 checksums:

```text
text_model.onnx   622F10372BCA71B5017F2EFC5F8C2886610A2592B636DE8984D717F03213F031
vision_model.onnx A841F72C5A5085748BBE271A1D5718ABA877822A15CBA865BDBD0D37036B849E
```

Build a debug APK:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

Release builds must be signed with your own Android keystore. To update an already installed build without losing its application data, sign the new APK with the same key as the installed APK.

## Privacy

Photo/video indexing and vector search run locally. Repository signing keys and local Android SDK configuration are intentionally excluded.

## Upstream and license

This modification is based on [greyovo/PicQuery](https://github.com/greyovo/PicQuery). The source code remains available under the [MIT License](LICENSE). Model files and third-party dependencies may have their own terms.
