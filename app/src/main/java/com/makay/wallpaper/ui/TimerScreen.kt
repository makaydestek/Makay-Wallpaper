package com.makay.wallpaper.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.core.WallpaperScheduler
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.IntervalUnit
import com.makay.wallpaper.domain.model.TimerSettings
import com.makay.wallpaper.util.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { WallpaperSettingsRepository.getInstance(context) }
    val albums = remember { AlbumRepository.getInstance(context) }
    val timer by repo.timer.collectAsState()
    val activeAlbumId by repo.activeAlbumId.collectAsState()
    var valueText by remember(timer.intervalValue) {
        mutableStateOf(timer.intervalValue.toString())
    }

    fun save(updated: TimerSettings) {
        repo.setTimer(updated)
        if (updated.enabled) {
            WallpaperScheduler.scheduleTimer(context)
        } else {
            // Pasife alınca tüm alarm / WorkManager işlerini iptal et
            WallpaperScheduler.cancelTimer(context)
        }
    }

    fun setEnabled(enabled: Boolean) {
        val v = valueText.toIntOrNull()?.coerceAtLeast(1) ?: timer.intervalValue
        save(timer.copy(intervalValue = v, enabled = enabled))
        Toast.makeText(
            context,
            if (enabled) {
                LanguageManager.t("Zamanlayıcı açıldı", "Timer enabled", context)
            } else {
                LanguageManager.t("Zamanlayıcı kapatıldı", "Timer disabled", context)
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    fun applyNow() {
        val v = valueText.toIntOrNull()?.coerceAtLeast(1) ?: timer.intervalValue
        // Mevcut açık/kapalı durumunu koru — Uygula zorla açmasın
        val updated = timer.copy(intervalValue = v)
        save(updated)

        if (!updated.enabled) {
            Toast.makeText(
                context,
                LanguageManager.t(
                    "Ayarlar kaydedildi — zamanlayıcı kapalı",
                    "Settings saved — timer is off",
                    context
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val albumId = activeAlbumId
        val album = albumId?.let { albums.getById(it) }
        if (albumId == null || album == null || album.images.isEmpty()) {
            Toast.makeText(
                context,
                LanguageManager.t(
                    "Önce bir albüme görsel ekleyin ve aktif yapın",
                    "Add images to an album and activate it first",
                    context
                ),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val ok = WallpaperController.applyCurrent(context)
        Toast.makeText(
            context,
            if (ok) {
                LanguageManager.t(
                    "Zamanlayıcı çalışıyor — duvar kağıdı güncellendi",
                    "Timer running — wallpaper updated",
                    context
                )
            } else {
                LanguageManager.t(
                    "Zamanlayıcı kaydedildi; duvar kağıdı uygulanamadı",
                    "Timer saved; wallpaper could not be applied",
                    context
                )
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        LanguageManager.t("Zamanlayıcı", "Timer", context),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TvIconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvFocusRow(onClick = { setEnabled(!timer.enabled) }) {
                Text(
                    LanguageManager.t("Etkin", "Enabled", context),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = timer.enabled,
                    onCheckedChange = { setEnabled(it) },
                    modifier = Modifier.tvSkipChildFocus()
                )
            }

            Text(
                if (timer.enabled) {
                    LanguageManager.t(
                        "Durum: açık — otomatik değişim sürüyor",
                        "Status: on — auto-change is running",
                        context
                    )
                } else {
                    LanguageManager.t(
                        "Durum: kapalı — otomatik değişim yok",
                        "Status: off — no auto-change",
                        context
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (timer.enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            OutlinedTextField(
                value = valueText,
                onValueChange = {
                    valueText = it.filter { c -> c.isDigit() }.take(5)
                    val v = valueText.toIntOrNull()?.coerceAtLeast(1) ?: return@OutlinedTextField
                    save(timer.copy(intervalValue = v))
                },
                label = { Text(LanguageManager.t("Aralık değeri", "Interval value", context)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(LanguageManager.t("Birim", "Unit", context), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntervalUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = timer.unit == unit,
                        onClick = { save(timer.copy(unit = unit)) },
                        label = {
                            Text(
                                when (unit) {
                                    IntervalUnit.SECONDS -> LanguageManager.t("Sn", "Sec", context)
                                    IntervalUnit.MINUTES -> LanguageManager.t("Dk", "Min", context)
                                    IntervalUnit.HOURS -> LanguageManager.t("Sa", "Hr", context)
                                    IntervalUnit.DAYS -> LanguageManager.t("Gün", "Day", context)
                                }
                            )
                        }
                    )
                }
            }

            TvFocusRow(onClick = { save(timer.copy(random = !timer.random)) }) {
                Text(
                    LanguageManager.t("Rastgele sırada", "Random order", context),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = timer.random,
                    onCheckedChange = { save(timer.copy(random = it)) },
                    modifier = Modifier.tvSkipChildFocus()
                )
            }

            TvActionButton(
                onClick = { applyNow() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(LanguageManager.t("Uygula", "Apply", context))
            }
        }
    }
}
