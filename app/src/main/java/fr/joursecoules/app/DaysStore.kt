package fr.joursecoules.app

import android.content.Context
import java.time.LocalDate

/** Sauvegarde la date et le nom de chaque widget (un widget = un compteur). */
object DaysStore {
    private const val PREFS = "jours_ecoules"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, widgetId: Int, date: LocalDate, label: String) {
        prefs(context).edit()
            .putString("date_$widgetId", date.toString())
            .putString("label_$widgetId", label)
            .apply()
    }

    fun date(context: Context, widgetId: Int): LocalDate? =
        prefs(context).getString("date_$widgetId", null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun label(context: Context, widgetId: Int): String =
        prefs(context).getString("label_$widgetId", "") ?: ""

    fun delete(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove("date_$widgetId")
            .remove("label_$widgetId")
            .apply()
    }
}
