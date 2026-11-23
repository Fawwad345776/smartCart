package com.aura.smartcart.ui.plan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.smartcart.AppContainer
import com.aura.smartcart.R
import com.aura.smartcart.data.remote.PricePoint
import com.aura.smartcart.ui.account.SignInRequiredDialog
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.util.Money
import com.aura.smartcart.util.PriceMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** Cheapest known price for one list item. */
class Pick(val itemName: String, val point: PricePoint)

/** A shop and the list items that are cheapest there. */
class StoreStop(val store: String, val km: Double, val picks: List<Pick>) {
    val total: Double get() = picks.sumOf { it.point.price }
}

enum class PlanProblem { NOT_SIGNED_IN, NO_LOCATION, NO_PRICES }

data class StorePlanState(
    val listName: String = "",
    val itemNames: List<String> = emptyList(),
    val radiusKm: Int = 3,
    val loading: Boolean = false,
    val stops: List<StoreStop>? = null,
    val missing: List<String> = emptyList(),
    val problem: PlanProblem? = null
)

class StorePlanViewModel(private val container: AppContainer, private val listId: Long) : ViewModel() {
    private val _state = MutableStateFlow(StorePlanState())
    val state: StateFlow<StorePlanState> = _state.asStateFlow()
    val isPro: StateFlow<Boolean> = container.pro.isPro

    private class ListItem(val name: String, val quantity: String)
    private var items: List<ListItem> = emptyList()

    init {
        viewModelScope.launch {
            val list = container.lists.getList(listId) ?: return@launch
            items = list.items.filter { it.name.isNotBlank() }.map { ListItem(it.name, it.quantity) }
            _state.update { it.copy(listName = list.list.name, itemNames = items.map { i -> i.name }) }
        }
    }

    fun hasLocationPermission() = container.location.hasPermission()
    fun isSignedIn() = container.auth.isSignedIn()

    fun setRadius(km: Int) = _state.update { it.copy(radiusKm = km) }

    /** Finds, within the chosen distance, where each list item was cheapest in the last 30 days. */
    fun build() {
        if (!isSignedIn()) {
            _state.update { it.copy(problem = PlanProblem.NOT_SIGNED_IN, stops = null) }
            return
        }
        _state.update { it.copy(loading = true, problem = null, stops = null) }
        viewModelScope.launch {
            val place = container.location.current()
            if (place == null) {
                _state.update { it.copy(loading = false, problem = PlanProblem.NO_LOCATION) }
                return@launch
            }
            val radius = _state.value.radiusKm.toDouble()
            val points = container.prices.nearby(place.lat, place.lng, radius, days = 30)

            val picks = mutableListOf<Pick>()
            val missing = mutableListOf<String>()
            for (item in items) {
                val best = points
                    .filter { PriceMatch.sameItem(item.name, it.name) }
                    .filter { item.quantity.isBlank() || PriceMatch.sameQuantity(item.quantity, it.quantity) }
                    .minByOrNull { it.price }
                if (best == null) missing += item.name else picks += Pick(item.name, best)
            }

            // Same shop = same name at (about) the same place.
            val stops = picks
                .groupBy { "${it.point.store.lowercase(Locale.ROOT).trim()}|${"%.3f".format(Locale.US, it.point.lat)}|${"%.3f".format(Locale.US, it.point.lng)}" }
                .values
                .map { group ->
                    val p = group.first().point
                    StoreStop(p.store, PriceMatch.distanceKm(place.lat, place.lng, p.lat, p.lng), group)
                }
                .sortedWith(compareByDescending<StoreStop> { it.picks.size }.thenBy { it.km })

            _state.update {
                it.copy(
                    loading = false,
                    stops = stops,
                    missing = missing,
                    problem = if (stops.isEmpty()) PlanProblem.NO_PRICES else null
                )
            }
        }
    }
}

/** Smart Store Plan (Pro): pick a distance and see which nearby shop is cheapest for each list item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorePlanScreen(listId: Long, onBack: () -> Unit, onOpenPro: () -> Unit) {
    val vm = appViewModel(key = "plan_$listId") { StorePlanViewModel(it, listId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    var askSignIn by rememberSaveable { mutableStateOf(false) }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.build()
    }
    val build: () -> Unit = {
        if (!vm.isSignedIn()) {
            askSignIn = true
        } else if (!vm.hasLocationPermission()) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            vm.build()
        }
    }

    if (askSignIn) {
        SignInRequiredDialog(onSignedIn = { askSignIn = false; build() }, onDismiss = { askSignIn = false })
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
                title = { Text(stringResource(R.string.pro_feature_plan_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.plan_intro, state.listName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }

                if (!isPro) {
                    item { LockedCard(onOpenPro) }
                    return@LazyColumn
                }

                item {
                    Text(stringResource(R.string.plan_distance), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 5, 10).forEach { km ->
                            FilterChip(
                                selected = state.radiusKm == km,
                                onClick = { vm.setRadius(km) },
                                label = { Text(stringResource(R.string.distance_km, km.toString())) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.primary,
                                    selectedLabelColor = colors.onPrimary
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = build, enabled = !state.loading && state.itemNames.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.plan_build))
                    }
                }

                if (state.loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }

                state.problem?.let { problem ->
                    item {
                        Text(
                            stringResource(
                                when (problem) {
                                    PlanProblem.NOT_SIGNED_IN -> R.string.plan_need_sign_in
                                    PlanProblem.NO_LOCATION -> R.string.plan_no_location
                                    PlanProblem.NO_PRICES -> R.string.plan_no_prices
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error
                        )
                    }
                }

                state.stops?.let { stops ->
                    items(stops) { stop -> StopCard(stop) }
                    if (state.missing.isNotEmpty() && stops.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.plan_missing, state.missing.joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedCard(onOpenPro: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = colors.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Map, contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.pro_feature_plan_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenPro) { Text(stringResource(R.string.plan_get_pro)) }
        }
    }
}

@Composable
private fun StopCard(stop: StoreStop) {
    val colors = MaterialTheme.colorScheme
    val distance = if (stop.km < 1) stringResource(R.string.distance_m, (stop.km * 1000).roundToInt())
    else stringResource(R.string.distance_km, "%.1f".format(stop.km))
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Storefront, contentDescription = null, tint = colors.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stop.store.ifBlank { stringResource(R.string.notif_cheaper_unknown_store) },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                    Text(
                        "$distance · " + pluralStringResource(R.plurals.plan_items_here, stop.picks.size, stop.picks.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = colors.outline.copy(alpha = 0.5f))
            stop.picks.forEach { pick ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pick.itemName, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                        val days = ((System.currentTimeMillis() - pick.point.at) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)
                        Text(
                            listOf(pick.point.quantity, pluralStringResource(R.plurals.days_ago, days, days))
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Text(Money.format(pick.point.price, ""), style = MaterialTheme.typography.titleSmall, color = colors.primary)
                }
            }
        }
    }
}
