package com.aura.smartcart.service

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.aura.smartcart.BuildConfig
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Usage log for the admin dashboard in `app_sessions`: ONE document per phone per day
 * (`<phone id>_<yyyyMMdd>`), identified by the phone's ANDROID_ID, holding who uses it (guest or
 * Google user), device, Android version, country, and that day's sessions (start/end time,
 * duration, screens visited) in the `sessions` map.
 *
 * Guests are signed in anonymously so their session can be written; the rest of the app still
 * treats them as signed out. Only the admin account can read this collection (see firestore.rules).
 */
object SessionTracker : Application.ActivityLifecycleCallbacks {

    const val COLLECTION = "app_sessions"

    /** If the app comes back within this window, it continues the same session. */
    private const val SESSION_GAP_MS = 30_000L
    private const val TAG = "SessionTracker"

    private lateinit var appContext: Context
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var startedActivities = 0
    private var sessionId: String? = null
    private var sessionAuthUid: String? = null
    private var sessionStartMs = 0L
    private var lastStopMs = 0L
    private var pendingCreate = false
    private var signingIn = false
    private var sessionIsGuest = false
    private val screens = mutableListOf<String>()

    fun init(app: Application) {
        appContext = app.applicationContext
        app.registerActivityLifecycleCallbacks(this)

        // Guest → Google sign-in (or sign-out) mid-session: start a fresh session
        // under the new identity so name and id are right and the rules allow the write.
        auth.addAuthStateListener { fa ->
            val uid = fa.currentUser?.uid
            if (uid == null) {
                // Signed out while the app is open: continue as a guest.
                if (startedActivities > 0 && sessionId != null) {
                    sessionAuthUid = null
                    pendingCreate = true
                    signInGuest()
                }
                return@addAuthStateListener
            }
            val user = fa.currentUser ?: return@addAuthStateListener
            when {
                pendingCreate -> createSessionDoc()
                sessionId != null && sessionAuthUid != null && uid != sessionAuthUid && startedActivities > 0 -> {
                    beginSession(System.currentTimeMillis())
                }
                // A guest linked their Google account: same user id, so just add the name and email.
                sessionIsGuest && !user.isAnonymous && uid == sessionAuthUid -> {
                    sessionIsGuest = false
                    if (sessionId == null) return@addAuthStateListener
                    firestore.collection(COLLECTION).document(docId()).set(
                        mapOf(
                            "deviceId" to deviceId,
                            "authUid" to user.uid,
                            "isGuest" to false,
                            "name" to (user.displayName?.takeIf { it.isNotBlank() }
                                ?: user.providerData.firstOrNull { it.providerId == "google.com" }?.displayName),
                            "email" to (user.email?.takeIf { it.isNotBlank() }
                                ?: user.providerData.firstOrNull { it.providerId == "google.com" }?.email)
                        ),
                        SetOptions.merge()
                    )
                }
            }
        }
    }

