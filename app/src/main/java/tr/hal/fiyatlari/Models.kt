package tr.hal.fiyatlari

/** trend: 1 = önceki kayda göre arttı, -1 = düştü, 0 = değişmedi / bilinmiyor. */
data class HalItem(val name: String, val unit: String, val min: Double?, val max: Double?, val trend: Int = 0)

data class Hall(
    val id: String,
    val name: String,
    val source: String,
    val note: String,
    val items: List<HalItem>,
    /** true: fiyatlar önceki kayıtla birebir aynı, kaynak güncellenmemiş olabilir. */
    val stale: Boolean = false,
    /** Kaynak sayfanın fiyatlar için yazdığı tarih (YYYY-MM-DD); sayfa yazmıyorsa null. */
    val sourceDate: String? = null,
)

data class DayData(val date: String, val halls: List<Hall>)

/** halls: (id, ad) çiftleri */
data class Index(val dates: List<String>, val halls: List<Pair<String, String>> = emptyList())
