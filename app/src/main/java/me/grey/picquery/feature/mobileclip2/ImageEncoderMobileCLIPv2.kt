package me.grey.picquery.feature.mobileclip2

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.feature.ImageEncoderONNX

class ImageEncoderMobileCLIPv2(
    context: Context,
    preprocessor: PreprocessorMobileCLIPv2,
    dispatcher: CoroutineDispatcher,
    preferenceRepository: PreferenceRepository
) : ImageEncoderONNX(
    dim = PreprocessorMobileCLIPv2.IMAGE_SIZE.toLong(),
    context = context,
    modelPath = MODEL_PATH,
    preprocessor = preprocessor,
    dispatcher = dispatcher,
    modelFormat = null,
    preferenceRepository = preferenceRepository
) {
    companion object {
        const val MODEL_PATH = "vision_model.onnx"
    }
}
