package de.baseball.diamond9

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

@Composable
fun DiamondInfield(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)
        val center = Offset(w / 2, h / 2)
        val radius = minDim * 0.31f // Reduced from 0.35 to avoid clipping

        // 1. Draw Infield Dirt Area (simplified as a rotated square)
        rotate(45f, pivot = center) {
            drawRect(
                color = Color(0xFFD2B48C), // Tan color for dirt
                topLeft = Offset(center.x - radius * 1.05f, center.y - radius * 1.05f),
                size = Size(radius * 2.1f, radius * 2.1f)
            )
        }

        // 2. Define Base Positions
        // Coordinate system: Home at bottom center, 1st right center, 2nd top center, 3rd left center
        val homePos = Offset(center.x, center.y + radius)
        val firstPos = Offset(center.x + radius, center.y)
        val secondPos = Offset(center.x, center.y - radius)
        val thirdPos = Offset(center.x - radius, center.y)

        // 3. Draw Foul Lines and Base Paths
        val lineWeight = 2.dp.toPx()
        val pathColor = Color.White
        
        // From home to 1st and home to 3rd (foul lines)
        drawLine(pathColor, homePos, firstPos, strokeWidth = lineWeight)
        drawLine(pathColor, homePos, thirdPos, strokeWidth = lineWeight)
        
        // Between bases
        drawLine(pathColor, firstPos, secondPos, strokeWidth = lineWeight)
        drawLine(pathColor, secondPos, thirdPos, strokeWidth = lineWeight)

        // 4. Draw Bases
        val baseSize = 14.dp.toPx()
        
        // 1st, 2nd, 3rd (Squares rotated 45 deg)
        drawBaseSquare(firstPos, baseSize)
        drawBaseSquare(secondPos, baseSize)
        drawBaseSquare(thirdPos, baseSize)
        
        // Home Plate (Pentagon)
        drawHomePlate(homePos, baseSize * 1.2f)
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
