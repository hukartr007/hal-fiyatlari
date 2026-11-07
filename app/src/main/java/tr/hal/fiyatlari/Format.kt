package tr.hal.fiyatlari

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val trLocale = Locale("tr", "TR")

fun num(d: Double): String =
    DecimalFormat("0.##", DecimalFormatSymbols(trLocale)).format(d)

fun priceText(i: HalItem): String {
    val lo = i.min
    val hi = i.max
    return when {
        lo != null && hi != null && lo != hi -> "${num(lo)} – ${num(hi)} ₺/${i.unit}"
        lo != null -> "${num(lo)} ₺/${i.unit}"
        hi != null -> "${num(hi)} ₺/${i.unit}"
        else -> "Pazarlık"
    }
}

fun trendMark(i: HalItem): String = when (i.trend) {
    1 -> " ▲"
    -1 -> " ▼"
    else -> ""
}

fun prettyDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEE", trLocale))
} catch (e: Exception) {
    iso
}

fun shortDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", trLocale))
} catch (e: Exception) {
    iso
}

/** Kaynaktaki fiyat tarihi, verinin derlendiği günden kaç gün eski? Tarih yoksa null. */
fun sourceAgeDays(sourceDate: String?, dataDate: String): Long? = try {
    if (sourceDate == null) null else ChronoUnit.DAYS.between(LocalDate.parse(sourceDate), LocalDate.parse(dataDate))
} catch (e: Exception) {
    null
}

/** Fiyatı olan halin fiyatı güncellenmemişse, tarihi eskiyse ya da kaynak tarih yazmıyorsa true (sekmede ⚠ gösterilir). */
fun hallWarn(h: Hall, dataDate: String): Boolean {
    if (h.items.isEmpty()) return false
    val age = sourceAgeDays(h.sourceDate, dataDate)
    return h.stale || h.sourceDate == null || (age != null && age > 0)
}
