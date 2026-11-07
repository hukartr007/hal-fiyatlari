package tr.hal.fiyatlari

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    var dates by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var day by remember { mutableStateOf<DayData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var loadingIdx by remember { mutableStateOf(true) }
    var loadingDay by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var favs by remember { mutableStateOf(prefs.favorites) }
    var showDate by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showProducts by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var hallOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    LaunchedEffect(reload) {
        loadingIdx = true
        try {
            val idx = withContext(Dispatchers.IO) { Repo.index(ctx) }
            dates = idx.dates
            hallOptions = idx.halls
            val cur = selected
            if (cur == null || cur !in idx.dates) selected = idx.dates.firstOrNull()
            err = null
        } catch (e: Exception) {
            err = "Bağlantı hatası: ${e.message}"
        }
        loadingIdx = false
    }

    LaunchedEffect(selected, reload) {
        val d = selected
        if (d == null) {
            day = null
            return@LaunchedEffect
        }
        loadingDay = true
        try {
            val latest = dates.firstOrNull()
            day = withContext(Dispatchers.IO) { Repo.day(ctx, d, d == latest) }
            err = null
        } catch (e: Exception) {
            day = null
            err = "Veri alınamadı: ${e.message}"
        }
        loadingDay = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hal Fiyatları") },
                actions = {
                    TextButton(onClick = { showDate = true }) {
                        Text(selected?.let { prettyDate(it) } ?: "Tarih")
                    }
                    TextButton(onClick = { showSettings = true }) { Text("Ayarlar") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val e = err
            val d = day
            when {
                loadingIdx || loadingDay -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                e != null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(e)
                    Button(onClick = { reload++ }) { Text("Tekrar dene") }
                }
                dates.isEmpty() -> Text(
                    "Henüz kayıtlı veri yok. Fiyat toplama görevi ilk kez çalıştığında burada görünecek.",
                    Modifier.padding(16.dp)
                )
                d == null || d.halls.isEmpty() -> Text("Bu tarih için hal verisi yok.", Modifier.padding(16.dp))
                else -> {
                    val idx = tab.coerceIn(0, d.halls.lastIndex)
                    ScrollableTabRow(selectedTabIndex = idx, edgePadding = 8.dp) {
                        d.halls.forEachIndexed { i, h ->
                            Tab(
                                selected = idx == i,
                                onClick = { tab = i },
                                text = { Text(h.name + if (hallWarn(h, d.date)) " ⚠" else "") }
                            )
                        }
                    }
                    HallList(
                        hall = d.halls[idx],
                        dataDate = d.date,
                        favs = favs,
                        onToggle = { name ->
                            val n = favs.toMutableSet()
                            if (!n.remove(name)) n.add(name)
                            favs = n
                            prefs.favorites = n
                        }
                    )
                }
            }
        }
    }

    if (showDate) {
        val available = remember(dates) { dates.map { LocalDate.parse(it).toEpochDay() }.toSet() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selected?.let { LocalDate.parse(it).toEpochDay() * DAY_MS },
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Math.floorDiv(utcTimeMillis, DAY_MS) in available

                override fun isSelectableYear(year: Int): Boolean = true
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        selected = LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS)).toString()
                    }
                    showDate = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("İptal") } }
        ) {
            DatePicker(state = state)
        }
    }

    if (showSettings) {
        var enabled by remember { mutableStateOf(prefs.notifEnabled) }
        var hour by remember { mutableIntStateOf(prefs.notifHour) }
        var renotify by remember { mutableStateOf(prefs.renotify) }
        var smsOn by remember { mutableStateOf(prefs.smsEnabled) }
        var smsNums by remember { mutableStateOf(prefs.smsNumbers) }
        var selHalls by remember {
            mutableStateOf(prefs.notifHalls ?: hallOptions.map { it.first }.toSet())
        }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Bildirimler") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Günlük bildirim", Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bildirim saati", Modifier.weight(1f))
                        TextButton(onClick = { hour = (hour + 23) % 24 }) { Text("−") }
                        Text("%02d:00".format(hour))
                        TextButton(onClick = { hour = (hour + 1) % 24 }) { Text("+") }
                    }
                    if (hallOptions.isNotEmpty()) {
                        Text("Bildirimde gösterilecek haller", fontWeight = FontWeight.SemiBold)
                        hallOptions.forEach { (id, name) ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selHalls = if (id in selHalls) selHalls - id else selHalls + id
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = id in selHalls,
                                    onCheckedChange = { on ->
                                        selHalls = if (on) selHalls + id else selHalls - id
                                    }
                                )
                                Text(name)
                            }
                        }
                    }
                    Text("Bildirimde gösterilecek ürünler", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (favs.isEmpty()) "Henüz ürün seçilmedi" else "${favs.size} ürün seçili",
                            Modifier.weight(1f)
                        )
                        TextButton(onClick = { showProducts = true }) { Text("Ürün seç") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fiyat değişince tekrar bildir", Modifier.weight(1f))
                        Switch(checked = renotify, onCheckedChange = { renotify = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SMS de gönder", Modifier.weight(1f))
                        Switch(checked = smsOn, onCheckedChange = { smsOn = it })
                    }
                    if (smsOn) {
                        OutlinedTextField(
                            value = smsNums,
                            onValueChange = { smsNums = it },
                            label = { Text("SMS numaraları (virgülle ayır)") },
                            placeholder = { Text("05xx xxx xx xx, 05xx xxx xx xx") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "SMS, telefonundaki hattan gönderilir ve operatör ücreti uygulanır. " +
                                "Yalnızca seçili (★) ürünlerin fiyatı gönderilir.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "Ürünleri \"Ürün seç\"ten ya da listedeki ☆ simgesinden seçebilirsin (ikisi aynı listedir); bildirimde seçili ürünlerin " +
                            "fiyatı gösterilir. Android arka plan işlerini tam saatinde çalıştırmayı " +
                            "garanti etmez, bildirim birkaç saat kayabilir.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.notifEnabled = enabled
                    prefs.notifHour = hour
                    prefs.renotify = renotify
                    prefs.smsEnabled = smsOn
                    prefs.smsNumbers = smsNums.trim()
                    prefs.lastSignature = ""
                    prefs.notifHalls =
                        if (hallOptions.isEmpty() || selHalls.containsAll(hallOptions.map { it.first })) null
                        else selHalls
                    prefs.lastNotified = ""
                    if (enabled || smsOn) {
                        val perms = mutableListOf<String>()
                        if (enabled && Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        if (smsOn) perms.add(Manifest.permission.SEND_SMS)
                        if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray())
                        Scheduler.enable(ctx, renotify)
                    } else {
                        Scheduler.disable(ctx)
                    }
                    showSettings = false
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("İptal") } }
        )
    }

    if (showProducts) {
        val names = remember(day, favs) {
            (day?.halls.orEmpty().flatMap { h -> h.items.map { it.name } } + favs)
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }
        ProductPicker(
            all = names,
            selected = favs,
            onDone = { sel ->
                favs = sel
                prefs.favorites = sel
                prefs.lastSignature = ""
                showProducts = false
            },
            onCancel = { showProducts = false }
        )
    }
}

