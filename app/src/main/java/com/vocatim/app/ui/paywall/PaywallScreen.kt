package com.vocatim.app.ui.paywall

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.data.billing.QuotaStore
import com.vocatim.app.ui.theme.BrandGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val usedMs by viewModel.usedMs.collectAsStateWithLifecycle()
    val product by viewModel.productDetails.collectAsStateWithLifecycle()
    val message by viewModel.purchaseMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            val text = when (it) {
                "PURCHASE_SUCCESS" -> context.getString(R.string.paywall_success)
                "RESTORE_NONE" -> context.getString(R.string.paywall_restore_none)
                "STORE_UNAVAILABLE" -> context.getString(R.string.paywall_store_unavailable)
                else -> context.getString(R.string.paywall_failed)
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(BrandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AllInclusive,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }

            Text(
                stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            if (isPro) {
                Text(
                    stringResource(R.string.paywall_already_pro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                val remainingMin =
                    ((QuotaStore.FREE_LIMIT_MS - usedMs).coerceAtLeast(0) / 60_000)
                Text(
                    stringResource(R.string.paywall_quota_status, remainingMin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            Benefit(Icons.Default.AllInclusive, stringResource(R.string.paywall_benefit_unlimited))
            Benefit(Icons.Default.CloudOff, stringResource(R.string.paywall_benefit_offline))
            Benefit(Icons.Default.Lock, stringResource(R.string.paywall_benefit_private))
            Benefit(Icons.Default.Payments, stringResource(R.string.paywall_benefit_once))

            Spacer(Modifier.height(8.dp))

            if (!isPro) {
                val price = product?.oneTimePurchaseOfferDetails?.formattedPrice
                Button(
                    onClick = { (context as? Activity)?.let(viewModel::buy) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (price != null) {
                            stringResource(R.string.paywall_buy_with_price, price)
                        } else {
                            stringResource(R.string.paywall_buy)
                        }
                    )
                }
                TextButton(onClick = viewModel::restore) {
                    Text(stringResource(R.string.paywall_restore))
                }
                Text(
                    stringResource(R.string.paywall_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
