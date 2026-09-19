package me.grey.picquery.ui.home

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.grey.picquery.R
import me.grey.picquery.common.calculateRemainingTime
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.ui.albums.EncodingState
import me.grey.picquery.domain.VideoIndexManager
import me.grey.picquery.data.video.model.VideoIndexStage
import org.koin.compose.koinInject

@Composable
fun IndexingProgressBars() {
    Column(modifier = Modifier.fillMaxWidth()) {
        EncodingProgressBar()
        VideoEncodingProgressBar()
    }
}

@Composable
fun EncodingProgressBar(albumManager: AlbumManager = koinInject()) {
    val state by remember { albumManager.encodingState }
    var progress = (state.current.toDouble() / state.total).toFloat()
    if (progress.isNaN()) progress = 0.0f
    val finished = state.status == EncodingState.Status.Finish

    fun onClickOk() {
        albumManager.clearIndexingState()
    }

    AnimatedVisibility(visible = state.status != EncodingState.Status.None) {
        Surface(tonalElevation = 3.dp) {
            Column(
                Modifier
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.indexing_progress,
                            state.current,
                            state.total
                        )
                    )
                    val remain = calculateRemainingTime(
                        state.current,
                        state.total,
                        state.cost
                    )
                    TextButton(
                        onClick = { onClickOk() },
                        enabled = finished
                    ) {
                        Text(
                            text = if (finished) {
                                stringResource(R.string.finish_button)
                            } else {
                                stringResource(R.string.estimate_remain_time) +
                                    " ${DateUtils.formatElapsedTime(remain)}"
                            }
                        )
                    }
                }
                Box(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun VideoEncodingProgressBar(manager: VideoIndexManager = koinInject()) {
    val state by manager.progress.collectAsState()
    val visible = state.running || state.stage == VideoIndexStage.COMPLETE || state.stage == VideoIndexStage.FAILED
    val frameRatio = if (state.totalFrames > 0) {
        (state.completedFrames.toFloat() / state.totalFrames).coerceIn(0f, 1f)
    } else 0f
    val currentRatio = when (state.stage) {
        VideoIndexStage.DECODING, VideoIndexStage.FALLBACK_DECODING -> frameRatio * 0.5f
        VideoIndexStage.EMBEDDING -> 0.5f + frameRatio * 0.5f
        VideoIndexStage.COMMITTING, VideoIndexStage.COMPLETE -> 1f
        else -> 0f
    }
    val overall = if (state.totalVideos > 0) {
        ((state.completedVideos + if (state.running) currentRatio else 0f) / state.totalVideos)
            .coerceIn(0f, 1f)
    } else 0f
    val stageLabel = stringResource(
        when (state.stage) {
            VideoIndexStage.DISCOVERING -> R.string.video_stage_discovering
            VideoIndexStage.DECODING -> R.string.video_stage_decoding
            VideoIndexStage.FALLBACK_DECODING -> R.string.video_stage_fallback_decoding
            VideoIndexStage.EMBEDDING -> R.string.video_stage_embedding
            VideoIndexStage.COMMITTING -> R.string.video_stage_committing
            VideoIndexStage.COMPLETE -> R.string.video_stage_complete
            VideoIndexStage.FAILED -> R.string.video_stage_failed
            VideoIndexStage.IDLE -> R.string.video_stage_idle
        }
    )

    AnimatedVisibility(visible = visible) {
        Surface(tonalElevation = 3.dp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.video_overall_progress, state.completedVideos, state.totalVideos),
                        style = MaterialTheme.typography.labelSmall
                    )
                    TextButton(
                        onClick = manager::clearProgress,
                        enabled = !state.running,
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(R.string.finish_button), style = MaterialTheme.typography.labelSmall)
                    }
                }
                LinearProgressIndicator(progress = { overall }, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(
                        R.string.video_current_progress,
                        state.currentVideoIndex,
                        state.totalVideos,
                        state.currentVideo,
                        stageLabel,
                        state.completedFrames,
                        state.totalFrames
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                LinearProgressIndicator(progress = { currentRatio }, modifier = Modifier.fillMaxWidth())
                if (state.debugStatus.isNotBlank()) {
                    Text(
                        state.debugStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                state.lastError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
