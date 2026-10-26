package com.example.tiktokautostreak

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast

class StreakWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, StreakWidgetProvider::class.java).apply {
                action = ACTION_START_STREAK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.streak_widget)
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_START_STREAK) {
            val service = AutomationService.instance
            if (service != null) {
                Toast.makeText(context, "🚀 Запуск AutoStreak...", Toast.LENGTH_SHORT).show()
                service.startAutomation()
            } else {
                Toast.makeText(
                    context, 
                    "⚠️ Помилка: Увімкніть AutoStreak у Спеціальних Можливостях!", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val ACTION_START_STREAK = "com.example.tiktokautostreak.START_STREAK"
    }
}