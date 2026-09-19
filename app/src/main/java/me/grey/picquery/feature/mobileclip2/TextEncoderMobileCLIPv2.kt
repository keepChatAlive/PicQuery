package me.grey.picquery.feature.mobileclip2

import android.content.Context
import me.grey.picquery.feature.TextEncoderONNX

class TextEncoderMobileCLIPv2(context: Context) : TextEncoderONNX(context) {
    override val modelPath: String = "text_model.onnx"
    override val modelType: Int = 1
    override val modelFormat: String? = null
}
