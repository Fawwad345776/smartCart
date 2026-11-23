package com.aura.smartcart

import com.aura.smartcart.service.notify.EXTRA_OPEN_PRO
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.smartcart.data.ThemeMode
import com.aura.smartcart.navigation.AppNavigation
import com.aura.smartcart.service.notify.EXTRA_OPEN_LIST_ID
import com.aura.smartcart.ui.theme.SmartCartTheme
import com.aura.smartcart.util.Country

class MainActivity : AppCompatActivity() {

    /** A list to open because the user tapped a shopping reminder. */
    private val openListId = mutableStateOf<Long?>(null)

    /** True when the user tapped a cheaper-price teaser (opens the Pro screen). */
    private val openPro = mutableStateOf(false)

    /** True once the saved theme is loaded and Compose has drawn its first frame. */
    private var composeReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the (invisible) system splash up until Compose has drawn, like What2Eat,
        // so there is never a black gap before the animated splash.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !composeReady }
        applyDefaultLanguageOnce()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Window background = splash colour, so the theme switch can't flash black.
        window.setBackgroundDrawableResource(R.color.splash_background)
        readOpenListExtra(intent)

        val container = (application as SmartCartApp).container
        setContent {
            // Null until the saved theme is read, so we never draw the wrong theme first.
            val themeMode by container.prefs.themeMode.collectAsStateWithLifecycle(initialValue = null)
            val mode = themeMode ?: return@setContent
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val pendingListId by openListId
            SmartCartTheme(darkTheme = dark) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    SideEffect { composeReady = true }
                    AppNavigation(
                        container = container,
                        openListId = pendingListId,
                        onOpenListHandled = { openListId.value = null },
                        openPro = openPro.value,
                        onOpenProHandled = { openPro.value = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readOpenListExtra(intent)
    }

    private fun readOpenListExtra(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_LIST_ID, -1L) ?: -1L
        if (id > 0) openListId.value = id
        if (intent?.getBooleanExtra(EXTRA_OPEN_PRO, false) == true) openPro.value = true
    }

    /**
     * On first launch pick the app language from the country: India -> Hindi,
     * Arab countries -> Arabic, everywhere else English. The user can change it in Settings.
     */
    private fun applyDefaultLanguageOnce() {
        val prefs = getSharedPreferences("locale_init", MODE_PRIVATE)
        if (prefs.getBoolean("done", false)) return
        prefs.edit().putBoolean("done", true).apply()
        if (!AppCompatDelegate.getApplicationLocales().isEmpty) return
        val lang = Country.defaultAppLanguage(Country.detect(this))
        // Already showing that language: don't set it, so the screen isn't rebuilt on first launch.
        if (java.util.Locale.getDefault().language == lang.tag) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
    }
}
