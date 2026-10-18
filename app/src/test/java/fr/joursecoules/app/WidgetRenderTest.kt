package fr.joursecoules.app

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
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
        theme = "android:Theme.Material.NoActionBar",
        showSystemUi = false
    )

    private val today = LocalDate.of(2026, 10, 19)

    private fun render(theme: WidgetTheme, date: LocalDate?, label: String, wDp: Int, hDp: Int): View {
        val ctx = paparazzi.context
        val density = ctx.resources.displayMetrics.density
        val frame = FrameLayout(ctx).apply { setBackgroundColor(0xFF3B5B7A.toInt()) }
        // Simule l'arrondi appliqué par l'écran d'accueil (rayon 28dp, le pire cas courant).
        val holder = FrameLayout(ctx).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, 28 * density)
            }
            clipToOutline = true
        }
        val pad = (16 * density).toInt()
        frame.setPadding(pad, pad, pad, pad)
        val views = DaysWidgetProvider.buildViews(ctx, date, label, theme, today, widthDp = wDp)
        holder.addView(views.apply(ctx, holder))
        frame.addView(holder, ViewGroup.LayoutParams((wDp * density).toInt(), (hDp * density).toInt()))
        return frame
    }

    @Test fun vectorheart_past() =
        paparazzi.snapshot(render(WidgetTheme.VECTORHEART, LocalDate.of(2026, 6, 13), "Arrêt du tabac", 160, 160))

    @Test fun vectorheart_future_nolabel() =
        paparazzi.snapshot(render(WidgetTheme.VECTORHEART, LocalDate.of(2026, 12, 25), "", 160, 160))

    @Test fun vectorheart_big_number_wide() =
        paparazzi.snapshot(render(WidgetTheme.VECTORHEART, LocalDate.of(1997, 4, 2), "Depuis ma naissance", 320, 160))

    @Test fun vectorheart_big_number_square() =
        paparazzi.snapshot(render(WidgetTheme.VECTORHEART, LocalDate.of(1997, 4, 2), "Naissance", 160, 160))

    @Test fun vectorheart_small() =
        paparazzi.snapshot(render(WidgetTheme.VECTORHEART, LocalDate.of(2026, 10, 18), "Rencontre", 110, 110))

    @Test fun vectorheart_unset() =
        paparazzi.snapshot(render(WidgetTheme.VECTORHEART, null, "", 160, 160))

    @Test fun classic_past() =
        paparazzi.snapshot(render(WidgetTheme.CLASSIC, LocalDate.of(2026, 6, 13), "Arrêt du tabac", 160, 160))

    @Test fun milestones() {
        org.junit.Assert.assertEquals(150L to 0.56f, DaysWidgetProvider.milestone(128, past = true))
        org.junit.Assert.assertEquals(150L to 0f, DaysWidgetProvider.milestone(100, past = true))
        org.junit.Assert.assertEquals(11000L, DaysWidgetProvider.milestone(10792, past = true).first)
        org.junit.Assert.assertEquals(50L to 0.66f, DaysWidgetProvider.milestone(67, past = false))
        org.junit.Assert.assertEquals(0L, DaysWidgetProvider.milestone(3, past = false).first)
        org.junit.Assert.assertEquals(4000L, DaysWidgetProvider.milestone(4500, past = false).first)
    }
}
