package fr.joursecoules.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Dessine une carte (une date) dans le thème choisi. Sert au widget et aux aperçus. */
object CardRenderer {

    private val VH_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Largeur (dp) en dessous de laquelle le panneau de droite est masqué pour laisser la place au nombre. */
    private const val VH_PANEL_MIN_WIDTH_DP = 120

    /** Hauteur (dp) en dessous de laquelle la ligne « ANS » du panneau est retirée. */
    private const val VH_YEARS_MIN_HEIGHT_DP = 150

    /** Paliers affichés par la jauge du thème Vectorheart. */
    private val MILESTONES = longArrayOf(
        7, 14, 30, 50, 100, 150, 200, 250, 365, 500, 730, 1000, 1500, 2000, 2500, 3000
    )

    /**
     * @param index position de la carte (0..total-1), pour l'indicateur « 2/5 »
     * @param widthDp largeur de la carte, 0 si inconnue
     * @param heightDp hauteur de la carte, 0 si inconnue
     */
    fun build(
        context: Context,
        counter: Counter?,
        theme: WidgetTheme,
        today: LocalDate = LocalDate.now(),
        widthDp: Int = 0,
        index: Int = 0,
        total: Int = 1,
        heightDp: Int = 0
    ): RemoteViews = when (theme) {
        WidgetTheme.CLASSIC -> buildClassic(context, counter, today, index, total)
        WidgetTheme.VECTORHEART -> buildVectorheart(context, counter, today, widthDp, heightDp, index, total)
    }

    private fun unitText(context: Context, days: Long, count: Long): String = when {
        days == 0L -> context.getString(R.string.unit_today)
        days > 0 -> context.resources.getQuantityString(R.plurals.widget_unit_past, count.toInt())
        else -> context.resources.getQuantityString(R.plurals.widget_unit_future, count.toInt())
    }

    private fun buildClassic(
        context: Context,
        counter: Counter?,
        today: LocalDate,
        index: Int,
        total: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_days)
        val res = context.resources

        if (total > 1) {
            views.setViewVisibility(R.id.days_page, View.VISIBLE)
            views.setTextViewText(R.id.days_page, (0 until total).joinToString(" ") { if (it == index) "●" else "○" })
        } else {
            views.setViewVisibility(R.id.days_page, View.GONE)
        }

        if (counter == null) {
            views.setViewVisibility(R.id.days_label, View.GONE)
            views.setTextViewText(R.id.days_number, "?")
            views.setTextViewText(R.id.days_unit, res.getString(R.string.widget_not_set))
            views.setTextViewText(R.id.days_since, res.getString(R.string.widget_tap_to_setup))
            return views
        }

        val days = ChronoUnit.DAYS.between(counter.date, today)
        val count = abs(days)
        val dateText = counter.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))

        views.setTextViewText(R.id.days_number, NumberFormat.getIntegerInstance().format(count))
        views.setTextViewText(R.id.days_unit, unitText(context, days, count))
        views.setTextViewText(
            R.id.days_since,
            res.getString(if (days >= 0) R.string.widget_since else R.string.widget_until, dateText)
        )

        if (counter.label.isBlank()) {
            views.setViewVisibility(R.id.days_label, View.GONE)
        } else {
            views.setViewVisibility(R.id.days_label, View.VISIBLE)
            views.setTextViewText(R.id.days_label, counter.label)
        }
        return views
    }

    private fun buildVectorheart(
        context: Context,
        counter: Counter?,
        today: LocalDate,
        widthDp: Int,
        heightDp: Int,
        index: Int,
        total: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vectorheart)
        val res = context.resources

        views.setTextViewText(
            R.id.vh_label,
            counter?.label.orEmpty().ifBlank { res.getString(R.string.vh_default_label) }.uppercase()
        )
        if (total > 1) {
            views.setViewVisibility(R.id.vh_page, View.VISIBLE)
            views.setTextViewText(R.id.vh_page, "%02d/%02d".format(index + 1, total))
        } else {
            views.setViewVisibility(R.id.vh_page, View.GONE)
        }

        // 0 = taille inconnue (aperçu, ancien lanceur) : on affiche le panneau.
        val showPanel = counter != null && (widthDp == 0 || widthDp >= VH_PANEL_MIN_WIDTH_DP)
        views.setViewVisibility(R.id.vh_panel, if (showPanel) View.VISIBLE else View.GONE)
        // Widget étroit : on retire graduations et date pour laisser la place au nombre.
        val compact = widthDp in 1 until VH_PANEL_MIN_WIDTH_DP
        views.setViewVisibility(R.id.vh_ruler, if (compact) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.vh_footer, if (compact) View.GONE else View.VISIBLE)

        if (counter == null) {
            views.setTextViewText(R.id.vh_sign, "T?")
            views.setTextViewText(R.id.days_number, "--")
            views.setTextViewText(R.id.days_unit, res.getString(R.string.widget_not_set).uppercase())
            views.setTextViewText(R.id.days_since, res.getString(R.string.widget_tap_to_setup).uppercase())
            return views
        }

        val date = counter.date
        val days = ChronoUnit.DAYS.between(date, today)
        val count = abs(days)
        val past = days >= 0
        val dateText = date.format(VH_DATE)

        views.setTextViewText(R.id.days_number, NumberFormat.getIntegerInstance().format(count))
        views.setTextViewText(R.id.vh_sign, if (past) "T+" else "T−")
        views.setTextViewText(R.id.days_unit, unitText(context, days, count).uppercase())
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
            val fmt = NumberFormat.getIntegerInstance()
            views.setTextViewText(R.id.vh_weeks, readout("SEM ", fmt.format(count / 7), dim))
            views.setTextViewText(R.id.vh_months, readout("MOIS", fmt.format(months), dim))
            views.setTextViewText(R.id.vh_years, readout("ANS ", yearsText, dim))
            val showYears = heightDp == 0 || heightDp >= VH_YEARS_MIN_HEIGHT_DP
            views.setViewVisibility(R.id.vh_years, if (showYears) View.VISIBLE else View.GONE)
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
}
