package me.grey.picquery.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import me.grey.picquery.R
import me.grey.picquery.data.model.Photo
import me.grey.picquery.ui.common.CentralLoadingProgressBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun SimilarPhotosBottomSheet(
    photos: List<Photo>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onPhotoClick: (Photo, Int) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.find_similar_results_title),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 620.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> CentralLoadingProgressBar()
                photos.isEmpty() -> Text(stringResource(R.string.no_similar_photos))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                        GlideImage(
                            model = photo.uri,
                            contentDescription = photo.label,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(3.dp)
                                .clickable { onPhotoClick(photo, index) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}
