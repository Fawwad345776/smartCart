package com.aura.smartcart.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aura.smartcart.R
import com.aura.smartcart.ui.components.CartLogo
import com.aura.smartcart.ui.components.SystemBarIcons
import com.aura.smartcart.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPLASH_DURATION_MS = 2600L

/**
 * Animated splash: rings grow, the logo tile pops in, the check draws itself,
 * then the name, tagline and loading dots fade up. Calls [onFinished] when done.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val brand = Brand.colors
    val colors = MaterialTheme.colorScheme
    SystemBarIcons(darkStatusIcons = !brand.isDark)

    val rings = remember { Animatable(0f) }
    val tile = remember { Animatable(1f) }
    val check = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val footer = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)

    // The system splash already shows the tile + cart in the centre, so the tile starts visible
    // (no blank frame), gives a small bounce, then the check draws and the name rises in.
    LaunchedEffect(Unit) {
        launch { rings.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        launch {
            tile.animateTo(1.08f, tween(220, easing = FastOutSlowInEasing))
            tile.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        }
        launch { delay(250); check.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        launch { delay(500); title.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        launch { delay(700); tagline.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        launch { delay(900); footer.animateTo(1f, tween(500)) }
        delay(SPLASH_DURATION_MS)
        finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Background circles (centred on the tile)
        Box(
            Modifier
                .align(Alignment.Center)
                .size(460.dp)
                .graphicsLayer {
                    val s = 0.5f + 0.5f * rings.value
                    scaleX = s; scaleY = s; alpha = rings.value
                }
                .background(colors.surfaceVariant, CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(320.dp)
                .graphicsLayer {
                    val s = 0.5f + 0.5f * rings.value
                    scaleX = s; scaleY = s; alpha = rings.value
                }
                .border(1.dp, colors.primary.copy(alpha = 0.22f), CircleShape)
        )

        // Logo tile: exactly in the screen centre, same size as the system splash icon.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(128.dp)
                .graphicsLayer { scaleX = tile.value; scaleY = tile.value }
                .shadow((20 * rings.value).dp, RoundedCornerShape(36.dp), spotColor = brand.logoTile)
                .background(brand.logoTile, RoundedCornerShape(36.dp))
                .semantics { contentDescription = "SmartCart" },
            contentAlignment = Alignment.Center
        ) {
            CartLogo(
                modifier = Modifier.size(84.dp),
                cartColor = brand.logoCart,
                checkColor = brand.logoCheck,
                checkProgress = check.value
            )
        }

        // Name + tagline below the tile
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 132.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = buildAnnotatedString {
                    append("Smart")
                    withStyle(SpanStyle(color = colors.primary)) { append("Cart") }
                },
                style = MaterialTheme.typography.displaySmall,
                color = colors.onBackground,
                modifier = Modifier.fadeUp(title.value)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fadeUp(tagline.value)
            )
        }

        // Loading dots + methods line
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
                .fadeUp(footer.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoadingDots(color = colors.primary)
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.splash_methods),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

private fun Modifier.fadeUp(progress: Float): Modifier = graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * 18.dp.toPx()
}

@Composable
private fun LoadingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val v by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0f at 0
                        1f at 480
                        0f at 960
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(i * 200)
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        val s = 0.8f + 0.4f * v
                        scaleX = s; scaleY = s
                        alpha = 0.3f + 0.7f * v
                    }
                    .background(color, CircleShape)
            )
        }
    }
}
