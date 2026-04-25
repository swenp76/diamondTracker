package de.baseball.diamond9

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RunSuggestionDialog(
    reachedBaseCount: Int,
    runnerOuts: Int,
    initialLob: Int = 0,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var lob by remember { mutableIntStateOf(initialLob) }
    val suggestedRuns = (reachedBaseCount - runnerOuts - lob).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_run_suggestion_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.dialog_run_suggestion_lob_question))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (0..3).forEach { value ->
                        val isSelected = lob == value
                        OutlinedButton(
                            onClick = { lob = value },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(value.toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider()

                val summary = if (runnerOuts > 0) {
                    // %1$d reached − %2$d runner out − %3$d LOB = %4$d Runs
                    stringResource(R.string.dialog_run_suggestion_summary_with_outs, reachedBaseCount, runnerOuts, lob, suggestedRuns)
                } else {
                    stringResource(R.string.dialog_run_suggestion_summary, reachedBaseCount, lob, suggestedRuns)
                }

                Text(
                    text = summary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(suggestedRuns) }) {
                Text(stringResource(R.string.btn_save_to_scoreboard))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
