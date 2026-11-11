package com.mycare.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "mycare_reminders"
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channel, "تذكيرات MY CARE", NotificationManager.IMPORTANCE_HIGH))
        val text = intent.getStringExtra("text") ?: "لديك تذكير من MY CARE"
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channel) else @Suppress("DEPRECATION") Notification.Builder(context)
        manager.notify(System.currentTimeMillis().toInt(), builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("تذكير من MY CARE").setContentText(text).setAutoCancel(true).build())
    }
}
