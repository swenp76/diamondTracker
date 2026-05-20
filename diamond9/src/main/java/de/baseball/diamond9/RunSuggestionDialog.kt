package de.baseball.diamond9

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RunSuggestionDialog(
    reachedBaseCount: Int,
    runnerOuts: Int,
    recordedOuts: Int,
    rollOverEnabled: Boolean = false,
    initialLob: Int = 0,
    initialRollOver: Boolean = false,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var lob by remember { mutableIntStateOf(initialLob) }
    var rollOver by remember { mutableStateOf(initialRollOver && rollOverEnabled) }

    val rollOverRuns = if (rollOver && rollOverEnabled) (3 - recordedOuts).coerceAtLeast(0) else 0
    val suggestedRuns = (reachedBaseCount - runnerOuts - lob).coerceAtLeast(0) + rollOverRuns

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_run_suggestion_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Outs info – always visible
                Text(
                    text = stringResource(R.string.dialog_run_suggestion_outs_recorded, recordedOuts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorResource(R.color.color_text_secondary)
                )

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
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(value.toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Roll-Over toggle – only visible when the league setting is enabled
                if (rollOverEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dialog_run_suggestion_rollover),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (rollOver && rollOverRuns > 0) {
                                Text(
                                    text = stringResource(R.string.dialog_run_suggestion_rollover_extra, rollOverRuns),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Switch(
                            checked = rollOver,
                            onCheckedChange = { rollOver = it }
                        )
                    }
                }

                HorizontalDivider()

                // Summary – built programmatically to handle all combinations
                val summary = buildString {
                    append("$reachedBaseCount reached base")
                    if (runnerOuts > 0) append(" − $runnerOuts runner out")
                    append(" − $lob LOB")
                    if (rollOver && rollOverRuns > 0) append(" + $rollOverRuns Roll-Over")
                    append(" = $suggestedRuns Runs")
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
