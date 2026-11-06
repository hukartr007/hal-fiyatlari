package tr.hal.fiyatlari

data class HalItem(val name: String, val unit: String, val min: Double?, val max: Double?)

data class Hall(
    val id: String,
    val name: String,
    val source: String,
    val note: String,
    val items: List<HalItem>,
    /** true: fiyatlar önceki kayıtla birebir aynı, kaynak güncellenmemiş olabilir. */
    val stale: Boolean = false,
)

data class DayData(val date: String, val halls: List<Hall>)

/** halls: (id, ad) çiftleri */
data class Index(val dates: List<String>, val halls: List<Pair<String, String>> = emptyList())
