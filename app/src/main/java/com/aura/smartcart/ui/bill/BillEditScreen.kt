package com.aura.smartcart.ui.bill

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Storefront
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.smartcart.R
import com.aura.smartcart.data.repo.BillDraftItem
import com.aura.smartcart.service.scan.ImageLoader
import com.aura.smartcart.ui.common.DateTimePickerDialog
import com.aura.smartcart.ui.common.LanguageChips
import com.aura.smartcart.ui.common.SectionLabel
import com.aura.smartcart.ui.common.VoiceCaptureSheet
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.ui.common.rememberDocumentScanner
import com.aura.smartcart.ui.common.scanErrorText
import com.aura.smartcart.ui.components.SystemBarIcons
import com.aura.smartcart.ui.editor.ScanProblem
import com.aura.smartcart.ui.theme.Brand
import com.aura.smartcart.util.BillVoiceParser
import com.aura.smartcart.util.Dates
import com.aura.smartcart.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEditScreen(
    billId: Long?,
    listId: Long?,
    imagePath: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val vm = appViewModel(key = "bill_${billId}_${listId}_$imagePath") { BillEditViewModel(it, billId, listId, imagePath) }
    val state by vm.state.collectAsStateWithLifecycle()
    val language by vm.inputLanguage.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft = state.draft
    SystemBarIcons(darkStatusIcons = !Brand.colors.isDark)

    var showDate by rememberSaveable { mutableStateOf(false) }
    var showImage by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showVoice by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // "Write" adds a row at the bottom: bring it on screen so its name box can take the keyboard.
    LaunchedEffect(state.focusKey) {
        if (state.focusKey != null) {
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) listState.animateScrollToItem(last)
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.fetchLocation(force = true)
    }
    LaunchedEffect(state.loading) {
        if (!state.loading && draft.id == null) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val startScan = rememberDocumentScanner { uri ->
        scope.launch {
            val path = withContext(Dispatchers.IO) { ImageLoader.copyToBillsFolder(context, uri) }
            path?.let(vm::scanImage)
        }
    }

    val image by produceState<ImageBitmap?>(null, draft.imagePath) {
        value = draft.imagePath?.let { path ->
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(if (draft.id == null) R.string.bill_title_new else R.string.bill_title_edit)) },
                actions = {
                    if (draft.id != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        bottomBar = {
            BillTotalBar(
                currency = draft.currency,
                totalText = state.totalText,
                itemsSum = state.itemsSum,
                totalManual = state.totalManual,
                saving = state.saving,
                reading = state.reading,
                onTotal = vm::setTotal,
                onUseSum = vm::useItemsSum,
                onSave = { vm.save(onSaved) }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // How to add items: write, speak, scan
                item(key = "methods") {
                    SectionLabel(stringResource(R.string.bill_add_items_by))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BillMethod(Icons.Rounded.EditNote, stringResource(R.string.method_write), Modifier.weight(1f)) { vm.addItem() }
                        BillMethod(Icons.Rounded.Mic, stringResource(R.string.method_voice), Modifier.weight(1f)) { showVoice = true }
                        BillMethod(Icons.Rounded.DocumentScanner, stringResource(R.string.method_scan), Modifier.weight(1f)) { startScan() }
                    }
                }

                if (draft.imagePath != null || state.reading) {
                    item(key = "scan") {
                        ScanStatusCard(
                            image = image,
                            reading = state.reading,
                            problem = state.scanProblem,
                            onImageClick = { showImage = true },
                            languageContent = { LanguageChips(selected = language, onSelect = vm::rescanIn) }
                        )
                    }
                }

                item(key = "store") {
                    OutlinedTextField(
                        value = draft.storeName,
                        onValueChange = vm::setStore,
                        label = { Text(stringResource(R.string.bill_store)) },
                        leadingIcon = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item(key = "location") {
                    OutlinedTextField(
                        value = draft.address,
                        onValueChange = vm::setAddress,
                        label = { Text(stringResource(R.string.bill_location)) },
                        leadingIcon = { Icon(Icons.Rounded.Place, contentDescription = null) },
                        trailingIcon = {
                            if (state.locating) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = {
                                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }) {
                                    Icon(Icons.Rounded.MyLocation, contentDescription = stringResource(R.string.bill_use_location))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item(key = "date") {
                    Surface(
                        onClick = { showDate = true },
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = colors.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(Dates.dateTime(draft.purchasedAt), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                // Items table
                item(key = "tableHeader") {
                    Column {
                        SectionLabel(stringResource(R.string.bill_items), Modifier.padding(top = 6.dp, bottom = 8.dp))
                        TableHeader()
                    }
                }
                if (draft.items.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.bill_items_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        )
                    }
                }
                itemsIndexed(draft.items, key = { _, it -> it.key }) { index, item ->
                    BillTableRow(
                        index = index + 1,
                        item = item,
                        requestFocus = state.focusKey == item.key,
                        onFocusHandled = vm::focusHandled,
                        onChange = { n, q, p -> vm.updateItem(item.key, n, q, p) },
                        onRemove = { vm.removeItem(item.key) },
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "addRow") {
                    TextButton(onClick = vm::addItem, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.bill_add_item))
                    }
                }
            }
        }
    }

    if (showVoice) {
        VoiceCaptureSheet(
            title = stringResource(R.string.bill_voice_title),
            hint = stringResource(R.string.bill_voice_hint),
            language = language,
            onLanguageChange = vm::setInputLanguage,
            parse = BillVoiceParser::parse,
            onItems = vm::addVoice,
            onRemove = vm::removeVoice,
            label = { v -> listOf(v.name, v.quantity, v.price.takeIf { it.isNotBlank() }?.let { Money.format(Money.parse(it) ?: 0.0, draft.currency) }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ") },
            onDismiss = { showVoice = false }
        )
    }
    if (showDate) {
        DateTimePickerDialog(
            initialMillis = draft.purchasedAt,
            allowPast = true,
            onPicked = { vm.setDate(it); showDate = false },
            onDismiss = { showDate = false }
        )
    }
    if (showImage && image != null) {
        Dialog(onDismissRequest = { showImage = false }) {
            Box(Modifier.background(colors.background, RoundedCornerShape(16.dp)).padding(8.dp)) {
                Image(bitmap = image!!, contentDescription = stringResource(R.string.bill_image), modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.bill_delete_title)) },
            text = { Text(stringResource(R.string.bill_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) {
                    Text(stringResource(R.string.action_delete), color = colors.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun BillMethod(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = modifier
    ) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(44.dp).background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = colors.primary) }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
        }
    }
}

@Composable
private fun ScanStatusCard(
    image: ImageBitmap?,
    reading: Boolean,
    problem: ScanProblem?,
    onImageClick: () -> Unit,
    languageContent: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = stringResource(R.string.bill_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp, 96.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onImageClick)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.scan_language), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                languageContent()
                Spacer(Modifier.height(8.dp))
                when {
                    reading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.bill_reading), style = MaterialTheme.typography.bodySmall)
                    }
                    problem != null -> Text(
                        scanErrorText(problem == ScanProblem.NEEDS_INTERNET),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error
                    )
                    else -> Text(
                        stringResource(R.string.bill_scan_check),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private const val NAME_WEIGHT = 1.7f
private const val QTY_WEIGHT = 0.9f
private const val PRICE_WEIGHT = 1f

@Composable
private fun TableHeader() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.primaryContainer, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val style = MaterialTheme.typography.labelMedium
        Text(stringResource(R.string.col_item), style = style, color = colors.onPrimaryContainer, modifier = Modifier.weight(NAME_WEIGHT))
        Text(stringResource(R.string.col_qty), style = style, color = colors.onPrimaryContainer, modifier = Modifier.weight(QTY_WEIGHT))
        Text(stringResource(R.string.col_price), style = style, color = colors.onPrimaryContainer, textAlign = TextAlign.End, modifier = Modifier.weight(PRICE_WEIGHT))
        Spacer(Modifier.width(36.dp))
    }
}

@Composable
private fun BillTableRow(
    index: Int,
    item: BillDraftItem,
    requestFocus: Boolean,
    onFocusHandled: () -> Unit,
    onChange: (String, String, String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            nameFocus.requestFocus()
            onFocusHandled()
        }
    }
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cell(
                value = item.name,
                placeholder = "$index. " + stringResource(R.string.col_item),
                onValue = { onChange(it, item.quantity, item.price) },
                modifier = Modifier.weight(NAME_WEIGHT).focusRequester(nameFocus)
            )
            Cell(
                value = item.quantity,
                placeholder = stringResource(R.string.col_qty),
                onValue = { onChange(item.name, it, item.price) },
                modifier = Modifier.weight(QTY_WEIGHT)
            )
            Cell(
                value = item.price,
                placeholder = "0",
                onValue = { onChange(item.name, item.quantity, it) },
                keyboardType = KeyboardType.Decimal,
                alignEnd = true,
                missing = item.price.isBlank() && item.name.isNotBlank(),
                modifier = Modifier.weight(PRICE_WEIGHT)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove), tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = colors.outline.copy(alpha = 0.5f))
    }
}

/** A compact editable table cell. */
@Composable
private fun Cell(
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    alignEnd: Boolean = false,
    missing: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        focused -> colors.primary
        missing -> colors.error.copy(alpha = 0.6f)
        else -> colors.outline
    }
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = colors.onSurface,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
    )
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions.Default,
        modifier = modifier
            .heightIn(min = 44.dp)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle.copy(color = colors.onSurfaceVariant.copy(alpha = 0.7f)), maxLines = 1)
                }
                inner()
            }
        }
    )
}

@Composable
private fun BillTotalBar(
    currency: String,
    totalText: String,
    itemsSum: Double,
    totalManual: Boolean,
    saving: Boolean,
    reading: Boolean,
    onTotal: (String) -> Unit,
    onUseSum: () -> Unit,
    onSave: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background, shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.bill_total), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.bill_items_sum, Money.format(itemsSum, currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = totalText,
                    onValueChange = onTotal,
                    singleLine = true,
                    prefix = { Text("$currency ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.width(170.dp)
                )
            }
            val total = Money.parse(totalText) ?: 0.0
            if (totalManual && itemsSum > 0 && kotlin.math.abs(itemsSum - total) > 0.009) {
                TextButton(onClick = onUseSum) {
                    Text(stringResource(R.string.bill_use_sum, Money.format(itemsSum, currency)))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSave,
                enabled = !saving && !reading,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(stringResource(R.string.bill_save)) }
        }
    }
}
