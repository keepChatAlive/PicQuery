package me.grey.picquery.ui.video

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object VideoPlaybackLauncher {
    private val mxPlayerPackages = listOf(
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro",
        "com.mxtech.videoplayer"
    )

    fun open(context: Context, uriText: String, mimeType: String, timestampMs: Long) {
        val uri = Uri.parse(uriText)
        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "video/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("position", timestampMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        for (packageName in mxPlayerPackages) {
            try {
                context.startActivity(Intent(baseIntent).setPackage(packageName))
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next MX Player package.
            }
        }
        context.startActivity(Intent.createChooser(baseIntent, null))
    }
}
