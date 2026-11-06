package tr.hal.fiyatlari

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("hal", Context.MODE_PRIVATE)

    var notifEnabled: Boolean
        get() = sp.getBoolean("notif", false)
        set(v) = sp.edit().putBoolean("notif", v).apply()

    var notifHour: Int
        get() = sp.getInt("hour", 9)
        set(v) = sp.edit().putInt("hour", v).apply()

    var favorites: Set<String>
        get() = HashSet(sp.getStringSet("fav", emptySet()) ?: emptySet())
        set(v) = sp.edit().putStringSet("fav", HashSet(v)).apply()

    /** Bildirimde gösterilecek hal kimlikleri; null = tüm haller. */
    var notifHalls: Set<String>?
        get() = sp.getStringSet("halls", null)?.let { HashSet(it) }
        set(v) {
            if (v == null) sp.edit().remove("halls").apply()
            else sp.edit().putStringSet("halls", HashSet(v)).apply()
        }

    /** Aynı gün içinde favori ürünlerin fiyatı değişirse yeniden bildir. */
    var renotify: Boolean
        get() = sp.getBoolean("renotify", true)
        set(v) = sp.edit().putBoolean("renotify", v).apply()

    var smsEnabled: Boolean
        get() = sp.getBoolean("sms", false)
        set(v) = sp.edit().putBoolean("sms", v).apply()

    /** Virgülle ayrılmış telefon numaraları. */
    var smsNumbers: String
        get() = sp.getString("smsnums", "") ?: ""
        set(v) = sp.edit().putString("smsnums", v).apply()

    /** En son bildirilen favori fiyatların özeti (değişimi anlamak için). */
    var lastSignature: String
        get() = sp.getString("sig", "") ?: ""
        set(v) = sp.edit().putString("sig", v).apply()

    /** Son bildirim gönderilen veri tarihi (aynı günü tekrar bildirmemek için). */
    var lastNotified: String
        get() = sp.getString("last", "") ?: ""
        set(v) = sp.edit().putString("last", v).apply()
}
