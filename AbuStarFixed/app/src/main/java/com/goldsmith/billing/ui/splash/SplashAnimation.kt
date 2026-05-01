package com.goldsmith.billing.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.goldsmith.billing.R
import com.goldsmith.billing.ui.theme.AuraColors
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }

    val diamondDraw by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(900, easing = EaseInOutCubic), label = "dd"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0.3f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "ls"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(700), label = "la"
    )
    val textOffY by animateFloatAsState(
        targetValue = if (phase >= 2) 0f else 50f,
        animationSpec = tween(500, easing = EaseOutCubic), label = "ty"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(500), label = "ta"
    )
    val shimmer = rememberInfiniteTransition(label = "sh")
    val shimX by shimmer.animateFloat(
        -250f, 250f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "sx"
    )

    LaunchedEffect(Unit) {
        delay(150); phase = 1
        delay(900); phase = 2
        delay(1000); onFinished()
    }

    Box(Modifier.fillMaxSize().background(AuraColors.Background), contentAlignment = Alignment.Center) {
        // Ambient glow
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x15D4AF37), Color.Transparent),
                    Offset(size.width / 2f, size.height / 2f),
                    size.minDimension * 0.65f
                ),
                radius = size.minDimension * 0.65f
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // Animated diamond
                Canvas(Modifier.size(160.dp)) {
                    drawDiamond(diamondDraw, shimX)
                }
                // Logo fades in over the diamond
                if (logoAlpha > 0.01f) {
                    coil.compose.AsyncImage(
                        model = R.drawable.abu_star_logo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(110.dp)
                            .graphicsLayer { scaleX = logoScale; scaleY = logoScale; alpha = logoAlpha },
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { translationY = textOffY; alpha = textAlpha }
            ) {
                Text(
                    "ABU STAR DIAMONDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraColors.PrimaryContainer,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "TRUST  ·  PURITY  ·  ELEGANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraColors.OnSurfaceVariant.copy(alpha = 0.45f),
                    fontSize = 11.sp, letterSpacing = 3.sp
                )
            }
        }

        // Gold bottom accent
        Box(
            Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, AuraColors.PrimaryContainer.copy(0.6f), Color.Transparent)
                    )
                )
        )
    }
}

private fun DrawScope.drawDiamond(progress: Float, shimX: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r  = size.minDimension * 0.40f

    // Diamond vertices
    val top   = Offset(cx, cy - r)
    val left  = Offset(cx - r * 0.88f, cy - r * 0.12f)
    val right = Offset(cx + r * 0.88f, cy - r * 0.12f)
    val bot   = Offset(cx, cy + r)
    val mL    = Offset(cx - r * 0.35f, cy - r * 0.12f)
    val mR    = Offset(cx + r * 0.35f, cy - r * 0.12f)
    val mC    = Offset(cx, cy - r * 0.08f)

    val gold1 = Color(0xFFD4AF37)
    val gold2 = Color(0xFFF2CA50)
    val gold3 = Color(0xFFB8960C)
    val sw    = 1.8.dp.toPx()

    // Fill facets when progress > 0.5
    if (progress > 0.5f) {
        val fp = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)
        arrayOf(
            Pair(Path().apply { moveTo(top.x,top.y); lineTo(mL.x,mL.y); lineTo(mC.x,mC.y); close() }, gold2.copy(alpha = 0.18f * fp)),
            Pair(Path().apply { moveTo(top.x,top.y); lineTo(mR.x,mR.y); lineTo(mC.x,mC.y); close() }, gold1.copy(alpha = 0.14f * fp)),
            Pair(Path().apply { moveTo(mL.x,mL.y);  lineTo(bot.x,bot.y); lineTo(mC.x,mC.y); close() }, gold3.copy(alpha = 0.18f * fp)),
            Pair(Path().apply { moveTo(mR.x,mR.y);  lineTo(bot.x,bot.y); lineTo(mC.x,mC.y); close() }, gold1.copy(alpha = 0.12f * fp)),
            Pair(Path().apply { moveTo(top.x,top.y); lineTo(left.x,left.y); lineTo(mL.x,mL.y); close() }, gold2.copy(alpha = 0.10f * fp)),
            Pair(Path().apply { moveTo(top.x,top.y); lineTo(right.x,right.y); lineTo(mR.x,mR.y); close() }, gold1.copy(alpha = 0.10f * fp))
        ).forEach { (path, col) -> drawPath(path, col, style = Fill) }
    }

    // Progressively draw outline segments
    fun progressLine(a: Offset, b: Offset, col: Color, segStart: Float, segEnd: Float) {
        val t = ((progress - segStart) / (segEnd - segStart)).coerceIn(0f, 1f)
        if (t <= 0f) return
        drawLine(col, a, Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t),
            strokeWidth = sw, cap = StrokeCap.Round)
    }

    // Draw in sequence — outer outline first, then inner facets
    progressLine(top, right, gold1, 0.00f, 0.15f)
    progressLine(right, bot,  gold1, 0.12f, 0.28f)
    progressLine(bot,  left,  gold1, 0.25f, 0.40f)
    progressLine(left, top,   gold1, 0.37f, 0.52f)
    progressLine(top,  mR,    gold2, 0.48f, 0.58f)
    progressLine(top,  mL,    gold2, 0.48f, 0.58f)
    progressLine(mL,   mC,    gold2, 0.55f, 0.65f)
    progressLine(mR,   mC,    gold2, 0.55f, 0.65f)
    progressLine(mL,   bot,   gold3, 0.62f, 0.75f)
    progressLine(mR,   bot,   gold3, 0.62f, 0.75f)
    progressLine(mL,   left,  gold3, 0.72f, 0.82f)
    progressLine(mR,   right, gold3, 0.72f, 0.82f)

    // Starburst shimmer point at top when near complete
    if (progress > 0.85f) {
        val a = ((progress - 0.85f) / 0.15f).coerceIn(0f, 1f)
        val sr = 8.dp.toPx() * a
        for (i in 0..3) {
            val angle = (i * 45f) * (PI.toFloat() / 180f)
            drawLine(
                gold2.copy(alpha = 0.8f * a),
                Offset(top.x - cos(angle) * sr * 0.3f, top.y - sin(angle) * sr * 0.3f),
                Offset(top.x + cos(angle) * sr, top.y + sin(angle) * sr),
                strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round
            )
        }
    }

    // Shimmer sweep
    if (progress > 0.75f) {
        val sa = ((progress - 0.75f) / 0.25f) * 0.35f
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(sa), Color.Transparent),
                Offset(cx + shimX - 15f, cy - r), Offset(cx + shimX + 15f, cy + r)
            ),
            topLeft = Offset(cx + shimX - 20f, cy - r),
            size    = androidx.compose.ui.geometry.Size(40f, r * 2f)
        )
    }
}
