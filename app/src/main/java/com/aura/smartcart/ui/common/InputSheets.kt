package com.aura.smartcart.ui.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aura.smartcart.R
import com.aura.smartcart.service.voice.ContinuousSpeech
import com.aura.smartcart.util.InputLanguage
import com.aura.smartcart.util.ItemTextParser
import com.aura.smartcart.util.ParsedItem

/** Horizontal row of language choices for voice / scan. */
@Composable
fun LanguageChips(selected: InputLanguage, onSelect: (InputLanguage) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InputLanguage.entries.forEach { lang ->
            FilterChip(
                selected = lang == selected,
                onClick = { onSelect(lang) },
                label = { Text(lang.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

private class VoiceChip<T>(val id: Int, val item: T)

/** Voice input for a shopping list: names and quantities. */
@Composable
fun VoiceInputSheet(
    language: InputLanguage,
    onLanguageChange: (InputLanguage) -> Unit,
    onItems: (List<ParsedItem>) -> List<ParsedItem>,
    onRemove: (ParsedItem) -> Unit,
    onDismiss: () -> Unit
) = VoiceCaptureSheet(
    title = stringResource(R.string.voice_title),
    hint = stringResource(R.string.voice_hint),
    language = language,
    onLanguageChange = onLanguageChange,
    parse = ItemTextParser::parse,
    onItems = onItems,
    onRemove = onRemove,
    label = { if (it.quantity.isBlank()) it.name else "${it.name} · ${it.quantity}" },
    onDismiss = onDismiss
)

/**
 * Listens continuously; every phrase is turned into items by [parse] and added straight away.
 * [onItems] adds them and returns the ones really added; each gets a chip with ✕ so a wrongly
 * heard item can be removed and said again.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun <T> VoiceCaptureSheet(
    title: String,
    hint: String,
    language: InputLanguage,
    onLanguageChange: (InputLanguage) -> Unit,
    parse: (String) -> List<T>,
    onItems: (List<T>) -> List<T>,
    onRemove: (T) -> Unit,
    label: (T) -> String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val added = remember { mutableStateListOf<VoiceChip<T>>() }
    val currentParse by rememberUpdatedState(parse)
    var nextChipId by remember { mutableIntStateOf(0) }
    val currentOnRemove by rememberUpdatedState(onRemove)
    var partial by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var listening by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val currentOnItems by rememberUpdatedState(onItems)
    val errorMessage = stringResource(R.string.voice_error)

    val speech = remember {
        ContinuousSpeech(
            context = context,
            onPartial = { partial = it },
            onPhrase = { phrase ->
                val items = currentParse(phrase)
                if (items.isNotEmpty()) {
                    val accepted = currentOnItems(items)
                    accepted.forEach { added += VoiceChip(nextChipId++, it) }
                }
            },
            onLevel = { level = it },
            onError = { listening = false; errorText = errorMessage }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) errorText = context.getString(R.string.voice_permission)
    }

    LaunchedEffect(hasPermission, language) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (!speech.isAvailable()) {
            errorText = context.getString(R.string.voice_unavailable)
        } else {
            speech.stop()
            errorText = null
            speech.start(language.speechTag)
            listening = true
        }
    }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }

    val pulse by animateFloatAsState(if (listening) 1f + level * 0.35f else 1f, label = "micPulse")

    ModalBottomSheet(
        onDismissRequest = { speech.stop(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
            Spacer(Modifier.height(4.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            LanguageChips(selected = language, onSelect = onLanguageChange)
            Spacer(Modifier.height(28.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                Box(
                    Modifier
                        .size(104.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.25f }
                        .background(colors.primary, CircleShape)
                )
                Box(
                    Modifier
                        .size(84.dp)
                        .background(if (listening) colors.primary else colors.surfaceVariant, CircleShape)
                        .clickable {
                            if (listening) {
                                speech.stop(); listening = false
                            } else if (hasPermission) {
                                errorText = null
                                speech.start(language.speechTag); listening = true
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (listening) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                        contentDescription = stringResource(if (listening) R.string.voice_stop else R.string.voice_start),
                        tint = if (listening) colors.onPrimary else colors.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = errorText ?: partial.ifBlank {
                    stringResource(if (listening) R.string.voice_listening else R.string.voice_paused)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (errorText != null) colors.error else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 48.dp)
            )

            if (added.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.voice_added_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    added.forEach { chip ->
                        val text = label(chip.item)
                        val removeLabel = stringResource(R.string.voice_remove_item, text)
                        InputChip(
                            selected = false,
                            onClick = {
                                added.remove(chip)
                                currentOnRemove(chip.item)
                            },
                            label = { Text(text) },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = removeLabel,
                                    modifier = Modifier.size(InputChipDefaults.IconSize)
                                )
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { speech.stop(); onDismiss() },
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) { Text(stringResource(R.string.action_done)) }
        }
    }
}

/** One row with a checkbox; used by the pick-items sheets. */
data class PickRow(val key: Long, val title: String, val subtitle: String = "")

/**
 * Lets the user tick which items to add, with "Select all".
 * Used for scanned lists and for "remaining items from last time".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickItemsSheet(
    title: String,
    rows: List<PickRow>,
    initiallySelected: Boolean,
    confirmLabel: String,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val selected = remember(rows) {
        mutableStateListOf<Long>().apply { if (initiallySelected) addAll(rows.map { it.key }) }
    }
    val allSelected = rows.isNotEmpty() && selected.size == rows.size

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onBackground, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (allSelected) selected.clear() else { selected.clear(); selected.addAll(rows.map { it.key }) }
                }) {
                    Text(stringResource(if (allSelected) R.string.action_clear_all else R.string.action_select_all))
                }
            }
            header?.let {
                Box(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) { it() }
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(rows, key = { it.key }) { row ->
                    val checked = row.key in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (checked) selected.remove(row.key) else selected.add(row.key) }
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { if (it) selected.add(row.key) else selected.remove(row.key) })
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
                            if (row.subtitle.isNotBlank()) {
                                Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onConfirm(selected.toSet()) },
                enabled = selected.isNotEmpty(),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(52.dp)
            ) { Text(confirmLabel) }
        }
    }
}
