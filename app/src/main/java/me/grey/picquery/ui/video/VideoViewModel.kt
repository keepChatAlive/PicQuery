package me.grey.picquery.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.grey.picquery.data.video.model.VideoFrameMatch
import me.grey.picquery.data.video.model.VideoSearchResult
import me.grey.picquery.domain.VideoIndexManager

class VideoViewModel(private val manager: VideoIndexManager) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()
    private val _results = MutableStateFlow<List<VideoSearchResult>>(emptyList())
    val results = _results.asStateFlow()
    private val _selectedResult = MutableStateFlow<VideoSearchResult?>(null)
    val selectedResult = _selectedResult.asStateFlow()
    private val _loadingScenes = MutableStateFlow(false)
    val loadingScenes = _loadingScenes.asStateFlow()
    private val _similarSceneSearch = MutableStateFlow(false)
    val similarSceneSearch = _similarSceneSearch.asStateFlow()

    fun setQuery(value: String) { _query.value = value }

    fun search(query: String = _query.value) {
        val text = query.trim()
        if (text.isEmpty() || _searching.value) return
        _query.value = text
        _similarSceneSearch.value = false
        viewModelScope.launch {
            _searching.value = true
            try {
                _results.value = manager.search(text)
            } finally {
                _searching.value = false
            }
        }
    }

    fun findSimilarScene(match: VideoFrameMatch) {
        if (_searching.value) return
        _selectedResult.value = null
        _loadingScenes.value = false
        _query.value = ""
        _similarSceneSearch.value = true
        _searching.value = true
        viewModelScope.launch {
            try {
                _results.value = manager.searchSimilarFrame(match.frameId)
            } finally {
                _searching.value = false
            }
        }
    }

    fun openScenes(result: VideoSearchResult) {
        _selectedResult.value = result
        if (result.matchesLoaded || _loadingScenes.value) return
        viewModelScope.launch {
            _loadingScenes.value = true
            try {
                val matches = manager.loadAllMatches(result.video.id)
                val loaded = result.copy(matches = matches, matchesLoaded = true)
                _selectedResult.value = loaded
                _results.update { results ->
                    results.map { if (it.video.id == loaded.video.id) loaded else it }
                }
            } finally {
                _loadingScenes.value = false
            }
        }
    }

    fun closeScenes() { _selectedResult.value = null }
}
