package me.grey.picquery.ui.video

import SearchInput
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import java.io.File
import java.util.Locale
import me.grey.picquery.R
import me.grey.picquery.data.video.model.VideoFrameMatch
import me.grey.picquery.data.video.model.VideoSearchResult
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalGlideComposeApi::class, InternalTextApi::class)
@Composable
fun VideoScreen(
    initialQuery: String,
    onNavigateBack: () -> Unit,
    viewModel: VideoViewModel = koinViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val selectedResult by viewModel.selectedResult.collectAsState()
    val loadingScenes by viewModel.loadingScenes.collectAsState()
    val similarSceneSearch by viewModel.similarSceneSearch.collectAsState()

    LaunchedEffect(initialQuery) {
        viewModel.setQuery(initialQuery)
        viewModel.search(initialQuery)
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchInput(
                queryText = query,
                onStartSearch = viewModel::search,
                onImageSearch = {},
                onQueryChange = viewModel::setQuery,
                onNavigateBack = onNavigateBack,
                showBackButton = true,
                allowImageSearch = false
            )
            if (similarSceneSearch) {
                Text(
                    stringResource(R.string.video_similar_scene_results),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (searching) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.video_no_results))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(148.dp),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.video.id }) { result ->
                        VideoResultTile(result = result, onClick = { viewModel.openScenes(result) })
                    }
                }
            }
        }
    }

    selectedResult?.let { result ->
        VideoSceneMatchesSheet(
            result = result,
            loading = loadingScenes,
            onDismiss = viewModel::closeScenes,
            onFindSimilar = viewModel::findSimilarScene
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun VideoResultTile(result: VideoSearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            GlideImage(
                model = File(result.bestMatch.thumbnailPath),
                contentDescription = result.video.displayName,
                modifier = Modifier.fillMaxWidth().size(148.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                result.video.displayName,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatTimestamp(result.bestMatch.timestampMs)} · ${formatConfidence(result.bestMatch.similarity)}",
                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 7.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
private fun VideoSceneMatchesSheet(
    result: VideoSearchResult,
    loading: Boolean,
    onDismiss: () -> Unit,
    onFindSimilar: (VideoFrameMatch) -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Text(
                result.video.displayName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(stringResource(R.string.video_scene_matches), style = MaterialTheme.typography.bodyMedium)
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(128.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(result.matches, key = { it.timestampMs }) { match ->
                        SceneTile(
                            match = match,
                            onPlay = {
                                VideoPlaybackLauncher.open(
                                    context,
                                    result.video.uri,
                                    result.video.mimeType,
                                    match.timestampMs
                                )
                            },
                            onFindSimilar = { onFindSimilar(match) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SceneTile(
    match: VideoFrameMatch,
    onPlay: () -> Unit,
    onFindSimilar: () -> Unit
) {
    Card(
        modifier = Modifier.combinedClickable(
            onClick = onPlay,
            onLongClick = onFindSimilar
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box {
            GlideImage(
                model = File(match.thumbnailPath),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().size(128.dp),
                contentScale = ContentScale.Crop
            )
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.video_play),
                modifier = Modifier.align(Alignment.Center).size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(
            "${formatTimestamp(match.timestampMs)} · ${formatConfidence(match.similarity)}",
            modifier = Modifier.padding(6.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatTimestamp(milliseconds: Long): String =
    DateUtils.formatElapsedTime((milliseconds / 1000).coerceAtLeast(0))

private fun formatConfidence(similarity: Float): String =
    String.format(Locale.getDefault(), "%.1f%%", similarity * 100f)
