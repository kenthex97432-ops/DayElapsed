package fr.joursecoules.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.DatePicker
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import java.time.LocalDate

/** Ajout ou modification d'une date, avec aperçu en direct de la carte. */
class CounterEditActivity : Activity() {

    companion object {
        const val EXTRA_INDEX = "index"          // -1 = nouvelle date
        const val EXTRA_DATE = "date"
        const val EXTRA_LABEL = "label"
        const val EXTRA_THEME = "theme"
        const val EXTRA_DELETED = "deleted"
        const val EXTRA_MOVE_FIRST = "move_first"

        fun intent(context: Context, index: Int, counter: Counter?, theme: WidgetTheme): Intent =
            Intent(context, CounterEditActivity::class.java)
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_DATE, counter?.date?.toString())
                .putExtra(EXTRA_LABEL, counter?.label)
                .putExtra(EXTRA_THEME, theme.key)
    }

    private var index = -1
    private lateinit var theme: WidgetTheme
    private lateinit var labelInput: EditText
    private lateinit var picker: DatePicker
    private lateinit var preview: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        index = intent.getIntExtra(EXTRA_INDEX, -1)
        theme = WidgetTheme.fromKey(intent.getStringExtra(EXTRA_THEME))
        val start = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()

        labelInput = findViewById(R.id.label_input)
        picker = findViewById(R.id.date_picker)
        preview = findViewById(R.id.preview_container)

        findViewById<TextView>(R.id.edit_title)
            .setText(if (index < 0) R.string.edit_title_new else R.string.edit_title)
        labelInput.setText(intent.getStringExtra(EXTRA_LABEL).orEmpty())
        picker.updateDate(start.year, start.monthValue - 1, start.dayOfMonth)

        val moveFirst = findViewById<CheckBox>(R.id.move_first)
        moveFirst.visibility = if (index > 0) View.VISIBLE else View.GONE

        picker.setOnDateChangedListener { _, _, _, _ -> refreshPreview() }
        labelInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshPreview()
        })
        refreshPreview()

        findViewById<Button>(R.id.delete_button).apply {
            visibility = if (index >= 0) View.VISIBLE else View.GONE
            setOnClickListener {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_INDEX, index).putExtra(EXTRA_DELETED, true))
                finish()
            }
        }
        findViewById<Button>(R.id.cancel_button).setOnClickListener { finish() }
        findViewById<Button>(R.id.ok_button).setOnClickListener {
            val c = current()
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(EXTRA_INDEX, index)
                    .putExtra(EXTRA_DATE, c.date.toString())
                    .putExtra(EXTRA_LABEL, c.label)
                    .putExtra(EXTRA_MOVE_FIRST, moveFirst.isChecked)
            )
            finish()
        }
    }

    private fun current() = Counter(
        LocalDate.of(picker.year, picker.month + 1, picker.dayOfMonth),
        labelInput.text.toString().trim()
    )

    private fun refreshPreview() {
        val views = CardRenderer.build(this, current(), theme, widthDp = 170)
        preview.removeAllViews()
        preview.addView(views.apply(this, preview))
    }
}
