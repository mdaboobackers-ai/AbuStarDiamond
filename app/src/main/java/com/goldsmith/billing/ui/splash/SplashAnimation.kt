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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldsmith.billing.R
import com.goldsmith.billing.ui.theme.AuraColors
import kotlinx.coroutines.delay

@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    val progress by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(900, easing = EaseInOutCubic),
        label = "launch_progress"
    )
    val logoScale by animateFloatAsState(
        targetValue = when {
            phase == 0 -> 0.9f
            phase == 1 -> 1f
            else -> 0.96f
        },
        animationSpec = tween(900, easing = EaseInOutCubic),
        label = "splash_logo_scale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (phase == 1) 1f else 0f,
        animationSpec = tween(420),
        label = "splash_logo_alpha"
    )
    val loaderAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(420),
        label = "splash_loader_alpha"
    )
    val loadingTextAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 0.72f else 0f,
        animationSpec = tween(420),
        label = "splash_loading_text"
    )
    val shimmer = rememberInfiniteTransition(label = "splash_shimmer")
    val shimmerX by shimmer.animateFloat(
        initialValue = -120f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "splash_shimmer_x"
    )
    val ringRotation by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "splash_ring_rotation"
    )

    LaunchedEffect(Unit) {
        delay(120)
        phase = 1
        delay(1250)
        phase = 2
        delay(900)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1F2428), Color(0xFF10151A), Color(0xFF090D10)),
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AuraColors.PrimaryContainer.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension * 0.62f
                ),
                radius = size.minDimension * 0.62f
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.abu_star_launch_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                            alpha = logoAlpha
                        }
                )
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .padding(34.dp)
                        .graphicsLayer { alpha = loaderAlpha }
                ) { drawLaunchAura(progress, shimmerX, ringRotation) }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Loading secure jewellery workspace",
                color = AuraColors.PrimaryContainer.copy(alpha = loadingTextAlpha),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

private fun DrawScope.drawLaunchAura(progress: Float, shimmerX: Float, ringRotation: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension * 0.41f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x33F2CA50), Color(0x10F2CA50), Color.Transparent),
            center = Offset(cx, cy),
            radius = r * 1.2f
        ),
        radius = r * 1.2f,
        center = Offset(cx, cy)
    )
    drawArc(
        color = AuraColors.Primary.copy(alpha = 0.18f + (progress * 0.24f)),
        startAngle = ringRotation,
        sweepAngle = 220f,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2f, r * 2f),
        style = Stroke(width = 2.4f, cap = StrokeCap.Round)
    )

    val top = Offset(cx, cy - r)
    val right = Offset(cx + r * 0.9f, cy - r * 0.1f)
    val bottom = Offset(cx, cy + r)
    val left = Offset(cx - r * 0.9f, cy - r * 0.1f)
    val points = listOf(top to right, right to bottom, bottom to left, left to top, top to bottom, left to right)
    points.forEachIndexed { index, pair ->
        val local = ((progress * points.size) - index).coerceIn(0f, 1f)
        if (local > 0f) {
            val end = Offset(
                x = pair.first.x + (pair.second.x - pair.first.x) * local,
                y = pair.first.y + (pair.second.y - pair.first.y) * local
            )
            drawLine(AuraColors.PrimaryContainer, pair.first, end, strokeWidth = 3.2f, cap = StrokeCap.Round)
        }
    }
    if (progress > 0.75f) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.24f), Color.Transparent),
                start = Offset(cx + shimmerX - 24f, cy - r),
                end = Offset(cx + shimmerX + 24f, cy + r)
            ),
            topLeft = Offset(cx + shimmerX - 20f, cy - r),
            size = Size(40f, r * 2f)
        )
    }
}
