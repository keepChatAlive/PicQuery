package me.grey.picquery.data.video

import android.content.Context
import android.net.Uri
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.query
import java.io.File
import me.grey.picquery.data.video.model.VideoFrameEmbedding
import me.grey.picquery.data.video.model.VideoFrameEmbedding_
import me.grey.picquery.data.video.model.VideoRecord
import me.grey.picquery.data.video.model.VideoSource

class VideoIndexRepository(
    private val context: Context,
    private val store: BoxStore,
    private val videoBox: Box<VideoRecord>,
    private val frameBox: Box<VideoFrameEmbedding>
) {
    val cacheRoot = File(context.noBackupFilesDir, "video-frame-cache")
    val stagingRoot = File(cacheRoot, "staging")
    val completedRoot = File(cacheRoot, "completed")

    init {
        stagingRoot.mkdirs()
        completedRoot.mkdirs()
    }

    fun allVideos(): List<VideoRecord> = videoBox.all

    fun reconcileAlbumMetadata(sources: List<VideoSource>) {
        val sourcesByContent = sources.associateBy(VideoSource::contentKey)
        val updated = videoBox.all.mapNotNull { record ->
            val source = sourcesByContent[record.contentKey] ?: return@mapNotNull null
            if (
                record.albumId == source.albumId &&
                record.albumName == source.albumName &&
                record.uri == source.uri
            ) return@mapNotNull null
            record.copy(
                sourceKey = source.sourceKey,
                uri = source.uri,
                albumId = source.albumId,
                albumName = source.albumName
            )
        }
        if (updated.isNotEmpty()) videoBox.put(updated)
    }

    fun videosForAlbum(albumId: Long): List<VideoRecord> =
        videoBox.all.filter { it.albumId == albumId }

    fun completedByContentKey(contentKey: String): VideoRecord? =
        videoBox.all.firstOrNull { it.contentKey == contentKey }

    fun framesForVideo(videoId: Long): List<VideoFrameEmbedding> = frameBox.query {
        equal(VideoFrameEmbedding_.videoId, videoId)
    }.find()

    fun frameById(frameId: Long): VideoFrameEmbedding? = frameBox.get(frameId)

    fun searchFrames(queryVector: FloatArray, topK: Int): List<Pair<VideoFrameEmbedding, Float>> {
        if (frameBox.isEmpty) return emptyList()
        val query = frameBox.query()
            .nearestNeighbors(VideoFrameEmbedding_.data, queryVector, topK.coerceAtLeast(1))
            .build()
        return try {
            query.findWithScores().map { it.get() to (1f - it.score.toFloat()) }
        } finally {
            query.close()
        }
    }

    fun commitVideo(record: VideoRecord, frames: List<VideoFrameEmbedding>): List<String> {
        val stale = videoBox.all.filter {
            it.sourceKey == record.sourceKey || it.contentKey == record.contentKey
        }
        val staleDirectories = stale.map { it.cacheDirectoryName }
        store.runInTx {
            stale.forEach { old ->
                frameBox.query { equal(VideoFrameEmbedding_.videoId, old.id) }.remove()
                videoBox.remove(old)
            }
            val videoId = videoBox.put(record)
            frames.forEach { it.videoId = videoId }
            frameBox.put(frames)
        }
        return staleDirectories.filter { it != record.cacheDirectoryName }
    }

    fun deleteCacheDirectories(names: Collection<String>) {
        names.forEach { name ->
            File(completedRoot, name).takeIf(File::exists)?.deleteRecursively()
        }
    }

    fun canOpen(video: VideoRecord): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(video.uri), "r")?.use {
            it.fileDescriptor.valid()
        } == true
    }.getOrDefault(false)

    fun deleteVideos(videos: Collection<VideoRecord>) {
        if (videos.isEmpty()) return
        val directories = videos.map { it.cacheDirectoryName }
        store.runInTx {
            videos.forEach { video ->
                frameBox.query { equal(VideoFrameEmbedding_.videoId, video.id) }.remove()
                videoBox.remove(video)
            }
        }
        deleteCacheDirectories(directories)
    }

    fun deleteAlbum(albumId: Long) = deleteVideos(videosForAlbum(albumId))

    fun cleanupIncompleteAndOrphanedCaches() {
        stagingRoot.listFiles()?.forEach(File::deleteRecursively)
        val referenced = videoBox.all.mapTo(mutableSetOf()) { it.cacheDirectoryName }
        completedRoot.listFiles()?.forEach { directory ->
            if (directory.name !in referenced) directory.deleteRecursively()
        }
    }

    fun thumbnailPath(video: VideoRecord, frame: VideoFrameEmbedding): String =
        File(File(completedRoot, video.cacheDirectoryName), frame.thumbnailFileName).absolutePath
}
