package com.ham.audiolog.domain.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 接收通知栏媒体播放控制器的广播操作
 */
class AudioPlaybackReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val playerManager = AudioPlayerManager.activeInstance ?: return

        when (action) {
            ACTION_PLAY_PAUSE -> {
                playerManager.togglePlayPause()
            }
            ACTION_REWIND_5S -> {
                playerManager.rewind5s()
            }
            ACTION_FORWARD_5S -> {
                playerManager.forward5s()
            }
            ACTION_STOP -> {
                playerManager.stop()
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.ham.audiolog.PLAYBACK_PLAY_PAUSE"
        const val ACTION_REWIND_5S = "com.ham.audiolog.PLAYBACK_REWIND_5S"
        const val ACTION_FORWARD_5S = "com.ham.audiolog.PLAYBACK_FORWARD_5S"
        const val ACTION_STOP = "com.ham.audiolog.PLAYBACK_STOP"
    }
}
