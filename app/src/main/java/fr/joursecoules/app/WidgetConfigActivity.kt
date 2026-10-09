package fr.joursecoules.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.TextView
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Écran ouvert à l'ajout du widget, ou quand on le touche : choix de la date et du nom. */
class WidgetConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Si l'utilisateur quitte sans valider, le widget n'est pas ajouté.
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.activity_config)

        val labelInput = findViewById<EditText>(R.id.label_input)
        val picker = findViewById<DatePicker>(R.id.date_picker)
        val preview = findViewById<TextView>(R.id.preview_text)

        val start = DaysStore.date(this, widgetId) ?: LocalDate.now()
        labelInput.setText(DaysStore.label(this, widgetId))
        picker.updateDate(start.year, start.monthValue - 1, start.dayOfMonth)
        preview.text = previewText(start)

        picker.setOnDateChangedListener { _, year, month, day ->
            preview.text = previewText(LocalDate.of(year, month + 1, day))
        }

        findViewById<Button>(R.id.cancel_button).setOnClickListener { finish() }

        findViewById<Button>(R.id.save_button).setOnClickListener {
            val date = LocalDate.of(picker.year, picker.month + 1, picker.dayOfMonth)
            DaysStore.save(this, widgetId, date, labelInput.text.toString().trim())

            DaysWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
            DaysWidgetProvider.scheduleMidnightRefresh(this)

            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            )
            finish()
        }
    }

    private fun previewText(date: LocalDate): String {
        val days = ChronoUnit.DAYS.between(date, LocalDate.now())
        val count = abs(days).toInt()
        return when {
            days == 0L -> getString(R.string.preview_today)
            days > 0 -> resources.getQuantityString(R.plurals.preview_past, count, count)
            else -> resources.getQuantityString(R.plurals.preview_future, count, count)
        }
    }
}
