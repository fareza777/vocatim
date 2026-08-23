package com.vocatim.app.data.billing

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "BillingManager"

/**
 * One-time "Remove ads" purchase via Google Play Billing.
 *
 * Two products matter here. [PRODUCT_REMOVE_ADS] is what the paywall sells
 * today. [PRODUCT_LIFETIME] is the retired tier from when the app charged for
 * features; it is never offered again, only honoured — anyone who bought it
 * stays ad-free forever.
 *
 * The entitlement is cached in [AdFreeStore] so a paid app stays ad-free with
 * no network or Play connection, which this app has to support.
 */
class BillingManager(
    context: Context,
    private val adFreeStore: AdFreeStore,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _formattedPrice = MutableStateFlow<String?>(null)
    /** Play's formatted price (e.g. "$4.99"), or null until the catalog loads. */
    val formattedPrice: StateFlow<String?> = _formattedPrice.asStateFlow()

    private val _purchaseMessage = MutableStateFlow<String?>(null)
    /** One-shot user-facing feedback from the last purchase attempt. */
    val purchaseMessage: StateFlow<String?> = _purchaseMessage.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        // Billing 8 requires the params form; one-time products only.
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    refreshEntitlement()
                } else {
                    Log.w(TAG, "Billing setup failed: " + result.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnected lazily on the next purchase/restore attempt.
            }
        })
    }

    fun launchPurchase(activity: Activity) {
        val details = _productDetails.value
        if (details == null || !client.isReady) {
            connect()
            _purchaseMessage.value = "STORE_UNAVAILABLE"
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    /** Re-checks purchases with Play; used by the paywall's restore button. */
    fun restore() {
        if (!client.isReady) {
            connect()
            return
        }
        refreshEntitlement(notifyWhenNone = true)
    }

    fun consumeMessage() {
        _purchaseMessage.value = null
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach(::handlePurchase)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> {
                Log.w(TAG, "Purchase failed: " + result.responseCode + " " + result.debugMessage)
                _purchaseMessage.value = "PURCHASE_FAILED"
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val removeAds = purchase.products.contains(PRODUCT_REMOVE_ADS)
        val legacy = purchase.products.contains(PRODUCT_LIFETIME)
        if (!removeAds && !legacy) return

        scope.launch {
            if (removeAds) adFreeStore.setAdFree(true)
            if (legacy) adFreeStore.setLegacyLifetime(true)
            _purchaseMessage.value = "PURCHASE_SUCCESS"
        }
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { ackResult ->
                if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Acknowledge failed: " + ackResult.debugMessage)
                }
            }
        }
    }

    /** Only the remove-ads product is offered; the retired tier is not listed. */
    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_REMOVE_ADS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = detailsResult.productDetailsList.firstOrNull()
                _productDetails.value = details
                _formattedPrice.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
                if (details == null) {
                    Log.w(TAG, "Product $PRODUCT_REMOVE_ADS not in catalog")
                }
            } else {
                Log.w(TAG, "Product query failed: " + result.debugMessage)
            }
        }
    }

    private fun refreshEntitlement(notifyWhenNone: Boolean = false) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            fun owns(productId: String) = purchases.any { p ->
                p.products.contains(productId) &&
                    p.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            val hasRemoveAds = owns(PRODUCT_REMOVE_ADS)
            val hasLegacy = owns(PRODUCT_LIFETIME)
            purchases.forEach(::handlePurchase)
            scope.launch {
                if (hasRemoveAds) adFreeStore.setAdFree(true)
                if (hasLegacy) adFreeStore.setLegacyLifetime(true)
                if (!hasRemoveAds && !hasLegacy && notifyWhenNone) {
                    // Only an explicit Restore may clear the cache. A background
                    // sync can report "not owned" just because Play is signed
                    // into another account, and that must never put ads back in
                    // front of someone who actually paid.
                    adFreeStore.setAdFree(false)
                    adFreeStore.setLegacyLifetime(false)
                    _purchaseMessage.value = "RESTORE_NONE"
                }
            }
        }
    }

    companion object {
        /** Current product: removes ads, nothing else is gated. */
        const val PRODUCT_REMOVE_ADS = "remove_ads"

        /** Retired paid tier — honoured for existing buyers, never sold again. */
        const val PRODUCT_LIFETIME = "lifetime_unlimited"
    }
}
