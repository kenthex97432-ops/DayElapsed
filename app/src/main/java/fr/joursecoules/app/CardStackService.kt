package fr.joursecoules.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Fournit les cartes de la pile au widget. */
class CardStackService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CardStackFactory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        )
}

private const val STACK_SCALE = 0.75f

class CardStackFactory(
    private val context: Context,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var counters: List<Counter> = emptyList()
    private var theme = WidgetTheme.CLASSIC
    private var widthDp = 0
    private var heightDp = 0

    override fun onCreate() {}

    override fun onDataSetChanged() {
        counters = DaysStore.counters(context, widgetId)
        theme = DaysStore.theme(context, widgetId)
        // Dans la pile, Android réduit chaque carte (~75 %) pour laisser voir celles de derrière.
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        widthDp = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) * STACK_SCALE).toInt()
        heightDp = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) * STACK_SCALE).toInt()
    }

    override fun getCount(): Int = counters.size

    override fun getViewAt(position: Int): RemoteViews {
        val counter = counters.getOrNull(position)
        val views = CardRenderer.build(
            context, counter, theme,
            widthDp = widthDp, heightDp = heightDp, index = position, total = counters.size
        )
        views.setOnClickFillInIntent(
            R.id.widget_root,
            Intent().putExtra(WidgetConfigActivity.EXTRA_EDIT_INDEX, position)
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = WidgetTheme.entries.size
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
    override fun onDestroy() {}
}
