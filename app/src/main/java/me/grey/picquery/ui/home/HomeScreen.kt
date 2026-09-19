package me.grey.picquery.ui.home

import me.grey.picquery.ui.common.AppBottomSheetState
import LogoImage
import LogoRow
import LogoText
import SearchInput
import me.grey.picquery.data.model.SearchMediaMode
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.common.Constants
import me.grey.picquery.common.showToast
import me.grey.picquery.data.model.SavedSearchType
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.domain.ImageSearcher
import me.grey.picquery.domain.VideoIndexManager
import me.grey.picquery.ui.search.SearchConfigBottomSheet
import me.grey.picquery.ui.search.SearchRangeBottomSheet
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import me.grey.picquery.ui.common.rememberAppBottomSheetState
import me.grey.picquery.ui.search.SAVED_SEARCH_PREFIX
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = koinViewModel(),
    albumManager: AlbumManager = koinInject(),
    videoIndexManager: VideoIndexManager = koinInject(),
    navigateToSearch: (String) -> Unit,
    navigateToSearchWitImage: (Uri) -> Unit,
    navigateToSetting: () -> Unit,
    navigateToVideo: (String) -> Unit
) {
    InitPermissions()

    val userGuideVisible = remember { homeViewModel.userGuideVisible }
    val searchMode by homeViewModel.searchMode.collectAsState()
    val videoFailure by videoIndexManager.failure.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val albumListSheetState = rememberAppBottomSheetState()
    val imageSearcher: ImageSearcher = koinInject()
    var showSearchFilterBottomSheet by remember { mutableStateOf(false) }
    var showSearchRangeBottomSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val busyHint = stringResource(R.string.busy_when_add_album_toast)

    LaunchedEffect(searchMode) {
        if (searchMode != SearchMediaMode.PHOTO) {
            showSearchRangeBottomSheet = false
        }
    }

    val onOpenIndexAlbums: () -> Unit = {
        val busy = if (searchMode == SearchMediaMode.PHOTO) {
            albumManager.isEncoderBusy
        } else {
            videoIndexManager.progress.value.running
        }
        if (!busy) {
            scope.launch { albumListSheetState.show() }
        } else {
            showToast(busyHint)
        }
    }

    // Handle bottom sheet
    if (albumListSheetState.isVisible) {
        AddAlbumBottomSheet(
            sheetState = albumListSheetState,
            mediaMode = searchMode,
            onStartIndexing = { mode ->
                if (mode == SearchMediaMode.PHOTO) homeViewModel.doneIndexAlbum()
            }
        )
    }

    // Main scaffold
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = { IndexingProgressBars() },
        topBar = {
            HomeTopBar(
                onClickHelpButton = homeViewModel::showUserGuide
            )
        }
    ) { padding ->
        MainContent(
            padding = padding,
            userGuideVisible = userGuideVisible.value,
            homeViewModel = homeViewModel,
            navigateToSearch = navigateToSearch,
            navigateToSearchWitImage = navigateToSearchWitImage,
            albumListSheetState = albumListSheetState,
            onOpenIndexAlbums = onOpenIndexAlbums,
            onOpenSearchRange = {
                if (searchMode == SearchMediaMode.PHOTO) {
                    showSearchRangeBottomSheet = true
                }
            },
            onOpenSearchConfig = { showSearchFilterBottomSheet = true },
            navigateToSetting = navigateToSetting,
            navigateToVideo = navigateToVideo
        )
    }

    if (showSearchFilterBottomSheet) {
        SearchConfigBottomSheet(
            imageSearcher = imageSearcher,
            onDismiss = { showSearchFilterBottomSheet = false }
        )
    }
    if (showSearchRangeBottomSheet && searchMode == SearchMediaMode.PHOTO) {
        SearchRangeBottomSheet(dismiss = {
            showSearchRangeBottomSheet = false
        })
    }
    videoFailure?.let { failure ->
        VideoIndexFailureDialog(
            failure = failure,
            onDismiss = videoIndexManager::dismissFailure
        )
    }
}

@Composable
private fun MainContent(
    padding: PaddingValues,
    userGuideVisible: Boolean,
    homeViewModel: HomeViewModel,
    navigateToSearch: (String) -> Unit,
    navigateToSearchWitImage: (Uri) -> Unit,
    albumListSheetState: AppBottomSheetState,
    onOpenIndexAlbums: () -> Unit,
    onOpenSearchRange: () -> Unit,
    onOpenSearchConfig: () -> Unit,
    navigateToSetting: () -> Unit,
    navigateToVideo: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        SearchSection(
            userGuideVisible = userGuideVisible,
            homeViewModel = homeViewModel,
            navigateToSearch = navigateToSearch,
            navigateToSearchWitImage = navigateToSearchWitImage,
            onOpenIndexAlbums = onOpenIndexAlbums,
            onOpenSearchRange = onOpenSearchRange,
            onOpenSearchConfig = onOpenSearchConfig,
            navigateToSetting = navigateToSetting,
            navigateToVideo = navigateToVideo
        )

        GuideSection(
            userGuideVisible = userGuideVisible,
            homeViewModel = homeViewModel,
            albumListSheetState = albumListSheetState
        )
    }
}

