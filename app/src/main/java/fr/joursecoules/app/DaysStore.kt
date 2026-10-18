package fr.joursecoules.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Thèmes disponibles pour le widget. */
enum class WidgetTheme(val key: String) {
    CLASSIC("classic"),
    VECTORHEART("vectorheart");

    companion object {
        fun fromKey(key: String?): WidgetTheme = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

/** Une date suivie par le widget. Passée : jours écoulés. Future : jours restants. */
data class Counter(val date: LocalDate, val label: String)

/** Sauvegarde, pour chaque widget, sa liste de dates et son thème. */
object DaysStore {
    private const val PREFS = "jours_ecoules"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, widgetId: Int, counters: List<Counter>, theme: WidgetTheme) {
        val json = JSONArray()
        counters.forEach { json.put(JSONObject().put("date", it.date.toString()).put("label", it.label)) }
        prefs(context).edit()
            .putString("counters_$widgetId", json.toString())
            .putString("theme_$widgetId", theme.key)
            // Anciennes clés (version à date unique) : remplacées par la liste.
            .remove("date_$widgetId")
            .remove("label_$widgetId")
            .apply()
    }

    fun counters(context: Context, widgetId: Int): List<Counter> {
        val p = prefs(context)
        p.getString("counters_$widgetId", null)?.let { raw ->
            return runCatching {
                val json = JSONArray(raw)
                (0 until json.length()).mapNotNull { i ->
                    val o = json.getJSONObject(i)
                    runCatching { Counter(LocalDate.parse(o.getString("date")), o.optString("label")) }.getOrNull()
                }
            }.getOrDefault(emptyList())
        }
        // Migration depuis la version à date unique.
        val oldDate = p.getString("date_$widgetId", null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return emptyList()
        return listOf(Counter(oldDate, p.getString("label_$widgetId", "") ?: ""))
    }

    /** Date actuellement affichée par le widget (navigation avec les flèches). */
    fun currentIndex(context: Context, widgetId: Int): Int =
        prefs(context).getInt("index_$widgetId", 0)

    fun setCurrentIndex(context: Context, widgetId: Int, index: Int) {
        prefs(context).edit().putInt("index_$widgetId", index).apply()
    }

    fun theme(context: Context, widgetId: Int): WidgetTheme =
        WidgetTheme.fromKey(prefs(context).getString("theme_$widgetId", null))

    fun delete(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove("counters_$widgetId")
            .remove("theme_$widgetId")
            .remove("index_$widgetId")
            .remove("date_$widgetId")
            .remove("label_$widgetId")
            .apply()
    }
}
