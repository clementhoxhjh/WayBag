package com.example.waybag.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.waybag.ui.theme.*

/**
 * Dynamic, flowing "rainbow" background gradient.
 */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    
    // Animate coordinates for the radial gradients to create a "flowing" effect
    val xOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "xOffset"
    )
    
    val yOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WayBag_Lime.copy(alpha = 0.3f))
    ) {
        // Flowing Lavender
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Pale_Lavender.copy(alpha = 0.6f), Color.Transparent),
                        center = Offset(xOffset, yOffset),
                        radius = 2000f
                    )
                )
        )
        // Flowing Aqua
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Soft_Aqua.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(1000f - xOffset, 1500f - yOffset),
                        radius = 1800f
                    )
                )
        )
        // Flowing Seafoam
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Seafoam_Green.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(200f + xOffset * 0.5f, 1000f + yOffset * 0.5f),
                        radius = 1600f
                    )
                )
        )
        // Flowing Yellow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Light_Yellow.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(800f - xOffset * 0.2f, 500f + yOffset * 0.8f),
                        radius = 1400f
                    )
                )
        )

        content()
    }
}

/**
 * Hyper-realistic glass panel with inner shadow and glowing edge.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(elevation = 20.dp, shape = RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(28.dp))
            .background(Glass_White)
            // Simulated Inner Shadow / Highlight for "frosted" depth
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
                        startY = 0f,
                        endY = 100f
                    )
                )
            }
            .border(1.dp, Glass_Stroke, RoundedCornerShape(28.dp))
            .border(0.5.dp, Glass_Glow_Green, RoundedCornerShape(28.dp)) // Fine glowing pastel green edge
            .padding(1.dp) // Offset content slightly from the glowing border
    ) {
        content()
    }
}

/**
 * A "thin glass" panel with a precise lime green outline.
 */
@Composable
fun ThinGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Glass_White)
            .border(1.dp, WayBag_Lime, RoundedCornerShape(20.dp))
    ) {
        content()
    }
}
