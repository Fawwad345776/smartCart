package com.aura.smartcart.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.aura.smartcart.BuildConfig
import com.aura.smartcart.R
import com.aura.smartcart.ui.common.toast
import java.util.Locale

/** Opens the user's email app with a feedback email that already contains the phone and app details. */
object Feedback {
    const val EMAIL = "fawwadshamim@gmail.com"

    fun open(context: Context) {
        val subject = "SmartCart feedback (v${BuildConfig.VERSION_NAME})"
        val body = buildString {
            append(context.getString(R.string.feedback_write_here)).append("\n\n\n\n")
            append("----------\n")
            append(deviceDetails())
        }
        // Subject and body go both in the mailto URI and as extras: email apps differ in which one they read.
        val uri = Uri.parse("mailto:$EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.toast(R.string.feedback_no_email_app)
        }
    }

    private fun deviceDetails(): String {
        val appLanguage = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
        return """
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            App language: $appLanguage
            Phone language: ${Locale.getDefault().toLanguageTag()}
        """.trimIndent()
    }
}
