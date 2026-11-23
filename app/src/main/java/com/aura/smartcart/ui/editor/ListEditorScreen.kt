package com.aura.smartcart.ui.editor

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.smartcart.R
import com.aura.smartcart.data.repo.DraftItem
import com.aura.smartcart.service.notify.Notifier
import com.aura.smartcart.ui.common.DateTimePickerDialog
import com.aura.smartcart.ui.common.LanguageChips
import com.aura.smartcart.ui.common.PickItemsSheet
import com.aura.smartcart.ui.common.PickRow
import com.aura.smartcart.ui.common.SectionLabel
import com.aura.smartcart.ui.common.VoiceInputSheet
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.ui.common.rememberDocumentScanner
import com.aura.smartcart.ui.common.scanErrorText
import com.aura.smartcart.ui.components.SystemBarIcons
import com.aura.smartcart.ui.theme.Brand
import com.aura.smartcart.util.Dates

private enum class Sheet { NONE, VOICE, REMAINING, OLD_LISTS, OLD_LIST_ITEMS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListEditorScreen(
    listId: Long?,
    copyFrom: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val vm = appViewModel(key = "editor_${listId}_$copyFrom") { EditorViewModel(it, listId, copyFrom) }
    val state by vm.state.collectAsStateWithLifecycle()
    val language by vm.inputLanguage.collectAsStateWithLifecycle()
    val remaining by vm.remaining.collectAsStateWithLifecycle()
    val oldLists by vm.oldLists.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    SystemBarIcons(darkStatusIcons = !Brand.colors.isDark)

    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var pickedOldListId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DraftItem?>(null) }
    var typed by rememberSaveable { mutableStateOf("") }
    var typedQty by rememberSaveable { mutableStateOf("") }

