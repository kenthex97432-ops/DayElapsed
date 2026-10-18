package fr.joursecoules.app

import android.view.View
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
        val holder = FrameLayout(ctx)
        val pad = (16 * density).toInt()
        frame.setPadding(pad, pad, pad, pad)
        val views = DaysWidgetProvider.buildViews(ctx, date, label, theme, today)
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
}
