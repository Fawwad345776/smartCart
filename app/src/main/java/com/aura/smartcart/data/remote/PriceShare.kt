package com.aura.smartcart.data.remote

import android.util.Log
import com.aura.smartcart.data.db.BillEntity
import com.aura.smartcart.data.db.BillItemEntity
import com.aura.smartcart.util.PriceMatch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** One item price from someone's bill. */
data class PricePoint(
    val id: String,
    val uid: String,
    val name: String,
    val quantity: String,
    val price: Double,
    val store: String,
    val lat: Double,
    val lng: Double,
    val at: Long
)

/**
 * Shared price board in Firestore ("prices" collection): every saved bill with a location adds
 * one document per item (name, quantity, price, shop name and shop location). No user name,
 * email or address is stored. Used for cheaper-price alerts and the Smart Store Plan.
 *
 * Nearby search: each document stores the geohash cell of the shop (cell5 ≈ 5 km, cell4 ≈ 20-40 km);
 * a query asks for the user's cell and the 8 around it, then distance and dates are checked on the phone.
 */
class PriceShare {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val prices get() = firestore.collection(COLLECTION)

    fun myUid(): String? = auth.currentUser?.uid

    /** Shares the prices of a saved bill (replacing what this bill shared before). Never throws. */
    suspend fun publish(bill: BillEntity, items: List<BillItemEntity>) {
        val uid = myUid() ?: return
        val lat = bill.lat ?: return
        val lng = bill.lng ?: return
        try {
            removeFor(bill.remoteId)
            val batch = firestore.batch()
            items.filter { it.price > 0 && it.name.isNotBlank() }.take(MAX_ITEMS).forEachIndexed { i, item ->
                val doc = mapOf(
                    "uid" to uid,
                    "billId" to bill.remoteId,
                    "name" to item.name.take(120),
                    "key" to PriceMatch.key(item.name),
                    "quantity" to item.quantity.take(40),
                    "price" to item.price,
                    "currency" to bill.currency,
                    "store" to bill.storeName.take(80),
                    "lat" to lat,
                    "lng" to lng,
                    "cell5" to PriceMatch.geohash(lat, lng, 5),
                    "cell4" to PriceMatch.geohash(lat, lng, 4),
                    "at" to bill.purchasedAt
                )
                batch.set(prices.document("${bill.remoteId}_$i"), doc)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.w(TAG, "Sharing prices failed", e)
        }
    }

    /** Removes the prices a bill shared (bill deleted, or before sharing it again). */
    suspend fun removeFor(billRemoteId: String) {
        val uid = myUid() ?: return
        try {
            val old = prices.whereEqualTo("billId", billRemoteId).whereEqualTo("uid", uid).get().await()
            if (old.isEmpty) return
            val batch = firestore.batch()
            old.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.w(TAG, "Removing shared prices failed", e)
        }
    }

    /**
     * Prices within [radiusKm] of a point, from the last [days] days.
     * Needs a signed-in user (Firestore rules). Returns empty on any error.
     */
    suspend fun nearby(lat: Double, lng: Double, radiusKm: Double, days: Int): List<PricePoint> {
        if (myUid() == null) return emptyList()
        val field = if (radiusKm <= 5.0) "cell5" else "cell4"
        val precision = if (radiusKm <= 5.0) 5 else 4
        val since = System.currentTimeMillis() - days * DAY
        return try {
            prices.whereIn(field, PriceMatch.cellsAround(lat, lng, precision))
                .limit(MAX_RESULTS)
                .get().await()
                .documents.mapNotNull { d ->
                    PricePoint(
                        id = d.id,
                        uid = d.getString("uid") ?: return@mapNotNull null,
                        name = d.getString("name") ?: return@mapNotNull null,
                        quantity = d.getString("quantity").orEmpty(),
                        price = d.getDouble("price") ?: return@mapNotNull null,
                        store = d.getString("store").orEmpty(),
                        lat = d.getDouble("lat") ?: return@mapNotNull null,
                        lng = d.getDouble("lng") ?: return@mapNotNull null,
                        at = d.getLong("at") ?: 0L
                    )
                }
                .filter { it.at >= since && PriceMatch.distanceKm(lat, lng, it.lat, it.lng) <= radiusKm }
        } catch (e: Exception) {
            Log.w(TAG, "Nearby prices failed", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "PriceShare"
        const val COLLECTION = "prices"
        const val MAX_ITEMS = 200
        const val MAX_RESULTS = 2000L
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
