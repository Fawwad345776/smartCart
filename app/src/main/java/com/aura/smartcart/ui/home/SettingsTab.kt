package com.aura.smartcart.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.smartcart.AppContainer
import com.aura.smartcart.BuildConfig
import com.aura.smartcart.R
import com.aura.smartcart.data.ThemeMode
import com.aura.smartcart.data.WeeklyReminder
import com.aura.smartcart.service.notify.Notifier
import com.aura.smartcart.service.notify.ReminderScheduler
import com.aura.smartcart.ui.common.LanguageChips
import com.aura.smartcart.ui.common.TimePickerDialog
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.ui.common.currentAppLanguage
import com.aura.smartcart.ui.common.resolveInputLanguage
import com.aura.smartcart.util.AppLanguage
import com.aura.smartcart.util.Dates
import com.aura.smartcart.util.InputLanguage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val theme: StateFlow<ThemeMode> = container.prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val inputLanguage: StateFlow<InputLanguage> = container.prefs.inputLanguage
        .map { resolveInputLanguage(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), resolveInputLanguage(null))
    val weekly: StateFlow<WeeklyReminder?> = container.prefs.weeklyReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sharePrices: StateFlow<Boolean> = container.prefs.sharePrices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setSharePrices(on: Boolean) = viewModelScope.launch { container.prefs.setSharePrices(on) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { container.prefs.setThemeMode(mode) }
    fun setInputLanguage(l: InputLanguage) = viewModelScope.launch { container.prefs.setInputLanguage(l) }

    fun updateWeekly(reminder: WeeklyReminder) {
        viewModelScope.launch {
            container.prefs.setWeeklyReminder(reminder)
            ReminderScheduler.scheduleWeekly(container.appContext, reminder)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(contentPadding: PaddingValues) {
    val vm = appViewModel { SettingsViewModel(it) }
    val theme by vm.theme.collectAsStateWithLifecycle()
    val inputLanguage by vm.inputLanguage.collectAsStateWithLifecycle()
    val weekly by vm.weekly.collectAsStateWithLifecycle()
    val sharePrices by vm.sharePrices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val appLanguage = currentAppLanguage()

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard(Icons.Rounded.Language, stringResource(R.string.settings_app_language), stringResource(R.string.settings_app_language_hint)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        GreenChip(selected = lang == appLanguage, label = lang.label) {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
                        }
                    }
                }
            }

            SettingsCard(Icons.Rounded.Mic, stringResource(R.string.settings_input_language), stringResource(R.string.settings_input_language_hint)) {
                LanguageChips(selected = inputLanguage, onSelect = vm::setInputLanguage)
            }

            SettingsCard(Icons.Rounded.DarkMode, stringResource(R.string.settings_theme), null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GreenChip(theme == ThemeMode.SYSTEM, stringResource(R.string.theme_system)) { vm.setTheme(ThemeMode.SYSTEM) }
                    GreenChip(theme == ThemeMode.LIGHT, stringResource(R.string.theme_light)) { vm.setTheme(ThemeMode.LIGHT) }
                    GreenChip(theme == ThemeMode.DARK, stringResource(R.string.theme_dark)) { vm.setTheme(ThemeMode.DARK) }
                }
            }

            SettingsCard(
                Icons.Rounded.Storefront,
                stringResource(R.string.settings_share_prices),
                stringResource(R.string.settings_share_prices_hint),
                trailing = { Switch(checked = sharePrices, onCheckedChange = { vm.setSharePrices(it) }) }
            ) {}

            weekly?.let { w ->
                SettingsCard(
                    Icons.Rounded.Storefront,
                    stringResource(R.string.settings_weekly),
                    stringResource(R.string.settings_weekly_hint),
                    trailing = {
                        Switch(checked = w.enabled, onCheckedChange = { on ->
                            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(context)) {
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            vm.updateWeekly(w.copy(enabled = on))
                        })
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            DayOfWeek.entries.forEach { day ->
                                val on = day in w.days
                                DayCircle(
                                    label = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                    selected = on,
                                    enabled = w.enabled,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    vm.updateWeekly(w.copy(days = if (on) w.days - day else w.days + day))
                                }
                            }
                        }
                        Surface(
                            onClick = { pickTime = true },
                            enabled = w.enabled,
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.settings_weekly_time, Dates.time(w.minuteOfDay)), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (pickTime) {
                    TimePickerDialog(
                        initialMinuteOfDay = w.minuteOfDay,
                        onPicked = { vm.updateWeekly(w.copy(minuteOfDay = it)); pickTime = false },
                        onDismiss = { pickTime = false }
                    )
                }
            }

            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp).align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(colors.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = colors.primary) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun GreenChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun DayCircle(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg = when {
        selected && enabled -> colors.primary
        selected -> colors.primary.copy(alpha = 0.4f)
        else -> colors.surfaceVariant
    }
    Box(
        modifier
            .height(40.dp)
            .background(bg, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant
        )
    }
}
