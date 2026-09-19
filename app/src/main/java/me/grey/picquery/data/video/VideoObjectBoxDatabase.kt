package me.grey.picquery.data.video

import android.content.Context
import io.objectbox.BoxStore
import me.grey.picquery.data.MyObjectBox
import me.grey.picquery.data.video.model.VideoFrameEmbedding
import me.grey.picquery.data.video.model.VideoRecord

class VideoObjectBoxDatabase private constructor() {
    private lateinit var boxStore: BoxStore

    @Synchronized
    fun initialize(context: Context) {
        if (::boxStore.isInitialized) return
        boxStore = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .name("video-index")
            .build()
    }

    fun repository(context: Context): VideoIndexRepository {
        check(::boxStore.isInitialized) { "Video ObjectBox is not initialized" }
        return VideoIndexRepository(
            context = context.applicationContext,
            store = boxStore,
            videoBox = boxStore.boxFor(VideoRecord::class.java),
            frameBox = boxStore.boxFor(VideoFrameEmbedding::class.java)
        )
    }

    companion object {
        val instance: VideoObjectBoxDatabase by lazy { VideoObjectBoxDatabase() }
    }
}
