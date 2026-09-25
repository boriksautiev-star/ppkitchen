package com.ppkitchen.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDatePickerDialog(
    initialDate: String? = null,
    forceToday: Boolean = false,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Material3 DatePicker работает в UTC — используем тот же часовой пояс
    val utc = remember { TimeZone.getTimeZone("UTC") }
    val sdf = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = utc
        }
    }

    val initialMillis = remember(initialDate, forceToday) {
        if (forceToday) {
            // Всегда открываем на сегодня
            // Округляем текущий день до UTC-полуночи
            val now = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance(utc).apply {
                timeInMillis = now
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            calendar.timeInMillis
        } else {
            initialDate?.let {
                try { sdf.parse(it)?.time } catch (e: Exception) { null }
            }
        }
    }

    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onDateSelected(sdf.format(Date(it)))
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    ) {
        DatePicker(state = state)
    }
}