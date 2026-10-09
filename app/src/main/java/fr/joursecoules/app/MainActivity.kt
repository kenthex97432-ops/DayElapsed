package fr.joursecoules.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

/** Écran d'accueil de l'appli : explique comment ajouter le widget, avec un bouton raccourci. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.add_widget_button).setOnClickListener {
            val manager = AppWidgetManager.getInstance(this)
            val provider = ComponentName(this, DaysWidgetProvider::class.java)
            if (manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(provider, null, null)
            } else {
                Toast.makeText(this, R.string.pin_not_supported, Toast.LENGTH_LONG).show()
            }
        }
    }
}
