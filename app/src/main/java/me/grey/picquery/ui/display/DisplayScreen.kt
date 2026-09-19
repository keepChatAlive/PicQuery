package me.grey.picquery.ui.display

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import java.io.File
import me.grey.picquery.R
import me.grey.picquery.data.model.Photo
import me.grey.picquery.ui.search.SimilarPhotosBottomSheet
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DisplayScreen(
    initialPage: Int,
    onNavigateBack: () -> Unit,
    onOpenSimilarPhoto: (Int) -> Unit,
    displayViewModel: DisplayViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val photoList by displayViewModel.photoList.collectAsState()
    val similarPhotos by displayViewModel.similarPhotos.collectAsState()
    val showSimilarPhotos by displayViewModel.showSimilarPhotos.collectAsState()
    val similarPhotosLoading by displayViewModel.similarPhotosLoading.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = 0,
        initialPageOffsetFraction = 0f,
        pageCount = { photoList.size }
    )

    LaunchedEffect(initialPage) {
        if (initialPage == -1) return@LaunchedEffect
        displayViewModel.loadPhotos()
        if (photoList.isNotEmpty()) {
            pagerState.scrollToPage(initialPage)
        }
    }

    LaunchedEffect(photoList) {
        if (photoList.isNotEmpty()) {
            pagerState.scrollToPage(initialPage)
        }
    }

    Scaffold(
        topBar = {
            if (pagerState.currentPage + 1 <= photoList.size) {
                val currentPhoto = photoList[pagerState.currentPage]
                val bgColor = MaterialTheme.colorScheme.surface.copy(
                    alpha = 0.4f
                )
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                    title = { TopPhotoInfoBar(currentPhoto) },
                    navigationIcon = {
                        IconButton(onClick = { onNavigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { openPhotoExternally(context, currentPhoto) }) {
                            Icon(
                                Icons.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.open_with_external_app)
                            )
                        }
                    }
                )
            }
        }
    ) {
        it.apply { }
        HorizontalPager(state = pagerState) { index ->
            ZoomablePagerImage(
                photo = photoList[index],
                onFindSimilar = displayViewModel::findSimilar
            ) { }
        }
    }
    if (showSimilarPhotos) {
        SimilarPhotosBottomSheet(
            photos = similarPhotos,
            loading = similarPhotosLoading,
            onDismiss = displayViewModel::dismissSimilarPhotos,
            onPhotoClick = { _, index ->
                displayViewModel.prepareSimilarResultsForDisplay()
                onOpenSimilarPhoto(index)
            }
        )
    }
}

@Composable
private fun TopPhotoInfoBar(currentPhoto: Photo) {
    val hintColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val titleStyle = MaterialTheme.typography.bodyLarge
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(color = hintColor)
    val iconSize = 16.dp
    Column {
        Text(
            text = currentPhoto.label,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(iconSize),
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = hintColor
            )
            Box(modifier = Modifier.width(4.dp))
            Text(text = currentPhoto.albumLabel, style = bodyStyle)
            Box(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Outlined.DateRange,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = hintColor
            )
            Box(modifier = Modifier.width(4.dp))
            Text(
                text = DateUtils.getRelativeDateTimeString(
                    LocalContext.current,
                    currentPhoto.timestamp * 1000,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS,
                    DateUtils.FORMAT_SHOW_TIME
                ).toString(),
                style = bodyStyle
            )
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalGlideComposeApi::class
)
@Composable
fun ZoomablePagerImage(
    modifier: Modifier = Modifier,
    photo: Photo,
    maxScale: Float = 5f,
    onFindSimilar: (Photo) -> Unit,
    onItemClick: () -> Unit
) {
    val zoomState = rememberZoomState(maxScale = maxScale)
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    val callback = {
        showDialog = false
    }

    if (showDialog) {
        PhotoContextDialog(callback, photo, context, onFindSimilar)
    }
    Scaffold {
        it.apply { }
        GlideImage(
            modifier = modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onDoubleClick = {
                    },
                    onClick = onItemClick,
                    onLongClick = { showDialog = true }
                )
                .zoomable(zoomState = zoomState),
            model = File(photo.path),
            contentDescription = photo.label,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun PhotoContextDialog(
    callback: () -> Unit,
    photo: Photo,
    context: Context,
    onFindSimilar: (Photo) -> Unit
) {
    AlertDialog(
        onDismissRequest = { callback() },
        title = { Text(stringResource(R.string.photo_actions)) },
        confirmButton = {
            Button(onClick = {
                onFindSimilar(photo)
                callback()
            }) {
                Text(stringResource(R.string.find_similar))
            }
        },
        dismissButton = {
            Button(onClick = {
                openPhotoExternally(context, photo)
                callback()
            }) {
                Text(stringResource(R.string.open_with_external_app))
            }
        }
    )
}

private fun openPhotoExternally(context: Context, photo: Photo) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(photo.uri, "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.open_with_external_app))
    )
}
