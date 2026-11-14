package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.preferences.StudioSettings
import com.example.di.StudioContainer
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.BanglaStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalStrings
import com.example.ui.navigation.AudioHistoryRoute
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.DownloadedAudioRoute
import com.example.ui.navigation.OfflineStudioRoute
import com.example.ui.navigation.OnlineStudioRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.VoiceLibraryRoute
import com.example.ui.screens.AudioLibraryScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OfflineStudioScreen
import com.example.ui.screens.OnlineStudioScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VoiceLibraryScreen
import com.example.ui.theme.VoiceCloneStudioTheme
import com.example.ui.viewmodel.AudioLibraryViewModel
import com.example.ui.viewmodel.OfflineStudioViewModel
import com.example.ui.viewmodel.OnlineStudioViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.VoiceLibraryViewModel
import kotlinx.coroutines.launch

@Composable
fun StudioApp(container: StudioContainer) {
    val settings by container.settingsDataStore.settingsFlow
        .collectAsStateWithLifecycle(initialValue = StudioSettings())
    val isOnline by container.networkMonitor.isOnline.collectAsStateWithLifecycle()
    val playbackState by container.audioPlayerManager.playbackState.collectAsStateWithLifecycle()
    val allVoices by container.voiceProfileRepository.allVoices
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allClips by container.audioClipDao.observeAllClips()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadedClips by container.audioClipDao.observeDownloadedClips()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val strings = when (settings.appLanguage) {
        AppLanguage.ENGLISH -> EnglishStrings
        AppLanguage.BANGLA -> BanglaStrings
    }

    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()

    val onlineStudioVm: OnlineStudioViewModel = viewModel(
        factory = OnlineStudioViewModel.Factory(
            geminiTtsRepository = container.geminiTtsRepository,
            audioExportManager = container.audioExportManager,
            audioPlayerManager = container.audioPlayerManager,
            voiceProfileRepository = container.voiceProfileRepository,
            settingsDataStore = container.settingsDataStore,
            networkMonitor = container.networkMonitor
        )
    )

    val offlineStudioVm: OfflineStudioViewModel = viewModel(
        factory = OfflineStudioViewModel.Factory(
            offlineTtsRepository = container.offlineTtsRepository,
            audioExportManager = container.audioExportManager,
            audioPlayerManager = container.audioPlayerManager,
            settingsDataStore = container.settingsDataStore
        )
    )

    val voiceLibraryVm: VoiceLibraryViewModel = viewModel(
        factory = VoiceLibraryViewModel.Factory(
            voiceProfileRepository = container.voiceProfileRepository,
            voiceRecorderManager = container.voiceRecorderManager,
            audioPlayerManager = container.audioPlayerManager,
            audioExportManager = container.audioExportManager,
            voiceReplicationRepository = container.voiceReplicationRepository,
            geminiTtsRepository = container.geminiTtsRepository,
            settingsDataStore = container.settingsDataStore,
            networkMonitor = container.networkMonitor
        )
    )

    val audioLibraryVm: AudioLibraryViewModel = viewModel(
        factory = AudioLibraryViewModel.Factory(
            audioClipDao = container.audioClipDao,
            audioExportManager = container.audioExportManager,
            audioPlayerManager = container.audioPlayerManager,
            settingsDataStore = container.settingsDataStore
        )
    )

    val settingsVm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            settingsDataStore = container.settingsDataStore,
            audioExportManager = container.audioExportManager,
            audioPlayerManager = container.audioPlayerManager,
            voiceProfileRepository = container.voiceProfileRepository
        )
    )

    CompositionLocalProvider(LocalStrings provides strings) {
        VoiceCloneStudioTheme(themeMode = settings.themeMode) {
            NavHost(
                navController = navController,
                startDestination = DashboardRoute
            ) {
                composable<DashboardRoute> {
                    DashboardScreen(
                        settings = settings,
                        isOnline = isOnline,
                        voiceCount = allVoices.size,
                        clipCount = allClips.size,
                        downloadedCount = downloadedClips.size,
                        playbackState = playbackState,
                        onToggleMiniPlay = {
                            if (playbackState.isPlaying) {
                                container.audioPlayerManager.pause()
                            } else {
                                container.audioPlayerManager.resume()
                            }
                        },
                        onStopMiniPlay = { container.audioPlayerManager.stop() },
                        onToggleLanguage = {
                            val nextLang = if (settings.appLanguage == AppLanguage.ENGLISH) {
                                AppLanguage.BANGLA
                            } else {
                                AppLanguage.ENGLISH
                            }
                            coroutineScope.launch {
                                container.settingsDataStore.setAppLanguage(nextLang)
                            }
                        },
                        onOpenOnlineStudio = { navController.navigate(OnlineStudioRoute) },
                        onOpenOfflineStudio = { navController.navigate(OfflineStudioRoute) },
                        onOpenVoiceLibrary = { openCreate ->
                            navController.navigate(VoiceLibraryRoute(openCreateTab = openCreate))
                        },
                        onOpenAudioHistory = { navController.navigate(AudioHistoryRoute) },
                        onOpenDownloadedAudio = { navController.navigate(DownloadedAudioRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) }
                    )
                }

                composable<OnlineStudioRoute> {
                    OnlineStudioScreen(
                        viewModel = onlineStudioVm,
                        onBack = { navController.popBackStack() },
                        onNavigateToOfflineStudio = {
                            navController.navigate(OfflineStudioRoute)
                        }
                    )
                }

                composable<OfflineStudioRoute> {
                    OfflineStudioScreen(
                        viewModel = offlineStudioVm,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<VoiceLibraryRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<VoiceLibraryRoute>()
                    VoiceLibraryScreen(
                        viewModel = voiceLibraryVm,
                        initialOpenCreateTab = route.openCreateTab,
                        onBack = { navController.popBackStack() },
                        onUsePresetInOnlineStudio = { presetName ->
                            onlineStudioVm.selectPresetByName(presetName)
                            navController.navigate(OnlineStudioRoute)
                        }
                    )
                }

                composable<AudioHistoryRoute> {
                    AudioLibraryScreen(
                        viewModel = audioLibraryVm,
                        downloadedOnly = false,
                        onBack = { navController.popBackStack() },
                        onOpenOnlineStudio = { navController.navigate(OnlineStudioRoute) }
                    )
                }

                composable<DownloadedAudioRoute> {
                    AudioLibraryScreen(
                        viewModel = audioLibraryVm,
                        downloadedOnly = true,
                        onBack = { navController.popBackStack() },
                        onOpenOnlineStudio = { navController.navigate(OnlineStudioRoute) }
                    )
                }

                composable<SettingsRoute> {
                    SettingsScreen(
                        viewModel = settingsVm,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
