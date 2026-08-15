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
            val strokeWidth = 8f // Thick stroke matching the new design
            
            // Draw the handle
            val handlePath = Path().apply {
                moveTo(size.width * 0.35f, size.height * 0.35f)
                cubicTo(
                    size.width * 0.35f, size.height * 0.12f,
                    size.width * 0.65f, size.height * 0.12f,
                    size.width * 0.65f, size.height * 0.35f
                )
            }
            drawPath(path = handlePath, color = color, style = Stroke(width = strokeWidth))

            // Draw the bag body
            val bagPath = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.35f)
                lineTo(size.width * 0.78f, size.height * 0.35f)
                quadraticTo(size.width * 0.88f, size.height * 0.35f, size.width * 0.9f, size.height * 0.45f)
                lineTo(size.width * 0.94f, size.height * 0.85f)
                quadraticTo(size.width * 0.96f, size.height * 0.95f, size.width * 0.82f, size.height * 0.95f)
                lineTo(size.width * 0.18f, size.height * 0.95f)
                quadraticTo(size.width * 0.04f, size.height * 0.95f, size.width * 0.06f, size.height * 0.85f)
                lineTo(size.width * 0.1f, size.height * 0.45f)
                quadraticTo(size.width * 0.12f, size.height * 0.35f, size.width * 0.22f, size.height * 0.35f)
                close()
            }
            
            drawPath(
                path = bagPath,
                color = color,
                style = Stroke(width = strokeWidth)
            )

            // Draw the smile
            val smilePath = Path().apply {
                moveTo(size.width * 0.35f, size.height * 0.60f)
                quadraticTo(size.width * 0.5f, size.height * 0.80f, size.width * 0.65f, size.height * 0.60f)
            }
            drawPath(path = smilePath, color = color, style = Stroke(width = strokeWidth))
        }
    }
}
