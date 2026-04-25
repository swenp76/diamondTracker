package de.baseball.diamond9

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay

@Composable
fun ScoringNotification(
    scoringRunners: List<GameRunner>,
    onFinished: () -> Unit
) {
    if (scoringRunners.isEmpty()) return

    LaunchedEffect(scoringRunners) {
        delay(4000) // Show for 4 seconds
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            color = colorResource(R.color.color_green_dark),
            contentColor = Color.White,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 8.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "⚾",
                    fontSize = 24.sp
                )
                Column {
                    Text(
                        text = if (scoringRunners.size > 1) 
                            stringResource(R.string.score_notif_multiple, scoringRunners.size)
                        else 
                            stringResource(R.string.score_notif_single),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    scoringRunners.forEach { runner ->
                        val label = runner.jerseyNumber?.takeIf { it.isNotBlank() } ?: "Slot ${runner.slot}"
                        Text(
                            text = "${runner.name} (#$label)",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}
