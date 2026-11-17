package com.aura.smartcart.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.smartcart.AppContainer
import com.aura.smartcart.R
import com.aura.smartcart.ui.common.SectionLabel
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.util.Country
import com.aura.smartcart.util.Money
import com.aura.smartcart.util.Months
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MonthRow(val monthKey: String, val spent: Double, val budget: Double?)

data class BudgetUi(
    val monthKey: String,
    val budget: Double?,
    val spent: Double,
    val currency: String,
    val history: List<MonthRow>
)

class BudgetViewModel(private val container: AppContainer) : ViewModel() {
    private val month = Months.currentKey()

    init {
        viewModelScope.launch { container.budgets.carryOverIfMissing(month) }
    }

    val ui: StateFlow<BudgetUi?> = combine(
        container.budgets.observe(month),
        container.bills.observeMonthTotal(month),
        container.bills.observeMonthTotals(),
        container.budgets.observeAll(),
        container.prefs.country
    ) { budget, spent, totals, budgets, country ->
        val budgetMap = budgets.associate { it.monthKey to it.amount }
        val keys = (totals.map { it.monthKey } + budgetMap.keys).distinct().filter { it != month }.sortedDescending()
        val totalMap = totals.associate { it.monthKey to it.total }
        BudgetUi(
            monthKey = month,
            budget = budget?.amount,
            spent = spent,
            currency = Country.currencyCode(country ?: Country.detect(container.appContext)),
            history = keys.map { MonthRow(it, totalMap[it] ?: 0.0, budgetMap[it]) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setBudget(amount: Double) {
        viewModelScope.launch { container.budgets.set(month, amount) }
    }
}

@Composable
fun BudgetTab(contentPadding: PaddingValues) {
    val vm = appViewModel { BudgetViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    val data = ui ?: return
    val colors = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { CurrentMonthCard(data, onEdit = { editing = true }) }
            if (data.history.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.budget_history), Modifier.padding(top = 8.dp)) }
                items(data.history, key = { it.monthKey }) { row -> HistoryRow(row, data.currency) }
            }
        }
    }

    if (editing) {
        var text by rememberSaveable { mutableStateOf(data.budget?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.budget_set_title, Months.label(data.monthKey))) },
            text = {
                Column {
                    Text(stringResource(R.string.budget_set_body), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        prefix = { Text(data.currency + " ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Money.parse(text)?.let { vm.setBudget(it) }
                    editing = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun CurrentMonthCard(data: BudgetUi, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val budget = data.budget
    val over = budget != null && data.spent > budget
    Surface(shape = RoundedCornerShape(24.dp), color = colors.primary, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Months.label(data.monthKey), style = MaterialTheme.typography.titleSmall, color = colors.onPrimary, modifier = Modifier.weight(1f))
                if (budget != null) {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = colors.onPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_edit), color = colors.onPrimary)
                    }
                }
            }
            Text(stringResource(R.string.budget_spent), style = MaterialTheme.typography.labelMedium, color = colors.onPrimary.copy(alpha = 0.85f))
            Text(Money.format(data.spent, data.currency), style = MaterialTheme.typography.displaySmall, color = colors.onPrimary)
            if (budget == null) {
                Text(stringResource(R.string.budget_none), style = MaterialTheme.typography.bodyMedium, color = colors.onPrimary)
                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.onPrimary, contentColor = colors.primary)
                ) { Text(stringResource(R.string.budget_set)) }
            } else {
                LinearProgressIndicator(
                    progress = { if (budget <= 0) 1f else (data.spent / budget).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = if (over) colors.error else colors.onPrimary,
                    trackColor = colors.onPrimary.copy(alpha = 0.25f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Row {
                    Text(
                        stringResource(R.string.budget_of, Money.format(budget, data.currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimary.copy(alpha = 0.9f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (over) stringResource(R.string.budget_over, Money.format(data.spent - budget, data.currency))
                        else stringResource(R.string.budget_left, Money.format(budget - data.spent, data.currency)),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimary
                    )
                }
                if (!over) {
                    Text(
                        stringResource(R.string.budget_days_left, Months.daysLeftInMonth()),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(row: MonthRow, currency: String) {
    val colors = MaterialTheme.colorScheme
    val over = row.budget != null && row.spent > row.budget
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Months.label(row.monthKey), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
                Text(
                    Money.format(row.spent, currency),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (over) colors.error else colors.onSurface
                )
            }
            if (row.budget != null) {
                LinearProgressIndicator(
                    progress = { if (row.budget <= 0) 1f else (row.spent / row.budget).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (over) colors.error else colors.primary,
                    trackColor = colors.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Text(
                    stringResource(R.string.budget_of, Money.format(row.budget, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}
