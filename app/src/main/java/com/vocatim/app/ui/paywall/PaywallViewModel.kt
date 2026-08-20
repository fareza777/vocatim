package com.vocatim.app.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.vocatim.app.data.billing.BillingManager
import com.vocatim.app.data.billing.AdFreeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billingManager: BillingManager,
    adFreeStore: AdFreeStore,
) : ViewModel() {

    val isAdFree: StateFlow<Boolean> = adFreeStore.isAdFree
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val productDetails: StateFlow<ProductDetails?> = billingManager.productDetails

    val purchaseMessage: StateFlow<String?> = billingManager.purchaseMessage

    init {
        billingManager.connect()
    }

    fun buy(activity: Activity) = billingManager.launchPurchase(activity)

    fun restore() = billingManager.restore()

    fun consumeMessage() = billingManager.consumeMessage()
}
