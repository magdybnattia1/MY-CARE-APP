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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val careTypes = listOf("موعد", "جلسة", "شكوى", "دواء", "تحليل/أشعة")

class MainActivity : ComponentActivity() {
    private val dao by lazy { CareDatabase.get(this).careDao() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MyCareTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    CareScreen(dao = dao, context = this@MainActivity, scope = lifecycleScope)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareScreen(dao: CareDao, context: Context, scope: CoroutineScope) {
    var selected by remember { mutableStateOf(careTypes.first()) }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    val records by dao.observeAll().collectAsState(initial = emptyList())

    val parsedDate = if (date.isBlank()) null else parseDate(date)
    val dateInvalid = date.isNotBlank() && parsedDate == null
    val canSave = title.isNotBlank() && !dateInvalid
    val accent = typeColor(selected)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MY CARE", fontWeight = FontWeight.Bold)
                        Text("سجلك العلاجي في مكان واحد", style = MaterialTheme.typography.bodySmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("إضافة سجل جديد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            careTypes.forEach { type ->
                                FilterChip(
                                    selected = selected == type,
                                    onClick = { selected = type },
                                    label = { Text(type) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = typeColor(type).copy(alpha = 0.18f),
                                        selectedLabelColor = typeColor(type)
                                    )
                                )
                            }
                        }
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("العنوان أو اسم الطبيب") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        OutlinedTextField(
                            value = details,
                            onValueChange = { details = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("التفاصيل / الشكوى / التعليمات") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp)
                        )
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("التاريخ") },
                            placeholder = { Text("YYYY-MM-DD") },
                            supportingText = {
                                Text(if (dateInvalid) "اكتب التاريخ بصيغة 2026-12-25" else "اختياري")
                            },
                            isError = dateInvalid,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Button(
                            onClick = {
                                val millis = parsedDate ?: 0L
                                val cleanTitle = title.trim()
                                val cleanDetails = details.trim()
                                scope.launch {
                                    val id = dao.insert(CareRecord(type = selected, title = cleanTitle, details = cleanDetails, dateMillis = millis))
                                    if (selected == "موعد" || selected == "جلسة") scheduleReminder(context, id.toInt(), cleanTitle, millis)
                                }
                                title = ""
                                details = ""
                                date = ""
                            },
                            enabled = canSave,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) { Text("حفظ السجل", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "سجلاتي",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${records.size} سجل",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (records.isEmpty()) {
                item { EmptyState() }
            }
            items(records, key = { it.id }) { record ->
                RecordCard(record = record, onDelete = { scope.launch { dao.delete(record) } })
            }
            item {
                Text(
                    "تنبيه: MY CARE أداة لتنظيم المعلومات فقط، ولا تقدم تشخيصًا ولا تغيّر تعليمات الطبيب.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("لا توجد سجلات بعد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "ابدأ بإضافة موعد أو شكوى أو دواء، وسيصلك تذكير قبل الموعد بيوم.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RecordCard(record: CareRecord, onDelete: () -> Unit) {
    val accent = typeColor(record.type)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Column(
                Modifier.weight(1f).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        record.type,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(record.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (record.details.isNotBlank()) {
                    Text(
                        record.details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (record.dateMillis > 0) {
                        Text(
                            "التاريخ: " + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(record.dateMillis)),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onDelete) {
                        Text("حذف", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun parseDate(text: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(text.trim())?.time
} catch (_: Exception) {
    null
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
