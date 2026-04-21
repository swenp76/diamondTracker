package de.baseball.diamond9

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
fun ExportFormatDialog(
    onDismiss: () -> Unit,
    onSelect: (ExportFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_format_dialog_title)) },
        text = {
            Column {
                listOf(
                    R.string.export_format_pdf to ExportFormat.PDF,
                    R.string.export_format_jpg to ExportFormat.JPG,
                    R.string.export_format_csv to ExportFormat.CSV
                ).forEach { (labelRes, format) ->
                    TextButton(
                        onClick = { onSelect(format) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(labelRes))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
