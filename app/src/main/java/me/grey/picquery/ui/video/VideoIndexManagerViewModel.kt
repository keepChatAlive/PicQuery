package me.grey.picquery.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.grey.picquery.data.video.model.IndexedVideoStatus
import me.grey.picquery.domain.VideoIndexManager

class VideoIndexManagerViewModel(private val manager: VideoIndexManager) : ViewModel() {
    val progress = manager.progress
    private val _items = MutableStateFlow<List<IndexedVideoStatus>>(emptyList())
    val items = _items.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _lastRemovedCount = MutableStateFlow<Int?>(null)
    val lastRemovedCount = _lastRemovedCount.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            try {
                manager.refresh()
                _items.value = manager.indexedVideoStatuses()
            } finally {
                _loading.value = false
            }
        }
    }

    fun cleanupInvalid() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _lastRemovedCount.value = manager.cleanupInvalidVideos()
                _items.value = manager.indexedVideoStatuses()
            } finally {
                _loading.value = false
            }
        }
    }

    fun delete(videoId: Long) {
        viewModelScope.launch {
            _loading.value = true
            try {
                manager.deleteIndexedVideo(videoId)
                _items.value = manager.indexedVideoStatuses()
            } finally {
                _loading.value = false
            }
        }
    }
}
