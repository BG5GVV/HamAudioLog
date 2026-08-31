package com.ham.audiolog.domain.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.ham.audiolog.data.model.AudioMarkerEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var lastNotificationSec: Long = -1L

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState = _playerState.asStateFlow()

    init {
        activeInstance = this
        createNotificationChannel()
        initPlayer()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "通联录音回放控制",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示当前播放的通联录音进度与快捷控制"
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun initPlayer() {
        exoPlayer = ExoPlayer.Builder(context)
            .setSeekParameters(SeekParameters.EXACT)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) {
                            startProgressPolling()
                            updatePlaybackNotification()
                        } else {
                            stopProgressPolling()
                            if (_playerState.value.currentPositionMs > 0) {
                                updatePlaybackNotification()
                            } else {
                                cancelPlaybackNotification()
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                val dur = duration.coerceAtLeast(0L)
                                _playerState.value = _playerState.value.copy(
                                    totalDurationMs = dur,
                                    currentPositionMs = currentPosition
                                )
                                if (_playerState.value.isPlaying) {
                                    updatePlaybackNotification()
                                }
                            }
                            Player.STATE_ENDED -> {
                                _playerState.value = _playerState.value.copy(
                                    isPlaying = false,
                                    activeMarkerId = null
                                )
                                stopProgressPolling()
                                cancelPlaybackNotification()
                            }
                        }
                    }
                })
            }
    }

    /**
     * 加载/预备音频文件（可选是否自动开始播放）
     */
    fun loadFile(filePath: String, autoPlay: Boolean = false, startPositionMs: Long = 0L) {
        val file = File(filePath)
        if (!file.exists()) return

        val player = exoPlayer ?: return

        // 若当前已经加载了同一个文件且处于就绪/播放状态，且不要求重置位置，直接处理播放/暂停
        if (_playerState.value.currentFilePath == filePath && player.mediaItemCount > 0) {
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs)
            }
            if (autoPlay) {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0L)
                }
                player.play()
            }
            return
        }

        val mediaItem = MediaItem.fromUri(file.toURI().toString())
        player.setMediaItem(mediaItem)
        player.prepare()
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        if (autoPlay) {
            player.play()
        }

        _playerState.value = _playerState.value.copy(
            currentFilePath = filePath,
            activeMarkerId = null,
            currentPositionMs = startPositionMs,
            isPlaying = autoPlay
        )
    }

    /**
     * 播放完整录音文件（从指定毫秒位置开始，默认从开头 0 播放）
     */
    fun playFile(filePath: String, startPositionMs: Long = 0L) {
        loadFile(filePath = filePath, autoPlay = true, startPositionMs = startPositionMs)
    }

    /**
     * 播放指定时间锚点音频（预留 3 秒提前量缓冲）
     */
    fun playMarker(filePath: String, marker: AudioMarkerEntity) {
        val file = File(filePath)
        if (!file.exists()) return

        val player = exoPlayer ?: return
        val targetOffsetMs = maxOf(0L, marker.audioOffsetMs - 3000L) // 预留 3 秒提前量缓冲

        if (_playerState.value.currentFilePath != filePath || player.mediaItemCount == 0) {
            val mediaItem = MediaItem.fromUri(file.toURI().toString())
            player.setMediaItem(mediaItem)
            player.prepare()
        }

        player.seekTo(targetOffsetMs)
        player.play()

        _playerState.value = _playerState.value.copy(
            currentFilePath = filePath,
            activeMarkerId = marker.id,
            currentPositionMs = targetOffsetMs,
            isPlaying = true
        )
    }

    /**
     * 播放/暂停切换。
     * 若未加载文件且传入了 fallbackFilePath，则自动加载并播放该文件。
     */
    fun togglePlayPause(filePath: String? = null) {
        val player = exoPlayer ?: return

        // 如果未加载当前文件且传入了有效文件路径
        val targetPath = filePath ?: _playerState.value.currentFilePath
        if (targetPath.isNotBlank() && (_playerState.value.currentFilePath != targetPath || player.mediaItemCount == 0)) {
            playFile(targetPath, 0L)
            return
        }

        if (player.isPlaying) {
            player.pause()
        } else {
            // 如果已到达末尾，重头开始播放
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
        _playerState.value = _playerState.value.copy(isPlaying = false)
        cancelPlaybackNotification()
    }

    @Deprecated("Use togglePlayPause() instead", ReplaceWith("togglePlayPause(filePath)"))
    fun playPause() {
        togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        player.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
        updatePlaybackNotification()
    }

    fun forward5s() {
        val player = exoPlayer ?: return
        val dur = if (player.duration > 0) player.duration else Long.MAX_VALUE
        val newPos = (player.currentPosition + 5000L).coerceAtMost(dur)
        player.seekTo(newPos)
        _playerState.value = _playerState.value.copy(currentPositionMs = newPos)
        updatePlaybackNotification()
    }

    fun rewind5s() {
        val player = exoPlayer ?: return
        val newPos = (player.currentPosition - 5000L).coerceAtLeast(0L)
        player.seekTo(newPos)
        _playerState.value = _playerState.value.copy(currentPositionMs = newPos)
        updatePlaybackNotification()
    }

    fun stop() {
        val player = exoPlayer ?: return
        player.pause()
        player.seekTo(0L)
        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            currentPositionMs = 0L,
            activeMarkerId = null
        )
        cancelPlaybackNotification()
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive) {
                exoPlayer?.let { p ->
                    val dur = p.duration.coerceAtLeast(0L)
                    val cur = p.currentPosition
                    _playerState.value = _playerState.value.copy(
                        currentPositionMs = cur,
                        totalDurationMs = if (dur > 0) dur else _playerState.value.totalDurationMs
                    )

                    // 每秒刷新一次通知栏进度显示
                    val currentSec = cur / 1000
                    if (currentSec != lastNotificationSec && _playerState.value.isPlaying) {
                        lastNotificationSec = currentSec
                        updatePlaybackNotification()
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
    }

    /**
     * 发送或刷新通知栏媒体控制器卡片
     */
    fun updatePlaybackNotification() {
        val state = _playerState.value
        val file = File(state.currentFilePath)
        if (!file.exists() && state.currentFilePath.isBlank()) {
            cancelPlaybackNotification()
            return
        }

        val fileName = file.name.ifBlank { "通联录音" }
        val currentSec = state.currentPositionMs / 1000
        val totalSec = state.totalDurationMs / 1000
        val currentStr = "%02d:%02d".format(currentSec / 60, currentSec % 60)
        val totalStr = "%02d:%02d".format(totalSec / 60, totalSec % 60)

        val statusTitle = if (state.isPlaying) "▶ 正在播放: $fileName" else "⏸ 播放暂停: $fileName"
        val progressText = "进度: $currentStr / $totalStr"

        // 点击通知唤起主界面
        val openAppIntent = Intent(context, com.ham.audiolog.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // 4 个控制按钮广播 Intent
        val rewindIntent = Intent(context, AudioPlaybackReceiver::class.java).apply {
            action = AudioPlaybackReceiver.ACTION_REWIND_5S
        }
        val rewindPending = PendingIntent.getBroadcast(
            context, 10, rewindIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = Intent(context, AudioPlaybackReceiver::class.java).apply {
            action = AudioPlaybackReceiver.ACTION_PLAY_PAUSE
        }
        val playPausePending = PendingIntent.getBroadcast(
            context, 11, playPauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardIntent = Intent(context, AudioPlaybackReceiver::class.java).apply {
            action = AudioPlaybackReceiver.ACTION_FORWARD_5S
        }
        val forwardPending = PendingIntent.getBroadcast(
            context, 12, forwardIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(context, AudioPlaybackReceiver::class.java).apply {
            action = AudioPlaybackReceiver.ACTION_STOP
        }
        val stopPending = PendingIntent.getBroadcast(
            context, 13, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(statusTitle)
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(stopPending)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_rew, "⏪ -5s", rewindPending)
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "⏸ 暂停" else "▶ 播放",
                playPausePending
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "⏹ 停止", stopPending)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancelPlaybackNotification() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.cancel(NOTIFICATION_ID)
    }

    fun release() {
        cancelPlaybackNotification()
        if (activeInstance == this) {
            activeInstance = null
        }
        stopProgressPolling()
        coroutineScope.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }

    companion object {
        var activeInstance: AudioPlayerManager? = null
            private set

        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "qso_playback_channel"

        /**
         * 全局安全终止任何正在进行的录音回放（如开始新录音时互斥调用）
         */
        fun stopAll() {
            activeInstance?.stop()
        }
    }
}

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentFilePath: String = "",
    val activeMarkerId: Long? = null,
    val currentPositionMs: Long = 0,
    val totalDurationMs: Long = 0
)

