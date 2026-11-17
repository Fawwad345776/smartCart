package com.aura.smartcart.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.smartcart.R
import com.aura.smartcart.ui.components.SystemBarIcons
import com.aura.smartcart.ui.theme.Brand
import kotlinx.coroutines.launch

const val ONBOARDING_PAGES = 3

private data class PageText(val title: Int, val body: Int)

private val pageTexts = listOf(
    PageText(R.string.onb1_title, R.string.onb1_body),
    PageText(R.string.onb2_title, R.string.onb2_body),
    PageText(R.string.onb3_title, R.string.onb3_body)
)

/** Three swipeable intro pages. [onFinished] runs on "Get Started" or "Skip". */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val brand = Brand.colors
    // Light icons over the green / dark hero; nav bar sits on the sheet.
    SystemBarIcons(darkStatusIcons = false, darkNavIcons = !brand.isDark)

    val pager = rememberPagerState { ONBOARDING_PAGES }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val isLast = page == ONBOARDING_PAGES - 1

    BackHandler(enabled = page > 0) {
        scope.launch { pager.animateScrollToPage(page - 1) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(brand.hero)
    ) {
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp

        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            OnboardingPage(index = index, pager = pager, wide = wide)
        }

        // Top bar: back / step counter on the start side, skip on the end side
        Row(
            modifier = Modifier
                .fillMaxWidth(if (wide) 0.5f else 1f)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page > 0) {
                Surface(
                    onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                    shape = CircleShape,
                    color = brand.heroGlow,
                    contentColor = brand.onHero,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.onb_back),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.onb_step, page + 1, ONBOARDING_PAGES),
                    style = MaterialTheme.typography.labelMedium,
                    color = brand.onHero,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (!isLast) {
                Surface(
                    onClick = onFinished,
                    shape = RoundedCornerShape(22.dp),
                    color = brand.heroGlow,
                    contentColor = brand.onHero,
                    modifier = Modifier.height(44.dp)
                ) {
                    Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.onb_skip), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Bottom control: progress-ring "Next" button, or "Get Started" on the last page
        Box(
            modifier = Modifier
                .align(if (wide) Alignment.BottomEnd else Alignment.BottomCenter)
                .fillMaxWidth(if (wide) 0.5f else 1f)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isLast,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(initialScale = 0.85f)) togetherWith
                        (fadeOut(tween(200)) + scaleOut(targetScale = 0.85f))
                },
                label = "bottomButton"
            ) { last ->
                if (last) {
                    Button(
                        onClick = onFinished,
                        shape = RoundedCornerShape(29.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .height(58.dp)
                    ) {
                        Text(stringResource(R.string.onb_get_started), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                } else {
                    NextRingButton(
                        progress = (page + 1f) / ONBOARDING_PAGES,
                        onClick = { scope.launch { pager.animateScrollToPage(page + 1) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(index: Int, pager: PagerState, wide: Boolean) {
    val active = pager.settledPage == index
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .padding(top = 56.dp)
            ) { OnboardingHero(index = index, active = active) }
            Sheet(
                index = index,
                shape = RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                centerVertically = true
            )
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 56.dp)
            ) { OnboardingHero(index = index, active = active) }
            Sheet(
                index = index,
                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                modifier = Modifier.fillMaxWidth(),
                centerVertically = false
            )
        }
    }
}

@Composable
private fun Sheet(
    index: Int,
    shape: RoundedCornerShape,
    modifier: Modifier,
    centerVertically: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val text = pageTexts[index]
    Column(
        modifier = modifier
            .background(colors.background, shape)
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (centerVertically) Arrangement.Center else Arrangement.Top
    ) {
        PageDots(current = index)
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(text.title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(text.body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp)
        )
        // Room for the bottom button that floats over the sheet
        Spacer(Modifier.height(BOTTOM_CONTROL_SPACE))
    }
}

private val BOTTOM_CONTROL_SPACE: Dp = 132.dp

@Composable
private fun PageDots(current: Int) {
    val colors = MaterialTheme.colorScheme
    val brand = Brand.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(ONBOARDING_PAGES) { i ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (i == current) 28.dp else 6.dp)
                    .background(if (i == current) colors.primary else brand.dotOff, CircleShape)
            )
        }
    }
}

@Composable
private fun NextRingButton(progress: Float, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val brand = Brand.colors
    val animated by animateFloatAsState(progress, tween(700), label = "ring")
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = 3.dp.toPx()
            val inset = strokePx / 2f + 3.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(brand.dotOff, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokePx))
            drawArc(
                colors.primary, -90f, 360f * animated, false, Offset(inset, inset), arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round)
            )
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = colors.primary,
            contentColor = colors.onPrimary,
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.onb_next),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
