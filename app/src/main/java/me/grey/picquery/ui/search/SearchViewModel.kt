package me.grey.picquery.ui.search

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.R
import me.grey.picquery.common.showToast
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.ImageSearcher
import timber.log.Timber

enum class SearchState {
    NO_INDEX, // 没有索引
    LOADING, // 初始化加载模型中
    READY,  // 准备好搜索
    SEARCHING,  // 正在搜索
    FINISHED,  // 搜索已完成
}

internal class SearchRouteInitializationGuard {
    private var initializedRoute: String? = null

    fun shouldInitialize(route: String): Boolean {
        if (route.isBlank() || route == initializedRoute) return false
        initializedRoute = route
        return true
    }
}

class SearchViewModel(
    private val imageSearcher: ImageSearcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val repo: PhotoRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {
    companion object {
        private const val TAG = "SearchResultViewModel"
    }

    private val _resultList = MutableStateFlow<List<Photo>>(emptyList())
    val resultList = _resultList.asStateFlow()
    private val _resultMap = MutableStateFlow<Map<Long, Double>>(mutableMapOf())
    val resultMap: StateFlow<Map<Long, Double>> = _resultMap.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState.LOADING)
    val searchState = _searchState.asStateFlow()
    private val _similarPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val similarPhotos = _similarPhotos.asStateFlow()
    private val _showSimilarPhotos = MutableStateFlow(false)
    val showSimilarPhotos = _showSimilarPhotos.asStateFlow()
    private val _similarPhotosLoading = MutableStateFlow(false)
    val similarPhotosLoading = _similarPhotosLoading.asStateFlow()

    private val _searchText = MutableStateFlow<String>("")
    private val routeInitializationGuard = SearchRouteInitializationGuard()
    val searchText: StateFlow<String> = _searchText.map { it }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ""
    )

    private val context: Context
        get() {
            return PicQueryApplication.context
        }

    init {
        Timber.tag(TAG).d("init!!! SearchViewModel")
    }

    /**
     * Initialize this navigation entry exactly once per ViewModel instance.
     * If Android/Compose recreates the ViewModel after returning from preview,
     * a new guard reloads cached saved-search results instead of leaving an
     * independently restored UI flag pointing at an empty result list.
     */
    fun initializeFromRoute(initialQuery: String) {
        if (!routeInitializationGuard.shouldInitialize(initialQuery)) return

        when {
            initialQuery.startsWith(SAVED_SEARCH_PREFIX) -> {
                loadSavedSearch(initialQuery.removePrefix(SAVED_SEARCH_PREFIX))
            }
            initialQuery.startsWith("content") -> {
                startSearch(Uri.parse(initialQuery))
                _searchText.value = ""
            }
            else -> startSearch(initialQuery)
        }
    }

    fun onQueryChange(query: String) {
        if (query==_searchText.value) return
        Timber.tag(TAG).d("onQueryChange: $query")
        _searchText.value = query
        _searchState.value = SearchState.READY
    }

    fun startSearch(text: String) {
        if (text.trim().isEmpty()) {
            showToast(context.getString(R.string.empty_search_content_toast))
            Timber.tag(TAG).w("搜索字段为空")
            return
        }
        _searchText.value = text
        viewModelScope.launch(ioDispatcher) {
            _searchState.value = SearchState.SEARCHING
            imageSearcher.searchV2(text) { ids ->
                Timber.tag(TAG).d("searchV2 ids: $ids")
                applyResults(ids)
                preferenceRepository.saveSearch(text, ids.map { it.first })
                _searchState.value = SearchState.FINISHED
            }
        }
    }

    fun startSearch(uri: Uri) {
        // 从 uri 获取图片
        val photo = repo.getBitmapFromUri(uri)
        if (photo == null) {
            showToast(context.getString(R.string.empty_search_content_toast))
            Log.w(TAG, "搜索字段为空")
            return
        }
        viewModelScope.launch(ioDispatcher) {
            _searchState.value = SearchState.SEARCHING
            imageSearcher.searchWithRangeV2(photo) { ids ->
                applyResults(ids)
                _searchState.value = SearchState.FINISHED
            }
        }
    }

    fun loadSavedSearch(id: String) {
        viewModelScope.launch(ioDispatcher) {
            _searchState.value = SearchState.LOADING
            val saved = preferenceRepository.getSavedSearch(id)
            if (saved == null) {
                _resultList.value = emptyList()
                _resultMap.value = emptyMap()
            } else {
                _searchText.value = saved.query
                val photos = repo.getPhotoListByIds(saved.resultPhotoIds)
                _resultList.value = reOrderList(photos, saved.resultPhotoIds)
                _resultMap.value = emptyMap()
                imageSearcher.setDisplayResultIds(_resultList.value.map { it.id })
            }
            _searchState.value = SearchState.FINISHED
        }
    }

    fun findSimilar(photo: Photo) {
        viewModelScope.launch(ioDispatcher) {
            _showSimilarPhotos.value = true
            _similarPhotosLoading.value = true
            _similarPhotos.value = emptyList()
            imageSearcher.findSimilarToPhoto(photo.id) { ids ->
                val orderedIds = ids.map { it.first }
                val photos = repo.getPhotoListByIds(orderedIds)
                _similarPhotos.value = reOrderList(photos, orderedIds)
                preferenceRepository.saveSimilarSearch(photo.id, photo.label, orderedIds)
                _similarPhotosLoading.value = false
            }
        }
    }

    fun prepareSimilarResultsForDisplay() {
        imageSearcher.setDisplayResultIds(_similarPhotos.value.map { it.id })
    }

    fun dismissSimilarPhotos() {
        _showSimilarPhotos.value = false
        _similarPhotos.value = emptyList()
    }

    private fun applyResults(ids: List<Pair<Long, Double>>) {
        val orderedIds = ids.map { it.first }
        val photos = repo.getPhotoListByIds(orderedIds)
        _resultList.value = reOrderList(photos, orderedIds)
        _resultMap.value = ids.associate { it.first to (1.0 - it.second) }
        imageSearcher.setDisplayResultIds(_resultList.value.map { it.id })
        Timber.tag(TAG).d("searchV2 photos re-orders: ${_resultList.value.size}")
    }

    // fix the order of the result list
    private fun reOrderList(originalList: List<Photo>, orderList: List<Long>): List<Photo> {
        val photoMap = originalList.associateBy { it.id }
        return orderList.mapNotNull { id -> photoMap[id] }
    }
}
