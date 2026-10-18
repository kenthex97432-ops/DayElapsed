package fr.joursecoules.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
        private val VH_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DaysWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
            if (ids.isNotEmpty()) scheduleMidnightRefresh(context)
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val widthDp = manager.getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val views = buildViews(
                context,
                DaysStore.date(context, widgetId),
                DaysStore.label(context, widgetId),
                DaysStore.theme(context, widgetId),
                widthDp = widthDp
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
            today: LocalDate = LocalDate.now(),
            widthDp: Int = 0
        ): RemoteViews = when (theme) {
            WidgetTheme.CLASSIC -> buildClassic(context, date, label, today)
            WidgetTheme.VECTORHEART -> buildVectorheart(context, date, label, today, widthDp)
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

        /** Largeur (dp) en dessous de laquelle le panneau de droite est masqué pour laisser la place au nombre. */
        private const val VH_PANEL_MIN_WIDTH_DP = 135

        /** Paliers affichés par la jauge du thème Vectorheart. */
        private val MILESTONES = longArrayOf(
            7, 14, 30, 50, 100, 150, 200, 250, 365, 500, 730, 1000, 1500, 2000, 2500, 3000
        )

        private fun buildVectorheart(
            context: Context,
            date: LocalDate?,
            label: String,
            today: LocalDate,
            widthDp: Int
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_vectorheart)
            val res = context.resources

            views.setTextViewText(
                R.id.vh_label,
                label.ifBlank { res.getString(R.string.vh_default_label) }.uppercase()
            )

            // 0 = taille inconnue (aperçu, ancien lanceur) : on affiche le panneau.
            val showPanel = date != null && (widthDp == 0 || widthDp >= VH_PANEL_MIN_WIDTH_DP)
            views.setViewVisibility(R.id.vh_panel, if (showPanel) View.VISIBLE else View.GONE)
            // Widget étroit : on retire graduations et date pour laisser la place au nombre.
            val compact = widthDp in 1 until VH_PANEL_MIN_WIDTH_DP
            views.setViewVisibility(R.id.vh_ruler, if (compact) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.vh_footer, if (compact) View.GONE else View.VISIBLE)

            if (date == null) {
                views.setTextViewText(R.id.vh_sign, "T?")
                views.setTextViewText(R.id.days_number, "--")
                views.setTextViewText(R.id.days_unit, res.getString(R.string.widget_not_set).uppercase())
                views.setTextViewText(R.id.days_since, res.getString(R.string.widget_tap_to_setup).uppercase())
                return views
            }

            val days = ChronoUnit.DAYS.between(date, today)
            val count = abs(days)
            val past = days >= 0
            val dateText = date.format(VH_DATE)

            views.setTextViewText(R.id.days_number, NumberFormat.getIntegerInstance().format(count))
            views.setTextViewText(R.id.vh_sign, if (past) "T+" else "T−")
            views.setTextViewText(
                R.id.days_unit,
                res.getQuantityString(
                    if (past) R.plurals.widget_unit_past else R.plurals.widget_unit_future,
                    count.toInt()
                ).uppercase()
            )
            views.setTextViewText(
                R.id.days_since,
                res.getString(if (past) R.string.vh_since else R.string.vh_until, dateText)
            )

            if (showPanel) {
                val (target, progress) = milestone(count, past)
                views.setTextViewText(R.id.vh_next, "→" + NumberFormat.getIntegerInstance().format(target))
                views.setImageViewBitmap(R.id.vh_gauge, gaugeBitmap(context, progress))

                val months = abs(ChronoUnit.MONTHS.between(date, today))
                val years = count / 365.25
                val yearsText = if (years < 10) String.format("%.1f", years) else years.toLong().toString()
                val dim = context.getColor(R.color.vh_text_dim)
                views.setTextViewText(R.id.vh_weeks, readout("SEM ", NumberFormat.getIntegerInstance().format(count / 7), dim))
                views.setTextViewText(R.id.vh_months, readout("MOIS", NumberFormat.getIntegerInstance().format(months), dim))
                views.setTextViewText(R.id.vh_years, readout("ANS ", yearsText, dim))
            }
            return views
        }

        /** Plus petit palier strictement au-dessus de n (au-delà de 3000 : tous les 1000). */
        private fun milestoneAbove(n: Long): Long =
            MILESTONES.firstOrNull { it > n } ?: ((n / 1000) + 1) * 1000

        /** Plus grand palier strictement en dessous de n, ou 0. */
        private fun milestoneBelow(n: Long): Long =
            if (n > MILESTONES.last()) ((n - 1) / 1000) * 1000
            else MILESTONES.lastOrNull { it < n } ?: 0L

        /**
         * Prochain palier et progression (0..1) vers lui.
         * Date passée : palier suivant au-dessus. Compte à rebours : palier suivant en dessous.
         */
        internal fun milestone(count: Long, past: Boolean): Pair<Long, Float> {
            val target: Long
            val from: Long
            if (past) {
                target = milestoneAbove(count)
                from = milestoneBelow(count + 1)
            } else {
                target = milestoneBelow(count)
                from = milestoneAbove(count - 1)
            }
            val progress = abs(count - from).toFloat() / abs(target - from).coerceAtLeast(1)
            return target to progress.coerceIn(0f, 1f)
        }

        private fun readout(label: String, value: String, dimColor: Int): CharSequence {
            val sb = SpannableStringBuilder(label)
            sb.setSpan(ForegroundColorSpan(dimColor), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(' ').append(value)
            return sb
        }

        /** Jauge en segments inclinés à 45°, dessinée à la densité de l'écran. */
        private fun gaugeBitmap(context: Context, progress: Float): Bitmap {
            val res = context.resources
            val w = res.getDimensionPixelSize(R.dimen.vh_gauge_width)
            val h = res.getDimensionPixelSize(R.dimen.vh_gauge_height)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.vh_accent) }
            val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.vh_line) }

            val segments = 8
            val slant = h.toFloat()
            val gap = h * 0.45f
            val segW = (w - slant - gap * (segments - 1)) / segments
            val filled = (progress * segments).let { if (progress > 0f && it < 1f) 1 else it.toInt() }
            for (i in 0 until segments) {
                val x = i * (segW + gap)
                val path = Path().apply {
                    moveTo(x + slant, 0f)
                    lineTo(x + slant + segW, 0f)
                    lineTo(x + segW, h.toFloat())
                    lineTo(x, h.toFloat())
                    close()
                }
                canvas.drawPath(path, if (i < filled) on else off)
            }
            return bmp
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
