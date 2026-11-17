package com.aura.smartcart.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.smartcart.AppContainer
import com.aura.smartcart.R
import com.aura.smartcart.data.db.BillWithItems
import com.aura.smartcart.ui.common.EmptyState
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.util.Dates
import com.aura.smartcart.util.Money
import com.aura.smartcart.util.Months
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BillsViewModel(container: AppContainer) : ViewModel() {
    val bills: StateFlow<List<BillWithItems>?> = container.bills.observeBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun BillsTab(contentPadding: PaddingValues, onOpenBill: (Long) -> Unit) {
    val vm = appViewModel { BillsViewModel(it) }
    val bills by vm.bills.collectAsStateWithLifecycle()
    val all = bills ?: return
    val colors = MaterialTheme.colorScheme

    if (all.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.ReceiptLong,
                title = stringResource(R.string.bills_empty_title),
                body = stringResource(R.string.bills_empty_body)
            )
        }
        return
    }

    val byMonth = all.groupBy { it.bill.monthKey }.toSortedMap(compareByDescending { it })
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            byMonth.forEach { (month, monthBills) ->
                item(key = "m_$month") {
                    val currency = monthBills.first().bill.currency
                    Row(Modifier.padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Months.label(month), style = MaterialTheme.typography.titleMedium, color = colors.onBackground, modifier = Modifier.weight(1f))
                        Text(
                            Money.format(monthBills.sumOf { it.bill.total }, currency),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primary
                        )
                    }
                }
                items(monthBills, key = { it.bill.id }) { b -> BillCard(b, onClick = { onOpenBill(b.bill.id) }) }
            }
        }
    }
}

@Composable
private fun BillCard(data: BillWithItems, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val b = data.bill
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(colors.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Receipt, contentDescription = null, tint = colors.primary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    b.storeName.ifBlank { b.listName.ifBlank { stringResource(R.string.bill_untitled) } },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    Dates.date(b.purchasedAt) + " · " + pluralStringResource(R.plurals.items_count, data.items.size, data.items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                if (b.address.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Place, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(b.address, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(b.total, b.currency), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Icon(
                    if (b.synced) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                    contentDescription = stringResource(if (b.synced) R.string.bill_synced else R.string.bill_not_synced),
                    tint = if (b.synced) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
