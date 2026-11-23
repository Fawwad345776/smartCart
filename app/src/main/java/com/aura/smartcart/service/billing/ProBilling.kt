package com.aura.smartcart.service.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One SmartCart Pro plan as set up in Play Console (e.g. monthly or yearly base plan). */
class ProPlan(
    val basePlanId: String,
    /** Price as Google Play formats it for the user's country, e.g. "Rs 299.00". */
    val price: String,
    /** ISO-8601 billing period: "P1M" monthly, "P1Y" yearly. */
    val period: String,
    internal val details: ProductDetails,
    internal val offerToken: String
)

/**
 * SmartCart Pro subscription through Google Play Billing.
 *
 * Play Console setup: Monetize > Subscriptions > create product id [PRODUCT_ID] with one or more
 * base plans (e.g. "monthly", "yearly"). Prices come from Play Console, so they are never hard-coded here.
 *
 * Pro status is cached on the phone so Pro keeps working offline; it is refreshed whenever Play is reachable.
 */
class ProBilling(context: Context) : PurchasesUpdatedListener {

    private val store = context.getSharedPreferences("pro_status", Context.MODE_PRIVATE)

    private val _isPro = MutableStateFlow(store.getBoolean(KEY_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _plans = MutableStateFlow<List<ProPlan>>(emptyList())
    val plans: StateFlow<List<ProPlan>> = _plans.asStateFlow()

    /** True once Play has answered (or failed): the Pro screen stops showing a spinner. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Connects to Google Play and refreshes plans and Pro status. Safe to call many times. */
    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    _loaded.value = true
                }
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() reconnects on the next call.
            }
        })
    }

    /** Re-reads plans and purchases ("Restore purchase"). */
    fun refresh() {
        if (!client.isReady) {
            connect()
            return
        }
        queryPlans()
        queryPurchases()
    }

    /** Opens Google Play's purchase sheet. Returns false if it could not be shown. */
    fun buy(activity: Activity, plan: ProPlan): Boolean {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(plan.details)
                        .setOfferToken(plan.offerToken)
                        .build()
                )
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> if (purchases != null) handle(purchases, fullList = false)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> Log.i(TAG, "Purchase not completed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private fun queryPlans() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _plans.value = details.productDetailsList.flatMap { toPlans(it) }
            } else {
                Log.w(TAG, "Plans query failed: ${result.debugMessage}")
            }
            _loaded.value = true
        }
    }

    /** One plan per base plan. A special offer (e.g. free trial) on that base plan is preferred. */
    private fun toPlans(details: ProductDetails): List<ProPlan> =
        details.subscriptionOfferDetails.orEmpty()
            .groupBy { it.basePlanId }
            .mapNotNull { (basePlanId, offers) ->
                val offer = offers.firstOrNull { it.offerId != null } ?: offers.first()
                // The last phase is the normal recurring price (earlier phases are trials/intro prices).
                val recurring = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                ProPlan(basePlanId, recurring.formattedPrice, recurring.billingPeriod, details, offer.offerToken)
            }
            .sortedBy { if (it.period == "P1M") 0 else 1 }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            // Offline or Play error: keep the cached status instead of removing Pro.
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases, fullList = true)
        }
    }

    /** [fullList]: these are all the user's subscriptions, so no active one means Pro has ended. */
    private fun handle(purchases: List<Purchase>, fullList: Boolean) {
        val active = purchases.filter { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        // Google refunds purchases that are not acknowledged within 3 days.
        active.filterNot { it.isAcknowledged }.forEach { purchase ->
            val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(ack) { r ->
                if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "Acknowledge failed: ${r.debugMessage}")
            }
        }
        if (active.isNotEmpty()) setPro(true) else if (fullList) setPro(false)
    }

    private fun setPro(value: Boolean) {
        _isPro.value = value
        store.edit().putBoolean(KEY_PRO, value).apply()
    }

    companion object {
        /** Subscription product id in Play Console. */
        const val PRODUCT_ID = "smartcart_pro"
        private const val KEY_PRO = "is_pro"
        private const val TAG = "ProBilling"
    }
}
