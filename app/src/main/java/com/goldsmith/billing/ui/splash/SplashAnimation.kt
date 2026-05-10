package com.goldsmith.billing.ui.splash

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldsmith.billing.ui.theme.AuraColors
import kotlinx.coroutines.delay

@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    val progress by animateFloatAsState(
        targetValue = if (phase > 0) 1f else 0f,
        animationSpec = tween(900, easing = EaseInOutCubic),
        label = "diamond_progress"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (phase > 1) 1f else 0f,
        animationSpec = tween(450),
        label = "splash_text"
    )
    val shimmer = rememberInfiniteTransition(label = "splash_shimmer")
    val shimmerX by shimmer.animateFloat(
        initialValue = -120f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "splash_shimmer_x"
    )

    LaunchedEffect(Unit) {
        delay(120)
        phase = 1
        delay(850)
        phase = 2
        delay(850)
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(AuraColors.Background), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(AuraColors.PrimaryContainer.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension * 0.62f
                ),
                radius = size.minDimension * 0.62f
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawAnimatedDiamond(progress, shimmerX) }
                Icon(Icons.Default.Diamond, null, tint = AuraColors.PrimaryContainer.copy(alpha = 0.92f), modifier = Modifier.size(72.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "ABU STAR DIAMONDS",
                color = AuraColors.PrimaryContainer.copy(alpha = textAlpha),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "TRUST  ·  PURITY  ·  ELEGANCE",
                color = AuraColors.OnSurfaceVariant.copy(alpha = textAlpha * 0.55f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

private fun DrawScope.drawAnimatedDiamond(progress: Float, shimmerX: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension * 0.38f
    val top = Offset(cx, cy - r)
    val right = Offset(cx + r * 0.9f, cy - r * 0.1f)
    val bottom = Offset(cx, cy + r)
    val left = Offset(cx - r * 0.9f, cy - r * 0.1f)
    val points = listOf(top to right, right to bottom, bottom to left, left to top, top to bottom, left to right)
    points.forEachIndexed { index, pair ->
        val local = ((progress * points.size) - index).coerceIn(0f, 1f)
        if (local > 0f) {
            val end = Offset(
                pair.first.x + (pair.second.x - pair.first.x) * local,
                pair.first.y + (pair.second.y - pair.first.y) * local
            )
            drawLine(AuraColors.PrimaryContainer, pair.first, end, strokeWidth = 3.2f, cap = StrokeCap.Round)
        }
    }
    if (progress > 0.75f) {
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.24f), Color.Transparent),
                start = Offset(cx + shimmerX - 24f, cy - r),
                end = Offset(cx + shimmerX + 24f, cy + r)
            ),
            topLeft = Offset(cx + shimmerX - 20f, cy - r),
            size = androidx.compose.ui.geometry.Size(40f, r * 2f)
        )
    }
}
