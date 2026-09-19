package me.grey.picquery.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.common.showToast
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.SearchMediaMode
import me.grey.picquery.data.video.model.VideoAlbum
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.domain.VideoIndexManager
import me.grey.picquery.ui.albums.AlbumCard
import me.grey.picquery.ui.common.AppBottomSheetState
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlbumBottomSheet(
    sheetState: AppBottomSheetState,
    mediaMode: SearchMediaMode,
    onStartIndexing: (SearchMediaMode) -> Unit,
    albumManager: AlbumManager = koinInject(),
    videoIndexManager: VideoIndexManager = koinInject()
) {
    val scope = rememberCoroutineScope()
    fun closeSheet() { scope.launch { sheetState.hide() } }

    ModalBottomSheet(
        onDismissRequest = { closeSheet() },
        sheetState = sheetState.sheetState
    ) {
        if (mediaMode == SearchMediaMode.PHOTO) {
            PhotoAlbumPicker(
                albumManager = albumManager,
                onClose = ::closeSheet,
                onStartIndexing = { onStartIndexing(SearchMediaMode.PHOTO) }
            )
        } else {
            VideoAlbumPicker(
                manager = videoIndexManager,
                onClose = ::closeSheet,
                onStartIndexing = { onStartIndexing(SearchMediaMode.VIDEO) }
            )
        }
    }
}

@Composable
private fun PhotoAlbumPicker(
    albumManager: AlbumManager,
    onClose: () -> Unit,
    onStartIndexing: () -> Unit
) {
    val list by albumManager.unsearchableAlbumList.collectAsState()
    if (list.isEmpty()) {
        EmptyAlbumTips(onClose)
        return
    }
    val selectedList = remember { albumManager.albumsToEncode }
    val noAlbumTips = stringResource(R.string.no_album_selected)
    AlbumSelectionList(
        list = list,
        selectedList = selectedList,
        onStartIndexing = {
            val snapshot = albumManager.albumsToEncode.toList()
            albumManager.albumsToEncode.clear()
            if (snapshot.isEmpty()) {
                showToast(noAlbumTips)
            } else {
                albumManager.processAlbums(snapshot)
                onStartIndexing()
            }
            onClose()
        },
        onToggleSelectAll = albumManager::toggleSelectAllAlbums,
        onSelectItem = albumManager::toggleAlbumSelection
    )
}

@Composable
private fun VideoAlbumPicker(
    manager: VideoIndexManager,
    onClose: () -> Unit,
    onStartIndexing: () -> Unit
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    var loading by remember { mutableStateOf(permissionGranted) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        loading = granted
        permissionGranted = granted
    }
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            loading = true
            try {
                manager.refresh()
            } finally {
                loading = false
            }
        } else permissionLauncher.launch(permission)
    }

    if (!permissionGranted) {
        PermissionTips(onRequest = { permissionLauncher.launch(permission) })
        return
    }

    val albums by manager.albums.collectAsState()
    val selectable = albums.filter { it.indexedCount < it.videoCount }
    var selectedIds by remember(selectable.map(VideoAlbum::id)) { mutableStateOf(emptySet<Long>()) }
    if (loading) {
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        return
    }
    if (selectable.isEmpty()) {
        EmptyAlbumTips(onClose)
        return
    }

    VideoAlbumSelectionList(
        albums = selectable,
        selectedIds = selectedIds,
        onToggle = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
        onToggleAll = {
            val all = selectable.mapTo(mutableSetOf(), VideoAlbum::id)
            selectedIds = if (selectedIds == all) emptySet() else all
        },
        onStart = {
            if (selectedIds.isNotEmpty()) {
                manager.startIndexing(selectedIds)
                onStartIndexing()
                onClose()
            }
        }
    )
}

@Composable
private fun PermissionTips(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().height(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.video_permission_required))
        Button(onClick = onRequest) { Text(stringResource(R.string.video_grant_permission)) }
    }
}

@Composable
private fun VideoAlbumSelectionList(
    albums: List<VideoAlbum>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onToggleAll: () -> Unit,
    onStart: () -> Unit
) {
    val selectedVideoCount = albums.filter { it.id in selectedIds }.sumOf { it.videoCount - it.indexedCount }
    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.video_add_album_title), style = MaterialTheme.typography.headlineSmall) },
            supportingContent = {
                Text(
                    if (selectedVideoCount == 0) stringResource(R.string.video_add_album_subtitle)
                    else stringResource(R.string.selected_videos_count, selectedVideoCount)
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onToggleAll) {
                        Text(stringResource(if (selectedIds.size == albums.size) R.string.unselect_all else R.string.select_all))
                    }
                    Button(onClick = onStart, enabled = selectedIds.isNotEmpty()) {
                        Text(stringResource(R.string.do_index))
                    }
                }
            }
        )
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.padding(horizontal = 6.dp)
    ) {
        items(albums, key = VideoAlbum::id) { album ->
            VideoAlbumIndexCard(
                album = album,
                selected = album.id in selectedIds,
                onClick = { onToggle(album.id) }
            )
        }
    }
}

@Composable
private fun VideoAlbumIndexCard(album: VideoAlbum, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.padding(6.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(48.dp))
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
        Text(album.name, modifier = Modifier.padding(horizontal = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.video_album_counts, album.indexedCount, album.videoCount),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun EmptyAlbumTips(onClose: () -> Unit) {
    Column(
        Modifier.height(180.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_albums),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Box(modifier = Modifier.height(20.dp))
        Button(onClick = onClose) { Text(stringResource(R.string.ok_button)) }
    }
}

@Composable
fun AlbumSelectionList(
    list: List<Album>,
    selectedList: List<Album>,
    onStartIndexing: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onSelectItem: (Album) -> Unit
) {
    val selectedPhotoCount = selectedList.sumOf(Album::count)
    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.add_album_title), style = MaterialTheme.typography.headlineSmall) },
            supportingContent = {
                Text(
                    if (selectedPhotoCount <= 0) stringResource(R.string.add_album_subtitle)
                    else stringResource(R.string.selected_images_count, selectedPhotoCount)
                )
            },
            trailingContent = {
                Row {
                    TextButton(onClick = onToggleSelectAll) {
                        Text(stringResource(if (list.size == selectedList.size) R.string.unselect_all else R.string.select_all))
                    }
                    Box(modifier = Modifier.width(5.dp))
                    Button(onClick = onStartIndexing, enabled = selectedPhotoCount > 0) {
                        Text(stringResource(R.string.do_index))
                    }
                }
            }
        )
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(100.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
        items(list, key = Album::id) { album ->
            AlbumCard(album, selected = selectedList.contains(album), onItemClick = onSelectItem)
        }
    }
}
