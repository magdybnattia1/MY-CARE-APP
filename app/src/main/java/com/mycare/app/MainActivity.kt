package com.mycare.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
                    CareApp(dao, this@MainActivity, lifecycleScope)
                }
            }
        }
    }
}

private val tabNames = listOf("الرئيسية", "المواعيد", "الجلسات", "الأدوية", "الشكاوى", "الأسئلة", "الملف")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareApp(dao: CareDao, context: Context, scope: CoroutineScope) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MY CARE", fontWeight = FontWeight.Bold)
                        Text("كل تفاصيل رحلتك العلاجية معاك", style = MaterialTheme.typography.bodySmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.surface) {
                tabNames.forEachIndexed { i, name ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name) })
                }
            }
            when (tab) {
                0 -> HomeTab(dao)
                1 -> AppointmentsTab(dao, context, scope)
                2 -> SessionsTab(dao, context, scope)
                3 -> MedicationsTab(dao, context, scope)
                4 -> ComplaintsTab(dao, scope)
                5 -> QuestionsTab(dao, scope)
                else -> ProfileTab(dao, context, scope)
            }
        }
    }
}

// ---------- helpers ----------

private data class FieldSpec(
    val key: String,
    val label: String,
    val initial: String = "",
    val multi: Boolean = false,
    val numeric: Boolean = false
)

private fun Map<String, String>.str(key: String): String = this[key]?.trim().orEmpty()

private fun parseDate(text: String): Long? =
    if (text.isBlank()) null
    else try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(text.trim())?.time
    } catch (_: Exception) {
        null
    }

