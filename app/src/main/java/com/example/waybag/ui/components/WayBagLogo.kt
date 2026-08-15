package com.example.waybag.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.waybag.ui.theme.Logo_Backlight
import com.example.waybag.ui.theme.Premium_Gray

@Composable
fun WayBagLogo(
    modifier: Modifier = Modifier,
    color: Color = Premium_Gray
) {
    Box(modifier = modifier.size(120.dp), contentAlignment = Alignment.Center) {
        // Soft Green Backlight
        Canvas(modifier = Modifier.size(100.dp).blur(30.dp)) {
            drawCircle(color = Logo_Backlight)
        }

        Canvas(modifier = Modifier.size(80.dp)) {
            val strokeWidth = 3f
            
            // Draw the bag body with a gradient for "frosted" effect
            val bagPath = Path().apply {
                moveTo(size.width * 0.3f, size.height * 0.45f)
                lineTo(size.width * 0.7f, size.height * 0.45f)
                quadraticTo(size.width * 0.85f, size.height * 0.45f, size.width * 0.85f, size.height * 0.55f)
                lineTo(size.width * 0.85f, size.height * 0.85f)
                quadraticTo(size.width * 0.85f, size.height * 0.95f, size.width * 0.7f, size.height * 0.95f)
                lineTo(size.width * 0.3f, size.height * 0.95f)
                quadraticTo(size.width * 0.15f, size.height * 0.95f, size.width * 0.15f, size.height * 0.85f)
                lineTo(size.width * 0.15f, size.height * 0.55f)
                quadraticTo(size.width * 0.15f, size.height * 0.45f, size.width * 0.3f, size.height * 0.45f)
                close()
            }
            
            drawPath(
                path = bagPath,
                color = color,
                style = Stroke(width = strokeWidth)
            )
            
            // Draw the handle
            val handlePath = Path().apply {
                moveTo(size.width * 0.42f, size.height * 0.45f)
                quadraticTo(size.width * 0.42f, size.height * 0.32f, size.width * 0.5f, size.height * 0.32f)
                quadraticTo(size.width * 0.58f, size.height * 0.32f, size.width * 0.58f, size.height * 0.45f)
            }
            drawPath(path = handlePath, color = color, style = Stroke(width = strokeWidth))

            // Refined steam
            val steam1 = Path().apply {
                moveTo(size.width * 0.48f, size.height * 0.28f)
                quadraticTo(size.width * 0.44f, size.height * 0.18f, size.width * 0.48f, size.height * 0.1f)
            }
            drawPath(path = steam1, color = color.copy(alpha = 0.6f), style = Stroke(width = 2f))
            
            val steam2 = Path().apply {
                moveTo(size.width * 0.52f, size.height * 0.28f)
                quadraticTo(size.width * 0.56f, size.height * 0.18f, size.width * 0.52f, size.height * 0.1f)
            }
            drawPath(path = steam2, color = color.copy(alpha = 0.6f), style = Stroke(width = 2f))
        }
    }
}
