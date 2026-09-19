package me.grey.picquery.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.data.model.InferenceBackend
import me.grey.picquery.data.model.DifficultVideoPolicy
import me.grey.picquery.data.model.VideoFastModeScope
import me.grey.picquery.domain.ImageSearcher

class SettingViewModel(
    private val preferenceRepository: PreferenceRepository,
    private val imageSearcher: ImageSearcher
) : ViewModel() {

    val enableUploadLog = preferenceRepository.getEnableUploadLog()
    val deviceId = preferenceRepository.getDeviceIdFlow()
    val inferenceBackend = preferenceRepository.getInferenceBackend()
    val indexingConcurrency = preferenceRepository.getIndexingConcurrency()
    val searchHistoryLimit = preferenceRepository.getSearchHistoryLimit()
    val difficultVideoPolicy = preferenceRepository.getDifficultVideoPolicy()
    val ffmpegThreadCount = preferenceRepository.getFfmpegThreadCount()
    val videoFastModeScope = preferenceRepository.getVideoFastModeScope()
    val videoSceneDedupThreshold = preferenceRepository.getVideoSceneDedupThreshold()
    val matchThreshold get() = imageSearcher.matchThreshold
    val topK get() = imageSearcher.topK

    fun setEnableUploadLog(enable: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setEnableUploadLog(enable)
        }
    }

    fun setInferenceBackend(backend: InferenceBackend) {
        viewModelScope.launch { preferenceRepository.setInferenceBackend(backend) }
    }

    fun setIndexingConcurrency(concurrency: Int) {
        viewModelScope.launch { preferenceRepository.setIndexingConcurrency(concurrency) }
    }

    fun setSearchHistoryLimit(limit: Int) {
        viewModelScope.launch { preferenceRepository.setSearchHistoryLimit(limit) }
    }

    fun setDifficultVideoPolicy(policy: DifficultVideoPolicy) {
        viewModelScope.launch { preferenceRepository.setDifficultVideoPolicy(policy) }
    }

    fun setFfmpegThreadCount(threads: Int) {
        viewModelScope.launch { preferenceRepository.setFfmpegThreadCount(threads) }
    }

    fun setVideoFastModeScope(scope: VideoFastModeScope) {
        viewModelScope.launch { preferenceRepository.setVideoFastModeScope(scope) }
    }

    fun setVideoSceneDedupThreshold(threshold: Float) {
        viewModelScope.launch { preferenceRepository.setVideoSceneDedupThreshold(threshold) }
    }

    fun clearUnpinnedHistory() {
        viewModelScope.launch { preferenceRepository.clearUnpinnedSavedSearches() }
    }

    fun setSearchConfiguration(matchThreshold: Float, topK: Int) {
        imageSearcher.updateSearchConfiguration(matchThreshold, topK)
    }
}
