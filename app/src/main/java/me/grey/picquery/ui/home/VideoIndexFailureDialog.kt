package me.grey.picquery.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.grey.picquery.R
import me.grey.picquery.data.video.model.VideoIndexFailure

@Composable
fun VideoIndexFailureDialog(
    failure: VideoIndexFailure,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(R.string.video_index_failure_title),
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            SelectionContainer {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(stringResource(R.string.video_index_failure_stopped))
                    FailureLine(stringResource(R.string.video_index_failure_video), failure.videoName)
                    FailureLine(stringResource(R.string.video_index_failure_decoder), failure.decoder)
                    FailureLine(stringResource(R.string.video_index_failure_type), failure.exceptionType)
                    FailureLine(stringResource(R.string.video_index_failure_message), failure.message)
                    if (failure.videoUri.isNotBlank()) {
                        FailureLine(stringResource(R.string.video_index_failure_uri), failure.videoUri)
                    }
                    Text(
                        failure.details,
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.video_index_failure_acknowledge))
            }
        }
    )
}

@Composable
private fun FailureLine(label: String, value: String) {
    Text(
        "$label: ${value.ifBlank { "—" }}",
        modifier = Modifier.padding(top = 5.dp),
        style = MaterialTheme.typography.bodySmall
    )
}
