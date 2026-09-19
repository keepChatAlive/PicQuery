package me.grey.picquery.feature

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.IntBuffer
import java.nio.LongBuffer
import kotlin.math.sqrt
import me.grey.picquery.common.AssetUtil
import me.grey.picquery.feature.base.TextEncoder
import timber.log.Timber

abstract class TextEncoderONNX(private val context: Context) : TextEncoder {
    private val TAG = this.javaClass.simpleName
    abstract val modelPath: String
    abstract val modelType: Int
    open val modelFormat: String? = "ORT"
    private var ortSession: OrtSession? = null
    private var tokenizer: BPETokenizer? = null
    private var options: OrtSession.SessionOptions? = null

    init {
        Timber.tag(TAG).d("Init $TAG")
    }

    override fun encode(input: String): FloatArray {
        if (tokenizer == null) {
            tokenizer = BPETokenizer(context)
        }
        val token = tokenizer!!.tokenize(input)
        val intBuffer = IntBuffer.wrap(token.first)
        val shape = token.second

        val ortEnv = OrtEnvironment.getEnvironment()
        if (ortSession == null) {
            val modelFile = AssetUtil.assetFilePath(context, modelPath)
            check(modelFile.isNotBlank()) { "Unable to copy ONNX text model asset: $modelPath" }
            options = OrtSession.SessionOptions().apply {
                modelFormat?.let { addConfigEntry("session.load_model_format", it) }
            }
            ortSession = ortEnv.createSession(modelFile, checkNotNull(options))
        }

        val session = checkNotNull(ortSession) { "ONNX text encoder session is closed." }
        val inputName = session.inputNames.iterator().next()
        val tensor = when (modelType) {
            0 -> OnnxTensor.createTensor(ortEnv, intBuffer, shape)
            1 -> {
                val longBuffer = LongBuffer.allocate(intBuffer.capacity()).apply {
                    while (intBuffer.hasRemaining()) {
                        put(intBuffer.get().toLong())
                    }
                    flip()
                }
                OnnxTensor.createTensor(ortEnv, longBuffer, shape)
            }

            else -> throw IllegalArgumentException("Unknown buffer type")
        }
        tensor.use {
            session.run(mapOf(Pair(inputName, tensor))).use { output ->
                val resultBuffer = output.get(0) as OnnxTensor
                val floatBuffer = resultBuffer.floatBuffer
                val result = FloatArray(floatBuffer.remaining())
                floatBuffer.get(result)
                return result.l2Normalized()
            }
        }
    }

    private fun FloatArray.l2Normalized(): FloatArray {
        val norm = sqrt(fold(0.0) { sum, value -> sum + value * value }).toFloat()
        if (norm > 0f) {
            for (index in indices) this[index] /= norm
        }
        return this
    }
}
