package com.ppkitchen.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val ctx = context.applicationContext
            ChefReminderScheduler.scheduleNext(ctx)
            ClientEndReminderScheduler.scheduleNext(ctx)
            CookReminderScheduler.scheduleNext(ctx)
            DeliveryReminderScheduler.scheduleNext(ctx)
        }
    }
}