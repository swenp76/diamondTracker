package de.baseball.diamond9

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun ExportFormatDialog(
    onDismiss: () -> Unit,
    onSelect: (ExportFormat, ExportAction) -> Unit
) {
    var selectedFormat by remember { mutableStateOf<ExportFormat?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_format_dialog_title)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    R.string.export_format_pdf to ExportFormat.PDF,
                    R.string.export_format_jpg to ExportFormat.JPG,
                    R.string.export_format_csv to ExportFormat.CSV
                ).forEach { (labelRes, format) ->
                    val selected = selectedFormat == format
                    if (selected) {
                        Button(
                            onClick = { selectedFormat = format },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(labelRes)) }
                    } else {
                        OutlinedButton(
                            onClick = { selectedFormat = format },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(labelRes)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedFormat?.let { onSelect(it, ExportAction.SHARE) } },
                enabled = selectedFormat != null
            ) { Text(stringResource(R.string.export_action_share)) }
        },
        dismissButton = {
            TextButton(
                onClick = { selectedFormat?.let { onSelect(it, ExportAction.SAVE) } },
                enabled = selectedFormat != null
            ) { Text(stringResource(R.string.export_action_save)) }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
