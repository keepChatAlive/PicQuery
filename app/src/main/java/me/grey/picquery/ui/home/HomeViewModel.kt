package me.grey.picquery.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Photo
import me.grey.picquery.data.model.SavedSearch
import me.grey.picquery.domain.ImageSearcher
import timber.log.Timber
import me.grey.picquery.data.model.SearchMediaMode

data class UserGuideTaskState(
    val permissionDone: Boolean = false,
    val indexDone: Boolean = false
) {
    val allFinished: Boolean
        get() = permissionDone && indexDone
}

data class SavedSearchPreview(
    val search: SavedSearch,
    val previewPhotos: List<Photo>
)

class HomeViewModel(
    private val imageSearcher: ImageSearcher,
    private val preferenceRepository: me.grey.picquery.data.data_source.PreferenceRepository,
    private val photoRepository: PhotoRepository,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText
    private val _searchMode = MutableStateFlow(SearchMediaMode.PHOTO)
    val searchMode: StateFlow<SearchMediaMode> = _searchMode

    val userGuideVisible = mutableStateOf(false)

    val savedSearches = preferenceRepository.getSavedSearches().map { searches ->
        withContext(ioDispatcher) {
            searches.map { search ->
                SavedSearchPreview(
                    search = search,
                    previewPhotos = photoRepository.getPhotoListByIds(search.resultPhotoIds.take(3))
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentGuideState = mutableStateOf(UserGuideTaskState())

    fun onQueryChange(query: String) {
        _searchText.value = query
    }

    fun setSearchMode(mode: SearchMediaMode) {
        _searchMode.value = mode
    }

    fun togglePin(id: String) {
        viewModelScope.launch { preferenceRepository.toggleSavedSearchPin(id) }
    }

    fun deleteSavedSearch(id: String) {
        viewModelScope.launch { preferenceRepository.deleteSavedSearch(id) }
    }

    init {
        viewModelScope.launch {
            // 检查用户是否已经完成过引导
            val guideCompleted = preferenceRepository.isUserGuideCompleted()
            val hasData = imageSearcher.hasEmbedding()
            
            if (guideCompleted || hasData) {
                // 用户已经完成引导或有索引数据，不需要显示引导
                currentGuideState.value = UserGuideTaskState(
                    permissionDone = true,
                    indexDone = true
                )
                userGuideVisible.value = false
                
                // 如果有数据但标记未设置，更新标记
                if (hasData && !guideCompleted) {
                    preferenceRepository.setUserGuideCompleted(true)
                }
            } else {
                // 首次使用，需要显示引导
                userGuideVisible.value = true
            }
        }
    }

    fun showUserGuide() {
        userGuideVisible.value = true
    }

    fun doneRequestPermission() {
        Timber.tag(TAG).d("doneRequestPermission")
        currentGuideState.value = currentGuideState.value.copy(permissionDone = true)
    }

    fun doneIndexAlbum() {
        currentGuideState.value = currentGuideState.value.copy(indexDone = true)
    }

    fun finishGuide() {
        userGuideVisible.value = false
        // 标记用户已完成引导
        viewModelScope.launch {
            preferenceRepository.setUserGuideCompleted(true)
        }
    }
}
