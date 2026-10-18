package fr.joursecoules.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class DaysWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
        scheduleMidnightRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MIDNIGHT,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { DaysStore.delete(context, it) }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(midnightIntent(context))
    }

    companion object {
        private const val ACTION_MIDNIGHT = "fr.joursecoules.app.ACTION_MIDNIGHT"
        private val VH_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DaysWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
            if (ids.isNotEmpty()) scheduleMidnightRefresh(context)
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = buildViews(
                context,
                DaysStore.date(context, widgetId),
                DaysStore.label(context, widgetId),
                DaysStore.theme(context, widgetId)
            )

            // Toucher le widget ouvre l'écran de réglage (date, nom, thème).
            val configIntent = Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pending = PendingIntent.getActivity(
                context,
                widgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            manager.updateAppWidget(widgetId, views)
        }

        /** Construit l'affichage du widget. Sert aussi pour l'aperçu de l'écran de réglage. */
        fun buildViews(
            context: Context,
            date: LocalDate?,
            label: String,
            theme: WidgetTheme,
            today: LocalDate = LocalDate.now()
        ): RemoteViews = when (theme) {
            WidgetTheme.CLASSIC -> buildClassic(context, date, label, today)
            WidgetTheme.VECTORHEART -> buildVectorheart(context, date, label, today)
        }

        private fun buildClassic(context: Context, date: LocalDate?, label: String, today: LocalDate): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_days)
            val res = context.resources

            if (date == null) {
                views.setViewVisibility(R.id.days_label, View.GONE)
                views.setTextViewText(R.id.days_number, "?")
                views.setTextViewText(R.id.days_unit, res.getString(R.string.widget_not_set))
                views.setTextViewText(R.id.days_since, res.getString(R.string.widget_tap_to_setup))
                return views
            }

            val days = ChronoUnit.DAYS.between(date, today)
            val count = abs(days)
            val dateText = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))

            views.setTextViewText(R.id.days_number, NumberFormat.getIntegerInstance().format(count))
            if (days >= 0) {
                views.setTextViewText(R.id.days_unit, res.getQuantityString(R.plurals.widget_unit_past, count.toInt()))
                views.setTextViewText(R.id.days_since, res.getString(R.string.widget_since, dateText))
            } else {
                views.setTextViewText(R.id.days_unit, res.getQuantityString(R.plurals.widget_unit_future, count.toInt()))
                views.setTextViewText(R.id.days_since, res.getString(R.string.widget_until, dateText))
            }

            if (label.isBlank()) {
                views.setViewVisibility(R.id.days_label, View.GONE)
            } else {
                views.setViewVisibility(R.id.days_label, View.VISIBLE)
                views.setTextViewText(R.id.days_label, label)
            }
            return views
        }

        private fun buildVectorheart(context: Context, date: LocalDate?, label: String, today: LocalDate): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_vectorheart)
            val res = context.resources

            views.setTextViewText(
                R.id.vh_label,
                label.ifBlank { res.getString(R.string.vh_default_label) }.uppercase()
            )

            if (date == null) {
                views.setTextViewText(R.id.vh_sign, "T?")
                views.setTextViewText(R.id.days_number, "--")
                views.setTextViewText(R.id.days_unit, res.getString(R.string.widget_not_set).uppercase())
                views.setTextViewText(R.id.days_since, res.getString(R.string.widget_tap_to_setup).uppercase())
                return views
            }

            val days = ChronoUnit.DAYS.between(date, today)
            val count = abs(days)
            val dateText = date.format(VH_DATE)

            views.setTextViewText(R.id.days_number, NumberFormat.getIntegerInstance().format(count))
            if (days >= 0) {
                views.setTextViewText(R.id.vh_sign, "T+")
                views.setTextViewText(
                    R.id.days_unit,
                    res.getQuantityString(R.plurals.widget_unit_past, count.toInt()).uppercase()
                )
                views.setTextViewText(R.id.days_since, res.getString(R.string.vh_since, dateText))
            } else {
                views.setTextViewText(R.id.vh_sign, "T−")
                views.setTextViewText(
                    R.id.days_unit,
                    res.getQuantityString(R.plurals.widget_unit_future, count.toInt()).uppercase()
                )
                views.setTextViewText(R.id.days_since, res.getString(R.string.vh_until, dateText))
            }
            return views
        }

        /** Programme un rafraîchissement juste après minuit pour que le compteur change de jour. */
        fun scheduleMidnightRefresh(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val nextMidnight = LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .plusMinutes(1)
                .toInstant()
                .toEpochMilli()
            alarmManager.set(AlarmManager.RTC, nextMidnight, midnightIntent(context))
        }

        private fun midnightIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, DaysWidgetProvider::class.java).setAction(ACTION_MIDNIGHT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
