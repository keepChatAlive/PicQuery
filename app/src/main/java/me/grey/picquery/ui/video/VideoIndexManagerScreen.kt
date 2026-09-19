package me.grey.picquery.ui.video

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import me.grey.picquery.R
import me.grey.picquery.data.video.model.IndexedVideoStatus
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoIndexManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: VideoIndexManagerViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val removedCount by viewModel.lastRemovedCount.collectAsState()
    val invalidCount = items.count { !it.isValid }
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { if (it) viewModel.refresh() }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.refresh()
        } else permissionLauncher.launch(permission)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.video_index_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !loading && !progress.running) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_button))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.video_index_manager_summary, items.size, invalidCount),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                Button(
                    onClick = viewModel::cleanupInvalid,
                    enabled = invalidCount > 0 && !loading && !progress.running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
                    else Icon(Icons.Default.Delete, contentDescription = null)
                    Text(stringResource(R.string.video_cleanup_invalid))
                }
            }
            removedCount?.let { count ->
                item { Text(stringResource(R.string.video_cleanup_result, count)) }
            }
            items(items, key = { it.video.id }) { item ->
                IndexedVideoCard(
                    item = item,
                    deleteEnabled = !loading && !progress.running,
                    onDelete = { viewModel.delete(item.video.id) }
                )
            }
        }
    }
}

@Composable
private fun IndexedVideoCard(
    item: IndexedVideoStatus,
    deleteEnabled: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (item.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (item.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(item.video.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (item.isValid) R.string.video_index_valid else R.string.video_index_invalid),
                    color = if (item.isValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(
                        R.string.video_index_item_details,
                        item.video.frameCount,
                        DateUtils.formatElapsedTime(item.video.durationMs / 1000)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDelete, enabled = deleteEnabled) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_search))
            }
        }
    }
}
