package com.mycare.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Reminders {
    fun appointmentCode(id: Long) = (id * 3 + 1).toInt()
    fun sessionCode(id: Long) = (id * 3 + 2).toInt()
    fun medicationCode(id: Long) = (id * 3 + 3).toInt()

    fun parseHm(text: String): Pair<Int, Int>? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h to m
    }

    fun scheduleAppointment(context: Context, a: Appointment) {
        val trigger = atTime(a.dateMillis, 9, 0, 1)
        setOne(context, appointmentCode(a.id), trigger, "غداً موعد مع د. ${a.doctor}" + if (a.time.isNotBlank()) " الساعة ${a.time}" else "", false)
    }

    fun scheduleSession(context: Context, s: TherapySession) {
        if (s.nextDateMillis <= 0) return
        setOne(context, sessionCode(s.id), atTime(s.nextDateMillis, 9, 0, 1), "غداً جلسة ${s.type}", false)
    }

    // Reminds once a day at the first dose time. Extra daily doses are not scheduled yet.
    fun scheduleMedication(context: Context, m: Medication) {
        val (h, min) = parseHm(m.firstTime) ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        setOne(context, medicationCode(m.id), cal.timeInMillis, "حان وقت دواء ${m.name}", true)
    }

    fun cancel(context: Context, code: Int) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pendingFor(context, code, ""))
    }

    private fun setOne(context: Context, code: Int, trigger: Long, text: String, repeatDaily: Boolean) {
        if (!repeatDaily && trigger <= System.currentTimeMillis()) return
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingFor(context, code, text)
        if (repeatDaily) alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger, AlarmManager.INTERVAL_DAY, pending)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    private fun pendingFor(context: Context, code: Int, text: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("text", text)
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun atTime(dateMillis: Long, hour: Int, minute: Int, daysBefore: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = dateMillis
            add(Calendar.DAY_OF_YEAR, -daysBefore)
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
