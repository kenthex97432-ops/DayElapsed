package fr.joursecoules.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RadioGroup
import java.time.LocalDate

/** Écran ouvert à l'ajout du widget, ou quand on le touche : date, nom et thème, avec aperçu en direct. */
class WidgetConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var labelInput: EditText
    private lateinit var picker: DatePicker
    private lateinit var themeGroup: RadioGroup
    private lateinit var previewContainer: FrameLayout

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

        labelInput = findViewById(R.id.label_input)
        picker = findViewById(R.id.date_picker)
        themeGroup = findViewById(R.id.theme_group)
        previewContainer = findViewById(R.id.preview_container)

        val start = DaysStore.date(this, widgetId) ?: LocalDate.now()
        labelInput.setText(DaysStore.label(this, widgetId))
        picker.updateDate(start.year, start.monthValue - 1, start.dayOfMonth)
        themeGroup.check(
            when (DaysStore.theme(this, widgetId)) {
                WidgetTheme.CLASSIC -> R.id.theme_classic
                WidgetTheme.VECTORHEART -> R.id.theme_vectorheart
            }
        )

        picker.setOnDateChangedListener { _, _, _, _ -> refreshPreview() }
        themeGroup.setOnCheckedChangeListener { _, _ -> refreshPreview() }
        labelInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshPreview()
        })
        refreshPreview()

        findViewById<Button>(R.id.cancel_button).setOnClickListener { finish() }

        findViewById<Button>(R.id.save_button).setOnClickListener {
            DaysStore.save(this, widgetId, selectedDate(), labelInput.text.toString().trim(), selectedTheme())

            DaysWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
            DaysWidgetProvider.scheduleMidnightRefresh(this)

            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            )
            finish()
        }
    }

    private fun selectedDate(): LocalDate =
        LocalDate.of(picker.year, picker.month + 1, picker.dayOfMonth)

    private fun selectedTheme(): WidgetTheme =
        if (themeGroup.checkedRadioButtonId == R.id.theme_vectorheart) WidgetTheme.VECTORHEART
        else WidgetTheme.CLASSIC

    /** Affiche le vrai widget, tel qu'il apparaîtra sur l'écran d'accueil. */
    private fun refreshPreview() {
        val views = DaysWidgetProvider.buildViews(
            this,
            selectedDate(),
            labelInput.text.toString().trim(),
            selectedTheme()
        )
        previewContainer.removeAllViews()
        previewContainer.addView(views.apply(this, previewContainer))
    }
}
