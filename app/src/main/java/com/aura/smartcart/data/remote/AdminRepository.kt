package com.aura.smartcart.data.remote

import com.aura.smartcart.service.SessionTracker
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.Locale

data class AdminStats(
    val users: Long,
    val proUsers: Long,
    val playProUsers: Long,
    val activeThisWeek: Long,
    val sharedPrices: Long
)

data class AdminUser(
    val uid: String,
    val userId: String,
    val name: String,
    val email: String,
    val isPro: Boolean,
    val playPro: Boolean,
    val lastSeen: Long?
)

/** One app session from `app_sessions` (written by SessionTracker). */
data class AdminSession(
    val id: String,
    /** The phone's ANDROID_ID: one person/phone in the dashboard. */
    val deviceId: String,
    val authUid: String,
    val isGuest: Boolean,
    val name: String?,
    val email: String?,
    val country: String,
    val deviceName: String,
    val androidVersion: String,
    val appVersion: String,
    val start: Date?,
    val end: Date?,
    val durationSec: Long,
    val screens: List<String>
)

/**
 * Admin panel data. Only the admin email can use it: the side-menu item is hidden for everyone else,
 * and Firestore rules (isAdmin()) refuse other accounts, so a modified app can't use it either.
 */
class AdminRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val users get() = firestore.collection("users")

    fun isAdmin(): Boolean = isAdminEmail(auth.currentUser?.email)

    suspend fun stats(): AdminStats {
        val weekAgo = Timestamp(Date(System.currentTimeMillis() - 7 * DAY))
        return AdminStats(
            users = users.count().get(AggregateSource.SERVER).await().count,
            proUsers = users.whereEqualTo("isPro", true).count().get(AggregateSource.SERVER).await().count,
            playProUsers = users.whereEqualTo("playPro", true).count().get(AggregateSource.SERVER).await().count,
            activeThisWeek = users.whereGreaterThan("lastSeenAt", weekAgo).count().get(AggregateSource.SERVER).await().count,
            sharedPrices = firestore.collection("prices").count().get(AggregateSource.SERVER).await().count
        )
    }

    /** Most recently active users first. */
    suspend fun users(limit: Long = 500): List<AdminUser> =
        users.orderBy("lastSeenAt", Query.Direction.DESCENDING).limit(limit).get().await().documents.map { d ->
            AdminUser(
                uid = d.id,
                userId = d.getString("userId").orEmpty(),
                name = d.getString("name").orEmpty(),
                email = d.getString("email").orEmpty(),
                isPro = d.getBoolean("isPro") == true,
                playPro = d.getBoolean("playPro") == true,
                lastSeen = d.getTimestamp("lastSeenAt")?.toDate()?.time
            )
        }

    /**
     * All sessions on the day starting at [day], newest first. Each phone has one document per day
     * (`<phone id>_<yyyyMMdd>`) with that day's sessions in its `sessions` map.
     */
    suspend fun sessions(day: Date): List<AdminSession> {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day)
        return firestore.collection(SessionTracker.COLLECTION)
            .whereEqualTo("date", date)
            .get().await().documents.flatMap { d ->
                val deviceId = d.getString("deviceId") ?: d.id.substringBefore('_')
                @Suppress("UNCHECKED_CAST")
                val sessions = (d.get("sessions") as? Map<String, Map<String, Any?>>).orEmpty()
                sessions.map { (id, s) ->
                    AdminSession(
                        id = id,
                        deviceId = deviceId,
                        authUid = d.getString("authUid").orEmpty(),
                        isGuest = d.getBoolean("isGuest") ?: true,
                        name = d.getString("name"),
                        email = d.getString("email"),
                        country = d.getString("country") ?: "Unknown",
                        deviceName = d.getString("deviceName") ?: "Unknown device",
                        androidVersion = d.getString("androidVersion").orEmpty(),
                        appVersion = d.getString("appVersion").orEmpty(),
                        start = (s["startTime"] as? Timestamp)?.toDate(),
                        end = (s["endTime"] as? Timestamp)?.toDate(),
                        durationSec = (s["durationSec"] as? Number)?.toLong() ?: 0L,
                        screens = (s["screens"] as? List<String>).orEmpty()
                    )
                }
            }.sortedByDescending { it.start }
    }

    /** The admin's Pro switch for one user (the user's app follows it live). */
    suspend fun setPro(uid: String, isPro: Boolean) {
        users.document(uid).update(
            mapOf(
                "isPro" to isPro,
                "proUpdatedAt" to FieldValue.serverTimestamp(),
                "proUpdatedBy" to (auth.currentUser?.email ?: "")
            )
        ).await()
    }

    companion object {
        /** The only account that sees and can use the admin panel. Must match firestore.rules. */
        const val ADMIN_EMAIL = "fawwadshamim.fs@gmail.com"
        private const val DAY = 24 * 60 * 60 * 1000L

        fun isAdminEmail(email: String?): Boolean =
            email != null && email.trim().lowercase(Locale.ROOT) == ADMIN_EMAIL
    }
}
