package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data object OnlineStudioRoute

@Serializable
data object OfflineStudioRoute

@Serializable
data class VoiceLibraryRoute(val openCreateTab: Boolean = false)

@Serializable
data object AudioHistoryRoute

@Serializable
data object DownloadedAudioRoute

@Serializable
data object SettingsRoute
