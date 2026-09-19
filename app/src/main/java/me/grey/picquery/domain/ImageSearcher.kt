package me.grey.picquery.domain

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import me.grey.picquery.R
import me.grey.picquery.common.encodeProgressCallback
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo
import java.util.AbstractMap

/**
 * Search target type for image or text search
 */
sealed class SearchTarget(val labelResId: Int, val icon: ImageVector) {
    data object Image : SearchTarget(R.string.search_target_image, Icons.Outlined.ImageSearch)
    data object Text : SearchTarget(R.string.search_target_text, Icons.Outlined.Translate)
}

/**
 * Image Searcher - External interface for search functionality
 *
 * This is the main entry point for search operations in the application.
 * It coordinates between services and manages search state.
 *
 * Responsibilities:
 * - Search range state management
 * - Search target type management
 * - Search result state management
 * - Public API for search operations
 *
 * Delegates to:
 * - EmbeddingService -> Encoding operations
 * - SearchConfigurationService -> Configuration management
 * - SearchOrchestrator -> Search execution
 */
class ImageSearcher(
    private val embeddingService: EmbeddingService,
    private val configurationService: SearchConfigurationService,
    private val searchOrchestrator: SearchOrchestrator,
    private val preferenceRepository: PreferenceRepository
) {
    val searchRange = mutableStateListOf<Album>()
    var isSearchAll = mutableStateOf(true)
    val searchResultIds = mutableStateListOf<Long>()
    private var availableAlbums: List<Album> = emptyList()
    private var savedRangeIds: Set<Long> = emptySet()
    @Volatile private var rangeLoaded = false
    @Volatile private var rangeChangedSinceLaunch = false

    // ============ Delegated to ConfigurationService ============

    val matchThreshold: State<Float>
        get() = configurationService.matchThreshold

    val topK: State<Int>
        get() = configurationService.topK

    // ============ Initialization ============

    /**
     * Initialize the searcher (delegated to ConfigurationService)
     */
    suspend fun initialize() {
        configurationService.initialize()
        val (savedSearchAll, savedAlbumIds) = preferenceRepository.loadSearchRangeSync()
        if (!rangeChangedSinceLaunch) {
            savedRangeIds = savedAlbumIds
            isSearchAll.value = savedSearchAll
            rangeLoaded = true
            applySavedRange()
        }
    }

    // ============ Methods Delegated to EmbeddingService ============

    /**
     * Check if embeddings exist
     */
    suspend fun hasEmbedding(): Boolean {
        return embeddingService.hasEmbedding()
    }

    /**
     * Encode photo list (delegated to EmbeddingService)
     */
    suspend fun encodePhotoListV2(
        photos: List<Photo>,
        progressCallback: encodeProgressCallback? = null
    ): Boolean {
        return embeddingService.encodePhotoList(photos, progressCallback)
    }

    // ============ Search Range Management ============

    /**
     * Update search range
     */
    suspend fun updateRange(range: List<Album>, searchAll: Boolean) {
        rangeChangedSinceLaunch = true
        rangeLoaded = true
        savedRangeIds = range.mapTo(linkedSetOf()) { it.id }
        searchRange.clear()
        searchRange.addAll(range.sortedByDescending { it.count })
        isSearchAll.value = searchAll
        preferenceRepository.saveSearchRange(searchAll, savedRangeIds)
    }

    /** Reconnect persisted album IDs to the current MediaStore/album records. */
    fun reconcileSearchRange(albums: List<Album>) {
        availableAlbums = albums
        if (rangeLoaded) applySavedRange()
    }

    private fun applySavedRange() {
        searchRange.clear()
        searchRange.addAll(
            availableAlbums
                .filter { it.id in savedRangeIds }
                .sortedByDescending { it.count }
        )
    }

    /**
     * Update search configuration (delegated to ConfigurationService)
     */
    fun updateSearchConfiguration(newMatchThreshold: Float, newTopK: Int) {
        configurationService.updateConfiguration(newMatchThreshold, newTopK)
    }

    /**
     * Text search with translation support
     * @param text Search query text
     * @param range Album range to search within (defaults to current searchRange)
     * @param onSuccess Callback with search results
     */
    suspend fun search(
        text: String,
        range: List<Album> = searchRange,
        onSuccess: suspend (MutableSet<MutableMap.MutableEntry<Double, Long>>) -> Unit
    ) {
        // Legacy API - kept for backward compatibility
        // This uses the old search format but delegates to orchestrator internally
        searchOrchestrator.searchByText(text, range, isSearchAll.value) { results ->
            // Convert new format to old format for backward compatibility
            val legacyResults = results
                .map { (photoId, score) ->
                    AbstractMap.SimpleEntry(score, photoId) as MutableMap.MutableEntry<Double, Long>
                }
                .toMutableSet()
            onSuccess(legacyResults)
        }
    }

    /**
     * Text search V2 with translation support
     * @param text Search query text
     * @param range Album range to search within (defaults to current searchRange)
     * @param onSuccess Callback with search results as List<Pair<PhotoId, Score>>
     */
    suspend fun searchV2(
        text: String,
        range: List<Album> = searchRange,
        onSuccess: suspend (MutableList<Pair<Long, Double>>) -> Unit
    ) {
        searchOrchestrator.searchByText(text, range, isSearchAll.value) { results ->
            // Update search result IDs
            searchResultIds.clear()
            searchResultIds.addAll(results.map { it.first })
            onSuccess(results)
        }
    }

    /**
     * Image search V2
     * @param image Image to search with
     * @param range Album range to search within (defaults to current searchRange)
     * @param onSuccess Callback with search results as List<Pair<PhotoId, Score>>
     */
    suspend fun searchWithRangeV2(
        image: Bitmap,
        range: List<Album> = searchRange,
        onSuccess: suspend (MutableList<Pair<Long, Double>>) -> Unit
    ) {
        searchOrchestrator.searchByImage(image, range, isSearchAll.value) { results ->
            // Update search result IDs
            searchResultIds.clear()
            searchResultIds.addAll(results.map { it.first })
            onSuccess(results)
        }
    }

    suspend fun findSimilarToPhoto(
        photoId: Long,
        range: List<Album> = searchRange,
        onSuccess: suspend (MutableList<Pair<Long, Double>>) -> Unit
    ) {
        searchOrchestrator.searchByPhotoId(photoId, range, isSearchAll.value, onSuccess)
    }

    fun setDisplayResultIds(ids: List<Long>) {
        searchResultIds.clear()
        searchResultIds.addAll(ids)
    }
}
