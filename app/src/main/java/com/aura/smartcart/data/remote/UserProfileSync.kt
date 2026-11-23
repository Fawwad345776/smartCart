package com.aura.smartcart.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/**
 * The signed-in user's profile in Firestore: `users/{firebaseUid}`.
 *
 * Fields:
 *  - userId: SmartCart's own short id, e.g. "SC-7K2M9QXA" (made once, never changes)
 *  - uid: Firebase Auth id
 *  - name, email: from the Google account
 *  - isPro: set by the admin in the Firebase console (true = Pro, false = free). The app can never change it.
 *  - playPro: whether the phone currently has an active Google Play subscription (for the admin to see)
 *  - createdAt, lastSeenAt: server times
 *
 * The user is Pro in the app when isPro is true OR they have an active Play subscription.
 */
class UserProfileSync {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private fun uid(): String? = auth.currentUser?.takeIf { !it.isAnonymous }?.uid
    private fun doc(uid: String) = firestore.collection(USERS).document(uid)

    /** Creates the profile on first sign-in, otherwise refreshes name, email, Play status and last seen. */
    suspend fun upsert(name: String, email: String, playPro: Boolean) {
        val uid = uid() ?: return
        try {
            val ref = doc(uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "userId" to newUserId(),
                        "uid" to uid,
                        "name" to name,
                        "email" to email,
                        "isPro" to false,
                        "playPro" to playPro,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "lastSeenAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val changes = mutableMapOf<String, Any>(
                    "name" to name,
                    "email" to email,
                    "playPro" to playPro,
                    "lastSeenAt" to FieldValue.serverTimestamp()
                )
                if (snap.getString("userId").isNullOrBlank()) changes["userId"] = newUserId()
                ref.update(changes).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Saving user profile failed", e)
        }
    }

    /** Keeps playPro up to date when a subscription starts or ends. */
    suspend fun setPlayPro(playPro: Boolean) {
        val uid = uid() ?: return
        try {
            doc(uid).update("playPro", playPro).await()
        } catch (e: Exception) {
            Log.w(TAG, "Updating playPro failed", e)
        }
    }

    /** The admin's isPro switch for the signed-in user, live (false when signed out or not set). */
    fun adminPro(): Flow<Boolean> = callbackFlow {
        val uid = uid()
        if (uid == null) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val registration = doc(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Listening to isPro failed", error)
                return@addSnapshotListener
            }
            trySend(snap?.getBoolean("isPro") == true)
        }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    private companion object {
        const val TAG = "UserProfileSync"
        const val USERS = "users"
        /** No 0/O or 1/I, so the id is easy to read out to support. */
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()

        fun newUserId(): String = "SC-" + (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }
}