    /** Called when the screen changes; [route] is the navigation route. */
    fun logScreen(route: String?) {
        val name = route?.substringBefore("/")?.substringBefore("?")?.takeIf { it.isNotBlank() } ?: return
        if (name == "splash" || screens.lastOrNull() == name) return
        screens.add(name)
        flush()
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        if (startedActivities == 1) {
            val now = System.currentTimeMillis()
            if (sessionId == null || now - lastStopMs > SESSION_GAP_MS) beginSession(now)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) {
            lastStopMs = System.currentTimeMillis()
            flush(endMs = lastStopMs)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private fun beginSession(nowMs: Long) {
        sessionId = UUID.randomUUID().toString()
        sessionStartMs = nowMs
        sessionAuthUid = null
        screens.clear()
        createSessionDoc()
    }

    private fun signInGuest() {
        if (signingIn) return
        signingIn = true
        auth.signInAnonymously()
            .addOnFailureListener { Log.w(TAG, "Guest sign-in failed; session not recorded", it) }
            .addOnCompleteListener { signingIn = false }
    }

    /**
     * This phone's id: Android's ANDROID_ID (fixed per phone, kept when the app is reinstalled).
     * Falls back to a random id saved in the app if the phone gives none.
     */
    val deviceId: String by lazy {
        val android = try {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
        android?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" } ?: run {
            val prefs = appContext.getSharedPreferences("session_tracker", Context.MODE_PRIVATE)
            prefs.getString("device_id", null) ?: UUID.randomUUID().toString().replace("-", "").take(16)
                .also { prefs.edit().putString("device_id", it).apply() }
        }
    }

    /** One document per phone per day: `<phone id>_<yyyyMMdd>`, with that day's sessions inside. */
    private fun docId(): String =
        deviceId + "_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(sessionStartMs))

    private fun createSessionDoc() {
        val id = sessionId ?: return
        val user = auth.currentUser
        if (user == null) {
            // The auth listener writes the session once the guest sign-in finishes.
            pendingCreate = true
            signInGuest()
            return
        }
        pendingCreate = false
        sessionAuthUid = user.uid

        val isGuest = user.isAnonymous
        sessionIsGuest = isGuest
        val google = user.providerData.firstOrNull { it.providerId == "google.com" }
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: google?.displayName
        val email = user.email?.takeIf { it.isNotBlank() } ?: google?.email
        val (countryCode, countryName) = detectCountry()
        // Merge: the phone's document for today may already hold earlier sessions.
        val data = hashMapOf<String, Any?>(
            "deviceId" to deviceId,
            "authUid" to user.uid,
            "isGuest" to isGuest,
            "name" to if (isGuest) null else displayName,
            "email" to if (isGuest) null else email,
            "country" to countryName,
            "countryCode" to countryCode,
            "deviceName" to deviceName(),
            "androidVersion" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "appVersion" to BuildConfig.VERSION_NAME,
            "date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sessionStartMs)),
            "lastSeen" to Timestamp(Date(System.currentTimeMillis())),
            "sessions" to mapOf(
                id to mapOf(
                    "startTime" to Timestamp(Date(sessionStartMs)),
                    "endTime" to Timestamp(Date(maxOf(sessionStartMs, System.currentTimeMillis()))),
                    "durationSec" to 0L,
                    "screens" to screens.toList()
                )
            )
        )
        firestore.collection(COLLECTION).document(docId()).set(data, SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Saving session failed", it) }
    }

    /** Updates end time, duration and screens. Uses "now" while the app is open. */
    private fun flush(endMs: Long = System.currentTimeMillis()) {
        val id = sessionId ?: return
        if (pendingCreate || sessionAuthUid == null) return
        if (auth.currentUser?.uid != sessionAuthUid) return
        val update = hashMapOf<String, Any>(
            "deviceId" to deviceId,
            "authUid" to (sessionAuthUid ?: return),
            "lastSeen" to Timestamp(Date(endMs)),
            "sessions" to mapOf(
                id to mapOf(
                    "endTime" to Timestamp(Date(endMs)),
                    "durationSec" to ((endMs - sessionStartMs) / 1000L).coerceAtLeast(0L),
                    "screens" to screens.toList()
                )
            )
        )
        firestore.collection(COLLECTION).document(docId()).set(update, SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Updating session failed", it) }
    }

    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    /** Country from mobile network → SIM → phone locale. No permission required. */
    private fun detectCountry(): Pair<String, String> {
        var code = ""
        try {
            val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            code = tm?.networkCountryIso?.takeIf { it.isNotBlank() }
                ?: tm?.simCountryIso?.takeIf { it.isNotBlank() }
                ?: ""
        } catch (_: Exception) {
        }
        if (code.isBlank()) code = Locale.getDefault().country
        code = code.uppercase()
        val name = if (code.isNotBlank()) Locale("", code).getDisplayCountry(Locale.ENGLISH) else "Unknown"
        return code to name.ifBlank { "Unknown" }
    }
}
