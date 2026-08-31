package com.ham.audiolog.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.ham.audiolog.MainActivity
import com.ham.audiolog.R
import com.ham.audiolog.service.QsoAudioRecorderService
import com.ham.audiolog.service.RecordingServiceState

/**
 * HamAudioLog 4X2 桌面小组件 Provider
 * 显示当前时间、打点个数，并提供“开始录音”、“结束录音”、“MARK 打点”、“打开应用”四个操作按钮
 */
class HamAudioLogWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val currentState = QsoAudioRecorderService.recordingState.value
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, currentState)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val currentState = QsoAudioRecorderService.recordingState.value
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HamAudioLogWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null) {
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId, currentState)
                }
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.ham.audiolog.widget.UPDATE_WIDGET"

        private const val REQUEST_OPEN_APP = 101
        private const val REQUEST_START_RECORDING = 102
        private const val REQUEST_STOP_RECORDING = 103
        private const val REQUEST_TRIGGER_MARK = 104

        /**
         * 全局刷新所有已添加的小组件实例
         */
        fun updateAllWidgets(context: Context, state: RecordingServiceState) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, HamAudioLogWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    for (appWidgetId in appWidgetIds) {
                        updateWidget(context, appWidgetManager, appWidgetId, state)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            state: RecordingServiceState
        ) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_ham_audio_log)

                // 1. 状态文本与样式更新
                if (state.isRecording) {
                    val totalSec = state.durationMs / 1000
                    val mm = totalSec / 60
                    val ss = totalSec % 60
                    val timeStr = "%02d:%02d".format(mm, ss)
                    views.setTextViewText(R.id.tv_widget_status, "🔴 录音中 ($timeStr)")
                    views.setTextColor(R.id.tv_widget_status, Color.parseColor("#FF5252"))
                } else {
                    views.setTextViewText(R.id.tv_widget_status, "⚪ 待机就绪")
                    views.setTextColor(R.id.tv_widget_status, Color.parseColor("#90A4AE"))
                }

                // 2. 打点个数更新
                views.setTextViewText(
                    R.id.tv_widget_marker_count,
                    context.getString(R.string.widget_markers_format, state.markerCount)
                )

                // 3. 渲染矢量图标为高清 Bitmap 赋予各按钮，确保在所有第三方 Launcher 中无异常解析
                views.setImageViewBitmap(
                    R.id.iv_widget_start,
                    vectorToBitmap(context, R.drawable.ic_widget_mic, 48, 48)
                )
                views.setImageViewBitmap(
                    R.id.iv_widget_stop,
                    vectorToBitmap(context, R.drawable.ic_widget_stop, 48, 48)
                )
                views.setImageViewBitmap(
                    R.id.iv_widget_mark,
                    vectorToBitmap(context, R.drawable.ic_widget_mark, 54, 54)
                )
                views.setImageViewBitmap(
                    R.id.iv_widget_open,
                    vectorToBitmap(context, R.drawable.ic_widget_radio, 48, 48)
                )

                // 4. 绑定“打开应用” PendingIntent (顶部区域 + 打开应用按钮)
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openAppPendingIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN_APP,
                    openAppIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_header, openAppPendingIntent)
                views.setOnClickPendingIntent(R.id.btn_widget_open, openAppPendingIntent)

                // 5. 绑定“开始录音” PendingIntent
                val startIntent = Intent(context, QsoAudioRecorderService::class.java).apply {
                    action = QsoAudioRecorderService.ACTION_START_RECORDING
                }
                val startPendingIntent = PendingIntent.getForegroundService(
                    context,
                    REQUEST_START_RECORDING,
                    startIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.btn_widget_start, startPendingIntent)

                // 6. 绑定“结束录音” PendingIntent
                val stopIntent = Intent(context, QsoAudioRecorderService::class.java).apply {
                    action = QsoAudioRecorderService.ACTION_STOP_RECORDING
                }
                val stopPendingIntent = PendingIntent.getService(
                    context,
                    REQUEST_STOP_RECORDING,
                    stopIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.btn_widget_stop, stopPendingIntent)

                // 7. 绑定“MARK 打点” PendingIntent
                val markIntent = Intent(context, QsoAudioRecorderService::class.java).apply {
                    action = QsoAudioRecorderService.ACTION_TRIGGER_MARK
                }
                val markPendingIntent = PendingIntent.getService(
                    context,
                    REQUEST_TRIGGER_MARK,
                    markIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.btn_widget_mark, markPendingIntent)

                // 更新到 AppWidgetManager
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun vectorToBitmap(
            context: Context,
            @DrawableRes drawableId: Int,
            widthDp: Int = 48,
            heightDp: Int = 48
        ): Bitmap {
            val drawable = ContextCompat.getDrawable(context, drawableId)
                ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val density = context.resources.displayMetrics.density
            val w = (widthDp * density).toInt().coerceAtLeast(1)
            val h = (heightDp * density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            return bitmap
        }
    }
}
