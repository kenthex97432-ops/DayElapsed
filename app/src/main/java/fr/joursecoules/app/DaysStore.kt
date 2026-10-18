package fr.joursecoules.app

import android.content.Context
import java.time.LocalDate

/** Thèmes disponibles pour le widget. */
enum class WidgetTheme(val key: String) {
    CLASSIC("classic"),
    VECTORHEART("vectorheart");

    companion object {
        fun fromKey(key: String?): WidgetTheme = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

/** Sauvegarde la date, le nom et le thème de chaque widget (un widget = un compteur). */
object DaysStore {
    private const val PREFS = "jours_ecoules"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, widgetId: Int, date: LocalDate, label: String, theme: WidgetTheme) {
        prefs(context).edit()
            .putString("date_$widgetId", date.toString())
            .putString("label_$widgetId", label)
            .putString("theme_$widgetId", theme.key)
            .apply()
    }

    fun date(context: Context, widgetId: Int): LocalDate? =
        prefs(context).getString("date_$widgetId", null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun label(context: Context, widgetId: Int): String =
        prefs(context).getString("label_$widgetId", "") ?: ""

    fun theme(context: Context, widgetId: Int): WidgetTheme =
        WidgetTheme.fromKey(prefs(context).getString("theme_$widgetId", null))

    fun delete(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove("date_$widgetId")
            .remove("label_$widgetId")
            .remove("theme_$widgetId")
            .apply()
    }
}
