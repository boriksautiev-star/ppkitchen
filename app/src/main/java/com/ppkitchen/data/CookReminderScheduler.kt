package com.ppkitchen.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CookReminderScheduler {
    private const val REQUEST_CODE = 13001
    private const val TAG = "CookReminder"

    fun scheduleNext(context: Context) {
        if (!SettingsPreferences.isCookReminderEnabled(context)) {
            cancel(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CookReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val hour = SettingsPreferences.getCookReminderHour(context)
        val minute = SettingsPreferences.getCookReminderMinute(context)

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
        SettingsPreferences.setCookReminderNextSchedule(context, triggerAt)

        try {
            val alarmClockIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE + 1,
                Intent(context, CookReminderReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else true

            if (canExact) {
                val alarmInfo = AlarmManager.AlarmClockInfo(triggerAt, pending)
                alarmManager.setAlarmClock(alarmInfo, alarmClockIntent)
                Log.d(TAG, "setAlarmClock на ${cal.time}")
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pending
                )
                Log.d(TAG, "setAndAllowWhileIdle на ${cal.time}")
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
            Intent(context, CookReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pendingClock = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE + 1,
            Intent(context, CookReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.cancel(pending)
        alarmManager.cancel(pendingClock)
        SettingsPreferences.setCookReminderNextSchedule(context, 0L)
        Log.d(TAG, "Будильники отменены")
    }

    fun getNextScheduleText(context: Context): String {
        val t = SettingsPreferences.getCookReminderNextSchedule(context)
        if (t <= 0L) return "не запланировано"
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(t))
    }
}