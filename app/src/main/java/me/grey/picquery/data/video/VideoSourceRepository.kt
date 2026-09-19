package me.grey.picquery.data.video

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.util.Locale
import me.grey.picquery.data.video.model.VideoSource

class VideoSourceRepository(private val context: Context) {
    fun discover(): List<VideoSource> =
        discoverMediaStore()
            .sortedByDescending(VideoSource::modifiedSeconds)

    private fun discoverMediaStore(): List<VideoSource> {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        return runCatching {
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                        val name = cursor.getString(nameColumn).orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
                        val size = cursor.getLong(sizeColumn)
                        val modified = cursor.getLong(modifiedColumn)
                        val duration = cursor.getLong(durationColumn)
                        add(
                            VideoSource(
                                sourceKey = uri.toString(),
                                contentKey = contentKey(name, size, modified),
                                uri = uri.toString(),
                                displayName = name,
                                mimeType = cursor.getString(mimeColumn).orEmpty().ifBlank { "video/*" },
                                sizeBytes = size,
                                modifiedSeconds = modified,
                                durationMs = duration,
                                albumId = cursor.getLong(bucketIdColumn),
                                albumName = cursor.getString(bucketNameColumn).orEmpty()
                                    .ifBlank { "Videos" }
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun contentKey(name: String, size: Long, modifiedSeconds: Long): String =
        "${name.lowercase(Locale.ROOT)}|$size|$modifiedSeconds"

}
