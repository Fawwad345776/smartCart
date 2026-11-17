package com.aura.smartcart.ui.components

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Sets whether the status / navigation bar icons are dark (for light backgrounds) or light
 * (for green or dark backgrounds). Each screen calls this for its own background.
 */
@Composable
fun SystemBarIcons(darkStatusIcons: Boolean, darkNavIcons: Boolean = darkStatusIcons) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkStatusIcons
            isAppearanceLightNavigationBars = darkNavIcons
        }
    }
}
