# PicQuery Mod

[English](README.md)

一款离线运行的 Android 自然语言图片与视频搜索应用。应用仅使用一套 MobileCLIP2-S2 图像/文本编码器，通过 ObjectBox HNSW 建立向量索引；视频来源使用 MediaStore，并提供 FFmpeg 软件解码回退。

## 构建要求

- Android Studio 或 JDK 17
- Android SDK 36
- 单独获取的 MobileCLIP2-S2 ONNX 模型文件

构建需要以下文件：

```text
local-models/mobileclip2-s2-onnx/text_model.onnx
local-models/mobileclip2-s2-onnx/vision_model.onnx
```

模型文件不会提交到 Git，因为单个文件超过 GitHub 普通文件大小限制。仅在本地构建时下载或重新生成。

预期 SHA-256：

```text
text_model.onnx   622F10372BCA71B5017F2EFC5F8C2886610A2592B636DE8984D717F03213F031
vision_model.onnx A841F72C5A5085748BBE271A1D5718ABA877822A15CBA865BDBD0D37036B849E
```

Windows 调试构建：

```powershell
.\gradlew.bat assembleDebug
```

发布版本必须使用您自己的 Android 密钥签名。如需在不丢失应用数据的情况下覆盖已安装版本，新 APK 必须使用与已安装 APK 相同的签名密钥。

## 隐私

图片和视频索引及向量搜索均在本地运行。仓库不会包含签名密钥或本机 Android SDK 配置。

## 上游项目及许可证

本修改基于 [greyovo/PicQuery](https://github.com/greyovo/PicQuery)。源代码继续采用 [MIT License](LICENSE)。模型文件及第三方依赖可能适用各自的条款。
