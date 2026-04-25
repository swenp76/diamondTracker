package de.baseball.diamond9

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DiamondInfield(
    modifier: Modifier = Modifier,
    runners: Map<Int, GameRunner> = emptyMap(),
    onBaseClick: (Int) -> Unit = {}
) {
    Canvas(modifier = modifier
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                // Hit test for bases
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val minDim = minOf(w, h)
                val center = Offset(w / 2, h / 2)
                val radius = minDim * 0.31f
                
                val basePositions = mapOf(
                    1 to Offset(center.x + radius, center.y),
                    2 to Offset(center.x, center.y - radius),
                    3 to Offset(center.x - radius, center.y),
                    0 to Offset(center.x, center.y + radius) // Home
                )
                
                val hitRadius = 24.dp.toPx()
                basePositions.forEach { (base, pos) ->
                    val dist = (offset - pos).getDistance()
                    if (dist < hitRadius) {
                        onBaseClick(base)
                    }
                }
            }
        }
    ) {
        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)
        val center = Offset(w / 2, h / 2)
        val radius = minDim * 0.31f // Reduced from 0.35 to avoid clipping

        // 1. Draw Infield Dirt Area
        rotate(45f, pivot = center) {
            drawRect(
                color = Color(0xFFD2B48C), // Tan color for dirt
                topLeft = Offset(center.x - radius * 1.05f, center.y - radius * 1.05f),
                size = Size(radius * 2.1f, radius * 2.1f)
            )
        }

        // 2. Define Base Positions
        val homePos = Offset(center.x, center.y + radius)
        val firstPos = Offset(center.x + radius, center.y)
        val secondPos = Offset(center.x, center.y - radius)
        val thirdPos = Offset(center.x - radius, center.y)

        // 3. Draw Foul Lines and Base Paths
        val lineWeight = 2.dp.toPx()
        val pathColor = Color.White
        
        drawLine(pathColor, homePos, firstPos, strokeWidth = lineWeight)
        drawLine(pathColor, homePos, thirdPos, strokeWidth = lineWeight)
        drawLine(pathColor, firstPos, secondPos, strokeWidth = lineWeight)
        drawLine(pathColor, secondPos, thirdPos, strokeWidth = lineWeight)

        // 4. Draw Bases
        val baseSize = 14.dp.toPx()
        drawBaseSquare(firstPos, baseSize)
        drawBaseSquare(secondPos, baseSize)
        drawBaseSquare(thirdPos, baseSize)
        drawHomePlate(homePos, baseSize * 1.2f)

        // 5. Draw Runners
        val runnerRadius = 14.dp.toPx()
        runners.forEach { (base, runner) ->
            val pos = when(base) {
                1 -> firstPos
                2 -> secondPos
                3 -> thirdPos
                else -> homePos
            }
            
            drawCircle(
                color = Color(0xFF1A5FA8), // Primary blue
                radius = runnerRadius,
                center = pos
            )
            
            // Draw Label (Jersey or Slot)
            val label = runner.jerseyNumber?.takeIf { it.isNotBlank() } ?: runner.slot.toString()
            if (label.isNotEmpty()) {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 11.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    pos.x,
                    pos.y + (paint.textSize / 3),
                    paint
                )
            }
        }
    }
}

private fun DrawScope.drawBaseSquare(center: Offset, size: Float) {
    rotate(45f, pivot = center) {
        drawRect(
            color = Color.White,
            topLeft = Offset(center.x - size / 2, center.y - size / 2),
            size = Size(size, size)
        )
    }
}

private fun DrawScope.drawHomePlate(center: Offset, size: Float) {
    val h = size
    val w = size
    val path = Path().apply {
        // Starting from bottom peak (pointing towards catcher/down)
        moveTo(center.x, center.y + h / 2)
        lineTo(center.x - w / 2, center.y)
        lineTo(center.x - w / 2, center.y - h / 2)
        lineTo(center.x + w / 2, center.y - h / 2)
        lineTo(center.x + w / 2, center.y)
        close()
    }
    drawPath(path, Color.White)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunnerManagementSheet(
    runner: GameRunner,
    onDismiss: () -> Unit,
    onMarkOut: () -> Unit,
    onMove: (Int) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val title = buildString {
                append(runner.name)
                runner.jerseyNumber?.takeIf { it.isNotBlank() }?.let { append(" (#$it)") }
                if (runner.jerseyNumber.isNullOrBlank()) append(" (Slot ${runner.slot})")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = stringResource(R.string.runner_mgmt_currently_on, runner.base),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..3).forEach { base ->
                    val isCurrent = runner.base == base
                    OutlinedButton(
                        onClick = { onMove(base) },
                        modifier = Modifier.weight(1f),
                        enabled = !isCurrent,
                        colors = if (isCurrent) ButtonDefaults.outlinedButtonColors(containerColor = colorResource(R.color.color_primary_light)) 
                                 else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("${base}B")
                    }
                }
                Button(
                    onClick = { onMove(0) }, // 0 means Score for this simplified UI
                    modifier = Modifier.weight(1.1f),
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_green_dark))
                ) {
                    Text(stringResource(R.string.runner_mgmt_score))
                }
            }

            Button(
                onClick = onMarkOut,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_strike))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.runner_mgmt_out))
            }
        }
    }
}
