package com.ppkitchen.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

object MessengerSender {

    fun sendWhatsApp(context: Context, phone: String, text: String) {
        if (phone.isBlank()) {
            Toast.makeText(context, "У клиента нет телефона", Toast.LENGTH_LONG).show()
            return
        }
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")
        val uri = Uri.parse("whatsapp://send?phone=$cleanPhone&text=${Uri.encode(text)}")

        val wa = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") }
        val waBiz = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp.w4b") }

        when {
            isPackageInstalled(context, "com.whatsapp") ->
                context.startActivity(wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            isPackageInstalled(context, "com.whatsapp.w4b") ->
                context.startActivity(waBiz.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> {
                // WhatsApp не установлен — общий шаринг
                shareGeneric(context, text)
            }
        }
    }

    fun sendTelegram(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        if (isPackageInstalled(context, "org.telegram.messenger")) {
            intent.setPackage("org.telegram.messenger")
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else if (isPackageInstalled(context, "org.thunderdog.challegram")) {
            intent.setPackage("org.thunderdog.challegram")
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            shareGeneric(context, text)
        }
    }

    fun sendSms(context: Context, phone: String, text: String) {
        if (phone.isBlank()) {
            Toast.makeText(context, "У клиента нет телефона", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phone")
            putExtra("sms_body", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun shareGeneric(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(send, "Отправить сообщение"))
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}