@OptIn(InternalTextApi::class)
@Composable
private fun SearchSection(
    userGuideVisible: Boolean,
    homeViewModel: HomeViewModel,
    navigateToSearch: (String) -> Unit,
    navigateToSearchWitImage: (Uri) -> Unit,
    onOpenIndexAlbums: () -> Unit,
    onOpenSearchRange: () -> Unit,
    onOpenSearchConfig: () -> Unit,
    navigateToSetting: () -> Unit,
    navigateToVideo: (String) -> Unit
) {
    val searchText by homeViewModel.searchText.collectAsState()
    val searchMode by homeViewModel.searchMode.collectAsState()
    val savedSearches by homeViewModel.savedSearches.collectAsState()
    AnimatedVisibility(
        visible = !userGuideVisible,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                LogoRow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
            item {
                SearchInput(
                    queryText = searchText,
                    onStartSearch = { text ->
                        if (text.isNotEmpty()) {
                            if (searchMode == SearchMediaMode.PHOTO) {
                                navigateToSearch(text)
                            } else {
                                navigateToVideo(text)
                            }
                        }
                    },
                    onQueryChange = { homeViewModel.onQueryChange(it) },
                    onImageSearch = { uri ->
                        if (uri.toString().isNotEmpty()) {
                            navigateToSearchWitImage(uri)
                        }
                    },
                    mediaMode = searchMode,
                    onMediaModeChange = homeViewModel::setSearchMode
                )
            }
            item { Spacer(modifier = Modifier.size(8.dp)) }
            item {
                QuickActionsSection(
                    onOpenIndexAlbums = onOpenIndexAlbums,
                    onOpenSearchRange = onOpenSearchRange,
                    showSearchRange = searchMode == SearchMediaMode.PHOTO,
                    onOpenSearchConfig = onOpenSearchConfig,
                    navigateToSetting = navigateToSetting
                )
            }
            if (searchMode == SearchMediaMode.PHOTO && savedSearches.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.recent_searches_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
                items(savedSearches, key = { it.search.id }) { preview ->
                    SavedSearchCard(
                        preview = preview,
                        onOpen = { navigateToSearch(SAVED_SEARCH_PREFIX + preview.search.id) },
                        onTogglePin = { homeViewModel.togglePin(preview.search.id) },
                        onDelete = { homeViewModel.deleteSavedSearch(preview.search.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun SavedSearchCard(
    preview: SavedSearchPreview,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val thumbnail = preview.previewPhotos.firstOrNull()
            if (thumbnail != null) {
                GlideImage(
                    model = thumbnail.uri,
                    contentDescription = thumbnail.label,
                    modifier = Modifier.size(52.dp).padding(2.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Spacer(modifier = Modifier.size(52.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = if (preview.search.type == SavedSearchType.SIMILAR_IMAGE) {
                        stringResource(
                            R.string.similar_search_history_title,
                            preview.search.query
                        )
                    } else {
                        preview.search.query
                    },
                    maxLines = 1
                )
                Text(
                    text = stringResource(
                        R.string.cached_result_count,
                        preview.search.resultPhotoIds.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = stringResource(R.string.pin_search),
                    modifier = Modifier.size(20.dp),
                    tint = if (preview.search.pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete_search),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun GuideSection(
    userGuideVisible: Boolean,
    homeViewModel: HomeViewModel,
    albumListSheetState: AppBottomSheetState
) {
    AnimatedVisibility(visible = userGuideVisible) {
        val scope = rememberCoroutineScope()
        val currentStep = remember { homeViewModel.currentGuideState }
        val mediaPermissions = rememberMediaPermissions()

        UserGuide(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            onRequestPermission = { mediaPermissions.launchMultiplePermissionRequest() },
            onOpenAlbum = { scope.launch { albumListSheetState.show() } },
            onFinish = { homeViewModel.finishGuide() },
            state = currentStep.value
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberMediaPermissions(
    homeViewModel: HomeViewModel = koinViewModel(),
    albumManager: AlbumManager = koinInject()
): MultiplePermissionsState {
    val scope = rememberCoroutineScope()
    return rememberMultiplePermissionsState(
        permissions = Constants.PERMISSIONS,
        onPermissionsResult = { permission ->
            if (permission.all { it.value }) {
                homeViewModel.doneRequestPermission()
                scope.launch { albumManager.initAllAlbumList() }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    onClickHelpButton: () -> Unit
) {
    TopAppBar(
        title = {
            LogoText(size = 20f)
        },
        actions = {
            IconButton(onClick = onClickHelpButton) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = "Help"
                )
            }
        }
    )
}

@Composable
private fun QuickActionsSection(
    onOpenIndexAlbums: () -> Unit,
    onOpenSearchRange: () -> Unit,
    showSearchRange: Boolean,
    onOpenSearchConfig: () -> Unit,
    navigateToSetting: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.quick_actions_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionButton(
                title = stringResource(R.string.menu_index_albums_short),
                onClick = onOpenIndexAlbums
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (showSearchRange) {
                QuickActionButton(
                    title = stringResource(R.string.menu_search_range_short),
                    onClick = onOpenSearchRange
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            QuickActionButton(
                title = stringResource(R.string.menu_settings),
                onClick = navigateToSetting
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
