package tr.hal.fiyatlari

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
        else -> "—"
    }
}

fun prettyDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEE", trLocale))
} catch (e: Exception) {
    iso
}
