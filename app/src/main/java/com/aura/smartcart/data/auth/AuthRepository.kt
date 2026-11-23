package com.aura.smartcart.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** The signed-in Google account, as shown in the side menu. */
data class AccountUser(val name: String, val email: String, val photoUrl: String? = null) {
    val initial: String get() = (name.ifBlank { email }).take(1).uppercase()
}

enum class SignInResult { SUCCESS, CANCELLED, NOT_CONFIGURED, FAILED }

/**
 * Google sign-in through Credential Manager + Firebase Auth.
 * Bills can only be added while signed in, so they are backed up to the user's own account.
 * Older versions signed in anonymously: that account is linked to Google so its bills are kept.
 */
class AuthRepository(private val appContext: Context) {
    // Created on first use, not at app start, so launching the app stays fast.
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /** Emits the Google account, or null when signed out (an old anonymous session counts as signed out). */
    val user: Flow<AccountUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser.toAccount()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    fun isSignedIn(): Boolean = auth.currentUser.toAccount() != null

    /** [activityContext] must be an Activity: Credential Manager shows the Google account picker over it. */
    suspend fun signInWithGoogle(activityContext: Context): SignInResult {
        val clientId = webClientId() ?: return SignInResult.NOT_CONFIGURED
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
                .build()
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignInResult.FAILED
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

            val current = auth.currentUser
            if (current != null && current.isAnonymous) {
                try {
                    // Keeps the same user id, so bills uploaded anonymously stay with this account.
                    val linked = current.linkWithCredential(firebaseCredential).await().user
                    // Linking doesn't copy the Google name and photo onto the account: copy them.
                    linked?.providerData?.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }?.let { g ->
                        try {
                            linked.updateProfile(
                                UserProfileChangeRequest.Builder()
                                    .setDisplayName(g.displayName)
                                    .setPhotoUri(g.photoUrl)
                                    .build()
                            ).await()
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not copy the Google name/photo", e)
                        }
                    }
                } catch (_: FirebaseAuthUserCollisionException) {
                    // This Google account already has SmartCart data: switch to it.
                    auth.signInWithCredential(firebaseCredential).await()
                }
            } else {
                auth.signInWithCredential(firebaseCredential).await()
            }
            SignInResult.SUCCESS
        } catch (_: GetCredentialCancellationException) {
            SignInResult.CANCELLED
        } catch (e: Exception) {
            Log.w(TAG, "Google sign-in failed", e)
            SignInResult.FAILED
        }
    }

    suspend fun signOut() {
        auth.signOut()
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear credential state", e)
        }
    }

    /**
     * The OAuth web client id, generated from google-services.json once Google sign-in is
     * enabled in the Firebase console. Looked up by name so the app still builds before that.
     */
    private fun webClientId(): String? {
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        return if (id == 0) null else appContext.getString(id).takeIf { it.isNotBlank() }
    }

    private fun FirebaseUser?.toAccount(): AccountUser? {
        if (this == null || isAnonymous) return null
        // An anonymous account linked to Google keeps an empty name/photo of its own; they are on the Google entry.
        val google = providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        val name = displayName?.takeIf { it.isNotBlank() } ?: google?.displayName.orEmpty()
        val mail = email?.takeIf { it.isNotBlank() } ?: google?.email.orEmpty()
        // Google gives the photo at 96px; ask for a sharper one for the side menu.
        val photo = (photoUrl ?: google?.photoUrl)?.toString()?.takeIf { it.isNotBlank() }?.replace("=s96-c", "=s192-c")
        return AccountUser(name = name, email = mail, photoUrl = photo)
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
