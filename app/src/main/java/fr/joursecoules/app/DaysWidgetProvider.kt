package fr.joursecoules.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

/**
 * Widget « Jours écoulés ».
 * - Une seule date : la carte occupe tout le widget.
 * - Plusieurs dates : une flèche de chaque côté pour passer à la date précédente / suivante.
 */
class DaysWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
        scheduleMidnightRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Le widget a été redimensionné : on adapte l'affichage.
        updateWidget(context, manager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MIDNIGHT,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)

            ACTION_PREV, ACTION_NEXT -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val total = DaysStore.counters(context, widgetId).size
                if (total < 2) return
                val step = if (intent.action == ACTION_NEXT) 1 else -1
                val index = Math.floorMod(DaysStore.currentIndex(context, widgetId) + step, total)
                DaysStore.setCurrentIndex(context, widgetId, index)
                updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
            }
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
        private const val ACTION_PREV = "fr.joursecoules.app.ACTION_PREV"
        private const val ACTION_NEXT = "fr.joursecoules.app.ACTION_NEXT"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DaysWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
            if (ids.isNotEmpty()) scheduleMidnightRefresh(context)
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val counters = DaysStore.counters(context, widgetId)
            val index = DaysStore.currentIndex(context, widgetId).coerceIn(0, (counters.size - 1).coerceAtLeast(0))
            val options = manager.getAppWidgetOptions(widgetId)
            val views = buildWidget(
                context,
                counters,
                DaysStore.theme(context, widgetId),
                index,
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
                widgetId
            )
            manager.updateAppWidget(widgetId, views)
        }

        /**
         * Construit le widget complet. [widgetId] null : aperçu sans actions (tests).
         */
        fun buildWidget(
            context: Context,
            counters: List<Counter>,
            theme: WidgetTheme,
            index: Int,
            widthDp: Int,
            heightDp: Int,
            widgetId: Int? = null,
            today: LocalDate = LocalDate.now()
        ): RemoteViews {
            val paged = counters.size > 1
            val res = context.resources
            val density = res.displayMetrics.density
            val arrowsDp = if (paged) (2 * res.getDimension(R.dimen.arrow_column) / density).toInt() else 0
            // Avec les flèches, les coins de la carte ne sont plus aux coins du widget :
            // la marge latérale anti-arrondi devient inutile, on la rend à la carte.
            val insetDp = (res.getDimension(R.dimen.vh_safe_inset) / density).toInt()
            val reclaimed = if (paged && theme == WidgetTheme.VECTORHEART) 2 * insetDp else 0
            val cardWidth = if (widthDp > 0) widthDp - arrowsDp + reclaimed else 0

            val card = CardRenderer.build(
                context, counters.getOrNull(index), theme, today,
                cardWidth, index, counters.size, heightDp
            )
            // Toucher la carte ouvre les réglages sur cette date.
            widgetId?.let { card.setOnClickPendingIntent(R.id.widget_root, editIntent(context, it, index)) }
            if (!paged) return card
            if (theme == WidgetTheme.VECTORHEART) {
                val inset = res.getDimensionPixelSize(R.dimen.vh_safe_inset)
                card.setViewPadding(R.id.widget_root, 0, inset, 0, inset)
            }

            val pager = RemoteViews(context.packageName, R.layout.widget_pager)
            pager.removeAllViews(R.id.card_container)
            pager.addView(R.id.card_container, card)

            val vh = theme == WidgetTheme.VECTORHEART
            val bg = if (vh) R.drawable.arrow_bg_vh else R.drawable.arrow_bg_classic
            pager.setImageViewResource(R.id.arrow_prev_icon, if (vh) R.drawable.arrow_left_vh else R.drawable.arrow_left_classic)
            pager.setImageViewResource(R.id.arrow_next_icon, if (vh) R.drawable.arrow_right_vh else R.drawable.arrow_right_classic)
            pager.setInt(R.id.arrow_prev_icon, "setBackgroundResource", bg)
            pager.setInt(R.id.arrow_next_icon, "setBackgroundResource", bg)

            widgetId?.let {
                pager.setOnClickPendingIntent(R.id.arrow_prev, navIntent(context, it, ACTION_PREV))
                pager.setOnClickPendingIntent(R.id.arrow_next, navIntent(context, it, ACTION_NEXT))
            }
            return pager
        }

        private fun navIntent(context: Context, widgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, DaysWidgetProvider::class.java)
                .setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            val code = widgetId * 2 + if (action == ACTION_NEXT) 1 else 0
            return PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun editIntent(context: Context, widgetId: Int, index: Int): PendingIntent {
            val intent = Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(WidgetConfigActivity.EXTRA_EDIT_INDEX, index)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            return PendingIntent.getActivity(
                context, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
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
