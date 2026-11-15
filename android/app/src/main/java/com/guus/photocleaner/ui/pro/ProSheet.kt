package com.guus.photocleaner.ui.pro

import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guus.photocleaner.R
import com.guus.photocleaner.billing.Billing
import com.guus.photocleaner.billing.ProductPrice
import com.guus.photocleaner.service.ProState

const val PRO_SHEET_TAG = "proSheet"
const val PRO_BUY_TAG = "proBuy"

private data class Feature(@param:DrawableRes val icon: Int, @param:StringRes val label: Int)

private val Features = listOf(
    Feature(R.drawable.ic_calendar_today, R.string.pro_feature_periods),
    Feature(R.drawable.ic_videocam, R.string.pro_feature_videos),
    Feature(R.drawable.ic_grid_view, R.string.pro_feature_grid),
    Feature(R.drawable.ic_filter_none, R.string.pro_feature_duplicates),
)

/**
 * The Pro sheet wired to [billing]: asks the store again when it opens (reconnects after a
 * dropped connection) and closes itself once Pro is unlocked (AC 11).
 */
@Composable
fun ProSheetHost(billing: Billing, onClose: () -> Unit) {
    val state by billing.entitlement.collectAsState()
    val price by billing.price.collectAsState()
    val activity = LocalActivity.current
    LaunchedEffect(Unit) { billing.refresh() }
    LaunchedEffect(state) { if (state == ProState.PRO) onClose() }
    ProSheet(
        state = state,
        price = price,
        onBuy = { activity?.let(billing::launchPurchase) },
        onRestore = billing::restore,
        onClose = onClose,
    )
}

/**
 * What Pro includes, the store price, Buy, Restore purchases and Close (specs/pro-unlock.md
 * AC 9, 10, 13). Friendly, no pressure: one primary button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSheet(
    state: ProState,
    price: ProductPrice,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(PRO_SHEET_TAG),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    painterResource(R.drawable.ic_auto_awesome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(14.dp).size(32.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.pro_subtitle),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.pro_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(feature.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(feature.label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            when {
                state == ProState.PENDING -> Notice(stringResource(R.string.pro_pending))
                price is ProductPrice.Unavailable -> Notice(stringResource(R.string.pro_store_unavailable))
            }
            Button(
                onClick = onBuy,
                enabled = state == ProState.FREE && price is ProductPrice.Available,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag(PRO_BUY_TAG),
            ) {
                Text(
                    when (price) {
                        is ProductPrice.Available -> stringResource(R.string.pro_buy, price.formatted)
                        ProductPrice.Loading -> stringResource(R.string.pro_loading_price)
                        ProductPrice.Unavailable -> stringResource(R.string.pro_buy_unavailable)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth()) {
                TextButton(onClick = onRestore, modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(stringResource(R.string.pro_restore))
                }
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text(stringResource(R.string.pro_close))
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}
