package com.goldsmith.billing.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.goldsmith.billing.ui.theme.AuraColors

// ─── Glass Card ───────────────────────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    goldBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val bgColor = if (elevated) AuraColors.GlassWhite12 else AuraColors.GlassWhite5
    val borderColor = if (goldBorder) AuraColors.GoldGlow else AuraColors.GlassBorder

    Column(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(24.dp))
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(1.dp)  // inner inset for the top-edge highlight
    ) {
        content()
    }
}

// ─── Gold Primary Button ──────────────────────────────────────────────────────
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = AuraColors.PrimaryContainer,
            contentColor = AuraColors.OnPrimary,
            disabledContainerColor = AuraColors.SurfaceContainerHigh,
            disabledContentColor = AuraColors.OnSurfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

// ─── Ghost Input ──────────────────────────────────────────────────────────────
@Composable
fun GhostTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    suffix: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column(modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                if (placeholder.isNotEmpty())
                    Text(placeholder, color = AuraColors.OnSurface.copy(alpha = 0.2f))
            },
            suffix = if (suffix.isNotEmpty()) ({ Text(suffix, color = AuraColors.OnSurfaceVariant) }) else null,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraColors.PrimaryContainer,
                unfocusedBorderColor = AuraColors.GlassWhite20,
                focusedTextColor = AuraColors.OnSurface,
                unfocusedTextColor = AuraColors.OnSurface,
                cursorColor = AuraColors.PrimaryContainer,
                focusedContainerColor = AuraColors.GlassWhite5,
                unfocusedContainerColor = AuraColors.GlassWhite5,
                disabledContainerColor = AuraColors.GlassWhite5,
                disabledTextColor = AuraColors.OnSurfaceVariant,
                disabledBorderColor = AuraColors.GlassWhite10
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// ─── Bottom Navigation ────────────────────────────────────────────────────────
data class BottomNavItem(val route: String, val icon: @Composable () -> Unit, val label: String)

@Composable
fun GoldsmithBottomBar(
    currentRoute: String,
    items: List<BottomNavItem>,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.85f),
        tonalElevation = 0.dp,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = AuraColors.GlassBorder,
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
            )
    ) {
        items.forEach { item ->
            val selected = currentRoute.startsWith(item.route.split("?")[0].split("/")[0])
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = { item.icon() },
                label = {
                    Text(
                        item.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AuraColors.PrimaryContainer,
                    selectedTextColor = AuraColors.PrimaryContainer,
                    unselectedIconColor = AuraColors.OnSurface.copy(alpha = 0.4f),
                    unselectedTextColor = AuraColors.OnSurface.copy(alpha = 0.4f),
                    indicatorColor = AuraColors.GlassWhite10
                )
            )
        }
    }
}

// ─── Gold Stat Card ───────────────────────────────────────────────────────────
@Composable
fun GoldRateCard(karat: String, rate: Double, trend: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = karat.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraColors.PrimaryContainer,
                    letterSpacing = 2.sp
                )
                Text(
                    text = when (trend) {
                        "up" -> "↑"; "down" -> "↓"; else -> "→"
                    },
                    color = when (trend) {
                        "up" -> Color(0xFF4CAF50)
                        "down" -> AuraColors.Error
                        else -> AuraColors.OnSurfaceVariant
                    },
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "₹${String.format("%,.0f", rate)}",
                style = MaterialTheme.typography.titleLarge,
                color = AuraColors.OnSurface
            )
            Text(
                text = "Per Gram",
                style = MaterialTheme.typography.labelSmall,
                color = AuraColors.Primary.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ─── PIN Dot Display ──────────────────────────────────────────────────────────
@Composable
fun PinDots(filledCount: Int, total: Int = 4) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        repeat(total) { idx ->
            val filled = idx < filledCount
            val scale by animateFloatAsState(
                targetValue = if (filled) 1.3f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "pin_dot_scale_$idx"
            )
            val color by animateColorAsState(
                targetValue = if (filled) AuraColors.PrimaryContainer else Color.Transparent,
                label = "pin_dot_color_$idx"
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        2.dp,
                        if (filled) AuraColors.PrimaryContainer
                        else AuraColors.GlassWhite20,
                        CircleShape
                    )
            )
        }
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String, action: String = "", onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
        if (action.isNotEmpty()) {
            TextButton(onClick = onAction) {
                Text(
                    action.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraColors.PrimaryContainer,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

// ─── App Bar ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldsmithTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = AuraColors.PrimaryContainer,
                letterSpacing = 3.sp,
                fontSize = 14.sp
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.Diamond,
                        contentDescription = "Back",
                        tint = AuraColors.PrimaryContainer
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f),
            titleContentColor = AuraColors.PrimaryContainer
        )
    )
}

// ─── Loading Shimmer ──────────────────────────────────────────────────────────
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val shimmerAnim = rememberInfiniteTransition(label = "shimmer")
    val alpha by shimmerAnim.animateFloat(
        initialValue = 0.2f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AuraColors.GlassWhite10.copy(alpha = alpha))
    )
}

// ─── Keypad Button ────────────────────────────────────────────────────────────
@Composable
fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "keypad_scale"
    )
    Box(
        modifier = modifier
            .size(68.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(AuraColors.GlassWhite5)
            .clickable {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        LaunchedEffect(pressed) {
            if (pressed) {
                kotlinx.coroutines.delay(100)
                pressed = false
            }
        }
        if (content != null) content()
        else Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = AuraColors.OnSurface
        )
    }
}

// ─── Balance Chip ─────────────────────────────────────────────────────────────
@Composable
fun BalanceChip(goldGrams: Double, cashBalance: Double) {
    val (color, label) = when {
        goldGrams > 0 -> AuraColors.Error to "Owes ${String.format("%.2f", goldGrams)}g"
        goldGrams < 0 -> AuraColors.Primary to "We owe ${String.format("%.2f", -goldGrams)}g"
        cashBalance != 0.0 && cashBalance > 0 -> AuraColors.Error to "₹${String.format("%,.0f", cashBalance)} due"
        else -> AuraColors.OnSurfaceVariant to "Balanced"
    }
    Box(
        Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
