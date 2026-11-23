package com.aura.smartcart.ui.pro

import android.app.Activity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.smartcart.AppContainer
import com.aura.smartcart.R
import com.aura.smartcart.data.ScanKind
import com.aura.smartcart.service.billing.ProBilling
import com.aura.smartcart.service.billing.ProPlan
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.ui.common.findActivity
import com.aura.smartcart.ui.common.toast
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ProViewModel(container: AppContainer) : ViewModel() {
    private val billing = container.pro
    val isPro: StateFlow<Boolean> = billing.isPro
    val plans: StateFlow<List<ProPlan>> = billing.plans
    val loaded: StateFlow<Boolean> = billing.loaded
    val used: StateFlow<Map<ScanKind, Int>> =
        container.quota.usedThisMonth.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        billing.connect()
    }

    fun buy(activity: Activity, plan: ProPlan) = billing.buy(activity, plan)
    fun restore() = billing.refresh()
}

/** SmartCart Pro: what Pro gives, this month's free scans, and the plans from Google Play. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(onBack: () -> Unit) {
    val vm = appViewModel { ProViewModel(it) }
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val used by vm.used.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.pro_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(84.dp).background(colors.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = colors.primary, modifier = Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
                    Text(
                        stringResource(R.string.pro_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                if (isPro) {
                    Surface(shape = RoundedCornerShape(18.dp), color = colors.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.pro_active),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // What Pro gives
                Feature(Icons.Rounded.DocumentScanner, stringResource(R.string.pro_feature_scan_title), stringResource(R.string.pro_feature_scan_body), comingSoon = false)
                Feature(Icons.Rounded.NotificationsActive, stringResource(R.string.pro_feature_rate_title), stringResource(R.string.pro_feature_rate_body), comingSoon = true)
                Feature(Icons.Rounded.Map, stringResource(R.string.pro_feature_plan_title), stringResource(R.string.pro_feature_plan_body), comingSoon = true)

                // Free scans used this month
                if (!isPro) {
                    Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.pro_free_usage), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                            UsageLine(R.string.pro_usage_bill_photo, used[ScanKind.BILL_PHOTO] ?: 0, ScanKind.BILL_PHOTO.freeLimit)
                            UsageLine(R.string.pro_usage_bill_video, used[ScanKind.BILL_VIDEO] ?: 0, ScanKind.BILL_VIDEO.freeLimit)
                            UsageLine(R.string.pro_usage_list, used[ScanKind.LIST] ?: 0, ScanKind.LIST.freeLimit)
                        }
                    }
                }

                // Plans from Google Play
                if (!isPro) {
                    when {
                        !loaded -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        plans.isEmpty() -> Text(
                            stringResource(R.string.pro_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error
                        )
                        else -> plans.forEach { plan ->
                            PlanCard(plan) {
                                val activity = context.findActivity()
                                if (activity == null || !vm.buy(activity, plan)) context.toast(R.string.pro_unavailable)
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = vm::restore) { Text(stringResource(R.string.pro_restore)) }
                    if (isPro) {
                        TextButton(onClick = {
                            uriHandler.openUri(
                                "https://play.google.com/store/account/subscriptions?sku=${ProBilling.PRODUCT_ID}&package=${context.packageName}"
                            )
                        }) { Text(stringResource(R.string.pro_manage)) }
                    }
                }
                Text(
                    stringResource(R.string.pro_cancel_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String, comingSoon: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                if (comingSoon) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = colors.secondaryContainer) {
                        Text(
                            stringResource(R.string.pro_coming_soon),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun UsageLine(label: Int, used: Int, limit: Int) {
    Text(
        stringResource(label, minOf(used, limit), limit),
        style = MaterialTheme.typography.bodyMedium,
        color = if (used >= limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PlanCard(plan: ProPlan, onBuy: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val period = when (plan.period) {
        "P1M" -> stringResource(R.string.pro_period_month)
        "P1Y" -> stringResource(R.string.pro_period_year)
        else -> plan.period
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.price, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Text(period, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Button(onClick = onBuy) { Text(stringResource(R.string.pro_subscribe)) }
        }
    }
}
