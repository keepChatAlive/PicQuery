package me.grey.picquery.data.video.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType

@Entity
data class VideoFrameEmbedding(
    @Id
    var id: Long = 0,
    @Index
    var videoId: Long = 0,
    val timestampMs: Long,
    val thumbnailFileName: String,
    @HnswIndex(
        dimensions = 512,
        distanceType = VectorDistanceType.COSINE
    )
    val data: FloatArray
)