private fun formatDate(millis: Long): String =
    if (millis <= 0) "" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun FormDialog(
    title: String,
    specs: List<FieldSpec>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Boolean
) {
    val values = remember { mutableStateMapOf<String, String>().also { m -> specs.forEach { m[it.key] = it.initial } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                specs.forEach { spec ->
                    val current = values[spec.key].orEmpty()
                    if (spec.key == "clinic") {
                        var expanded by remember { mutableStateOf(false) }
                        val clinics = listOf("باطنة", "الألم", "عظام", "جراحة", "أورام دم", "أخرى")
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = current, onValueChange = { }, readOnly = true,
                                label = { Text(spec.label) }, placeholder = { Text("اختر العيادة") },
                                trailingIcon = { Text("⌄") }, modifier = Modifier.fillMaxWidth()
                            )
                            Box(Modifier.matchParentSize().clickable { expanded = true })
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                clinics.forEach { clinic ->
                                    DropdownMenuItem(text = { Text(clinic) }, onClick = {
                                        values[spec.key] = clinic
                                        expanded = false
                                    })
                                }
                            }
                        }
                    } else if (spec.key in setOf("date", "nextDate", "startDate", "endDate", "birthDate")) {
                        val dateContext = LocalContext.current
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = current, onValueChange = { }, readOnly = true,
                                label = { Text(spec.label) },
                                placeholder = { Text("اختر اليوم / الشهر / السنة") },
                                trailingIcon = { Text("▦") }, modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Box(Modifier.matchParentSize().clickable {
                                val cal = Calendar.getInstance()
                                parseDate(current)?.let { cal.timeInMillis = it }
                                android.app.DatePickerDialog(
                                    dateContext,
                                    { _, year, month, day ->
                                        values[spec.key] = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                                    },
                                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            })
                        }
                    } else {
                        OutlinedTextField(
                            value = current,
                            onValueChange = { values[spec.key] = it },
                            label = { Text(spec.label) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = !spec.multi,
                            minLines = if (spec.multi) 2 else 1,
                            keyboardOptions = KeyboardOptions(keyboardType = if (spec.numeric) KeyboardType.Number else KeyboardType.Text)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (onSave(values.toMap())) onDismiss() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EmptyState(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ItemCard(accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isNotBlank()) Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("حذف", color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun <T> ListWithFab(
    list: List<T>,
    emptyText: String,
    addLabel: String,
    onAdd: () -> Unit,
    key: (T) -> Any,
    row: @Composable (T) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (list.isEmpty()) item { EmptyState(emptyText) }
            items(list, key = key) { row(it) }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Text("+", fontWeight = FontWeight.Bold) },
            text = { Text(addLabel) }
        )
    }
}

// ---------- tabs ----------

@Composable
private fun HomeTab(dao: CareDao) {
    val sessions by dao.observeSessions().collectAsState(initial = emptyList())
    val meds by dao.observeMedications().collectAsState(initial = emptyList())
    val complaints by dao.observeComplaints().collectAsState(initial = emptyList())
    val questions by dao.observeQuestions().collectAsState(initial = emptyList())
    val next = sessions.filter { it.nextDateMillis >= startOfToday() && it.done < it.total }.minByOrNull { it.nextDateMillis }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("أهلاً بيك", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item { SummaryCard("الجلسة القادمة", next?.let { "${it.type} - ${formatDate(it.nextDateMillis)} ${it.time}".trim() } ?: "لا توجد جلسة قادمة مسجلة", typeColor("جلسة")) }
        item { SummaryCard("جلسات جارية", "${sessions.count { it.done < it.total }} جلسة", typeColor("جلسة")) }
        item { SummaryCard("الأدوية المسجلة", "${meds.size} دواء", typeColor("دواء")) }
        item { SummaryCard("شكاوى للمناقشة", "${complaints.count { it.wantDiscuss && !it.discussed }} شكوى", typeColor("شكوى")) }
        item { SummaryCard("أسئلة بانتظار الرد", "${questions.count { !it.answered }} سؤال", typeColor("تحليل/أشعة")) }
        item {
            Text(
                "تنبيه: MY CARE أداة لتنظيم المعلومات فقط، ولا تقدم تشخيصًا ولا تغيّر تعليمات الطبيب.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, accent: Color) {
    ItemCard(accent) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppointmentsTab(dao: CareDao, context: Context, scope: CoroutineScope) {
    val appointments by dao.observeAppointments().collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        FormDialog(
            title = "موعد جديد",
            specs = listOf(
                FieldSpec("date", "تاريخ الموعد YYYY-MM-DD *"),
                FieldSpec("clinic", "اسم العيادة *"),
                FieldSpec("doctor", "اسم الدكتور (اختياري)")
            ),
            onDismiss = { adding = false },
            onSave = { v ->
                val date = parseDate(v.str("date")) ?: return@FormDialog false
                if (v.str("clinic").isBlank()) return@FormDialog false
                val item = Appointment(
                    doctor = v.str("doctor"),
                    place = v.str("clinic"),
                    dateMillis = date
                )
                scope.launch {
                    val id = dao.insertAppointment(item)
                    Reminders.scheduleAppointment(context, item.copy(id = id))
                }
                true
            }
        )
    }
    ListWithFab(
        list = appointments,
        emptyText = "لا توجد مواعيد مسجلة. اضغط موعد جديد لإضافة موعد العيادة.",
        addLabel = "موعد جديد",
        onAdd = { adding = true },
        key = { it.id }
    ) { a ->
        ItemCard(typeColor("موعد")) {
            Text(a.place, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("تاريخ الموعد: ${formatDate(a.dateMillis)}", style = MaterialTheme.typography.bodyMedium)
            InfoLine("الدكتور", a.doctor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DeleteButton {
                    scope.launch {
                        dao.deleteAppointment(a)
                        Reminders.cancel(context, Reminders.appointmentCode(a.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionsTab(dao: CareDao, context: Context, scope: CoroutineScope) {
    val items by dao.observeSessions().collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        FormDialog(
            title = "جلسات علاج جديدة",
            specs = listOf(
                FieldSpec("type", "نوع الجلسة *"),
                FieldSpec("center", "اسم المركز"),
                FieldSpec("total", "عدد الجلسات الإجمالي *", numeric = true),
                FieldSpec("done", "الجلسات المنفذة", initial = "0", numeric = true),
                FieldSpec("nextDate", "تاريخ الجلسة القادمة YYYY-MM-DD"),
                FieldSpec("time", "وقت الجلسة (مثال 09:00)"),
                FieldSpec("notes", "ملاحظات", multi = true)
            ),
            onDismiss = { adding = false },
            onSave = { v ->
                val total = v.str("total").toIntOrNull() ?: 0
                if (v.str("type").isBlank() || total <= 0) return@FormDialog false
                val done = (v.str("done").toIntOrNull() ?: 0).coerceIn(0, total)
                val next = parseDate(v.str("nextDate")) ?: 0L
                val item = TherapySession(
                    type = v.str("type"), center = v.str("center"), total = total, done = done,
                    nextDateMillis = next, time = v.str("time"), notes = v.str("notes")
                )
                scope.launch {
                    val id = dao.insertSession(item)
                    Reminders.scheduleSession(context, item.copy(id = id))
                }
                true
            }
        )
    }
    ListWithFab(
        list = items,
        emptyText = "لا توجد جلسات مسجلة بعد.",
        addLabel = "جلسة جديدة",
        onAdd = { adding = true },
        key = { it.id }
    ) { s ->
        val remaining = (s.total - s.done).coerceAtLeast(0)
        ItemCard(typeColor("جلسة")) {
            Text(s.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InfoLine("المركز", s.center)
            Text("تم ${s.done} من ${s.total} - متبقي $remaining", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { if (s.total > 0) s.done.toFloat() / s.total else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            if (s.nextDateMillis > 0) Text("الجلسة القادمة: ${formatDate(s.nextDateMillis)} ${s.time}".trim(), style = MaterialTheme.typography.labelLarge)
            InfoLine("ملاحظات", s.notes)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    scope.launch { dao.updateSession(s.copy(done = if (s.done < s.total) s.done + 1 else s.done)) }
                }) { Text("تسجيل جلسة منفذة") }
                DeleteButton {
                    scope.launch {
                        dao.deleteSession(s)
                        Reminders.cancel(context, Reminders.sessionCode(s.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationsTab(dao: CareDao, context: Context, scope: CoroutineScope) {
    val items by dao.observeMedications().collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        FormDialog(
            title = "دواء جديد",
            specs = listOf(
                FieldSpec("name", "اسم الدواء *"),
                FieldSpec("dose", "الجرعة كما وصفها الطبيب"),
                FieldSpec("timesPerDay", "عدد مرات الاستخدام في اليوم", initial = "1", numeric = true),
                FieldSpec("firstTime", "أول موعد يومياً (مثال 08:00)"),
                FieldSpec("startDate", "تاريخ البداية YYYY-MM-DD"),
                FieldSpec("endDate", "تاريخ النهاية (اختياري)"),
                FieldSpec("duration", "مستمر أو مؤقت", initial = "مستمر"),
                FieldSpec("instructions", "تعليمات الطبيب", multi = true),
                FieldSpec("notes", "ملاحظات", multi = true)
            ),
            onDismiss = { adding = false },
            onSave = { v ->
                if (v.str("name").isBlank()) return@FormDialog false
                val item = Medication(
                    name = v.str("name"), dose = v.str("dose"),
                    timesPerDay = v.str("timesPerDay").toIntOrNull()?.coerceIn(1, 24) ?: 1,
                    firstTime = v.str("firstTime"),
                    startMillis = parseDate(v.str("startDate")) ?: 0L,
                    endMillis = parseDate(v.str("endDate")) ?: 0L,
                    continuous = v.str("duration") != "مؤقت",
                    instructions = v.str("instructions"), notes = v.str("notes")
                )
                scope.launch {
                    val id = dao.insertMedication(item)
                    val saved = item.copy(id = id)
                    if (saved.continuous || saved.endMillis >= System.currentTimeMillis()) Reminders.scheduleMedication(context, saved)
                }
                true
            }
        )
    }
    ListWithFab(
        list = items,
        emptyText = "لا توجد أدوية مسجلة. أضف الأدوية كما وصفها الطبيب.",
        addLabel = "دواء جديد",
        onAdd = { adding = true },
        key = { it.id }
    ) { m ->
        ItemCard(typeColor("دواء")) {
            Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InfoLine("الجرعة", m.dose)
            InfoLine("عدد المرات يومياً", m.timesPerDay.toString())
            InfoLine("أول موعد", m.firstTime)
            Text(if (m.continuous) "دواء مستمر" else "دواء مؤقت", style = MaterialTheme.typography.labelLarge)
            InfoLine("تعليمات الطبيب", m.instructions)
            InfoLine("ملاحظات", m.notes)
            FileAttachments(context, "medication_files_${m.id}", "مرفقات الدواء")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DeleteButton {
                    scope.launch {
                        dao.deleteMedication(m)
                        Reminders.cancel(context, Reminders.medicationCode(m.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun ComplaintsTab(dao: CareDao, scope: CoroutineScope) {
    val items by dao.observeComplaints().collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        FormDialog(
            title = "شكوى أو عرض جديد",
            specs = listOf(
                FieldSpec("title", "الشكوى أو العرض *"),
                FieldSpec("startDate", "تاريخ بداية المشكلة YYYY-MM-DD"),
                FieldSpec("severity", "شدة العرض من 0 إلى 10", numeric = true),
                FieldSpec("pattern", "مستمر أو متقطع", initial = "مستمر"),
                FieldSpec("description", "وصف الأعراض", multi = true),
                FieldSpec("notes", "ملاحظات", multi = true)
            ),
            onDismiss = { adding = false },
            onSave = { v ->
                if (v.str("title").isBlank()) return@FormDialog false
                val item = Complaint(
                    title = v.str("title"),
                    startMillis = parseDate(v.str("startDate")) ?: 0L,
                    description = v.str("description"),
                    severity = v.str("severity").toIntOrNull()?.coerceIn(0, 10) ?: 0,
                    continuous = v.str("pattern") != "متقطع",
                    notes = v.str("notes")
                )
                scope.launch { dao.insertComplaint(item) }
                true
            }
        )
    }
    ListWithFab(
        list = items,
        emptyText = "سجّل أي شكوى أو عرض حتى لا تنساه في الزيارة.",
        addLabel = "شكوى جديدة",
        onAdd = { adding = true },
        key = { it.id }
    ) { c ->
        ItemCard(typeColor("شكوى")) {
            Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (c.startMillis > 0) Text("بدأت: ${formatDate(c.startMillis)}", style = MaterialTheme.typography.labelLarge)
            Text("الشدة: ${c.severity}/10 - ${if (c.continuous) "مستمر" else "متقطع"}", style = MaterialTheme.typography.bodyMedium)
            InfoLine("الوصف", c.description)
            InfoLine("ملاحظات", c.notes)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { scope.launch { dao.updateComplaint(c.copy(wantDiscuss = !c.wantDiscuss)) } }) {
                    Text(if (c.wantDiscuss) "✓ للمناقشة في الزيارة" else "أريد مناقشتها")
                }
                TextButton(onClick = { scope.launch { dao.updateComplaint(c.copy(discussed = !c.discussed)) } }) {
                    Text(if (c.discussed) "✓ تمت المناقشة" else "علامة: تمت المناقشة")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DeleteButton { scope.launch { dao.deleteComplaint(c) } }
            }
        }
    }
}

@Composable
private fun QuestionsTab(dao: CareDao, scope: CoroutineScope) {
    val items by dao.observeQuestions().collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        FormDialog(
            title = "سؤال للدكتور",
            specs = listOf(FieldSpec("text", "اكتب السؤال *", multi = true)),
            onDismiss = { adding = false },
            onSave = { v ->
                if (v.str("text").isBlank()) return@FormDialog false
                scope.launch { dao.insertQuestion(Question(text = v.str("text"))) }
                true
            }
        )
    }
    ListWithFab(
        list = items,
        emptyText = "اكتب الأسئلة التي تريد طرحها على الدكتور.",
        addLabel = "سؤال جديد",
        onAdd = { adding = true },
        key = { it.id }
    ) { q ->
        ItemCard(typeColor("تحليل/أشعة")) {
            Text(q.text, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { scope.launch { dao.updateQuestion(q.copy(answered = !q.answered)) } }) {
                    Text(if (q.answered) "✓ تم الرد عليه" else "علامة: تم الرد عليه")
                }
                DeleteButton { scope.launch { dao.deleteQuestion(q) } }
            }
        }
    }
}

@Composable
private fun ProfileTab(dao: CareDao, context: Context, scope: CoroutineScope) {
    val profile by dao.observeProfile().collectAsState(initial = null)
    var editing by remember { mutableStateOf(false) }
    val p = profile ?: Profile()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ItemCard(typeColor("جلسة")) {
                    Text("بيانات المريض", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (profile == null) {
                        Text("لم يتم تسجيل بيانات بعد. اضغط تعديل لإضافتها.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        InfoLine("الاسم", p.name)
                        InfoLine("تاريخ الميلاد", p.birthDate)
                        InfoLine("النوع", p.gender)
                        InfoLine("الهاتف", p.phone)
                        InfoLine("فصيلة الدم", p.bloodType)
                        InfoLine("الحساسية", p.allergies)
                        InfoLine("الأمراض المزمنة", p.chronic)
                        InfoLine("ملاحظات طبية", p.notes)
                    }
                    Spacer(Modifier.height(8.dp))
                    FileAttachments(context, "profile_medical_files", "مرفقات الملف الطبي")
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Text("✎") },
            text = { Text("تعديل الملف") }
        )
    }
    if (editing) {
        FormDialog(
            title = "الملف الشخصي",
            specs = listOf(
                FieldSpec("name", "الاسم *", p.name),
                FieldSpec("birthDate", "تاريخ الميلاد YYYY-MM-DD", p.birthDate),
                FieldSpec("gender", "النوع", p.gender),
                FieldSpec("phone", "رقم الهاتف (اختياري)", p.phone),
                FieldSpec("bloodType", "فصيلة الدم (اختياري)", p.bloodType),
                FieldSpec("allergies", "الحساسية المعروفة (اختياري)", p.allergies, multi = true),
                FieldSpec("chronic", "الأمراض المزمنة (اختياري)", p.chronic, multi = true),
                FieldSpec("notes", "ملاحظات طبية إضافية", p.notes, multi = true)
            ),
            onDismiss = { editing = false },
            onSave = { v ->
                if (v.str("name").isBlank()) return@FormDialog false
                scope.launch {
                    dao.saveProfile(
                        Profile(
                            name = v.str("name"), birthDate = v.str("birthDate"), gender = v.str("gender"),
                            phone = v.str("phone"), bloodType = v.str("bloodType"), allergies = v.str("allergies"),
                            chronic = v.str("chronic"), notes = v.str("notes")
                        )
                    )
                }
                true
            }
        )
    }
}


@Composable
private fun FileAttachments(context: Context, storageKey: String, title: String) {
    val prefs = remember(storageKey) { context.getSharedPreferences("my_care_attachments", Context.MODE_PRIVATE) }
    val entries = remember(storageKey) {
        mutableStateListOf<String>().apply { addAll(prefs.getStringSet(storageKey, emptySet()).orEmpty().sorted()) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            val name = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else "ملف مرفق"
                } ?: "ملف مرفق"
            } catch (_: Exception) { "ملف مرفق" }
            val entry = uri.toString() + "|" + name.replace("|", " ")
            if (!entries.contains(entry)) {
                entries.add(entry)
                prefs.edit().putStringSet(storageKey, entries.toSet()).apply()
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("＋ رفع ملف أو صورة أو PDF") }
        entries.forEach { entry ->
            val parts = entry.split("|", limit = 2)
            val uriText = parts.firstOrNull().orEmpty()
            val fileName = parts.getOrNull(1) ?: "ملف مرفق"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    try {
                        val uri = Uri.parse(uriText)
                        val mime = context.contentResolver.getType(uri) ?: "*/*"
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(context, "تعذر فتح الملف", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.weight(1f)) { Text("📎 $fileName", textAlign = TextAlign.Start) }
                TextButton(onClick = {
                    entries.remove(entry)
                    prefs.edit().putStringSet(storageKey, entries.toSet()).apply()
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (entries.isEmpty()) Text("لا توجد ملفات مرفقة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
