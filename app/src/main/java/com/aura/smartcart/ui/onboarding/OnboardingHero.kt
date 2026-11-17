package com.aura.smartcart.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.aura.smartcart.R
import com.aura.smartcart.ui.theme.Brand
import com.aura.smartcart.ui.theme.DisplayFont

/** The illustration area of one onboarding page: rotating ring, centre tile, floating chips. */
@Composable
fun OnboardingHero(index: Int, active: Boolean) {
    val brand = Brand.colors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minDim = min(maxWidth, maxHeight)
        val tileSize = (minDim * 0.38f).coerceIn(96.dp, 150.dp)

        val spin by rememberInfiniteTransition(label = "spin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
            label = "spinAngle"
        )

        // Slowly rotating dashed ring
        Canvas(
            Modifier
                .align(Alignment.Center)
                .size(minDim * 0.92f)
                .graphicsLayer { rotationZ = spin }
        ) {
            val dash = 6.dp.toPx()
            drawCircle(
                color = brand.heroLine,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
                )
            )
        }
        // Soft glow disc
        Box(
            Modifier
                .align(Alignment.Center)
                .size(minDim * 0.64f)
                .background(brand.heroGlow, CircleShape)
        )

        // Centre tile
        val tileScale by animateFloatAsState(
            targetValue = if (active) 1f else 0.85f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f),
            label = "tile"
        )
        val tileShape = RoundedCornerShape(tileSize * 0.29f)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(tileSize)
                .graphicsLayer { scaleX = tileScale; scaleY = tileScale }
                .shadow(24.dp, tileShape)
                .background(brand.heroTile, tileShape)
                .clip(tileShape),
            contentAlignment = Alignment.Center
        ) {
            when (index) {
                0 -> CenterIcon(Icons.Rounded.Mic, tileSize)
                1 -> BellIcon(tileSize)
                else -> ScanningReceipt(tileSize)
            }
        }

        when (index) {
            0 -> PageOneChips(active)
            1 -> PageTwoChips(active)
            else -> PageThreeChips(active)
        }
    }
}

// ---------- Centre icons ----------

@Composable
private fun CenterIcon(icon: ImageVector, tileSize: Dp) {
    Icon(icon, contentDescription = null, tint = Brand.colors.heroAccent, modifier = Modifier.size(tileSize * 0.43f))
}

@Composable
private fun BellIcon(tileSize: Dp) {
    val angle by rememberInfiniteTransition(label = "bell").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1600
                0f at 0
                -14f at 160
                12f at 320
                -8f at 480
                4f at 640
                0f at 800
            }
        ),
        label = "bellAngle"
    )
    Icon(
        Icons.Rounded.NotificationsActive,
        contentDescription = null,
        tint = Brand.colors.heroAccent,
        modifier = Modifier
            .size(tileSize * 0.42f)
            .graphicsLayer {
                rotationZ = angle
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.1f)
            }
    )
}

@Composable
private fun BoxScope.ScanningReceipt(tileSize: Dp) {
    val accent = Brand.colors.heroAccent
    CenterIcon(Icons.Rounded.Receipt, tileSize)
    val y by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scanY"
    )
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer { translationY = tileSize.toPx() * y }
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(3.dp)
            .shadow(8.dp, CircleShape, ambientColor = accent, spotColor = accent)
            .background(accent, CircleShape)
    )
}

// ---------- Floating chips ----------

@Composable
private fun BoxScope.FloatingChip(
    horizontalBias: Float,
    verticalBias: Float,
    active: Boolean,
    order: Int,
    floatMillis: Int,
    content: @Composable () -> Unit
) {
    val appear by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (active) tween(500, delayMillis = 250 + order * 150, easing = FastOutSlowInEasing) else snap(),
        label = "chipAppear$order"
    )
    val bob by rememberInfiniteTransition(label = "bob$order").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(floatMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bobY$order"
    )
    Box(
        Modifier
            .align(BiasAlignment(horizontalBias, verticalBias))
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .graphicsLayer {
                val s = 0.6f + 0.4f * appear
                scaleX = s; scaleY = s
                alpha = appear
                translationY = (1f - appear) * 20.dp.toPx() - bob * 10.dp.toPx()
            }
    ) { content() }
}

