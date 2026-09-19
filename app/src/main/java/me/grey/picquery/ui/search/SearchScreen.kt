package me.grey.picquery.ui.search

import SearchInput
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.res.stringResource
import me.grey.picquery.R
import me.grey.picquery.data.model.Photo
import org.koin.androidx.compose.koinViewModel

@OptIn(InternalTextApi::class)
@Composable
fun SearchScreen(
    initialQuery: String,
    onClickPhoto: (Photo, Int) -> Unit,
    onNavigateBack: () -> Unit,
    searchViewModel: SearchViewModel = koinViewModel()
) {

    val resultList by searchViewModel.resultList.collectAsState()
    val searchState by searchViewModel.searchState.collectAsState()
    val resultMap by searchViewModel.resultMap.collectAsState()
    val similarPhotos by searchViewModel.similarPhotos.collectAsState()
    val showSimilarPhotos by searchViewModel.showSimilarPhotos.collectAsState()
    val similarPhotosLoading by searchViewModel.similarPhotosLoading.collectAsState()
    var contextPhoto by remember { mutableStateOf<Photo?>(null) }

    LaunchedEffect(initialQuery) {
        searchViewModel.initializeFromRoute(initialQuery)
    }
    val queryText by searchViewModel.searchText.collectAsState()

    Surface {
        Column {
            SearchInput(
                onStartSearch = { searchViewModel.startSearch(it) },
                onImageSearch = { searchViewModel.startSearch(it) },
                queryText = queryText,
                onNavigateBack = onNavigateBack,
                onQueryChange = { searchViewModel.onQueryChange(it) },
                showBackButton = searchState == SearchState.FINISHED
            )

            SearchResultGrid(
                resultList = resultList,
                state = searchState,
                resultMap = resultMap,
                onClickPhoto = onClickPhoto,
                onLongClickPhoto = { contextPhoto = it }
            )
        }
    }

    if (showSimilarPhotos) {
        SimilarPhotosBottomSheet(
            photos = similarPhotos,
            loading = similarPhotosLoading,
            onDismiss = searchViewModel::dismissSimilarPhotos,
            onPhotoClick = { photo, index ->
                searchViewModel.prepareSimilarResultsForDisplay()
                onClickPhoto(photo, index)
            }
        )
    }
    contextPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { contextPhoto = null },
            title = { Text(photo.label) },
            text = { Text(stringResource(R.string.long_press_find_similar_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        contextPhoto = null
                        searchViewModel.findSimilar(photo)
                    }
                ) { Text(stringResource(R.string.find_similar)) }
            },
            dismissButton = {
                TextButton(onClick = { contextPhoto = null }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
}

const val SAVED_SEARCH_PREFIX = "saved-search:"
