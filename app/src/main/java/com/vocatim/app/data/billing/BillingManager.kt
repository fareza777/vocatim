package com.vocatim.app.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
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
 * One-time "Lifetime Unlimited" purchase via Google Play Billing.
 *
 * Entitlement is cached in [QuotaStore] so a bought app stays unlocked with
 * no network/Play connection — this app must work fully offline. When Play
 * is reachable, the cache re-syncs with the real purchase state.
 */
class BillingManager(
    context: Context,
    private val quotaStore: QuotaStore,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _purchaseMessage = MutableStateFlow<String?>(null)
    /** One-shot user-facing feedback from the last purchase attempt. */
    val purchaseMessage: StateFlow<String?> = _purchaseMessage.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    refreshEntitlement()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
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
                Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
                _purchaseMessage.value = "PURCHASE_FAILED"
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        scope.launch {
            quotaStore.setPro(true)
            _purchaseMessage.value = "PURCHASE_SUCCESS"
        }
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { ackResult ->
                if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Acknowledge failed: ${ackResult.debugMessage}")
                }
            }
        }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = detailsList.firstOrNull()
            }
        }
    }

    private fun refreshEntitlement(notifyWhenNone: Boolean = false) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any { p ->
                p.products.contains(PRODUCT_ID) &&
                    p.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            purchases.forEach(::handlePurchase)
            scope.launch {
                quotaStore.setPro(owned)
                if (!owned && notifyWhenNone) {
                    _purchaseMessage.value = "RESTORE_NONE"
                }
            }
        }
    }

    companion object {
        const val PRODUCT_ID = "lifetime_unlimited"
    }
}
