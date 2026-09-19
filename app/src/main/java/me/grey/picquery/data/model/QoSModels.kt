package me.grey.picquery.data.model

import kotlinx.serialization.Serializable

enum class InferenceBackend {
    AUTO,
    CPU,
    GPU
}

data class IndexingRuntimeSettings(
    val backend: InferenceBackend = InferenceBackend.AUTO,
    val concurrency: Int = 2
)

enum class DifficultVideoPolicy {
    FAIL_FAST,
    SKIP,
    CONTINUE
}

enum class VideoFastModeScope {
    NONE,
    DIFFICULT_ONLY,
    ALL
}

data class VideoDecodeSettings(
    val difficultVideoPolicy: DifficultVideoPolicy = DifficultVideoPolicy.FAIL_FAST,
    val ffmpegThreads: Int = 2,
    val fastModeScope: VideoFastModeScope = VideoFastModeScope.NONE
)

@Serializable
enum class SavedSearchType {
    TEXT,
    SIMILAR_IMAGE
}

@Serializable
data class SavedSearch(
    val id: String,
    val query: String,
    val resultPhotoIds: List<Long>,
    val createdAt: Long,
    val pinned: Boolean = false,
    val type: SavedSearchType = SavedSearchType.TEXT,
    val sourcePhotoId: Long? = null
)
