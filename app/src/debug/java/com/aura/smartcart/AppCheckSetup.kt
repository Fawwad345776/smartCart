package com.aura.smartcart

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds: App Check uses a debug token. On first run it is printed to logcat as
 * "Enter this debug secret into the allow list in the Firebase Console ...". Add it under
 * Firebase console > App Check > Apps > smartCart > Manage debug tokens.
 */
object AppCheckSetup {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
