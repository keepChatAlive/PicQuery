package me.grey.picquery.data.video.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class VideoRecord(
    @Id
    var id: Long = 0,
    @Index
    val sourceKey: String,
    @Index
    val contentKey: String,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedSeconds: Long,
    val durationMs: Long,
    val cacheDirectoryName: String,
    val frameCount: Int,
    val decoder: String,
    val indexedAt: Long,
    @Index
    val albumId: Long = 0,
    val albumName: String = "",
    val indexVersion: Int = 0
)
