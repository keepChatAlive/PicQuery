package me.grey.picquery.data.data_source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.data.model.IndexingRuntimeSettings
import me.grey.picquery.data.model.InferenceBackend
import me.grey.picquery.data.model.DifficultVideoPolicy
import me.grey.picquery.data.model.SavedSearch
import me.grey.picquery.data.model.SavedSearchType
import me.grey.picquery.data.model.VideoDecodeSettings
import me.grey.picquery.data.model.VideoFastModeScope

class PreferenceRepository {
    companion object {
        const val DEFAULT_VIDEO_SCENE_DEDUP_THRESHOLD = 0.95f
        const val MIN_VIDEO_SCENE_DEDUP_THRESHOLD = 0.80f
        const val MAX_VIDEO_SCENE_DEDUP_THRESHOLD = 0.99f

        val ACCEPT_AGREEMENT = booleanPreferencesKey("ACCEPT_AGREEMENT")
        val DEVICE_ID = stringPreferencesKey("DEVICE_ID")
        val ENABLE_UPLOAD_LOG = booleanPreferencesKey("ENABLE_UPLOAD_LOG")
        val SEARCH_MATCH_THRESHOLD = floatPreferencesKey("SEARCH_MATCH_THRESHOLD")
        val SEARCH_TOP_K = intPreferencesKey("SEARCH_TOP_K")
        val USER_GUIDE_COMPLETED = booleanPreferencesKey("USER_GUIDE_COMPLETED")
        val INDEXING_CONCURRENCY = intPreferencesKey("INDEXING_CONCURRENCY")
        val INFERENCE_BACKEND = stringPreferencesKey("INFERENCE_BACKEND")
        val SEARCH_HISTORY_LIMIT = intPreferencesKey("SEARCH_HISTORY_LIMIT")
        val SAVED_SEARCHES = stringPreferencesKey("SAVED_SEARCHES")
        val SEARCH_ALL_ALBUMS = booleanPreferencesKey("SEARCH_ALL_ALBUMS")
        val SEARCH_ALBUM_IDS = stringPreferencesKey("SEARCH_ALBUM_IDS")
        val DIFFICULT_VIDEO_POLICY = stringPreferencesKey("DIFFICULT_VIDEO_POLICY")
        val FFMPEG_THREAD_COUNT = intPreferencesKey("FFMPEG_THREAD_COUNT")
        val VIDEO_FAST_MODE_SCOPE = stringPreferencesKey("VIDEO_FAST_MODE_SCOPE")
        val VIDEO_SCENE_DEDUP_THRESHOLD = floatPreferencesKey("VIDEO_SCENE_DEDUP_THRESHOLD")
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
    private val json = Json { ignoreUnknownKeys = true }

    fun getDeviceIdFlow(): Flow<String> {
        return context.dataStore.data
            .map { preferences ->
                preferences[DEVICE_ID] ?: "unknown UUID"
            }
    }

    fun getAgreement(): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[ACCEPT_AGREEMENT] ?: false
            }
    }

    suspend fun acceptAgreement(enableUploadLog: Boolean = true) {
        context.dataStore.edit { settings ->
            settings[ACCEPT_AGREEMENT] = true
            settings[ENABLE_UPLOAD_LOG] = enableUploadLog
        }
    }

    suspend fun setEnableUploadLog(enable: Boolean) {
        context.dataStore.edit { settings ->
            settings[ENABLE_UPLOAD_LOG] = enable
        }
    }

    fun getEnableUploadLog(): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[ENABLE_UPLOAD_LOG] ?: true
            }
    }

    suspend fun saveSearchConfiguration(matchThreshold: Float, topK: Int) {
        context.dataStore.edit { settings ->
            settings[SEARCH_MATCH_THRESHOLD] = matchThreshold
            settings[SEARCH_TOP_K] = topK
        }
    }

    suspend fun loadSearchConfigurationSync(): Pair<Float, Int> {
        val preferences = context.dataStore.data.first()

        val matchThreshold = preferences[SEARCH_MATCH_THRESHOLD] ?: 0.20f
        val topK = preferences[SEARCH_TOP_K] ?: 30

        return Pair(matchThreshold, topK)
    }

    suspend fun loadSearchRangeSync(): Pair<Boolean, Set<Long>> {
        val preferences = context.dataStore.data.first()
        val searchAll = preferences[SEARCH_ALL_ALBUMS] ?: true
        val albumIds = preferences[SEARCH_ALBUM_IDS]
            .orEmpty()
            .split(',')
            .mapNotNull(String::toLongOrNull)
            .toSet()
        return searchAll to albumIds
    }

    suspend fun saveSearchRange(searchAll: Boolean, albumIds: Collection<Long>) {
        context.dataStore.edit { settings ->
            settings[SEARCH_ALL_ALBUMS] = searchAll
            settings[SEARCH_ALBUM_IDS] = albumIds.distinct().joinToString(",")
        }
    }

    suspend fun isUserGuideCompleted(): Boolean {
        val preferences = context.dataStore.data.first()
        return preferences[USER_GUIDE_COMPLETED] ?: false
    }

    suspend fun setUserGuideCompleted(completed: Boolean) {
        context.dataStore.edit { settings ->
            settings[USER_GUIDE_COMPLETED] = completed
        }
    }

    fun getIndexingConcurrency(): Flow<Int> = context.dataStore.data.map { preferences ->
        (preferences[INDEXING_CONCURRENCY] ?: 2).coerceIn(1, 4)
    }

    suspend fun setIndexingConcurrency(concurrency: Int) {
        context.dataStore.edit { it[INDEXING_CONCURRENCY] = concurrency.coerceIn(1, 4) }
    }

    fun getInferenceBackend(): Flow<InferenceBackend> = context.dataStore.data.map { preferences ->
        preferences[INFERENCE_BACKEND]
            ?.let { value -> runCatching { InferenceBackend.valueOf(value) }.getOrNull() }
            ?: InferenceBackend.AUTO
    }

    suspend fun setInferenceBackend(backend: InferenceBackend) {
        context.dataStore.edit { it[INFERENCE_BACKEND] = backend.name }
    }

    suspend fun loadIndexingRuntimeSettings(): IndexingRuntimeSettings {
        val preferences = context.dataStore.data.first()
        val backend = preferences[INFERENCE_BACKEND]
            ?.let { value -> runCatching { InferenceBackend.valueOf(value) }.getOrNull() }
            ?: InferenceBackend.AUTO
        return IndexingRuntimeSettings(
            backend = backend,
            concurrency = (preferences[INDEXING_CONCURRENCY] ?: 2).coerceIn(1, 4)
        )
    }

    fun getDifficultVideoPolicy(): Flow<DifficultVideoPolicy> = context.dataStore.data.map { preferences ->
        preferences[DIFFICULT_VIDEO_POLICY]
            ?.let { value -> runCatching { DifficultVideoPolicy.valueOf(value) }.getOrNull() }
            ?: DifficultVideoPolicy.FAIL_FAST
    }

    suspend fun setDifficultVideoPolicy(policy: DifficultVideoPolicy) {
        context.dataStore.edit { it[DIFFICULT_VIDEO_POLICY] = policy.name }
    }

    fun getFfmpegThreadCount(): Flow<Int> = context.dataStore.data.map { preferences ->
        (preferences[FFMPEG_THREAD_COUNT] ?: 2).coerceIn(1, 8)
    }

    suspend fun setFfmpegThreadCount(threads: Int) {
        context.dataStore.edit { it[FFMPEG_THREAD_COUNT] = threads.coerceIn(1, 8) }
    }

    fun getVideoFastModeScope(): Flow<VideoFastModeScope> = context.dataStore.data.map { preferences ->
        preferences[VIDEO_FAST_MODE_SCOPE]
            ?.let { value -> runCatching { VideoFastModeScope.valueOf(value) }.getOrNull() }
            ?: VideoFastModeScope.NONE
    }

    suspend fun setVideoFastModeScope(scope: VideoFastModeScope) {
        context.dataStore.edit { it[VIDEO_FAST_MODE_SCOPE] = scope.name }
    }

    fun getVideoSceneDedupThreshold(): Flow<Float> = context.dataStore.data.map { preferences ->
        (preferences[VIDEO_SCENE_DEDUP_THRESHOLD] ?: DEFAULT_VIDEO_SCENE_DEDUP_THRESHOLD)
            .coerceIn(MIN_VIDEO_SCENE_DEDUP_THRESHOLD, MAX_VIDEO_SCENE_DEDUP_THRESHOLD)
    }

    suspend fun setVideoSceneDedupThreshold(threshold: Float) {
        context.dataStore.edit {
            it[VIDEO_SCENE_DEDUP_THRESHOLD] = threshold.coerceIn(
                MIN_VIDEO_SCENE_DEDUP_THRESHOLD,
                MAX_VIDEO_SCENE_DEDUP_THRESHOLD
            )
        }
    }

    suspend fun loadVideoSceneDedupThreshold(): Float {
        val preferences = context.dataStore.data.first()
        return (preferences[VIDEO_SCENE_DEDUP_THRESHOLD] ?: DEFAULT_VIDEO_SCENE_DEDUP_THRESHOLD)
            .coerceIn(MIN_VIDEO_SCENE_DEDUP_THRESHOLD, MAX_VIDEO_SCENE_DEDUP_THRESHOLD)
    }

    suspend fun loadVideoDecodeSettings(): VideoDecodeSettings {
        val preferences = context.dataStore.data.first()
        val policy = preferences[DIFFICULT_VIDEO_POLICY]
            ?.let { value -> runCatching { DifficultVideoPolicy.valueOf(value) }.getOrNull() }
            ?: DifficultVideoPolicy.FAIL_FAST
        return VideoDecodeSettings(
            difficultVideoPolicy = policy,
            ffmpegThreads = (preferences[FFMPEG_THREAD_COUNT] ?: 2).coerceIn(1, 8),
            fastModeScope = preferences[VIDEO_FAST_MODE_SCOPE]
                ?.let { value -> runCatching { VideoFastModeScope.valueOf(value) }.getOrNull() }
                ?: VideoFastModeScope.NONE
        )
    }

    fun getSearchHistoryLimit(): Flow<Int> = context.dataStore.data.map { preferences ->
        (preferences[SEARCH_HISTORY_LIMIT] ?: 20).coerceIn(5, 100)
    }

    suspend fun setSearchHistoryLimit(limit: Int) {
        context.dataStore.edit { preferences ->
            preferences[SEARCH_HISTORY_LIMIT] = limit.coerceIn(5, 100)
            val searches = decodeSavedSearches(preferences[SAVED_SEARCHES])
            preferences[SAVED_SEARCHES] = json.encodeToString(trimHistory(searches, preferences))
        }
    }

    fun getSavedSearches(): Flow<List<SavedSearch>> = context.dataStore.data.map { preferences ->
        decodeSavedSearches(preferences[SAVED_SEARCHES])
            .sortedWith(compareByDescending<SavedSearch> { it.pinned }.thenByDescending { it.createdAt })
    }

    suspend fun saveSearch(query: String, resultPhotoIds: List<Long>) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        context.dataStore.edit { preferences ->
            val existing = decodeSavedSearches(preferences[SAVED_SEARCHES]).toMutableList()
            val duplicateIndex = existing.indexOfFirst {
                it.type == SavedSearchType.TEXT &&
                    it.query.equals(normalized, ignoreCase = true)
            }
            if (duplicateIndex >= 0) {
                val duplicate = existing[duplicateIndex]
                // Updating cached results does not refresh queue position: the
                // unpinned retention policy is strict first-in, first-out.
                existing[duplicateIndex] = duplicate.copy(resultPhotoIds = resultPhotoIds)
            } else {
                existing.add(
                    SavedSearch(
                        id = UUID.randomUUID().toString(),
                        query = normalized,
                        resultPhotoIds = resultPhotoIds,
                        createdAt = System.currentTimeMillis(),
                        pinned = false
                    )
                )
            }
            preferences[SAVED_SEARCHES] = json.encodeToString(trimHistory(existing, preferences))
        }
    }

    suspend fun saveSimilarSearch(
        sourcePhotoId: Long,
        sourceLabel: String,
        resultPhotoIds: List<Long>
    ) {
        val label = sourceLabel.ifBlank { sourcePhotoId.toString() }
        context.dataStore.edit { preferences ->
            val existing = decodeSavedSearches(preferences[SAVED_SEARCHES]).toMutableList()
            val duplicateIndex = existing.indexOfFirst {
                it.type == SavedSearchType.SIMILAR_IMAGE &&
                    it.sourcePhotoId == sourcePhotoId
            }
            if (duplicateIndex >= 0) {
                val duplicate = existing[duplicateIndex]
                // Keep its FIFO position and pin state while refreshing cached results.
                existing[duplicateIndex] = duplicate.copy(
                    query = label,
                    resultPhotoIds = resultPhotoIds
                )
            } else {
                existing.add(
                    SavedSearch(
                        id = UUID.randomUUID().toString(),
                        query = label,
                        resultPhotoIds = resultPhotoIds,
                        createdAt = System.currentTimeMillis(),
                        type = SavedSearchType.SIMILAR_IMAGE,
                        sourcePhotoId = sourcePhotoId
                    )
                )
            }
            preferences[SAVED_SEARCHES] = json.encodeToString(trimHistory(existing, preferences))
        }
    }

    suspend fun getSavedSearch(id: String): SavedSearch? =
        getSavedSearches().first().firstOrNull { it.id == id }

    suspend fun toggleSavedSearchPin(id: String) {
        updateSavedSearches { searches ->
            searches.map { search ->
                if (search.id == id) search.copy(pinned = !search.pinned) else search
            }
        }
    }

    suspend fun deleteSavedSearch(id: String) {
        updateSavedSearches { searches -> searches.filterNot { it.id == id } }
    }

    suspend fun clearUnpinnedSavedSearches() {
        updateSavedSearches { searches -> searches.filter { it.pinned } }
    }

    private suspend fun updateSavedSearches(transform: (List<SavedSearch>) -> List<SavedSearch>) {
        context.dataStore.edit { preferences ->
            val updated = transform(decodeSavedSearches(preferences[SAVED_SEARCHES]))
            preferences[SAVED_SEARCHES] = json.encodeToString(trimHistory(updated, preferences))
        }
    }

    private fun decodeSavedSearches(value: String?): List<SavedSearch> = runCatching {
        if (value.isNullOrBlank()) emptyList() else json.decodeFromString<List<SavedSearch>>(value)
    }.getOrDefault(emptyList())

    private fun trimHistory(searches: List<SavedSearch>, preferences: Preferences): List<SavedSearch> {
        val limit = (preferences[SEARCH_HISTORY_LIMIT] ?: 20).coerceIn(5, 100)
        // Pinned entries are intentionally outside the retention limit and are
        // never evicted automatically. Only unpinned entries participate in FIFO.
        val pinned = searches.filter { it.pinned }
        val recent = searches.filterNot { it.pinned }
            .sortedBy { it.createdAt }
            .takeLast(limit)
        return (pinned + recent).distinctBy { it.id }
    }

}
