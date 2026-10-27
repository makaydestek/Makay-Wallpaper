package com.makay.wallpaper.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * compact=true: özet satır + açılır ayarlar (Albümler sayfası için).
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
    var expanded by remember { mutableStateOf(!compact) }

    fun save(updated: TimerSettings) {
        // Her zaman son timer üzerine yaz — aralık kaydı sıralamayı geri almasın
        val base = repo.timer.value
        val merged = base.copy(
            enabled = updated.enabled,
            intervalValue = updated.intervalValue,
            unit = updated.unit,
            playOrder = updated.playOrder,
            random = updated.random
        )
        repo.setTimer(merged)
        if (merged.enabled) {
            WallpaperScheduler.scheduleTimer(context)
        } else {
            WallpaperScheduler.cancelTimer(context)
        }
    }

    fun setEnabled(enabled: Boolean) {
        val v = valueText.toIntOrNull()?.coerceAtLeast(1) ?: repo.timer.value.intervalValue
        save(repo.timer.value.copy(intervalValue = v, enabled = enabled))
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
        // repo üzerinden: stale Compose state ile playOrder ezilmesin
        repo.setPlayOrder(order)
        val latest = repo.timer.value
        if (latest.enabled) {
            WallpaperScheduler.scheduleTimer(context)
        }
    }

    fun applyNow() {
        val latest = repo.timer.value
        val v = valueText.toIntOrNull()?.coerceAtLeast(1) ?: latest.intervalValue
        val updated = latest.copy(intervalValue = v)
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

    fun unitLabel(unit: IntervalUnit): String = when (unit) {
        IntervalUnit.SECONDS -> LanguageManager.t("Sn", "Sec", context)
        IntervalUnit.MINUTES -> LanguageManager.t("Dk", "Min", context)
        IntervalUnit.HOURS -> LanguageManager.t("Sa", "Hr", context)
        IntervalUnit.DAYS -> LanguageManager.t("Gün", "Day", context)
    }

    fun orderLabel(order: PlayOrder): String = when (order) {
        PlayOrder.SEQUENTIAL -> LanguageManager.t("Sıralı", "Sequential", context)
        PlayOrder.RANDOM -> LanguageManager.t("Rastgele", "Random", context)
        PlayOrder.NEWEST_FIRST -> LanguageManager.t("Yeni → eski", "Newest first", context)
        PlayOrder.OLDEST_FIRST -> LanguageManager.t("Eski → yeni", "Oldest first", context)
    }

    val summary = if (timer.enabled) {
        LanguageManager.t(
            "Her ${timer.intervalValue} ${unitLabel(timer.unit)} · ${orderLabel(playOrder)}",
            "Every ${timer.intervalValue} ${unitLabel(timer.unit)} · ${orderLabel(playOrder)}",
            context
        )
    } else {
        LanguageManager.t("Kapalı", "Off", context)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            Modifier.padding(if (compact) 14.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .then(
                            if (compact) {
                                Modifier.clickable { expanded = !expanded }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            LanguageManager.t("Zamanlayıcı", "Timer", context),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (compact) {
                            Text(
                                if (expanded) "▾" else "▸",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (timer.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(
                    checked = timer.enabled,
                    onCheckedChange = { setEnabled(it) }
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()

                    OutlinedTextField(
                        value = valueText,
                        onValueChange = {
                            valueText = it.filter { c -> c.isDigit() }.take(5)
                            val v = valueText.toIntOrNull()?.coerceAtLeast(1)
                                ?: return@OutlinedTextField
                            save(repo.timer.value.copy(intervalValue = v))
                        },
                        label = {
                            Text(LanguageManager.t("Aralık", "Interval", context))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        LanguageManager.t("Birim", "Unit", context),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    EqualFilterChipGrid(
                        items = IntervalUnit.entries,
                        selected = timer.unit,
                        columns = 2,
                        label = { unitLabel(it) },
                        onSelect = { save(repo.timer.value.copy(unit = it)) }
                    )

                    Text(
                        LanguageManager.t("Sıralama", "Order", context),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    EqualFilterChipGrid(
                        items = PlayOrder.entries,
                        selected = playOrder,
                        columns = 2,
                        label = { orderLabel(it) },
                        onSelect = { setPlayOrder(it) }
                    )

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
    }
}

/** Eşit genişlikte chip ızgarası — düzensiz kaymayı önler. */
@Composable
fun <T> EqualFilterChipGrid(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    columns: Int = 2,
    modifier: Modifier = Modifier
) {
    val rows = items.chunked(columns.coerceAtLeast(1))
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { item ->
                    FilterChip(
                        selected = selected == item,
                        onClick = { onSelect(item) },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                label(item),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
