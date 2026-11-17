package com.aura.smartcart.ui.shop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.smartcart.AppContainer
import com.aura.smartcart.R
import com.aura.smartcart.data.db.ListItemEntity
import com.aura.smartcart.data.db.ListStatus
import com.aura.smartcart.data.db.ListWithItems
import com.aura.smartcart.service.notify.ReminderScheduler
import com.aura.smartcart.service.scan.ImageLoader
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.ui.common.rememberDocumentScanner
import com.aura.smartcart.ui.components.SystemBarIcons
import com.aura.smartcart.ui.theme.Brand
import com.aura.smartcart.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShopViewModel(private val container: AppContainer, private val listId: Long) : ViewModel() {
    val list: StateFlow<ListWithItems?> = container.lists.observeList(listId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(item: ListItemEntity) {
        viewModelScope.launch { container.lists.setChecked(item.id, !item.checked) }
    }

    fun moveUncheckedToRemaining() {
        viewModelScope.launch {
            container.lists.moveUncheckedToRemaining(listId)
            ReminderScheduler.cancelTrip(container.appContext, listId)
        }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            container.lists.completeList(listId)
            ReminderScheduler.cancelTrip(container.appContext, listId)
            onDone()
        }
    }
}

/** Shopping mode: tick items as you pick them up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    listId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddBill: () -> Unit
) {
    val vm = appViewModel(key = "shop_$listId") { ShopViewModel(it, listId) }
    val data by vm.list.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SystemBarIcons(darkStatusIcons = !Brand.colors.isDark)

    var confirmMove by rememberSaveable { mutableStateOf(false) }

    val list = data
    val items = list?.items.orEmpty()
    val sorted = items.filter { !it.checked }.sortedBy { it.position } +
        items.filter { it.checked }.sortedBy { it.checkedAt ?: 0L }
    val done = items.count { it.checked }
    val unchecked = items.size - done
    val finished = list?.list?.status == ListStatus.DONE || (items.isNotEmpty() && unchecked == 0)

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(list?.list?.name.orEmpty()) },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        bottomBar = {
            Surface(color = colors.background, shadowElevation = 8.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (finished) {
                        AllDoneCard(onAddBill = onAddBill, onFinish = { vm.finish(onBack) })
                    } else if (items.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { confirmMove = true },
                            enabled = unchecked > 0,
                            shape = RoundedCornerShape(26.dp),
                            border = BorderStroke(1.dp, colors.primary),
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Rounded.Inventory2, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(pluralStringResource(R.plurals.shop_move_remaining, unchecked, unchecked))
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "progress") {
                    ProgressCard(done = done, total = items.size, shoppingAt = list.list.shoppingAt)
                    Spacer(Modifier.height(4.dp))
                }
                items(sorted, key = { it.id }) { item ->
                    ShopItemRow(item = item, onToggle = { vm.toggle(item) }, modifier = Modifier.animateItem())
                }
            }
        }
    }

    if (confirmMove) {
        AlertDialog(
            onDismissRequest = { confirmMove = false },
            title = { Text(stringResource(R.string.shop_move_title)) },
            text = { Text(pluralStringResource(R.plurals.shop_move_body, unchecked, unchecked)) },
            confirmButton = {
                TextButton(onClick = { confirmMove = false; vm.moveUncheckedToRemaining() }) {
                    Text(stringResource(R.string.action_move))
                }
            },
            dismissButton = { TextButton(onClick = { confirmMove = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int, shoppingAt: Long?) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState(if (total == 0) 0f else done / total.toFloat(), label = "progress")
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.shop_progress, done, total),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = colors.primary,
                trackColor = colors.outline,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            if (shoppingAt != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Event, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Dates.dateTime(shoppingAt), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ShopItemRow(item: ListItemEntity, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (item.checked) colors.surfaceVariant else colors.surface, label = "rowBg")
    val textColor by animateColorAsState(if (item.checked) colors.onSurfaceVariant else colors.onSurface, label = "rowText")
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = if (item.checked) null else BorderStroke(1.dp, colors.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .then(
                        if (item.checked) Modifier.background(colors.primary, CircleShape)
                        else Modifier.border(2.dp, colors.primary, CircleShape)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null
                )
                if (item.quantity.isNotBlank()) {
                    Text(
                        item.quantity,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDoneCard(onAddBill: () -> Unit, onFinish: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.shop_all_done), style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.shop_scan_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAddBill,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.shop_add_bill))
            }
            TextButton(onClick = onFinish) { Text(stringResource(R.string.shop_finish_no_bill)) }
        }
    }
}
