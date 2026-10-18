package fr.joursecoules.app

import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Rendu des widgets sans téléphone (Paparazzi).
 * `./gradlew :app:recordPaparazziDebug` produit les images dans app/src/test/snapshots.
 */
class WidgetRenderTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2400, screenHeight = 1200),
        renderingMode = SessionParams.RenderingMode.SHRINK,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    private val today = LocalDate.of(2026, 10, 19)

    private val samples = listOf(
        Counter(LocalDate.of(2026, 6, 13), "Arrêt du tabac"),
        Counter(LocalDate.of(2026, 12, 25), "Noël"),
        Counter(LocalDate.of(1997, 4, 2), "Naissance"),
    )

    /** Cadre « écran d'accueil » avec l'arrondi que le lanceur applique (rayon 28dp, le pire cas courant). */
    private fun homeFrame(content: View, wDp: Int, hDp: Int): View {
        val ctx = paparazzi.context
        val density = ctx.resources.displayMetrics.density
        val frame = FrameLayout(ctx).apply { setBackgroundColor(0xFF3B5B7A.toInt()) }
        val holder = FrameLayout(ctx).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, 28 * density)
            }
            clipToOutline = true
        }
        val pad = (16 * density).toInt()
        frame.setPadding(pad, pad, pad, pad)
        holder.addView(content)
        frame.addView(holder, ViewGroup.LayoutParams((wDp * density).toInt(), (hDp * density).toInt()))
        return frame
    }

    private fun card(theme: WidgetTheme, counter: Counter?, wDp: Int, hDp: Int, index: Int = 0, total: Int = 1): View {
        val ctx = paparazzi.context
        val holder = FrameLayout(ctx)
        holder.addView(CardRenderer.build(ctx, counter, theme, today, wDp, index, total).apply(ctx, holder))
        return homeFrame(holder, wDp, hDp)
    }

    /** Le widget complet, avec les flèches quand il y a plusieurs dates. */
    private fun widget(theme: WidgetTheme, counters: List<Counter>, index: Int, wDp: Int, hDp: Int): View {
        val ctx = paparazzi.context
        val holder = FrameLayout(ctx)
        holder.addView(DaysWidgetProvider.buildWidget(ctx, counters, theme, index, wDp, hDp, today = today).apply(ctx, holder))
        return homeFrame(holder, wDp, hDp)
    }

    @Test fun vectorheart_past() =
        paparazzi.snapshot(card(WidgetTheme.VECTORHEART, samples[0], 160, 160))

    @Test fun vectorheart_future() =
        paparazzi.snapshot(card(WidgetTheme.VECTORHEART, samples[1], 160, 160, index = 1, total = 3))

    @Test fun vectorheart_today() =
        paparazzi.snapshot(card(WidgetTheme.VECTORHEART, Counter(today, "Jour J"), 160, 160))

    @Test fun vectorheart_big_number_wide() =
        paparazzi.snapshot(card(WidgetTheme.VECTORHEART, Counter(LocalDate.of(1997, 4, 2), "Depuis ma naissance"), 320, 160))

    @Test fun vectorheart_small() =
        paparazzi.snapshot(card(WidgetTheme.VECTORHEART, Counter(LocalDate.of(2026, 10, 18), "Rencontre"), 110, 110))

    @Test fun vectorheart_unset() =
        paparazzi.snapshot(card(WidgetTheme.VECTORHEART, null, 160, 160))

    @Test fun classic_past() =
        paparazzi.snapshot(card(WidgetTheme.CLASSIC, samples[0], 160, 160))

    @Test fun classic_future_paged() =
        paparazzi.snapshot(card(WidgetTheme.CLASSIC, samples[1], 160, 160, index = 1, total = 3))

    @Test fun pager_vectorheart() =
        paparazzi.snapshot(widget(WidgetTheme.VECTORHEART, samples, 0, 180, 170))

    @Test fun pager_vectorheart_wide() =
        paparazzi.snapshot(widget(WidgetTheme.VECTORHEART, samples, 1, 260, 170))

    @Test fun resize_vectorheart_large() =
        paparazzi.snapshot(widget(WidgetTheme.VECTORHEART, samples, 0, 330, 330))

    @Test fun resize_vectorheart_wide() =
        paparazzi.snapshot(widget(WidgetTheme.VECTORHEART, samples, 1, 330, 170))

    @Test fun resize_vectorheart_tall() =
        paparazzi.snapshot(widget(WidgetTheme.VECTORHEART, samples, 2, 170, 330))

    @Test fun resize_classic_large() =
        paparazzi.snapshot(widget(WidgetTheme.CLASSIC, samples, 0, 330, 330))

    @Test fun pager_classic() =
        paparazzi.snapshot(widget(WidgetTheme.CLASSIC, samples, 2, 180, 170))

    @Test fun pager_single_date_no_arrows() =
        paparazzi.snapshot(widget(WidgetTheme.VECTORHEART, samples.take(1), 0, 180, 170))

    /** L'écran de réglage, avec trois dates. */
    @Test fun config_screen() {
        val ctx = paparazzi.context
        val root = android.view.LayoutInflater.from(ctx).inflate(R.layout.activity_config, null)
        val row = root.findViewById<android.widget.LinearLayout>(R.id.cards_row)
        populateCards(row, samples, WidgetTheme.VECTORHEART, today) {}
        root.findViewById<android.widget.TextView>(R.id.dates_hint).setText(R.string.config_dates_hint_multi)
        root.findViewById<android.widget.RadioGroup>(R.id.theme_group).check(R.id.theme_vectorheart)
        val frame = FrameLayout(ctx).apply { setBackgroundColor(0xFFFAFAFA.toInt()) }
        val d = ctx.resources.displayMetrics.density
        frame.addView(root, ViewGroup.LayoutParams((392 * d).toInt(), (560 * d).toInt()))
        paparazzi.snapshot(frame)
    }

    @Test fun scale() {
        assertEquals(1f, CardRenderer.scaleFor(0, 0))
        assertEquals(1f, CardRenderer.scaleFor(160, 200))
        assertEquals(2f, CardRenderer.scaleFor(320, 330))
        assertEquals(0.85f, CardRenderer.scaleFor(90, 90))
    }

    @Test fun milestones() {
        assertEquals(150L to 0.56f, CardRenderer.milestone(128, past = true))
        assertEquals(150L to 0f, CardRenderer.milestone(100, past = true))
        assertEquals(11000L, CardRenderer.milestone(10792, past = true).first)
        assertEquals(50L to 0.66f, CardRenderer.milestone(67, past = false))
        assertEquals(0L, CardRenderer.milestone(3, past = false).first)
        assertEquals(4000L, CardRenderer.milestone(4500, past = false).first)
    }
}
