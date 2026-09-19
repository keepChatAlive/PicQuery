package me.grey.picquery.feature

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.grey.picquery.common.AssetUtil
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.data.model.InferenceBackend
import me.grey.picquery.feature.base.ImageEncoder
import me.grey.picquery.feature.base.Preprocessor
import timber.log.Timber

open class ImageEncoderONNX(
    private val dim: Long,
    modelPath: String,
    context: Context,
    private val preprocessor: Preprocessor,
    private val dispatcher: CoroutineDispatcher,
    private val modelFormat: String? = "ORT",
    private val preferenceRepository: PreferenceRepository? = null
) :
    ImageEncoder {

    private val TAG = this::class.java.simpleName
    private val appContext = context.applicationContext
    private val assetModelPath = modelPath
    private val sessionMutex = Mutex()

    var ortSession: OrtSession? = null
    val ortEnv = OrtEnvironment.getEnvironment()
    private var options: OrtSession.SessionOptions? = null
    private var activeKey: RuntimeKey? = null

    init {
        // Legacy ONNX/ORT subclasses access ortSession directly. The active S2
        // encoder supplies preferences and initializes lazily from its settings.
        if (preferenceRepository == null) createSession(InferenceBackend.CPU, 1)
    }

    fun clearSession() {
        ortSession?.close()
        ortSession = null
        options?.close()
        options = null
        activeKey = null
    }

    init {
        Log.d(TAG, "Init $TAG")
    }

    override suspend fun encodeBatch(bitmaps: List<Bitmap>): List<FloatArray> = withContext(dispatcher) {
        if (bitmaps.isEmpty()) return@withContext emptyList()
        Log.d(TAG, "${this@ImageEncoderONNX} Start encoding image...")

        val floatBuffer = preprocessor.preprocessBatch(bitmaps) as FloatBuffer
        val settings = preferenceRepository?.loadIndexingRuntimeSettings()
        val backend = settings?.backend ?: InferenceBackend.CPU
        val concurrency = settings?.concurrency ?: 1
        var session = ensureSession(backend, concurrency)
        try {
            return@withContext runSession(session, floatBuffer, bitmaps.size)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (backend == InferenceBackend.CPU) throw error
            Timber.tag(TAG).e(error, "NNAPI inference failed; retrying on CPU")
            sessionMutex.withLock {
                clearSession()
                createSession(InferenceBackend.CPU, concurrency)
                // Remember the requested setting so an unsupported NNAPI path
                // is not retried for every indexing batch.
                activeKey = RuntimeKey(backend, concurrency.coerceIn(1, 4))
            }
            session = checkNotNull(ortSession)
            floatBuffer.rewind()
            return@withContext runSession(session, floatBuffer, bitmaps.size)
        }
    }

    private fun runSession(
        session: OrtSession,
        floatBuffer: FloatBuffer,
        batchSize: Int
    ): List<FloatArray> {
        val inputName = session.inputNames.iterator().next()
        val shape: LongArray = longArrayOf(batchSize.toLong(), 3, dim, dim)
        OnnxTensor.createTensor(ortEnv, floatBuffer, shape).use { tensor ->
            session.run(Collections.singletonMap(inputName, tensor)).use { output ->
                val resultBuffer = output.get(0) as OnnxTensor
                val feat = resultBuffer.floatBuffer
                val embeddingSize = 512
                val numEmbeddings = feat.capacity() / embeddingSize
                val embeddings = mutableListOf<FloatArray>()

                for (i in 0 until numEmbeddings) {
                    val start = i * embeddingSize
                    val embeddingArray = FloatArray(embeddingSize)
                    feat.position(start)
                    for (j in 0 until embeddingSize) {
                        embeddingArray[j] = feat[start + j]
                    }
                    embeddings.add(embeddingArray.l2Normalized())
                }

                Log.d(TAG, "Finish encoding image!")
                return embeddings
            }
        }
    }

    private suspend fun ensureSession(
        backend: InferenceBackend,
        concurrency: Int
    ): OrtSession = sessionMutex.withLock {
        val key = RuntimeKey(backend, concurrency.coerceIn(1, 4))
        if (activeKey == key && ortSession != null) return@withLock checkNotNull(ortSession)
        clearSession()

        if (backend != InferenceBackend.CPU) {
            runCatching { createSession(backend, key.threads) }
                .onFailure { Timber.tag(TAG).w(it, "NNAPI unavailable; using ONNX Runtime CPU") }
            if (ortSession != null) {
                activeKey = key
                return@withLock checkNotNull(ortSession)
            }
        }

        createSession(InferenceBackend.CPU, key.threads)
        activeKey = key
        checkNotNull(ortSession)
    }

    private fun createSession(backend: InferenceBackend, threads: Int) {
        val modelFile = AssetUtil.assetFilePath(appContext, assetModelPath)
        check(modelFile.isNotBlank()) { "Unable to copy ONNX image model asset: $assetModelPath" }
        val newOptions = OrtSession.SessionOptions().apply {
            modelFormat?.let { addConfigEntry("session.load_model_format", it) }
            setIntraOpNumThreads(threads.coerceIn(1, 4))
            if (backend != InferenceBackend.CPU) addNnapi()
        }
        options = newOptions
        ortSession = try {
            ortEnv.createSession(modelFile, newOptions)
        } catch (error: Throwable) {
            newOptions.close()
            options = null
            throw error
        }
    }

    private fun FloatArray.l2Normalized(): FloatArray {
        val norm = sqrt(fold(0.0) { sum, value -> sum + value * value }).toFloat()
        if (norm > 0f) {
            for (index in indices) this[index] /= norm
        }
        return this
    }

    private data class RuntimeKey(val backend: InferenceBackend, val threads: Int)
}
