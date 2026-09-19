package me.grey.picquery.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.data.model.DifficultVideoPolicy
import me.grey.picquery.data.model.VideoDecodeSettings
import me.grey.picquery.data.model.VideoFastModeScope
import me.grey.picquery.data.video.VideoIndexRepository
import me.grey.picquery.data.video.VideoSourceRepository
import me.grey.picquery.data.video.model.VideoFrameEmbedding
import me.grey.picquery.data.video.model.VideoFrameMatch
import me.grey.picquery.data.video.model.VideoIndexProgress
import me.grey.picquery.data.video.model.VideoIndexStage
import me.grey.picquery.data.video.model.VideoIndexFailure
import me.grey.picquery.data.video.model.IndexedVideoStatus
import me.grey.picquery.data.video.model.VideoRecord
import me.grey.picquery.data.video.model.VideoSearchResult
import me.grey.picquery.data.video.model.VideoSource
import me.grey.picquery.data.video.model.VideoAlbum
import me.grey.picquery.feature.base.ImageEncoder
import me.grey.picquery.feature.base.TextEncoder
import timber.log.Timber

class VideoIndexManager(
    private val context: Context,
    private val sourceRepository: VideoSourceRepository,
    private val indexRepository: VideoIndexRepository,
    private val imageEncoder: ImageEncoder,
    private val textEncoder: TextEncoder,
    private val translator: MLKitTranslator,
    private val preferenceRepository: PreferenceRepository,
    private val searchConfigurationService: SearchConfigurationService,
    private val dispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope
) {
    private val indexing = AtomicBoolean(false)
    private val _sources = MutableStateFlow<List<VideoSource>>(emptyList())
    val sources: StateFlow<List<VideoSource>> = _sources.asStateFlow()
    private val _albums = MutableStateFlow<List<VideoAlbum>>(emptyList())
    val albums: StateFlow<List<VideoAlbum>> = _albums.asStateFlow()
    private val _indexedVideos = MutableStateFlow<List<VideoRecord>>(emptyList())
    val indexedVideos: StateFlow<List<VideoRecord>> = _indexedVideos.asStateFlow()
    private val _progress = MutableStateFlow(VideoIndexProgress())
    val progress: StateFlow<VideoIndexProgress> = _progress.asStateFlow()
    private val _failure = MutableStateFlow<VideoIndexFailure?>(null)
    val failure: StateFlow<VideoIndexFailure?> = _failure.asStateFlow()
    private var lastQueryVectors: List<FloatArray> = emptyList()
    private var lastExcludedFrameId: Long? = null

    init {
        scope.launch {
            indexRepository.cleanupIncompleteAndOrphanedCaches()
            refresh()
        }
    }

    suspend fun refresh() = withContext(dispatcher) {
        _progress.value = _progress.value.copy(stage = VideoIndexStage.DISCOVERING)
        _sources.value = sourceRepository.discover()
        indexRepository.reconcileAlbumMetadata(_sources.value)
        _indexedVideos.value = indexRepository.allVideos()
        val indexedCounts = _indexedVideos.value
            .filter { it.indexVersion == VIDEO_INDEX_VERSION }
            .groupingBy(VideoRecord::albumId)
            .eachCount()
        _albums.value = _sources.value
            .groupBy(VideoSource::albumId)
            .map { (albumId, videos) ->
                VideoAlbum(
                    id = albumId,
                    name = videos.first().albumName,
                    videoCount = videos.size,
                    indexedCount = indexedCounts[albumId] ?: 0
                )
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
        if (!_progress.value.running) {
            _progress.value = _progress.value.copy(stage = VideoIndexStage.IDLE)
        }
    }

    suspend fun indexedVideoStatuses(): List<IndexedVideoStatus> = withContext(dispatcher) {
        indexRepository.allVideos()
            .sortedByDescending(VideoRecord::indexedAt)
            .map { IndexedVideoStatus(video = it, isValid = indexRepository.canOpen(it)) }
    }

    suspend fun cleanupInvalidVideos(): Int = withContext(dispatcher) {
        if (indexing.get()) return@withContext 0
        val invalid = indexRepository.allVideos().filterNot(indexRepository::canOpen)
        indexRepository.deleteVideos(invalid)
        _indexedVideos.value = indexRepository.allVideos()
        invalid.size
    }

    suspend fun deleteIndexedVideo(videoId: Long): Boolean = withContext(dispatcher) {
        if (indexing.get()) return@withContext false
        val video = indexRepository.allVideos().firstOrNull { it.id == videoId }
            ?: return@withContext false
        indexRepository.deleteVideos(listOf(video))
        _indexedVideos.value = indexRepository.allVideos()
        true
    }

    suspend fun deleteAlbum(albumId: Long): Boolean = withContext(dispatcher) {
        if (indexing.get()) return@withContext false
        indexRepository.deleteAlbum(albumId)
        refresh()
        true
    }

    fun clearProgress() {
        if (!indexing.get()) _progress.value = VideoIndexProgress()
    }

    fun dismissFailure() {
        _failure.value = null
    }

    fun startIndexing(albumIds: Set<Long>) {
        if (albumIds.isEmpty()) return
        if (!indexing.compareAndSet(false, true)) return
        _failure.value = null
        _progress.value = VideoIndexProgress(
            running = true,
            stage = VideoIndexStage.DISCOVERING
        )
        scope.launch {
            try {
                indexRepository.cleanupIncompleteAndOrphanedCaches()
                refresh()
                val pending = _sources.value.filter { source ->
                    source.albumId in albumIds &&
                    indexRepository.completedByContentKey(source.contentKey)
                        ?.indexVersion != VIDEO_INDEX_VERSION
                }
                _progress.value = VideoIndexProgress(
                    running = true,
                    stage = VideoIndexStage.DECODING,
                    totalVideos = pending.size
                )
                var processed = 0
                var skipped = 0
                for (video in pending) {
                    try {
                        indexOneVideo(video, processed, skipped, pending.size)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: DifficultVideoException) {
                        Timber.tag(TAG).w(error, "Difficult video: ${video.displayName}")
                        indexRepository.stagingRoot.listFiles()?.forEach(File::deleteRecursively)
                        if (error.policy == DifficultVideoPolicy.SKIP) {
                            processed++
                            skipped++
                            _progress.value = _progress.value.copy(
                                stage = VideoIndexStage.DECODING,
                                completedVideos = processed,
                                currentVideoIndex = processed,
                                skippedVideos = skipped,
                                completedFrames = 0,
                                totalFrames = 0,
                                lastError = error.message
                            )
                            continue
                        }
                        publishFailure(video, error)
                        _progress.value = _progress.value.copy(
                            running = false,
                            stage = VideoIndexStage.FAILED,
                            skippedVideos = skipped,
                            lastError = error.message ?: error.javaClass.simpleName
                        )
                        return@launch
                    } catch (error: Throwable) {
                        Timber.tag(TAG).e(error, "Failed to index ${video.displayName}")
                        indexRepository.stagingRoot.listFiles()?.forEach(File::deleteRecursively)
                        publishFailure(video, error)
                        _progress.value = _progress.value.copy(
                            running = false,
                            stage = VideoIndexStage.FAILED,
                            lastError = error.message ?: error.javaClass.simpleName
                        )
                        return@launch
                    }
                    processed++
                    _progress.value = _progress.value.copy(
                        completedVideos = processed,
                        currentVideoIndex = processed,
                        skippedVideos = skipped,
                        completedFrames = _progress.value.totalFrames
                    )
                }
                _indexedVideos.value = indexRepository.allVideos()
                _progress.value = _progress.value.copy(
                    running = false,
                    stage = VideoIndexStage.COMPLETE,
                    completedVideos = processed,
                    skippedVideos = skipped,
                    totalVideos = pending.size,
                    lastError = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Timber.tag(TAG).e(error, "Video indexing failed before processing")
                publishFailure(null, error)
                _progress.value = _progress.value.copy(
                    running = false,
                    stage = VideoIndexStage.FAILED,
                    lastError = error.message ?: error.javaClass.simpleName
                )
            } finally {
                indexing.set(false)
            }
        }
    }

    private suspend fun indexOneVideo(
        source: VideoSource,
        completed: Int,
        skipped: Int,
        total: Int
    ) = withContext(dispatcher) {
        val directoryName = sha256(source.contentKey)
        val stagingDirectory = File(indexRepository.stagingRoot, directoryName)
        stagingDirectory.deleteRecursively()
        check(stagingDirectory.mkdirs()) { "Unable to create frame staging directory" }

        _progress.value = VideoIndexProgress(
            running = true,
            stage = VideoIndexStage.DECODING,
            currentVideo = source.displayName,
            currentVideoIndex = completed + 1,
            completedVideos = completed,
            skippedVideos = skipped,
            totalVideos = total
        )

        var durationMs = source.durationMs
        var decoder = "Android"
        var decoded = decodeWithAndroid(source, stagingDirectory) { current, count, duration ->
            durationMs = duration
            _progress.value = _progress.value.copy(completedFrames = current, totalFrames = count)
        }
        if (!decoded) {
            stagingDirectory.deleteRecursively()
            check(stagingDirectory.mkdirs()) { "Unable to recreate frame staging directory" }
            decoder = "FFmpeg"
            val expectedFrames = ceil(durationMs.toDouble() / SAMPLE_INTERVAL_MS)
                .toInt().coerceAtLeast(1)
            _progress.value = _progress.value.copy(
                stage = VideoIndexStage.FALLBACK_DECODING,
                completedFrames = 0,
                totalFrames = expectedFrames
            )
            decodeWithFfmpeg(source, stagingDirectory)
            decoded = true
        }
        check(decoded) { "Both Android and FFmpeg decoding failed" }

        val files = stagingDirectory.listFiles { file -> file.extension.equals("jpg", true) }
            ?.sortedBy { file -> file.nameWithoutExtension.toLongOrNull() ?: Long.MAX_VALUE }
            .orEmpty()
        check(files.isNotEmpty()) { "Decoder produced no frames" }
        if (durationMs <= 0) {
            durationMs = if (decoder == "FFmpeg") {
                (files.maxOfOrNull { it.nameWithoutExtension.toLongOrNull() ?: 0L } ?: 0L) +
                    SAMPLE_INTERVAL_MS
            } else {
                files.size * SAMPLE_INTERVAL_MS
            }
        }

        val concurrency = preferenceRepository.loadIndexingRuntimeSettings().concurrency.coerceIn(1, 4)
        val frameEntities = ArrayList<VideoFrameEmbedding>(files.size)
        _progress.value = _progress.value.copy(
            stage = VideoIndexStage.EMBEDDING,
            completedFrames = 0,
            totalFrames = files.size,
            debugStatus = ""
        )
        files.chunked(concurrency).forEachIndexed { chunkIndex, chunk ->
            val bitmaps = chunk.map { file ->
                checkNotNull(BitmapFactory.decodeFile(file.absolutePath)) { "Unable to read ${file.name}" }
            }
            try {
                val vectors = imageEncoder.encodeBatch(bitmaps)
                check(vectors.size == chunk.size) { "Embedding count mismatch" }
                chunk.forEachIndexed { index, file ->
                    val fallbackOrdinal = (chunkIndex * concurrency + index).toLong()
                    val timestampMs = if (decoder == "FFmpeg") {
                        file.nameWithoutExtension.toLongOrNull()?.coerceAtLeast(0L)
                            ?: fallbackOrdinal * SAMPLE_INTERVAL_MS
                    } else {
                        fallbackOrdinal * SAMPLE_INTERVAL_MS
                    }
                    frameEntities += VideoFrameEmbedding(
                        timestampMs = timestampMs,
                        thumbnailFileName = file.name,
                        data = vectors[index]
                    )
                }
            } finally {
                bitmaps.forEach(Bitmap::recycle)
            }
            _progress.value = _progress.value.copy(
                completedFrames = frameEntities.size,
                totalFrames = files.size
            )
        }

        _progress.value = _progress.value.copy(stage = VideoIndexStage.COMMITTING)
        val finalDirectory = File(indexRepository.completedRoot, directoryName)
        finalDirectory.deleteRecursively()
        check(stagingDirectory.renameTo(finalDirectory)) { "Unable to publish frame cache" }
        val record = VideoRecord(
            sourceKey = source.sourceKey,
            contentKey = source.contentKey,
            uri = source.uri,
            displayName = source.displayName,
            mimeType = source.mimeType,
            sizeBytes = source.sizeBytes,
            modifiedSeconds = source.modifiedSeconds,
            durationMs = durationMs,
            cacheDirectoryName = directoryName,
            frameCount = frameEntities.size,
            decoder = decoder,
            indexedAt = System.currentTimeMillis(),
            albumId = source.albumId,
            albumName = source.albumName,
            indexVersion = VIDEO_INDEX_VERSION
        )
        val staleDirectories = indexRepository.commitVideo(record, frameEntities)
        indexRepository.deleteCacheDirectories(staleDirectories)
        _indexedVideos.value = indexRepository.allVideos()
    }

    private fun decodeWithAndroid(
        source: VideoSource,
        directory: File,
        onProgress: (Int, Int, Long) -> Unit
    ): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(source.uri))
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: source.durationMs
            if (duration <= 0) return false
            val expected = ceil(duration.toDouble() / SAMPLE_INTERVAL_MS).toInt().coerceAtLeast(1)
            val frameWidth = scaledFrameWidth(retriever)
            val frameHeight = scaledFrameHeight(retriever)
            repeat(expected) { index ->
                val timeMs = index * SAMPLE_INTERVAL_MS
                val decodedBitmap = retriever.getScaledFrameAtTime(
                    timeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    frameWidth,
                    frameHeight
                ) ?: return false
                val bitmap = centerCrop(decodedBitmap)
                try {
                    File(directory, frameFileName(index)).outputStream().buffered().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
                    }
                } finally {
                    bitmap.recycle()
                    decodedBitmap.recycle()
                }
                onProgress(index + 1, expected, duration)
            }
            true
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Android decoder failed for ${source.displayName}")
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun decodeWithFfmpeg(source: VideoSource, directory: File) {
        val decodeSettings = preferenceRepository.loadVideoDecodeSettings()
        when (decodeSettings.fastModeScope) {
            VideoFastModeScope.NONE -> decodeFfmpegAttempt(
                source = source,
                directory = directory,
                decodeSettings = decodeSettings,
                fastMode = false,
                retryFastOnDifficult = false
            )

            VideoFastModeScope.ALL -> decodeFastWithCompatibilityFallback(
                source = source,
                directory = directory,
                decodeSettings = decodeSettings
            )

            VideoFastModeScope.DIFFICULT_ONLY -> {
                try {
                    decodeFfmpegAttempt(
                        source = source,
                        directory = directory,
                        decodeSettings = decodeSettings,
                        fastMode = false,
                        retryFastOnDifficult = true
                    )
                } catch (retry: RetryWithFastModeException) {
                    resetFfmpegAttemptDirectory(
                        directory,
                        "Normal FFmpeg was below 0.5x twice; restarting with fast mode"
                    )
                    decodeFastWithCompatibilityFallback(source, directory, decodeSettings)
                }
            }
        }
    }

    private suspend fun decodeFastWithCompatibilityFallback(
        source: VideoSource,
        directory: File,
        decodeSettings: VideoDecodeSettings
    ) {
        try {
            decodeFfmpegAttempt(
                source = source,
                directory = directory,
                decodeSettings = decodeSettings,
                fastMode = true,
                retryFastOnDifficult = false
            )
        } catch (unsupported: FFmpegCommandException) {
            Timber.tag(TAG).w(unsupported, "FFmpeg fast profile rejected; retrying normally")
            resetFfmpegAttemptDirectory(
                directory,
                "Fast profile was rejected by this codec; retrying normal FFmpeg"
            )
            decodeFfmpegAttempt(
                source = source,
                directory = directory,
                decodeSettings = decodeSettings,
                fastMode = false,
                retryFastOnDifficult = false
            )
        }
    }

    private fun resetFfmpegAttemptDirectory(directory: File, status: String) {
        directory.deleteRecursively()
        check(directory.mkdirs()) { "Unable to reset FFmpeg frame staging directory" }
        _progress.value = _progress.value.copy(
            stage = VideoIndexStage.FALLBACK_DECODING,
            completedFrames = 0,
            debugStatus = status
        )
    }

    private suspend fun decodeFfmpegAttempt(
        source: VideoSource,
        directory: File,
        decodeSettings: VideoDecodeSettings,
        fastMode: Boolean,
        retryFastOnDifficult: Boolean
    ) {
        val output = File(directory, "%d.jpg").absolutePath
        val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(source.uri), "r")
            ?: error("MediaStore did not provide a readable file descriptor")
        descriptor.use {
            val input = "/proc/self/fd/${it.fd}"
            val durationLimit = if (source.durationMs > 0) {
                "-t ${String.format(Locale.US, "%.3f", source.durationMs / 1000.0)} "
            } else ""
            val decoderFastOptions = if (fastMode) {
                "-lowres 2 -skip_loop_filter all -skip_frame nokey -flags2 +fast "
            } else ""
            val samplingFilter = if (fastMode) "" else "fps=1/3,"
            val scaleFlags = if (fastMode) ":flags=fast_bilinear" else ""
            val command = "-nostdin -hide_banner -loglevel warning -fflags +discardcorrupt " +
                "-err_detect ignore_err -threads ${decodeSettings.ffmpegThreads} " +
                decoderFastOptions + "-y " +
                "-i ${quote(input)} -map 0:v:0 -an -sn -dn " +
                durationLimit +
                "-vf \"${samplingFilter}settb=expr=1/1000," +
                "scale=$FRAME_SIZE:$FRAME_SIZE:" +
                "force_original_aspect_ratio=increase$scaleFlags,crop=$FRAME_SIZE:$FRAME_SIZE\" " +
                "-fps_mode vfr -enc_time_base 1:1000 -frame_pts 1 -q:v 2 ${quote(output)}"

            val completion = CompletableDeferred<com.arthenica.ffmpegkit.FFmpegSession>()
            val recentLogs = ArrayDeque<String>()
            val startMs = SystemClock.elapsedRealtime()
            val lastProgressMs = AtomicLong(startMs)
            val lastMediaTimeMs = AtomicLong(0)
            val lastFrame = AtomicInteger(0)
            val lastSpeed = AtomicLong(0)
            val consecutiveSlowReports = AtomicInteger(0)
            val difficultVideoDetected = AtomicBoolean(false)

            val session = FFmpegKit.executeAsync(
                command,
                { completed -> completion.complete(completed) },
                { log ->
                    synchronized(recentLogs) {
                        recentLogs.addLast(log.message)
                        while (recentLogs.size > MAX_FFMPEG_LOG_LINES) recentLogs.removeFirst()
                    }
                },
                { statistics ->
                    val frame = statistics.videoFrameNumber.coerceAtLeast(0)
                    val mediaTime = statistics.time.coerceAtLeast(0.0).toLong()
                    if (frame > lastFrame.get() || mediaTime > lastMediaTimeMs.get()) {
                        lastFrame.set(frame)
                        lastMediaTimeMs.set(mediaTime)
                        lastProgressMs.set(SystemClock.elapsedRealtime())
                    }
                    val speedMilli = (statistics.speed * 1000).toLong()
                    lastSpeed.set(speedMilli)
                    val hasDecodedProgress = frame > 0 || mediaTime > 0
                    if (hasDecodedProgress && speedMilli in 0 until DIFFICULT_VIDEO_SPEED_MILLI) {
                        if (consecutiveSlowReports.incrementAndGet() >= DIFFICULT_VIDEO_REPORT_COUNT) {
                            difficultVideoDetected.set(true)
                        }
                    } else {
                        consecutiveSlowReports.set(0)
                    }
                    _progress.value = _progress.value.copy(
                        completedFrames = frame.coerceAtMost(_progress.value.totalFrames),
                        debugStatus = ffmpegDebugStatus(
                            frame = frame,
                            mediaTimeMs = mediaTime,
                            speedMilli = lastSpeed.get(),
                            idleMs = 0,
                            fastMode = fastMode
                        )
                    )
                }
            )

            var watchdogFailure: String? = null
            var difficultFailure = false
            var retryWithFastMode = false
            try {
                while (!completion.isCompleted) {
                    delay(FFMPEG_WATCHDOG_POLL_MS)
                    val now = SystemClock.elapsedRealtime()
                    val elapsed = now - startMs
                    val idle = now - lastProgressMs.get()
                    _progress.value = _progress.value.copy(
                        debugStatus = ffmpegDebugStatus(
                            frame = lastFrame.get(),
                            mediaTimeMs = lastMediaTimeMs.get(),
                            speedMilli = lastSpeed.get(),
                            idleMs = idle,
                            fastMode = fastMode
                        )
                    )
                    if (difficultVideoDetected.get() &&
                        (retryFastOnDifficult ||
                            decodeSettings.difficultVideoPolicy != DifficultVideoPolicy.CONTINUE)
                    ) {
                        difficultFailure = true
                        retryWithFastMode = retryFastOnDifficult
                        watchdogFailure = "FFmpeg reported a decoding speed below 0.5x twice consecutively; " +
                            "policy=${decodeSettings.difficultVideoPolicy.name}, " +
                            "threads=${decodeSettings.ffmpegThreads}, " +
                            "mode=${if (fastMode) "FAST" else "NORMAL"}"
                        break
                    }
                    if (elapsed >= FFMPEG_STARTUP_GRACE_MS && idle >= FFMPEG_NO_PROGRESS_TIMEOUT_MS) {
                        watchdogFailure = "FFmpeg made no frame/time progress for ${idle / 1000}s"
                        break
                    }
                }
                if (watchdogFailure != null) {
                    FFmpegKit.cancel(session.sessionId)
                    withTimeoutOrNull(FFMPEG_CANCEL_WAIT_MS) { completion.await() }
                    val diagnostics = buildFfmpegDiagnostics(
                            reason = watchdogFailure,
                            command = command,
                            elapsedMs = SystemClock.elapsedRealtime() - startMs,
                            mediaTimeMs = lastMediaTimeMs.get(),
                            frame = lastFrame.get(),
                            speedMilli = lastSpeed.get(),
                            logs = synchronized(recentLogs) { recentLogs.joinToString("") }
                        )
                    if (difficultFailure) {
                        if (retryWithFastMode) throw RetryWithFastModeException(diagnostics)
                        throw DifficultVideoException(
                            policy = decodeSettings.difficultVideoPolicy,
                            message = diagnostics
                        )
                    }
                    throw FFmpegWatchdogException(diagnostics)
                }
                val completedSession = completion.await()
                if (!ReturnCode.isSuccess(completedSession.returnCode)) {
                    throw FFmpegCommandException(
                        buildFfmpegDiagnostics(
                        reason = "FFmpeg failed with return code ${completedSession.returnCode}",
                        command = command,
                        elapsedMs = SystemClock.elapsedRealtime() - startMs,
                        mediaTimeMs = lastMediaTimeMs.get(),
                        frame = lastFrame.get(),
                        speedMilli = lastSpeed.get(),
                        logs = synchronized(recentLogs) { recentLogs.joinToString("") }
                        )
                    )
                }
            } finally {
                if (!completion.isCompleted) FFmpegKit.cancel(session.sessionId)
            }
        }
        if (directory.listFiles { file -> file.extension.equals("jpg", true) }
                .orEmpty().isEmpty()
        ) {
            throw FFmpegCommandException("FFmpeg completed without producing frames")
        }
    }

    private fun ffmpegDebugStatus(
        frame: Int,
        mediaTimeMs: Long,
        speedMilli: Long,
        idleMs: Long,
        fastMode: Boolean
    ): String = "FFmpeg mode=${if (fastMode) "FAST" else "NORMAL"} frame=$frame media=${mediaTimeMs}ms " +
        "speed=${String.format(Locale.US, "%.2f", speedMilli / 1000.0)}x idle=${idleMs / 1000}s"

    private fun buildFfmpegDiagnostics(
        reason: String,
        command: String,
        elapsedMs: Long,
        mediaTimeMs: Long,
        frame: Int,
        speedMilli: Long,
        logs: String
    ): String = buildString {
        appendLine(reason)
        appendLine("elapsed=${elapsedMs}ms, mediaTime=${mediaTimeMs}ms, frame=$frame, speed=${speedMilli / 1000.0}x")
        appendLine("command=$command")
        appendLine("recent FFmpeg logs:")
        append(logs.takeLast(MAX_FFMPEG_LOG_CHARS))
    }

    private fun publishFailure(source: VideoSource?, error: Throwable) {
        val stage = _progress.value.stage
        val decoder = when (stage) {
            VideoIndexStage.DECODING -> "Android MediaMetadataRetriever"
            VideoIndexStage.FALLBACK_DECODING -> "FFmpeg"
            VideoIndexStage.EMBEDDING -> "MobileCLIP2-S2"
            VideoIndexStage.COMMITTING -> "ObjectBox commit"
            else -> "Video index coordinator"
        }
        _failure.value = VideoIndexFailure(
            videoName = source?.displayName.orEmpty(),
            videoUri = source?.uri.orEmpty(),
            stage = stage,
            decoder = decoder,
            exceptionType = error.javaClass.name,
            message = error.message ?: error.javaClass.simpleName,
            details = error.stackTraceToString()
        )
    }

    private fun scaledFrameWidth(retriever: MediaMetadataRetriever): Int {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: return FRAME_SIZE
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: return FRAME_SIZE
        return if (width >= height) {
            (FRAME_SIZE.toDouble() * width / height.coerceAtLeast(1)).toInt().coerceAtLeast(FRAME_SIZE)
        } else FRAME_SIZE
    }

    private fun scaledFrameHeight(retriever: MediaMetadataRetriever): Int {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: return FRAME_SIZE
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: return FRAME_SIZE
        return if (height >= width) {
            (FRAME_SIZE.toDouble() * height / width.coerceAtLeast(1)).toInt().coerceAtLeast(FRAME_SIZE)
        } else FRAME_SIZE
    }

    private fun centerCrop(input: Bitmap): Bitmap {
        val side = minOf(input.width, input.height)
        val left = (input.width - side) / 2
        val top = (input.height - side) / 2
        return Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(
                input,
                Rect(left, top, left + side, top + side),
                Rect(0, 0, FRAME_SIZE, FRAME_SIZE),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        }
    }

    suspend fun search(query: String): List<VideoSearchResult> = withContext(dispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        val candidates = queryCandidates(query)
        val vectors = candidates.distinct().map(textEncoder::encode)
        lastQueryVectors = vectors
        lastExcludedFrameId = null
        buildVideoSearchResults(vectors)
    }

    suspend fun searchSimilarFrame(frameId: Long): List<VideoSearchResult> = withContext(dispatcher) {
        val sourceFrame = indexRepository.frameById(frameId) ?: return@withContext emptyList()
        val vectors = listOf(sourceFrame.data)
        lastQueryVectors = vectors
        lastExcludedFrameId = sourceFrame.id
        buildVideoSearchResults(vectors, excludedFrameId = sourceFrame.id)
    }

    private fun buildVideoSearchResults(
        vectors: List<FloatArray>,
        excludedFrameId: Long? = null
    ): List<VideoSearchResult> {
        val topK = searchConfigurationService.getTopK()
        val threshold = searchConfigurationService.getMatchThreshold()
        val frameTopK = (topK * FRAME_OVERSAMPLE).coerceIn(100, MAX_FRAME_CANDIDATES)
        val bestFrames = linkedMapOf<Long, Pair<VideoFrameEmbedding, Float>>()
        vectors.forEach { vector ->
            indexRepository.searchFrames(vector, frameTopK)
                .filter { (frame, _) -> frame.id != excludedFrameId }
                .filter { it.second >= threshold }
                .forEach { (frame, similarity) ->
                    val previous = bestFrames[frame.id]
                    if (previous == null || similarity > previous.second) {
                        bestFrames[frame.id] = frame to similarity
                    }
                }
        }
        val videos = indexRepository.allVideos().associateBy(VideoRecord::id)
        return bestFrames.values
            .groupBy { it.first.videoId }
            .mapNotNull { (videoId, frames) ->
                val video = videos[videoId] ?: return@mapNotNull null
                val matches = frames.sortedByDescending { it.second }.map { (frame, similarity) ->
                    VideoFrameMatch(
                        frameId = frame.id,
                        timestampMs = frame.timestampMs,
                        thumbnailPath = indexRepository.thumbnailPath(video, frame),
                        similarity = similarity
                    )
                }
                val best = matches.maxByOrNull(VideoFrameMatch::similarity) ?: return@mapNotNull null
                VideoSearchResult(video = video, bestMatch = best, matches = matches)
            }
            .sortedByDescending { it.bestMatch.similarity }
            .take(topK)
    }

    suspend fun loadAllMatches(videoId: Long): List<VideoFrameMatch> = withContext(dispatcher) {
        val video = indexRepository.allVideos().firstOrNull { it.id == videoId } ?: return@withContext emptyList()
        val threshold = searchConfigurationService.getMatchThreshold()
        val dedupThreshold = preferenceRepository.loadVideoSceneDedupThreshold()
        val vectors = lastQueryVectors
        if (vectors.isEmpty()) return@withContext emptyList()
        val candidates = indexRepository.framesForVideo(videoId)
            .filter { frame -> frame.id != lastExcludedFrameId }
            .mapNotNull { frame ->
                val similarity = vectors.maxOf { vector -> cosine(vector, frame.data) }
                if (similarity < threshold) return@mapNotNull null
                ScoredVideoFrame(frame, similarity, vectorNorm(frame.data))
            }
            .sortedByDescending(ScoredVideoFrame::querySimilarity)

        val retained = ArrayList<ScoredVideoFrame>(candidates.size)
        candidates.forEach { candidate ->
            val isNearDuplicate = retained.any { existing ->
                cosineWithNorms(
                    candidate.frame.data,
                    existing.frame.data,
                    candidate.norm,
                    existing.norm
                ) > dedupThreshold
            }
            if (!isNearDuplicate) retained += candidate
        }

        retained.map { candidate ->
            VideoFrameMatch(
                frameId = candidate.frame.id,
                timestampMs = candidate.frame.timestampMs,
                thumbnailPath = indexRepository.thumbnailPath(video, candidate.frame),
                similarity = candidate.querySimilarity
            )
        }
    }

    private suspend fun queryCandidates(query: String): List<String> {
        val translated = CompletableDeferred<String>()
        runCatching {
            translator.translate(
                query,
                onSuccess = { translated.complete(it) },
                onError = { translated.complete(query) }
            )
        }.onFailure { translated.complete(query) }
        return ChineseQueryExpander.mergeCandidates(query, translated.await())
    }

    private fun cosine(left: FloatArray, right: FloatArray): Float {
        var result = 0f
        val size = minOf(left.size, right.size)
        for (index in 0 until size) result += left[index] * right[index]
        return result
    }

    private fun vectorNorm(vector: FloatArray): Double {
        var squaredNorm = 0.0
        vector.forEach { value -> squaredNorm += value.toDouble() * value }
        return sqrt(squaredNorm)
    }

    private fun cosineWithNorms(
        left: FloatArray,
        right: FloatArray,
        leftNorm: Double,
        rightNorm: Double
    ): Float {
        if (leftNorm == 0.0 || rightNorm == 0.0) return 0f
        var dot = 0.0
        val size = minOf(left.size, right.size)
        for (index in 0 until size) dot += left[index].toDouble() * right[index]
        return (dot / (leftNorm * rightNorm)).toFloat().coerceIn(-1f, 1f)
    }

    private fun frameFileName(index: Int) = String.format(Locale.US, "%08d.jpg", index)

    private fun quote(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(16)
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "VideoIndexManager"
        private const val SAMPLE_INTERVAL_MS = 3_000L
        private const val FRAME_SIZE = 256
        private const val JPEG_QUALITY = 95
        // Decode speed/quality preferences are deliberately excluded. Changing
        // fast mode must never invalidate already committed video indexes.
        private const val VIDEO_INDEX_VERSION = 2
        private const val FFMPEG_NO_PROGRESS_TIMEOUT_MS = 75_000L
        private const val FFMPEG_STARTUP_GRACE_MS = 30_000L
        private const val FFMPEG_WATCHDOG_POLL_MS = 1_000L
        private const val FFMPEG_CANCEL_WAIT_MS = 10_000L
        private const val DIFFICULT_VIDEO_SPEED_MILLI = 500L
        private const val DIFFICULT_VIDEO_REPORT_COUNT = 2
        private const val MAX_FFMPEG_LOG_LINES = 80
        private const val MAX_FFMPEG_LOG_CHARS = 12_000
        private const val FRAME_OVERSAMPLE = 20
        private const val MAX_FRAME_CANDIDATES = 50_000
    }
}

private data class ScoredVideoFrame(
    val frame: VideoFrameEmbedding,
    val querySimilarity: Float,
    val norm: Double
)

private class FFmpegWatchdogException(message: String) : IllegalStateException(message)

private class FFmpegCommandException(message: String) : IllegalStateException(message)

private class RetryWithFastModeException(message: String) : IllegalStateException(message)

private class DifficultVideoException(
    val policy: DifficultVideoPolicy,
    message: String
) : IllegalStateException(message)
