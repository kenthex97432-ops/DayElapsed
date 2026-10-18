package fr.joursecoules.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

/**
 * Widget « Jours écoulés ».
 * - Une seule date : la carte est affichée directement.
 * - Plusieurs dates : pile de cartes (StackView) qu'on fait défiler d'un geste vers le haut ou le bas.
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
        private const val ACTION_OPEN_SETTINGS = "fr.joursecoules.app.OPEN_SETTINGS"
        private const val ACTION_EDIT_CARD = "fr.joursecoules.app.EDIT_CARD"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DaysWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
            if (ids.isNotEmpty()) scheduleMidnightRefresh(context)
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val counters = DaysStore.counters(context, widgetId)
            val theme = DaysStore.theme(context, widgetId)

            if (counters.size <= 1) {
                val options = manager.getAppWidgetOptions(widgetId)
                val views = CardRenderer.build(
                    context, counters.firstOrNull(), theme,
                    widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
                    heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                )
                views.setOnClickPendingIntent(R.id.widget_root, settingsIntent(context, widgetId))
                manager.updateAppWidget(widgetId, views)
                return
            }

            val views = RemoteViews(context.packageName, R.layout.widget_stack)
            val serviceIntent = Intent(context, CardStackService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // Une URI propre à chaque widget, sinon Android réutilise la même liste pour tous.
            serviceIntent.data = Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.card_stack, serviceIntent)
            views.setEmptyView(R.id.card_stack, R.id.card_stack_empty)

            // Toucher une carte ouvre l'écran de réglage sur cette date (index ajouté par la carte).
            val template = Intent(context, WidgetConfigActivity::class.java)
                .setAction(ACTION_EDIT_CARD)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            views.setPendingIntentTemplate(
                R.id.card_stack,
                PendingIntent.getActivity(context, widgetId, template, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
            )
            views.setOnClickPendingIntent(R.id.card_stack_empty, settingsIntent(context, widgetId))

            manager.updateAppWidget(widgetId, views)
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.card_stack)
        }

        private fun settingsIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, WidgetConfigActivity::class.java)
                .setAction(ACTION_OPEN_SETTINGS)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
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
