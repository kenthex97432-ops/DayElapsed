package fr.joursecoules.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

/**
 * Réglages d'un widget : sa liste de dates (dans l'ordre de défilement) et son thème.
 * S'ouvre à l'ajout du widget, ou quand on le touche.
 */
class WidgetConfigActivity : Activity() {

    companion object {
        const val EXTRA_EDIT_INDEX = "edit_index"
        private const val REQ_EDIT = 1
    }

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val counters = mutableListOf<Counter>()
    private var theme = WidgetTheme.CLASSIC

    private lateinit var cardsRow: LinearLayout
    private lateinit var themeGroup: RadioGroup
    private lateinit var hint: TextView

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
        cardsRow = findViewById(R.id.cards_row)
        themeGroup = findViewById(R.id.theme_group)
        hint = findViewById(R.id.dates_hint)

        if (savedInstanceState != null) {
            restore(savedInstanceState)
        } else {
            counters += DaysStore.counters(this, widgetId)
            theme = DaysStore.theme(this, widgetId)
        }

        themeGroup.check(if (theme == WidgetTheme.VECTORHEART) R.id.theme_vectorheart else R.id.theme_classic)
        themeGroup.setOnCheckedChangeListener { _, id ->
            theme = if (id == R.id.theme_vectorheart) WidgetTheme.VECTORHEART else WidgetTheme.CLASSIC
            renderCards()
        }
        renderCards()

        findViewById<Button>(R.id.cancel_button).setOnClickListener { finish() }
        findViewById<Button>(R.id.save_button).setOnClickListener { save() }

        if (savedInstanceState == null) {
            val editIndex = intent.getIntExtra(EXTRA_EDIT_INDEX, -1)
            when {
                // Touché depuis une carte du widget : on ouvre directement cette date.
                editIndex in counters.indices -> edit(editIndex)
                // Nouveau widget : on commence par la première date.
                counters.isEmpty() -> edit(-1)
            }
        }
    }

    private fun renderCards() {
        populateCards(cardsRow, counters, theme, LocalDate.now()) { edit(it) }
        hint.setText(if (counters.size > 1) R.string.config_dates_hint_multi else R.string.config_dates_hint_single)
    }

    private fun edit(index: Int) {
        @Suppress("DEPRECATION")
        startActivityForResult(
            CounterEditActivity.intent(this, index, counters.getOrNull(index), theme),
            REQ_EDIT
        )
    }

    @Deprecated("API Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_EDIT || resultCode != RESULT_OK || data == null) return

        val index = data.getIntExtra(CounterEditActivity.EXTRA_INDEX, -1)
        if (data.getBooleanExtra(CounterEditActivity.EXTRA_DELETED, false)) {
            if (index in counters.indices) counters.removeAt(index)
        } else {
            val date = data.getStringExtra(CounterEditActivity.EXTRA_DATE)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
            val counter = Counter(date, data.getStringExtra(CounterEditActivity.EXTRA_LABEL).orEmpty())
            if (index in counters.indices) counters[index] = counter else counters += counter
            if (data.getBooleanExtra(CounterEditActivity.EXTRA_MOVE_FIRST, false) && index > 0) {
                counters.removeAt(index)
                counters.add(0, counter)
            }
        }
        renderCards()
    }

    private fun save() {
        if (counters.isEmpty()) {
            Toast.makeText(this, R.string.config_need_one, Toast.LENGTH_SHORT).show()
            return
        }
        DaysStore.save(this, widgetId, counters, theme)
        DaysWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
        DaysWidgetProvider.scheduleMidnightRefresh(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    // Conserve la liste en cours si l'écran est recréé (rotation…).
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArray("dates", counters.map { it.date.toString() }.toTypedArray())
        outState.putStringArray("labels", counters.map { it.label }.toTypedArray())
        outState.putString("theme", theme.key)
    }

    private fun restore(state: Bundle) {
        val dates = state.getStringArray("dates").orEmpty()
        val labels = state.getStringArray("labels").orEmpty()
        dates.forEachIndexed { i, d -> counters += Counter(LocalDate.parse(d), labels.getOrElse(i) { "" }) }
        theme = WidgetTheme.fromKey(state.getString("theme"))
    }
}

/**
 * Remplit la rangée de l'écran de réglage : les cartes telles qu'elles apparaîtront
 * dans le widget, puis la tuile « + ». [onEdit] reçoit l'index touché, -1 pour un ajout.
 */
fun populateCards(
    row: LinearLayout,
    counters: List<Counter>,
    theme: WidgetTheme,
    today: LocalDate,
    onEdit: (Int) -> Unit
) {
    val context = row.context
    val cardDp = 150
    fun dp(v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
    fun params() = LinearLayout.LayoutParams(dp(cardDp), dp(cardDp)).apply {
        marginStart = dp(4)
        marginEnd = dp(4)
    }

    row.removeAllViews()
    counters.forEachIndexed { i, counter ->
        val frame = FrameLayout(context)
        val card = CardRenderer.build(context, counter, theme, today, cardDp, i, counters.size, cardDp)
        frame.addView(card.apply(context, frame))
        frame.setOnClickListener { onEdit(i) }
        frame.contentDescription = counter.label.ifBlank { counter.date.toString() }
        row.addView(frame, params())
    }
    val add = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 15f
        setBackgroundResource(R.drawable.add_tile_bg)
        text = "+\n" + context.getString(R.string.config_add)
        setOnClickListener { onEdit(-1) }
    }
    row.addView(add, params())
}
