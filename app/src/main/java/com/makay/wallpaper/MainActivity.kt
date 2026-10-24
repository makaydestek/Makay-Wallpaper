package com.makay.wallpaper

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.makay.wallpaper.data.ThemeRepository
import com.makay.wallpaper.ui.AppWithSplash
import com.makay.wallpaper.ui.theme.MakayWallpaperTheme
import com.makay.wallpaper.update.AppUpdateHost
import com.makay.wallpaper.update.AppUpdateRepository
import java.lang.ref.WeakReference

class MainActivity : FragmentActivity() {

    companion object {
        var instance: WeakReference<MainActivity>? = null
    }

    private lateinit var themeRepository: ThemeRepository

    private val _hasMediaPermission = mutableStateOf(false)
    val hasMediaPermission: State<Boolean> = _hasMediaPermission

    private val _updatePromptToken = mutableLongStateOf(0L)
    private val _updateFromNotification = mutableStateOf(false)
    private val _updateAutoDownload = mutableStateOf(false)

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { checkMediaPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        instance = WeakReference(this)
        enableEdgeToEdge()

        themeRepository = ThemeRepository.getInstance(this)
        checkMediaPermission()
        consumeUpdateIntent(intent)

        setContent {
            MakayWallpaperTheme(themeRepository = themeRepository) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val promptToken by _updatePromptToken
                    val fromNotif by _updateFromNotification
                    val autoDl by _updateAutoDownload
                    Box(Modifier.fillMaxSize()) {
                        AppWithSplash()
                        AppUpdateHost(
                            openFromNotification = fromNotif,
                            autoDownload = autoDl,
                            promptToken = promptToken
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeUpdateIntent(intent)
    }

    private fun consumeUpdateIntent(intent: Intent?) {
        if (intent == null) return
        val show = intent.getBooleanExtra(AppUpdateRepository.EXTRA_SHOW_UPDATE, false)
        if (!show) return
        val auto = intent.getBooleanExtra(AppUpdateRepository.EXTRA_AUTO_DOWNLOAD, false)
        intent.removeExtra(AppUpdateRepository.EXTRA_SHOW_UPDATE)
        intent.removeExtra(AppUpdateRepository.EXTRA_AUTO_DOWNLOAD)
        intent.removeExtra(AppUpdateRepository.EXTRA_UPDATE_TAG)
        _updateFromNotification.value = true
        _updateAutoDownload.value = auto
        _updatePromptToken.longValue = System.currentTimeMillis()
    }

    private fun checkMediaPermission() {
        _hasMediaPermission.value = com.makay.wallpaper.util.PermissionHelper.hasMediaPermission(this)
    }

    fun requestMediaPermission() {
        if (com.makay.wallpaper.util.PermissionHelper.hasMediaPermission(this)) return
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        mediaPermissionLauncher.launch(perms)
    }

    override fun onResume() {
        super.onResume()
        checkMediaPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance?.get() === this) instance = null
    }
}
