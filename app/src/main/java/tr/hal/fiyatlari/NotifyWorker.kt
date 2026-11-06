package tr.hal.fiyatlari

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Düzenli uyanır (tekrar bildirim açıksa 30 dk, değilse 3 saatte bir). Ayarlanan saat geçtiyse:
 *  - günün ilk verisi geldiyse, veya
 *  - "fiyat değişince tekrar bildir" açıkken favori ürünlerin fiyatı değiştiyse
 * bildirim (ve açıksa SMS) gönderir.
 * Android arka plan işlerini tam saatinde çalıştırmayı garanti etmez; birkaç dakika-saat kayabilir.
 */
class NotifyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.notifEnabled && !prefs.smsEnabled) return Result.success()
        if (LocalTime.now().hour < prefs.notifHour) return Result.success()

        return try {
            withContext(Dispatchers.IO) {
                val idx = Repo.index(applicationContext)
                val latest = idx.dates.firstOrNull()
                if (latest != null) {
                    val day = Repo.day(applicationContext, latest, true)
                    val sig = favLines(prefs, day, false).joinToString("|")
                    val firstOfDay = latest != prefs.lastNotified
                    val changed = prefs.renotify && sig.isNotEmpty() && sig != prefs.lastSignature
                    if (firstOfDay || changed) {
                        var sent = false
                        if (prefs.notifEnabled && showNotification(applicationContext, prefs, day, !firstOfDay)) {
                            sent = true
                        }
                        if (prefs.smsEnabled && sendSms(applicationContext, prefs, day)) {
                            sent = true
                        }
                        if (sent) {
                            prefs.lastNotified = latest
                            prefs.lastSignature = sig
                        }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** Seçili hallerdeki favori ürünlerin fiyat satırları. */
    private fun favLines(prefs: Prefs, day: DayData, markStale: Boolean): List<String> {
        val selected = prefs.notifHalls
        val halls = day.halls.filter { selected == null || it.id in selected }
        val out = mutableListOf<String>()
        for (fav in prefs.favorites.sorted()) {
            val parts = halls.mapNotNull { h ->
                h.items.firstOrNull { it.name.equals(fav, ignoreCase = true) }
                    ?.let { "${h.name} ${priceText(it)}" + if (markStale && h.stale) " (değişmedi)" else "" }
            }
            if (parts.isNotEmpty()) out.add("$fav: ${parts.joinToString(", ")}")
        }
        return out
    }

    private fun showNotification(ctx: Context, prefs: Prefs, day: DayData, changedToday: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Günlük hal fiyatları", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val lines = favLines(prefs, day, true)
        val title = if (changedToday) {
            "Hal fiyatı değişti (${prettyDate(day.date)})"
        } else {
            "Hal fiyatları güncellendi (${prettyDate(day.date)})"
        }
        val text = if (lines.isEmpty()) {
            "Favori (★) eklediğin ürünlerin fiyatı burada görünür."
        } else {
            lines.joinToString("\n")
        }

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1, n)
        return true
    }

    @Suppress("DEPRECATION")
    private fun smsManager(ctx: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    /** Telefondaki hattan, ayarlarda girilen numaralara favori fiyatlarını SMS olarak yollar. */
    private fun sendSms(ctx: Context, prefs: Prefs, day: DayData): Boolean {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        val lines = favLines(prefs, day, false)
        if (lines.isEmpty()) return false

        val numberRegex = Regex("^\\+?[0-9]{10,13}$")
        val numbers = prefs.smsNumbers.split(',', ';', '\n')
            .map { s -> s.filter { it.isDigit() || it == '+' } }
            .filter { numberRegex.matches(it) }
            .distinct()
            .take(5)
        if (numbers.isEmpty()) return false

        val date = try {
            LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("dd.MM"))
        } catch (e: Exception) {
            day.date
        }
        val text = asciiTr("Hal fiyatlari $date\n" + lines.joinToString("\n"))

        val sms = smsManager(ctx)
        var ok = false
        for (n in numbers) {
            try {
                sms.sendMultipartTextMessage(n, null, sms.divideMessage(text), null, null)
                ok = true
            } catch (e: Exception) {
                // bu numara atlanır
            }
        }
        return ok
    }

    /** Türkçe karakterleri ve ₺'yi sadeleştirir; SMS kısa (160 karakterlik) parçalarda kalır, ücret düşer. */
    private fun asciiTr(s: String): String = s
        .replace("₺", "TL").replace("–", "-")
        .replace('ç', 'c').replace('Ç', 'C').replace('ğ', 'g').replace('Ğ', 'G')
        .replace('ı', 'i').replace('İ', 'I').replace('ö', 'o').replace('Ö', 'O')
        .replace('ş', 's').replace('Ş', 'S').replace('ü', 'u').replace('Ü', 'U')

    companion object {
        private const val CHANNEL = "hal_gunluk"
    }
}

object Scheduler {
    private const val NAME = "hal-notify"

    /** frequent=true: 30 dakikada bir kontrol (fiyat değişimi için); false: 3 saatte bir. */
    fun enable(ctx: Context, frequent: Boolean) {
        val minutes = if (frequent) 30L else 180L
        val req = PeriodicWorkRequestBuilder<NotifyWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun disable(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
    }
}
