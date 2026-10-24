package com.makay.wallpaper.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makay.wallpaper.core.WallpaperScheduler
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.ScheduleRepository
import com.makay.wallpaper.domain.model.Schedule
import com.makay.wallpaper.domain.model.ScheduleType
import com.makay.wallpaper.util.LanguageManager
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ScheduleRepository.getInstance(context) }
    val albums = remember { AlbumRepository.getInstance(context) }
    val schedules by repo.schedules.collectAsState()
    val albumList by albums.albums.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        LanguageManager.t("Programlar", "Schedules", context),
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
        ) {
            TvActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(LanguageManager.t("Program ekle", "Add schedule", context))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(schedules, key = { it.id }) { schedule ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "%02d:%02d · %s".format(
                                    schedule.hour,
                                    schedule.minute,
                                    if (schedule.type == ScheduleType.ALBUM) {
                                        LanguageManager.t("Albüm", "Album", context)
                                    } else {
                                        LanguageManager.t("Duvar kağıdı", "Wallpaper", context)
                                    }
                                ),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                formatScheduleRepeat(schedule, context),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (schedule.type == ScheduleType.ALBUM) {
                                val name = albumList.find { it.id == schedule.targetAlbumId }?.name
                                    ?: schedule.targetAlbumId.orEmpty()
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Switch(
                                    checked = schedule.enabled,
                                    onCheckedChange = {
                                        repo.setEnabled(schedule.id, it)
                                        WallpaperScheduler.scheduleAllAlarms(context)
                                    }
                                )
                                TextButton(onClick = {
                                    repo.delete(schedule.id)
                                    WallpaperScheduler.scheduleAllAlarms(context)
                                }) {
                                    Text(LanguageManager.t("Sil", "Delete", context))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddScheduleDialog(
            albumIds = albumList.map { it.id to it.name },
            onDismiss = { showAdd = false },
            onSave = { schedule ->
                repo.add(schedule)
                WallpaperScheduler.scheduleAllAlarms(context)
                showAdd = false
            }
        )
    }
}

@Composable
private fun AddScheduleDialog(
    albumIds: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit
) {
    val context = LocalContext.current
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var type by remember { mutableStateOf(ScheduleType.WALLPAPER) }
    var albumId by remember { mutableStateOf(albumIds.firstOrNull()?.first) }
    var hourText by remember { mutableStateOf("8") }
    var minuteText by remember { mutableStateOf("0") }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var useDayOfYear by remember { mutableStateOf(false) }
    var dayOfYearText by remember { mutableStateOf("") }

    val dayLabels = listOf(
        Calendar.SUNDAY to LanguageManager.t("Paz", "Sun", context),
        Calendar.MONDAY to LanguageManager.t("Pzt", "Mon", context),
        Calendar.TUESDAY to LanguageManager.t("Sal", "Tue", context),
        Calendar.WEDNESDAY to LanguageManager.t("Çar", "Wed", context),
        Calendar.THURSDAY to LanguageManager.t("Per", "Thu", context),
        Calendar.FRIDAY to LanguageManager.t("Cum", "Fri", context),
        Calendar.SATURDAY to LanguageManager.t("Cmt", "Sat", context)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LanguageManager.t("Yeni program", "New schedule", context)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = hourText,
                    onValueChange = {
                        hourText = it.filter { c -> c.isDigit() }.take(2)
                        hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: hour
                    },
                    label = { Text(LanguageManager.t("Saat", "Hour", context)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = {
                        minuteText = it.filter { c -> c.isDigit() }.take(2)
                        minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: minute
                    },
                    label = { Text(LanguageManager.t("Dakika", "Minute", context)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == ScheduleType.WALLPAPER,
                        onClick = { type = ScheduleType.WALLPAPER },
                        label = { Text(LanguageManager.t("Duvar kağıdı", "Wallpaper", context)) }
                    )
                    FilterChip(
                        selected = type == ScheduleType.ALBUM,
                        onClick = { type = ScheduleType.ALBUM },
                        label = { Text(LanguageManager.t("Albüm", "Album", context)) }
                    )
                }
                if (type == ScheduleType.ALBUM) {
                    albumIds.forEach { (id, name) ->
                        FilterChip(
                            selected = albumId == id,
                            onClick = { albumId = id },
                            label = { Text(name) }
                        )
                    }
                }

                Text(
                    LanguageManager.t("Tekrar — haftanın günleri", "Repeat — days of week", context),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    LanguageManager.t(
                        "Hiçbiri seçili değilse her gün tekrarlanır. Yılın günü açıksa hafta seçimi yok sayılır.",
                        "If none selected, repeats every day. Day-of-year overrides weekday selection.",
                        context
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dayLabels.forEach { (day, label) ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                selectedDays = if (day in selectedDays) {
                                    selectedDays - day
                                } else {
                                    selectedDays + day
                                }
                                if (selectedDays.isNotEmpty()) useDayOfYear = false
                            },
                            enabled = !useDayOfYear,
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        LanguageManager.t("Yılın günü (1–366)", "Day of year (1–366)", context),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = useDayOfYear,
                        onCheckedChange = {
                            useDayOfYear = it
                            if (it) selectedDays = emptySet()
                        }
                    )
                }
                if (useDayOfYear) {
                    OutlinedTextField(
                        value = dayOfYearText,
                        onValueChange = {
                            dayOfYearText = it.filter { c -> c.isDigit() }.take(3)
                        },
                        label = {
                            Text(
                                LanguageManager.t(
                                    "Örn. 1 = 1 Ocak, 365 = 31 Aralık",
                                    "e.g. 1 = Jan 1, 365 = Dec 31",
                                    context
                                )
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dayOfYear = if (useDayOfYear) {
                    dayOfYearText.toIntOrNull()?.coerceIn(1, 366) ?: 0
                } else {
                    0
                }
                if (useDayOfYear && dayOfYear !in 1..366) return@TextButton
                onSave(
                    Schedule(
                        id = "",
                        type = type,
                        hour = hour,
                        minute = minute,
                        daysOfWeek = if (useDayOfYear) emptySet() else selectedDays,
                        dayOfYear = dayOfYear,
                        enabled = true,
                        targetAlbumId = if (type == ScheduleType.ALBUM) albumId else null
                    )
                )
            }) {
                Text(LanguageManager.t("Kaydet", "Save", context))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LanguageManager.t("İptal", "Cancel", context))
            }
        }
    )
}

private fun formatScheduleRepeat(schedule: Schedule, context: android.content.Context): String {
    if (schedule.dayOfYear in 1..366) {
        return LanguageManager.t(
            "Yılın ${schedule.dayOfYear}. günü",
            "Day ${schedule.dayOfYear} of year",
            context
        )
    }
    if (schedule.daysOfWeek.isEmpty()) {
        return LanguageManager.t("Her gün", "Every day", context)
    }
    val labels = mapOf(
        Calendar.SUNDAY to LanguageManager.t("Paz", "Sun", context),
        Calendar.MONDAY to LanguageManager.t("Pzt", "Mon", context),
        Calendar.TUESDAY to LanguageManager.t("Sal", "Tue", context),
        Calendar.WEDNESDAY to LanguageManager.t("Çar", "Wed", context),
        Calendar.THURSDAY to LanguageManager.t("Per", "Thu", context),
        Calendar.FRIDAY to LanguageManager.t("Cum", "Fri", context),
        Calendar.SATURDAY to LanguageManager.t("Cmt", "Sat", context)
    )
    return schedule.daysOfWeek.sorted().joinToString(", ") { labels[it] ?: it.toString() }
}
