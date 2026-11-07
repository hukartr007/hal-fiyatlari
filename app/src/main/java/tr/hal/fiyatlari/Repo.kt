package tr.hal.fiyatlari

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Fiyat JSON dosyalarını (data/index.json, data/YYYY-MM-DD.json) indirir ve önbelleğe alır. */
object Repo {

    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** preferCache=true ise (geçmiş günler değişmez) önce diskteki kopya kullanılır. */
    private fun text(ctx: Context, name: String, preferCache: Boolean): String {
        val f = File(ctx.filesDir, "cache_$name")
        if (preferCache && f.exists()) return f.readText(Charsets.UTF_8)
        return try {
            val s = download("${BuildConfig.DATA_BASE_URL}/$name")
            f.writeText(s, Charsets.UTF_8)
            s
        } catch (e: Exception) {
            if (f.exists()) f.readText(Charsets.UTF_8) else throw e
        }
    }

    fun index(ctx: Context): Index {
        val o = JSONObject(text(ctx, "index.json", preferCache = false))
        val a = o.optJSONArray("dates")
        val list = mutableListOf<String>()
        if (a != null) for (i in 0 until a.length()) list.add(a.getString(i))
        val hl = mutableListOf<Pair<String, String>>()
        val hArr = o.optJSONArray("halls")
        if (hArr != null) {
            for (i in 0 until hArr.length()) {
                val h = hArr.getJSONObject(i)
                hl.add(h.getString("id") to h.optString("name", h.getString("id")))
            }
        }
        return Index(list.sortedDescending(), hl)
    }

    fun day(ctx: Context, date: String, isLatest: Boolean): DayData {
        val o = JSONObject(text(ctx, "$date.json", preferCache = !isLatest))
        val halls = mutableListOf<Hall>()
        val ha = o.optJSONArray("halls")
        if (ha != null) {
            for (i in 0 until ha.length()) {
                val h = ha.getJSONObject(i)
                val items = mutableListOf<HalItem>()
                val ia = h.optJSONArray("items")
                if (ia != null) {
                    for (j in 0 until ia.length()) {
                        val it = ia.getJSONObject(j)
                        items.add(
                            HalItem(
                                name = it.getString("name"),
                                unit = it.optString("unit", "kg"),
                                min = if (it.isNull("min")) null else it.getDouble("min"),
                                max = if (it.isNull("max")) null else it.getDouble("max"),
                                trend = when (it.optString("trend", "")) {
                                    "up" -> 1
                                    "down" -> -1
                                    else -> 0
                                },
                            )
                        )
                    }
                }
                halls.add(
                    Hall(
                        id = h.getString("id"),
                        name = h.optString("name", h.getString("id")),
                        source = h.optString("source", ""),
                        note = h.optString("note", ""),
                        items = items,
                        stale = h.optBoolean("stale", false),
                        staleSince = if (h.has("stale_since") && !h.isNull("stale_since")) {
                            h.getString("stale_since")
                        } else {
                            null
                        },
                        sourceDate = if (h.has("source_date") && !h.isNull("source_date")) {
                            h.getString("source_date")
                        } else {
                            null
                        },
                    )
                )
            }
        }
        return DayData(o.optString("date", date), halls)
    }
}