@Composable
private fun ChipShell(content: @Composable () -> Unit) {
    val brand = Brand.colors
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = Modifier
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.2f))
            .background(brand.chip, shape)
            .height(48.dp)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
private fun SoftIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Brand.colors.heroAccent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun IconChip(icon: ImageVector, label: String) {
    ChipShell {
        SoftIcon(icon)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.colors.onChip)
    }
}

@Composable
private fun CheckBoxChip(label: String, checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    val brand = Brand.colors
    ChipShell {
        if (checked) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(colors.primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
            }
        } else {
            Box(
                Modifier
                    .size(26.dp)
                    .border(2.dp, brand.heroAccent, RoundedCornerShape(8.dp))
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (checked) brand.onChipMuted else brand.onChip,
            textDecoration = if (checked) TextDecoration.LineThrough else null
        )
    }
}

@Composable
private fun PageOneChips(active: Boolean) = Box(Modifier.fillMaxSize()) {
    FloatingChip(-0.95f, -0.9f, active, 0, 3200) { IconChip(Icons.Rounded.Mic, stringResource(R.string.onb1_chip_voice)) }
    FloatingChip(0.95f, -0.5f, active, 1, 3600) { IconChip(Icons.Rounded.TextFields, stringResource(R.string.onb1_chip_type)) }
    FloatingChip(-0.9f, 0.6f, active, 2, 3400) { IconChip(Icons.Rounded.DocumentScanner, stringResource(R.string.onb1_chip_scan)) }
    FloatingChip(0.95f, 0.95f, active, 3, 3000) { IconChip(Icons.Rounded.History, stringResource(R.string.onb1_chip_old)) }
}

@Composable
private fun PageTwoChips(active: Boolean) = Box(Modifier.fillMaxSize()) {
    val brand = Brand.colors
    FloatingChip(-0.95f, -0.92f, active, 0, 3200) {
        ChipShell {
            SoftIcon(Icons.Rounded.Schedule)
            Column {
                Text(stringResource(R.string.onb2_chip_reminder), style = MaterialTheme.typography.labelMedium, color = brand.onChip)
                Text(stringResource(R.string.onb2_chip_reminder_time), style = MaterialTheme.typography.labelSmall, color = brand.onChipMuted)
            }
        }
    }
    FloatingChip(0.95f, -0.45f, active, 1, 3600) { CheckBoxChip(stringResource(R.string.onb2_chip_done), checked = true) }
    FloatingChip(-0.9f, 0.6f, active, 2, 3400) { CheckBoxChip(stringResource(R.string.onb2_chip_todo), checked = false) }
    FloatingChip(0.95f, 0.95f, active, 3, 3000) { IconChip(Icons.Rounded.Replay, stringResource(R.string.onb2_chip_move)) }
}

@Composable
private fun PageThreeChips(active: Boolean) = Box(Modifier.fillMaxSize()) {
    FloatingChip(-0.95f, -0.95f, active, 0, 3400) { BudgetCard(active) }
    FloatingChip(0.95f, -0.35f, active, 1, 3600) {
        ChipShell {
            Box(
                Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
            Text(stringResource(R.string.onb3_chip_saved), style = MaterialTheme.typography.labelMedium, color = Brand.colors.onChip)
        }
    }
    FloatingChip(-0.9f, 0.62f, active, 2, 3200) { IconChip(Icons.Rounded.Place, stringResource(R.string.onb3_chip_store)) }
    FloatingChip(0.95f, 0.95f, active, 3, 3000) { IconChip(Icons.Rounded.BarChart, stringResource(R.string.onb3_chip_history)) }
}

@Composable
private fun BudgetCard(active: Boolean) {
    val brand = Brand.colors
    val fill by animateFloatAsState(
        targetValue = if (active) 0.64f else 0f,
        animationSpec = if (active) tween(1000, delayMillis = 700) else snap(),
        label = "budgetFill"
    )
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .width(180.dp)
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.2f))
            .background(brand.chip, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.onb3_chip_month), style = MaterialTheme.typography.labelSmall, color = brand.onChipMuted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.onb3_chip_spent),
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = brand.onChip
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.onb3_chip_budget),
                style = MaterialTheme.typography.labelSmall,
                color = brand.onChipMuted,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill)
                    .background(brand.heroAccent, CircleShape)
            )
        }
    }
}
