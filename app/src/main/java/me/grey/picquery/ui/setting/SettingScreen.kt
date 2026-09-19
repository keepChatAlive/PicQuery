package me.grey.picquery.ui.setting

import LogoRow
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import me.grey.picquery.R
import me.grey.picquery.common.Constants.PRIVACY_URL
import me.grey.picquery.common.Constants.SOURCE_REPO_URL
import me.grey.picquery.data.model.InferenceBackend
import me.grey.picquery.data.model.DifficultVideoPolicy
import me.grey.picquery.data.model.VideoFastModeScope
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.ui.common.BackButton
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    onNavigateBack: () -> Unit,
    navigateToIndexMgr: () -> Unit,
    navigateToVideoIndexMgr: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton { onNavigateBack() } }
            )
        }
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            item {
                LogoRow(modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp))
            }
            item { InformationRow() }
            item { Box(modifier = Modifier.height(15.dp)) }
            item { UploadLogSettingItem() }
            item { RuntimeBackendSettingItem() }
            item { IndexingConcurrencySettingItem() }
            item { DifficultVideoPolicySettingItem() }
            item { FfmpegThreadCountSettingItem() }
            item { VideoFastModeSettingItem() }
            item { VideoSceneDedupSettingItem() }
            item { SearchConfigurationSettingItem() }
            item { SearchHistorySettingItem() }
            item { AlbumIndexManagerUIItem(navigateToIndexMgr) }
            item { VideoIndexManagerUIItem(navigateToVideoIndexMgr) }
        }
    }
}

@Composable
private fun VideoSceneDedupSettingItem(settingViewModel: SettingViewModel = koinViewModel()) {
    val persistedThreshold by settingViewModel.videoSceneDedupThreshold.collectAsState(
        initial = PreferenceRepository.DEFAULT_VIDEO_SCENE_DEDUP_THRESHOLD
    )
    var threshold by remember(persistedThreshold) { mutableStateOf(persistedThreshold) }
    ListItem(
        leadingContent = { Icon(Icons.Default.FilterList, contentDescription = null) },
        headlineContent = {
            Text(stringResource(R.string.video_scene_dedup_threshold_title, threshold))
        },
        supportingContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.video_scene_dedup_threshold_description))
                Slider(
                    value = threshold,
                    onValueChange = {
                        threshold = (it * 100f).roundToInt() / 100f
                    },
                    onValueChangeFinished = {
                        settingViewModel.setVideoSceneDedupThreshold(threshold)
                    },
                    valueRange = PreferenceRepository.MIN_VIDEO_SCENE_DEDUP_THRESHOLD..
                        PreferenceRepository.MAX_VIDEO_SCENE_DEDUP_THRESHOLD,
                    steps = 18
                )
            }
        }
    )
}

@Composable
private fun VideoFastModeSettingItem(settingViewModel: SettingViewModel = koinViewModel()) {
    val scope by settingViewModel.videoFastModeScope.collectAsState(initial = VideoFastModeScope.NONE)
    var expanded by remember { mutableStateOf(false) }
    fun labelFor(value: VideoFastModeScope): Int = when (value) {
        VideoFastModeScope.NONE -> R.string.video_fast_mode_none
        VideoFastModeScope.DIFFICULT_ONLY -> R.string.video_fast_mode_difficult_only
        VideoFastModeScope.ALL -> R.string.video_fast_mode_all
    }
    ListItem(
        leadingContent = { Icon(Icons.Default.Speed, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.video_fast_mode_title)) },
        supportingContent = { Text(stringResource(R.string.video_fast_mode_description)) },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(stringResource(labelFor(scope)))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    VideoFastModeScope.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(labelFor(option))) },
                            onClick = {
                                settingViewModel.setVideoFastModeScope(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun DifficultVideoPolicySettingItem(settingViewModel: SettingViewModel = koinViewModel()) {
    val policy by settingViewModel.difficultVideoPolicy.collectAsState(
        initial = DifficultVideoPolicy.FAIL_FAST
    )
    var expanded by remember { mutableStateOf(false) }
    fun labelFor(value: DifficultVideoPolicy): Int = when (value) {
        DifficultVideoPolicy.FAIL_FAST -> R.string.difficult_video_policy_fail_fast
        DifficultVideoPolicy.SKIP -> R.string.difficult_video_policy_skip
        DifficultVideoPolicy.CONTINUE -> R.string.difficult_video_policy_continue
    }
    ListItem(
        leadingContent = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.difficult_video_policy_title)) },
        supportingContent = { Text(stringResource(R.string.difficult_video_policy_description)) },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(stringResource(labelFor(policy)))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DifficultVideoPolicy.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(labelFor(option))) },
                            onClick = {
                                settingViewModel.setDifficultVideoPolicy(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun FfmpegThreadCountSettingItem(settingViewModel: SettingViewModel = koinViewModel()) {
    val threads by settingViewModel.ffmpegThreadCount.collectAsState(initial = 2)
    ListItem(
        leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.ffmpeg_thread_count_title, threads)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.ffmpeg_thread_count_description))
                Slider(
                    value = threads.toFloat(),
                    onValueChange = { settingViewModel.setFfmpegThreadCount(it.roundToInt()) },
                    valueRange = 1f..8f,
                    steps = 6
                )
            }
        }
    )
}