/** Bildirim (ve SMS) için ürün seçme penceresi; liste, hal listesindeki ★ ile aynıdır. */
@Composable
fun ProductPicker(all: List<String>, selected: Set<String>, onDone: (Set<String>) -> Unit, onCancel: () -> Unit) {
    var sel by remember { mutableStateOf(selected) }
    var q by remember { mutableStateOf("") }
    val shown = remember(q, all) { all.filter { it.contains(q, ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Bildirimde gösterilecek ürünler") },
        text = {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    label = { Text("Ürün ara") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${sel.size} ürün seçili",
                    Modifier.padding(vertical = 6.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown) { name ->
                        val on = sel.any { it.equals(name, ignoreCase = true) }
                        val toggle = {
                            sel = if (on) sel.filter { !it.equals(name, ignoreCase = true) }.toSet() else sel + name
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { toggle() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = { toggle() })
                            Text(name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(sel) }) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("İptal") } }
    )
}

@Composable
fun HallList(hall: Hall, dataDate: String, favs: Set<String>, onToggle: (String) -> Unit) {
    var query by remember(hall.id) { mutableStateOf("") }
    val shown = remember(hall, query) { hall.items.filter { it.name.contains(query, ignoreCase = true) } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Ürün ara") },
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        if (hall.note.isNotEmpty()) {
            Text(
                hall.note,
                Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (hall.items.isNotEmpty()) {
            val sd = hall.sourceDate
            val age = sourceAgeDays(sd, dataDate)
            if (sd == null) {
                Text(
                    "⚠ Kaynak, fiyatların hangi güne ait olduğunu yazmıyor.",
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (age != null && age > 0) {
                Text(
                    "⚠ Bu fiyatlar ${prettyDate(sd)} tarihli ($age gün eski). Güncel değil.",
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    "Fiyat tarihi: ${prettyDate(sd)}",
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (hall.stale) {
            Text(
                hall.staleSince?.let { "⚠ Fiyatlar güncellenmedi: ${prettyDate(it)} kaydıyla aynı." }
                    ?: "⚠ Fiyatlar güncellenmedi: önceki kayıtla aynı.",
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (hall.items.isEmpty()) {
            Text("Bu hal için bu tarihte veri yok.", Modifier.padding(16.dp))
        }
        LazyColumn {
            items(shown) { item ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onToggle(item.name) }) {
                        Text(if (item.name in favs) "★" else "☆", fontSize = 20.sp)
                    }
                    Text(item.name, Modifier.weight(1f))
                    Text(
                        priceText(item),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = if (item.trend == 0) 12.dp else 4.dp)
                    )
                    if (item.trend != 0) {
                        Text(
                            if (item.trend > 0) "▲" else "▼",
                            color = if (item.trend > 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
