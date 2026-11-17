package com.aura.smartcart.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aura.smartcart.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Picks a date, then a time. Returns epoch millis in the phone's time zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialMillis: Long?,
    onPicked: (Long) -> Unit,
    onDismiss: () -> Unit,
    allowPast: Boolean = false
) {
    val zone = ZoneId.systemDefault()
    val initial = initialMillis?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
        ?: LocalDateTime.now().plusHours(2).withMinute(0)
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initial.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                if (allowPast) return true
                val today = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                return utcTimeMillis >= today
            }
        }
    )
    val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)

    if (pickedDate == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                }) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
        ) {
            DatePicker(state = dateState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.pick_time)) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dt = pickedDate!!.atTime(timeState.hour, timeState.minute)
                    onPicked(dt.atZone(zone).toInstant().toEpochMilli())
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickedDate = null }) { Text(stringResource(R.string.action_back)) } }
        )
    }
}

/** Time only, for the weekly reminders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initialMinuteOfDay: Int, onPicked: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinuteOfDay / 60, initialMinute = initialMinuteOfDay % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_time)) },
        text = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onPicked(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