@Composable
private fun SearchConfigurationSettingItem(settingViewModel: SettingViewModel = koinViewModel()) {
    val persistedThreshold = settingViewModel.matchThreshold.value
    val persistedTopK = settingViewModel.topK.value
    var threshold by remember(persistedThreshold) { mutableStateOf(persistedThreshold) }
    var topK by remember(persistedTopK) { mutableStateOf(persistedTopK) }

    ListItem(
        leadingContent = { Icon(Icons.Default.FilterList, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.image_search_config_title)) },
        supportingContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("${stringResource(R.string.match_threshold_title)}: ${String.format("%.2f", threshold)}")
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    onValueChangeFinished = {
                        settingViewModel.setSearchConfiguration(threshold, topK)
                    },
                    valueRange = 0.1f..0.5f,
                    steps = 39
                )
                Text("${stringResource(R.string.top_k_results_title)}: $topK")
                Slider(
                    value = topK.toFloat(),
                    onValueChange = {
                        topK = ((it / 10f).roundToInt() * 10).coerceIn(10, 2000)
                    },
                    onValueChangeFinished = {
                        settingViewModel.setSearchConfiguration(threshold, topK)
                    },
                    valueRange = 10f..2000f,
                    steps = 198
                )
            }
        }
    )
}

@Composable
private fun RuntimeBackendSettingItem(settingViewModel: SettingViewModel = koinViewModel()) {
    val backend by settingViewModel.inferenceBackend.collectAsState(initial = InferenceBackend.AUTO)
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = { Icon(Icons.Default.Speed, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.inference_backend_title)) },
        supportingContent = { Text(stringResource(R.string.inference_backend_description)) },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        stringResource(
                            when (backend) {
                                InferenceBackend.AUTO -> R.string.inference_backend_auto
                                InferenceBackend.CPU -> R.string.inference_backend_cpu
                                InferenceBackend.GPU -> R.string.inference_backend_gpu
                            }
                        )
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    InferenceBackend.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            InferenceBackend.AUTO -> R.string.inference_backend_auto
                                            InferenceBackend.CPU -> R.string.inference_backend_cpu
                                            InferenceBackend.GPU -> R.string.inference_backend_gpu
                                        }
                                    )
                                )
                            },
                            onClick = {
                                settingViewModel.setInferenceBackend(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun IndexingConcurrencySettingItem(settingViewModel: SettingViewModel = koinViewModel()) {
    val concurrency by settingViewModel.indexingConcurrency.collectAsState(initial = 2)
    ListItem(
        leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
        headlineContent = {
            Text(stringResource(R.string.indexing_concurrency_title, concurrency))
        },
        supportingContent = {
            Slider(
                value = concurrency.toFloat(),
                onValueChange = { settingViewModel.setIndexingConcurrency(it.toInt()) },
                valueRange = 1f..4f,
                steps = 2
            )
        }
    )
}

@Composable
private fun SearchHistorySettingItem(settingViewModel: SettingViewModel = koinViewModel()) {
    val limit by settingViewModel.searchHistoryLimit.collectAsState(initial = 20)
    ListItem(
        leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.search_history_limit_title, limit)) },
        supportingContent = {
            Slider(
                value = limit.toFloat(),
                onValueChange = {
                    val rounded = ((it.toInt() / 5) * 5).coerceIn(5, 100)
                    settingViewModel.setSearchHistoryLimit(rounded)
                },
                valueRange = 5f..100f,
                steps = 18
            )
        },
        trailingContent = {
            TextButton(onClick = settingViewModel::clearUnpinnedHistory) {
                Text(stringResource(R.string.clear_unpinned_history))
            }
        }
    )
}

@Composable
private fun UploadLogSettingItem(settingViewModel: SettingViewModel = koinViewModel()) {
    val enable = settingViewModel.enableUploadLog.collectAsState(initial = true)
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Default.PermDeviceInformation,
                contentDescription = "Share Info"
            )
        },
        headlineContent = { Text(text = stringResource(R.string.share_anonymous_data)) },
        supportingContent = { Text(text = stringResource(R.string.share_anonymous_data_statement)) },
        trailingContent = {
            Switch(
                checked = enable.value,
                onCheckedChange = { enabled ->
                    settingViewModel.setEnableUploadLog(enabled)
                }
            )
        },
        modifier = Modifier.clickable { settingViewModel.setEnableUploadLog(!enable.value) }
    )
}

@Composable
private fun InformationRow() {
    val context = LocalContext.current
    fun launchURL(url: String) {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(context, intent, null)
    }

    Row(
        modifier = Modifier
            .padding(bottom = 15.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { launchURL(PRIVACY_URL) }) {
            Text(text = stringResource(R.string.privacy_policy))
        }
        Divider()
        TextButton(onClick = { launchURL(SOURCE_REPO_URL) }) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = stringResource(R.string.github)
            )
            Box(modifier = Modifier.width(5.dp))
            Text(text = stringResource(R.string.github))
        }
    }
}

@Composable
private fun Divider() {
    VerticalDivider(
        Modifier
            .height(20.dp)
            .padding(horizontal = 3.dp)
    )
}

@Composable
private fun AlbumIndexManagerUIItem(navigateToIndexMgr: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.Dataset,
                contentDescription = "Click to Manage Album Indexes"
            )
        },
        headlineContent = { Text(text = stringResource(R.string.album_index_manager_ui_title)) },
        supportingContent = { Text(text = stringResource(R.string.album_index_manager_ui_desc)) },
        modifier = Modifier.clickable { navigateToIndexMgr() }
    )
}

@Composable
private fun VideoIndexManagerUIItem(navigate: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(Icons.Filled.VideoLibrary, contentDescription = null)
        },
        headlineContent = { Text(stringResource(R.string.video_index_manager_title)) },
        supportingContent = { Text(stringResource(R.string.video_index_manager_settings_desc)) },
        modifier = Modifier.clickable(onClick = navigate)
    )
}
