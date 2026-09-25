package com.ppkitchen.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object ChefReminderScheduler {
    private const val REQUEST_CODE = 11001
    private const val TAG = "ChefReminder"

    fun scheduleNext(context: Context) {
        if (!SettingsPreferences.isChefReminderEnabled(context)) {
            cancel(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ChefReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val hour = SettingsPreferences.getChefSendHour(context)
        val minute = SettingsPreferences.getChefSendMinute(context)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val triggerAt = cal.timeInMillis
        SettingsPreferences.setChefNextSchedule(context, triggerAt)

        try {
            // setAlarmClock — самый надёжный, разбудит приложение даже в Doze
            val alarmClockIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE + 1,
                Intent(context, ChefReminderReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else true

            if (canExact) {
                // setAlarmClock — гарантирует точное срабатывание
                val alarmInfo = AlarmManager.AlarmClockInfo(triggerAt, pending)
                alarmManager.setAlarmClock(alarmInfo, alarmClockIntent)
                Log.d(TAG, "setAlarmClock на ${cal.time}")
            } else {
                // Fallback без точного разрешения
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pending
                )
                Log.d(TAG, "setAndAllowWhileIdle (нет разрешения на точные) на ${cal.time}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка планирования", e)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ChefReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pendingClock = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE + 1,
            Intent(context, ChefReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.cancel(pending)
        alarmManager.cancel(pendingClock)
        SettingsPreferences.setChefNextSchedule(context, 0L)
        Log.d(TAG, "Все будильники отменены")
    }

    fun getNextScheduleText(context: Context): String {
        val t = SettingsPreferences.getChefNextSchedule(context)
        if (t <= 0L) return "не запланировано"
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(t))
    }
}