package com.aura.smartcart.data.remote

import android.util.Log
import com.aura.smartcart.data.db.BillItemEntity
import com.aura.smartcart.data.db.BillEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Uploads bills to Firestore at users/{uid}/bills/{remoteId}, under the signed-in Google account.
 * Signed out, nothing is uploaded: bills stay marked unsynced and go up after the next sign-in.
 */
class BillCloudSync {
    // Created on first use, not at app start, so launching the app stays fast.
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // Guests have an anonymous account (for the admin's usage log only); bills need a Google account.
    private fun uid(): String = auth.currentUser?.takeIf { !it.isAnonymous }?.uid ?: error("Not signed in")

    /** Returns true when the bill is stored in the cloud. Never throws. */
    suspend fun upload(bill: BillEntity, items: List<BillItemEntity>): Boolean = try {
        val doc = mapOf(
            "store" to bill.storeName,
            "address" to bill.address,
            "location" to if (bill.lat != null && bill.lng != null) {
                mapOf("lat" to bill.lat, "lng" to bill.lng)
            } else null,
            "total" to bill.total,
            "currency" to bill.currency,
            "listName" to bill.listName,
            "purchasedAt" to bill.purchasedAt,
            "month" to bill.monthKey,
            "items" to items.map { mapOf("name" to it.name, "quantity" to it.quantity, "price" to it.price) },
            "updatedAt" to System.currentTimeMillis()
        )
        firestore.collection("users").document(uid())
            .collection("bills").document(bill.remoteId)
            .set(doc)
            .await()
        true
    } catch (e: Exception) {
        Log.w(TAG, "Bill upload failed, will retry later", e)
        false
    }

    suspend fun delete(remoteId: String) {
        try {
            firestore.collection("users").document(uid())
                .collection("bills").document(remoteId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Bill delete failed", e)
        }
    }

    private companion object {
        const val TAG = "BillCloudSync"
    }
}
