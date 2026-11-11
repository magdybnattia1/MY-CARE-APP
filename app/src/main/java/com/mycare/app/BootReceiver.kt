package com.mycare.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = CareDatabase.get(appContext).careDao()
                val now = System.currentTimeMillis()
                dao.upcomingSessions(now).forEach { Reminders.scheduleSession(appContext, it) }
                dao.allMedications()
                    .filter { it.continuous || it.endMillis >= now }
                    .forEach { Reminders.scheduleMedication(appContext, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
