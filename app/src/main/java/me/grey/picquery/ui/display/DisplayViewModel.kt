package me.grey.picquery.ui.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.ImageSearcher

class DisplayViewModel(
    private val photoRepository: PhotoRepository,
    private val imageSearcher: ImageSearcher,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _photoList = MutableStateFlow<MutableList<Photo>>(mutableListOf())
    val photoList: StateFlow<MutableList<Photo>> = _photoList
    private val _similarPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val similarPhotos: StateFlow<List<Photo>> = _similarPhotos
    private val _showSimilarPhotos = MutableStateFlow(false)
    val showSimilarPhotos: StateFlow<Boolean> = _showSimilarPhotos
    private val _similarPhotosLoading = MutableStateFlow(false)
    val similarPhotosLoading: StateFlow<Boolean> = _similarPhotosLoading

    fun loadPhotos() {
        viewModelScope.launch {
            val ids = imageSearcher.searchResultIds
            val list = reorderList(photoRepository.getPhotoListByIds(ids), ids)
            _photoList.emit(list.toMutableList())
        }
    }

    fun findSimilar(photo: Photo) {
        viewModelScope.launch {
            _showSimilarPhotos.value = true
            _similarPhotosLoading.value = true
            imageSearcher.findSimilarToPhoto(photo.id) { ids ->
                val orderedIds = ids.map { it.first }
                _similarPhotos.value = reorderList(
                    photoRepository.getPhotoListByIds(orderedIds),
                    orderedIds
                )
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

    private fun reorderList(originalList: List<Photo>, orderList: List<Long>): List<Photo> {
        val photoMap = originalList.associateBy { it.id }
        return orderList.mapNotNull { id -> photoMap[id] }
    }
}
