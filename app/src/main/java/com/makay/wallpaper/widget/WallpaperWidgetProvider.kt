package com.makay.wallpaper.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.makay.wallpaper.R

class WallpaperWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetId, appWidgetManager)
        }
    }

    private fun updateWidget(context: Context, appWidgetId: Int, appWidgetManager: AppWidgetManager) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        // Basit bir metin gösterimi; geri çağrılımlar daha sonra eklenebilir
        views.setTextViewText(R.id.widget_text, "Makay Wallpaper")

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}