package com.makay.wallpaper.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.makay.wallpaper.domain.model.PlayOrder
import com.makay.wallpaper.domain.model.TimerSettings
import com.makay.wallpaper.util.LanguageManager

/**
 * Zamanlayıcı kontrolleri — Albümler ekranında (ve isteğe bağlı ayrı Timer ekranında) kullanılır.
 */
@Composable
fun TimerControls(modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val repo = remember { WallpaperSettingsRepository.getInstance(context) }
    val albums = remember { AlbumRepository.getInstance(context) }
    val timer by repo.timer.collectAsState()
    val activeAlbumId by repo.activeAlbumId.collectAsState()
    var valueText by remember(timer.intervalValue) {
        mutableStateOf(timer.intervalValue.toString())
    }
    val playOrder = timer.effectivePlayOrder()

    fun save(updated: TimerSettings) {
        repo.setTimer(updated)
        if (updated.enabled) {
            WallpaperScheduler.scheduleTimer(context)
        } else {
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

    fun setPlayOrder(order: PlayOrder) {
        save(
            timer.copy(
                playOrder = order,
                random = order == PlayOrder.RANDOM
            )
        )
        if (order == PlayOrder.RANDOM) {
            repo.clearShuffleQueue()
        }
    }

    fun applyNow() {
        val v = valueText.toIntOrNull()?.coerceAtLeast(1) ?: timer.intervalValue
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.padding(if (compact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    LanguageManager.t("Zamanlayıcı", "Timer", context),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = timer.enabled,
                    onCheckedChange = { setEnabled(it) }
                )
            }

            Text(
                if (timer.enabled) {
                    LanguageManager.t("Açık — otomatik değişim", "On — auto-change", context)
                } else {
                    LanguageManager.t("Kapalı", "Off", context)
                },
                style = MaterialTheme.typography.labelMedium,
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
                label = { Text(LanguageManager.t("Aralık", "Interval", context)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(LanguageManager.t("Birim", "Unit", context), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

            Text(
                LanguageManager.t("Sıralama", "Order", context),
                fontWeight = FontWeight.SemiBold
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = playOrder == PlayOrder.SEQUENTIAL,
                        onClick = { setPlayOrder(PlayOrder.SEQUENTIAL) },
                        label = {
                            Text(LanguageManager.t("Sıralı", "Sequential", context))
                        }
                    )
                    FilterChip(
                        selected = playOrder == PlayOrder.RANDOM,
                        onClick = { setPlayOrder(PlayOrder.RANDOM) },
                        label = {
                            Text(LanguageManager.t("Rastgele", "Random", context))
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = playOrder == PlayOrder.NEWEST_FIRST,
                        onClick = { setPlayOrder(PlayOrder.NEWEST_FIRST) },
                        label = {
                            Text(
                                LanguageManager.t("Yeniden eskiye", "Newest first", context)
                            )
                        }
                    )
                    FilterChip(
                        selected = playOrder == PlayOrder.OLDEST_FIRST,
                        onClick = { setPlayOrder(PlayOrder.OLDEST_FIRST) },
                        label = {
                            Text(
                                LanguageManager.t("Eskiden yeniye", "Oldest first", context)
                            )
                        }
                    )
                }
            }

            TvActionButton(
                onClick = { applyNow() },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(LanguageManager.t("Uygula", "Apply", context))
            }
        }
    }
}
