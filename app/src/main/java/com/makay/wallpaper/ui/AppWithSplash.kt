package com.makay.wallpaper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.makay.wallpaper.util.LanguageManager
import com.makay.wallpaper.util.PlatformManager

sealed class AppScreen {
    data object Home : AppScreen()
    data object Albums : AppScreen()
    data class AlbumDetail(val id: String) : AppScreen()
    data class Crop(val albumId: String, val uri: String) : AppScreen()
    data object Timer : AppScreen()
    data object Schedules : AppScreen()
    data object Settings : AppScreen()
    data object Permissions : AppScreen()
}

@Composable
fun AppWithSplash() {
    var showSplash by remember { mutableStateOf(true) }
    val context = LocalContext.current
    LanguageManager.init(context)
    PlatformManager.init(context)
    val prefs = remember {
        context.getSharedPreferences("platform_prefs", android.content.Context.MODE_PRIVATE)
    }
    var showPlatformSelect by remember {
        mutableStateOf(prefs.getString("platform_mode", null) == null)
    }
    var showGuide by remember { mutableStateOf(!isFirstLaunchDone(context)) }

    when {
        showSplash -> SplashScreen(onSplashFinished = { showSplash = false })
        showPlatformSelect -> PlatformSelectScreen(
            context = context,
            onSelected = { showPlatformSelect = false }
        )
        showGuide -> FirstLaunchGuideScreen(
            context = context,
            onDone = {
                markFirstLaunchDone(context)
                showGuide = false
            }
        )
        else -> MainAppContent()
    }
}

@Composable
fun MainAppContent() {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var permissionsReturn by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

    BackHandler(enabled = currentScreen !is AppScreen.Home) {
        currentScreen = when (val s = currentScreen) {
            is AppScreen.AlbumDetail -> AppScreen.Albums
            is AppScreen.Crop -> AppScreen.AlbumDetail(s.albumId)
            AppScreen.Permissions -> permissionsReturn
            AppScreen.Albums, AppScreen.Timer, AppScreen.Schedules, AppScreen.Settings -> AppScreen.Home
            else -> AppScreen.Home
        }
    }

    when (val screen = currentScreen) {
        AppScreen.Home -> HomeScreen(
            onNavigateAlbums = { currentScreen = AppScreen.Albums },
            onNavigateTimer = { currentScreen = AppScreen.Timer },
            onNavigateSchedules = { currentScreen = AppScreen.Schedules },
            onNavigateSettings = { currentScreen = AppScreen.Settings },
            onNavigatePermissions = {
                permissionsReturn = AppScreen.Home
                currentScreen = AppScreen.Permissions
            }
        )
        AppScreen.Albums -> AlbumsScreen(
            onNavigateBack = { currentScreen = AppScreen.Home },
            onOpenAlbum = { id -> currentScreen = AppScreen.AlbumDetail(id) },
            onNeedPermission = {
                permissionsReturn = AppScreen.Albums
                currentScreen = AppScreen.Permissions
            }
        )
        is AppScreen.AlbumDetail -> AlbumDetailScreen(
            albumId = screen.id,
            onNavigateBack = { currentScreen = AppScreen.Albums },
            onOpenCrop = { albumId, uri -> currentScreen = AppScreen.Crop(albumId, uri) }
        )
        is AppScreen.Crop -> CropScreen(
            albumId = screen.albumId,
            imageUri = screen.uri,
            onDone = { currentScreen = AppScreen.AlbumDetail(screen.albumId) }
        )
        AppScreen.Timer -> TimerScreen(onNavigateBack = { currentScreen = AppScreen.Home })
        AppScreen.Schedules -> SchedulesScreen(onNavigateBack = { currentScreen = AppScreen.Home })
        AppScreen.Settings -> SettingsScreen(onNavigateBack = { currentScreen = AppScreen.Home })
        AppScreen.Permissions -> PermissionScreen(
            onDone = { currentScreen = permissionsReturn }
        )
    }
}
