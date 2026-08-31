package com.ham.audiolog

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ham.audiolog.service.QsoAudioRecorderService
import com.ham.audiolog.ui.screens.recording.RecordingScreen
import com.ham.audiolog.ui.screens.recording.RecordingViewModel
import com.ham.audiolog.ui.screens.sessions.SessionDetailScreen
import com.ham.audiolog.ui.screens.sessions.SessionListScreen
import com.ham.audiolog.ui.screens.sessions.SessionViewModel
import com.ham.audiolog.ui.theme.HamAudioLogTheme

class MainActivity : ComponentActivity() {

    private val recordingViewModel: RecordingViewModel by viewModels()
    private val sessionViewModel: SessionViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        // 显式指定暗色状态栏与导航栏样式，确保系统图标与文字呈明亮白色
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        // 申请 Android 16 麦克风与通知权限
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )

        setContent {
            HamAudioLogTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "recording"
                    ) {
                        composable("recording") {
                            RecordingScreen(
                                viewModel = recordingViewModel,
                                onNavigateToSessions = { navController.navigate("sessions") }
                            )
                        }

                        composable("sessions") {
                            SessionListScreen(
                                viewModel = sessionViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onSessionSelected = { session ->
                                    navController.navigate("session_detail")
                                }
                            )
                        }

                        composable("session_detail") {
                            val state by sessionViewModel.uiState.collectAsState()
                            state.selectedSession?.let { session ->
                                SessionDetailScreen(
                                    viewModel = sessionViewModel,
                                    session = session,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 捕获物理音量下键 (Volume Down)，实现息屏/看环境时的零视觉盲操打点
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val isRecording = QsoAudioRecorderService.recordingState.value.isRecording
            if (isRecording) {
                recordingViewModel.triggerMark()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
