package me.grey.picquery.data.video.model

data class VideoSource(
    val sourceKey: String,
    val contentKey: String,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedSeconds: Long,
    val durationMs: Long,
    val albumId: Long,
    val albumName: String
)

data class VideoAlbum(
    val id: Long,
    val name: String,
    val videoCount: Int,
    val indexedCount: Int = 0
)

data class VideoFrameMatch(
    val frameId: Long,
    val timestampMs: Long,
    val thumbnailPath: String,
    val similarity: Float
)

data class VideoSearchResult(
    val video: VideoRecord,
    val bestMatch: VideoFrameMatch,
    val matches: List<VideoFrameMatch>,
    val matchesLoaded: Boolean = false
)

data class IndexedVideoStatus(
    val video: VideoRecord,
    val isValid: Boolean
)

enum class VideoIndexStage {
    IDLE,
    DISCOVERING,
    DECODING,
    FALLBACK_DECODING,
    EMBEDDING,
    COMMITTING,
    COMPLETE,
    FAILED
}

data class VideoIndexFailure(
    val videoName: String,
    val videoUri: String,
    val stage: VideoIndexStage,
    val decoder: String,
    val exceptionType: String,
    val message: String,
    val details: String
)

data class VideoIndexProgress(
    val running: Boolean = false,
    val stage: VideoIndexStage = VideoIndexStage.IDLE,
    val currentVideo: String = "",
    val currentVideoIndex: Int = 0,
    val completedVideos: Int = 0,
    val skippedVideos: Int = 0,
    val totalVideos: Int = 0,
    val completedFrames: Int = 0,
    val totalFrames: Int = 0,
    val lastError: String? = null,
    val debugStatus: String = ""
)