    val defaultName = stringResource(R.string.editor_default_name, Dates.date(System.currentTimeMillis()))
    val doSave = { vm.save(defaultName, onSaved) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { doSave() }
    val save = {
        if (state.shoppingAt != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else doSave()
    }
    val startScan = rememberDocumentScanner { vm.scanList(it) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(if (listId == null) R.string.editor_title_new else R.string.editor_title_edit)) },
                actions = {
                    TextButton(onClick = save, enabled = !state.saving) { Text(stringResource(R.string.action_save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        bottomBar = {
            Surface(color = colors.background, shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    AddItemRow(
                        text = typed,
                        quantity = typedQty,
                        onText = { typed = it },
                        onQuantity = { typedQty = it },
                        onAdd = {
                            if (typed.isNotBlank()) {
                                vm.addTyped(typed, typedQty)
                                typed = ""; typedQty = ""
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = save,
                        enabled = !state.saving,
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(stringResource(R.string.editor_save_list))
                    }
                }
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = vm::setName,
                        label = { Text(stringResource(R.string.editor_name)) },
                        placeholder = { Text(defaultName) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    ShoppingTimeCard(
                        shoppingAt = state.shoppingAt,
                        onPick = { showDatePicker = true },
                        onClear = { vm.setShoppingAt(null) }
                    )
                }
                item {
                    SectionLabel(stringResource(R.string.editor_add_items), Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MethodCard(Icons.Rounded.Mic, stringResource(R.string.method_voice)) { sheet = Sheet.VOICE }
                        MethodCard(Icons.Rounded.DocumentScanner, stringResource(R.string.method_scan)) { startScan() }
                        MethodCard(Icons.Rounded.History, stringResource(R.string.method_old_list)) {
                            vm.loadOldLists(); sheet = Sheet.OLD_LISTS
                        }
                        val left = remaining.filter { it.id !in state.usedRemainingIds }
                        if (left.isNotEmpty()) {
                            MethodCard(
                                Icons.Rounded.Inventory2,
                                stringResource(R.string.method_remaining, left.size),
                                highlighted = true
                            ) { sheet = Sheet.REMAINING }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.scan_printed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(
                            pluralStringResource(R.plurals.items_count, state.items.size, state.items.size),
                            Modifier.weight(1f)
                        )
                    }
                }
                if (state.items.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.editor_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                itemsIndexed(state.items, key = { _, it -> it.key }) { index, item ->
                    Column(Modifier.animateItem()) {
                        DraftRow(
                            index = index + 1,
                            item = item,
                            onClick = { editing = item },
                            onRemove = { vm.removeItem(item.key) }
                        )
                        HorizontalDivider(color = colors.outline.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    // ----- dialogs & sheets -----

    if (showDatePicker) {
        DateTimePickerDialog(
            initialMillis = state.shoppingAt,
            onPicked = { vm.setShoppingAt(it); showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }

    editing?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { name, qty -> vm.updateItem(item.key, name, qty); editing = null }
        )
    }

    when (sheet) {
        Sheet.VOICE -> VoiceInputSheet(
            language = language,
            onLanguageChange = vm::setInputLanguage,
            onItems = vm::addParsed,
            onRemove = vm::removeParsed,
            onDismiss = { sheet = Sheet.NONE }
        )

        Sheet.REMAINING -> {
            val left = remaining.filter { it.id !in state.usedRemainingIds }
            PickItemsSheet(
                title = stringResource(R.string.remaining_title),
                rows = left.map { PickRow(it.id, it.name, listOf(it.quantity, it.fromListName).filter { s -> s.isNotBlank() }.joinToString(" · ")) },
                initiallySelected = false,
                confirmLabel = stringResource(R.string.action_add_selected),
                onConfirm = { vm.addRemaining(it); sheet = Sheet.NONE },
                onDismiss = { sheet = Sheet.NONE }
            )
        }

        Sheet.OLD_LISTS -> OldListsSheet(
            lists = oldLists.map { PickRow(it.list.id, it.list.name, pluralStringResource(R.plurals.items_count, it.items.size, it.items.size)) },
            onPick = { pickedOldListId = it; sheet = Sheet.OLD_LIST_ITEMS },
            onDismiss = { sheet = Sheet.NONE }
        )

        Sheet.OLD_LIST_ITEMS -> {
            val list = oldLists.firstOrNull { it.list.id == pickedOldListId }
            if (list == null) {
                androidx.compose.runtime.LaunchedEffect(Unit) { sheet = Sheet.NONE }
            } else {
                PickItemsSheet(
                    title = list.list.name,
                    rows = list.items.sortedBy { it.position }.map { PickRow(it.id, it.name, it.quantity) },
                    initiallySelected = true,
                    confirmLabel = stringResource(R.string.action_add_selected),
                    onConfirm = { vm.addFromOldList(list.list.id, it); sheet = Sheet.NONE },
                    onDismiss = { sheet = Sheet.NONE }
                )
            }
        }

        Sheet.NONE -> Unit
    }

    if (state.scanning) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.scan_reading_list)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.scan_wait))
                }
            }
        )
    }

    state.scanned?.let { scanned ->
        PickItemsSheet(
            title = stringResource(R.string.scan_found_title),
            rows = scanned.mapIndexed { i, p -> PickRow(i.toLong(), p.name, p.quantity) },
            initiallySelected = true,
            confirmLabel = stringResource(R.string.action_add_selected),
            onConfirm = { picked ->
                vm.addParsed(scanned.filterIndexed { i, _ -> i.toLong() in picked })
                vm.clearScan()
            },
            onDismiss = vm::clearScan,
            header = {
                Column {
                    Text(stringResource(R.string.scan_language), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    LanguageChips(selected = language, onSelect = vm::rescanIn)
                    state.scanProblem?.let { problem ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            scanErrorText(problem == ScanProblem.NEEDS_INTERNET),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error
                        )
                        // Handwritten list: offer voice input straight away.
                        TextButton(onClick = { vm.clearScan(); sheet = Sheet.VOICE }) {
                            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.scan_use_voice))
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ShoppingTimeCard(shoppingAt: Long?, onPick: () -> Unit, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onPick,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Event, contentDescription = null, tint = colors.primary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (shoppingAt == null) stringResource(R.string.editor_when) else Dates.dateTime(shoppingAt),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface
                )
                Text(
                    stringResource(if (shoppingAt == null) R.string.editor_when_hint else R.string.editor_reminder_set),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (shoppingAt != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        }
    }
}

@Composable
private fun MethodCard(icon: ImageVector, label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) colors.primaryContainer else colors.surface,
        border = BorderStroke(1.dp, if (highlighted) colors.primary.copy(alpha = 0.4f) else colors.outline),
        modifier = Modifier.width(104.dp)
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(if (highlighted) colors.primary else colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (highlighted) colors.onPrimary else colors.primary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddItemRow(
    text: String,
    quantity: String,
    onText: (String) -> Unit,
    onQuantity: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            placeholder = { Text(stringResource(R.string.editor_type_item)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = quantity,
            onValueChange = onQuantity,
            placeholder = { Text(stringResource(R.string.editor_qty)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier.width(88.dp)
        )
        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_add))
            }
        }
    }
}

@Composable
private fun DraftRow(index: Int, item: DraftItem, onClick: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(colors.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(index.toString(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            if (item.quantity.isNotBlank()) {
                Text(item.quantity, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove), tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun EditItemDialog(item: DraftItem, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var qty by remember { mutableStateOf(item.quantity) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.item_name)) }, singleLine = true)
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text(stringResource(R.string.editor_qty)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name, qty) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OldListsSheet(lists: List<PickRow>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.old_lists_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (lists.isEmpty()) {
                Text(
                    stringResource(R.string.old_lists_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
            LazyColumn(Modifier.height((lists.size * 64).coerceIn(0, 440).dp)) {
                itemsIndexed(lists, key = { _, it -> it.key }) { _, row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(row.key) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, tint = colors.primary)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(row.title, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
                            Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
