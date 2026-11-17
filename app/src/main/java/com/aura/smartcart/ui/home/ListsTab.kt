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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.smartcart.AppContainer
import com.aura.smartcart.R
import com.aura.smartcart.data.db.ListStatus
import com.aura.smartcart.data.db.ListWithItems
import com.aura.smartcart.data.db.RemainingItemEntity
import com.aura.smartcart.service.notify.ReminderScheduler
import com.aura.smartcart.ui.common.EmptyState
import com.aura.smartcart.ui.common.SectionLabel
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListsViewModel(private val container: AppContainer) : ViewModel() {
    val lists: StateFlow<List<ListWithItems>?> = container.lists.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val remaining: StateFlow<List<RemainingItemEntity>> = container.lists.observeRemaining()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch {
            ReminderScheduler.cancelTrip(container.appContext, id)
            container.lists.deleteList(id)
        }
    }
}

@Composable
fun ListsTab(
    contentPadding: PaddingValues,
    onOpenList: (Long) -> Unit,
    onEditList: (Long) -> Unit,
    onReuseList: (Long) -> Unit,
    onNewList: () -> Unit
) {
    val vm = appViewModel { ListsViewModel(it) }
    val lists by vm.lists.collectAsStateWithLifecycle()
    val remaining by vm.remaining.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }

    val all = lists ?: return
    val active = all.filter { it.list.status == ListStatus.ACTIVE }
    val done = all.filter { it.list.status == ListStatus.DONE }
    val now = System.currentTimeMillis()
    val nextTrip = active.filter { (it.list.shoppingAt ?: 0) > now }.minByOrNull { it.list.shoppingAt!! }

    if (all.isEmpty() && remaining.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.ShoppingCart,
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body)
            )
        }
        return
    }

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
            nextTrip?.let { trip ->
                item(key = "trip") { NextTripBanner(trip) { onOpenList(trip.list.id) } }
            }
            if (remaining.isNotEmpty()) {
                item(key = "remaining") { RemainingBanner(remaining.size, onNewList) }
            }
            if (active.isNotEmpty()) {
                item(key = "activeLabel") { SectionLabel(stringResource(R.string.lists_active), Modifier.padding(top = 6.dp)) }
                items(active, key = { it.list.id }) { lw ->
                    ListCard(
                        data = lw,
                        onClick = { onOpenList(lw.list.id) },
                        onEdit = { onEditList(lw.list.id) },
                        onReuse = null,
                        onDelete = { deleting = lw.list.id },
                        modifier = Modifier.animateItem()
                    )
                }
            }
            if (done.isNotEmpty()) {
                item(key = "doneLabel") { SectionLabel(stringResource(R.string.lists_done), Modifier.padding(top = 10.dp)) }
                items(done, key = { it.list.id }) { lw ->
                    ListCard(
                        data = lw,
                        onClick = { onOpenList(lw.list.id) },
                        onEdit = { onEditList(lw.list.id) },
                        onReuse = { onReuseList(lw.list.id) },
                        onDelete = { deleting = lw.list.id },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.list_delete_title)) },
            text = { Text(stringResource(R.string.list_delete_body)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(id); deleting = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun NextTripBanner(trip: ListWithItems, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = colors.primary, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(colors.onPrimary.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = colors.onPrimary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.lists_next_trip), style = MaterialTheme.typography.labelSmall, color = colors.onPrimary.copy(alpha = 0.85f))
                Text(trip.list.name, style = MaterialTheme.typography.titleMedium, color = colors.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Dates.dateTime(trip.list.shoppingAt!!), style = MaterialTheme.typography.bodySmall, color = colors.onPrimary.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun RemainingBanner(count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = colors.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = colors.onPrimaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.lists_remaining, count, count),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onPrimaryContainer
                )
                Text(stringResource(R.string.lists_remaining_hint), style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ListCard(
    data: ListWithItems,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onReuse: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val total = data.items.size
    val checked = data.items.count { it.checked }
    val isDone = data.list.status == ListStatus.DONE
    var menu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 14.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        data.list.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDone) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (isDone) stringResource(R.string.lists_done_on, Dates.date(data.list.completedAt ?: data.list.updatedAt))
                        else stringResource(R.string.shop_progress, checked, total),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                if (onReuse != null) {
                    TextButton(onClick = onReuse) {
                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.lists_use_again))
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menu = false; onDelete() }
                        )
                    }
                }
            }
            if (!isDone && total > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { checked / total.toFloat() },
                    modifier = Modifier.padding(end = 12.dp).fillMaxWidth().height(6.dp),
                    color = colors.primary,
                    trackColor = colors.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
            val preview = data.items.sortedBy { it.position }.take(4)
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    preview.joinToString(" · ") { it.name } + if (total > 4) " …" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textDecoration = if (isDone) TextDecoration.None else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            data.list.shoppingAt?.takeIf { !isDone }?.let {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Event, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Dates.dateTime(it), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
        }
    }
}
