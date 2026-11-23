package com.aura.smartcart.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Network {
    /** True when the phone has a working internet connection (not just Wi-Fi without internet). */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
