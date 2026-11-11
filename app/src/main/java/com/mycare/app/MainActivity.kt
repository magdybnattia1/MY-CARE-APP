package com.mycare.app

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val dao by lazy { CareDatabase.get(this).careDao() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF167D83), secondary = androidx.compose.ui.graphics.Color(0xFF62A9A2))) {
                var selected by remember { mutableStateOf("موعد") }
                var title by remember { mutableStateOf("") }
                var details by remember { mutableStateOf("") }
                var date by remember { mutableStateOf("") }
                val records by dao.observeAll().collectAsState(initial = emptyList())
                CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                    Scaffold { padding ->
                        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                Text("MY CARE", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                                Text("كل تفاصيل رحلتك العلاجية معاك.", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(8.dp))
                                Text("إضافة سجل جديد", style = MaterialTheme.typography.titleLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("موعد","جلسة","شكوى","دواء","تحليل/أشعة").forEach { type ->
                                        FilterChip(selected = selected == type, onClick = { selected = type }, label = { Text(type) })
                                    }
                                }
                                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("العنوان أو اسم الطبيب") }, singleLine = true)
                                OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text("التفاصيل / الشكوى / التعليمات") }, minLines = 2)
                                OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), label = { Text("التاريخ (مثال 2026-12-25)") }, singleLine = true)
                                Button(onClick = {
                                    if (title.isNotBlank()) {
                                        val millis = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time ?: 0L } catch (_: Exception) { 0L }
                                        lifecycleScope.launch {
                                            val id = dao.insert(CareRecord(type = selected, title = title.trim(), details = details.trim(), dateMillis = millis))
                                            if (selected == "موعد" || selected == "جلسة") scheduleReminder(this@MainActivity, id.toInt(), title, millis)
                                            title = ""; details = ""; date = ""
                                        }
                                    }
                                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("حفظ السجل") }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Text("سجلاتي", style = MaterialTheme.typography.titleLarge)
                            }
                            if (records.isEmpty()) item { Text("لا توجد سجلات بعد. ابدأ بإضافة موعد أو شكوى.") }
                            items(records, key = { it.id }) { record ->
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(record.type, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                        Text(record.title, style = MaterialTheme.typography.titleMedium)
                                        if (record.details.isNotBlank()) Text(record.details)
                                        if (record.dateMillis > 0) Text("التاريخ: " + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(record.dateMillis)))
                                        TextButton(onClick = { lifecycleScope.launch { dao.delete(record) } }) { Text("حذف") }
                                    }
                                }
                            }
                            item { Text("تنبيه: MY CARE أداة لتنظيم المعلومات فقط، ولا يقدم تشخيصًا أو يغيّر تعليمات الطبيب.", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

private fun scheduleReminder(context: Context, id: Int, title: String, dateMillis: Long) {
    if (dateMillis <= 0) return
    val trigger = dateMillis - 24L * 60L * 60L * 1000L
    if (trigger <= System.currentTimeMillis()) return
    val intent = Intent(context, ReminderReceiver::class.java).putExtra("title", title)
    val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
}
