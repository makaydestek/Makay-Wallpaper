package com.makay.wallpaper.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.wallpaper.util.LanguageManager

@Composable
fun FirstLaunchGuideScreen(
    context: Context,
    onDone: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val coreSteps = listOf(
        LanguageManager.t("Hızlı başlat", "Quick start", context) to LanguageManager.t(
            "Ana ekranda «Görsel seç» veya «Klasör seç»e dokunun. Albüm otomatik oluşur ve hemen uygulanır — ekstra adım yok.",
            "On Home tap Pick images or Pick folder. An album is created and applied instantly — no extra steps.",
            context
        ),
        LanguageManager.t("Zamanlayıcı", "Timer", context) to LanguageManager.t(
            "Belirlediğiniz aralıkta otomatik olarak sonraki duvar kağıdına geçin.",
            "Automatically advance wallpapers on your interval.",
            context
        ),
        LanguageManager.t("Programlar", "Schedules", context) to LanguageManager.t(
            "Belirli saatlerde duvar kağıdı değiştirin veya başka bir albüme geçin. Haftanın / yılın gününü seçebilirsiniz.",
            "Change wallpaper or switch albums at set times. Pick days of week or day of year.",
            context
        ),
        LanguageManager.t("Canlı duvar kağıdı", "Live wallpaper", context) to LanguageManager.t(
            "Canlı duvar kağıdı olarak ayarlayın; çift/üç dokunuşla sonraki veya albüm değiştirin.",
            "Set as live wallpaper; double/triple tap to change image or album.",
            context
        )
    )
    val fitSteps = fitHowToSteps().map { s ->
        LanguageManager.t(s.titleTr, s.titleEn, context) to
            LanguageManager.t(s.bodyTr, s.bodyEn, context)
    }
    val steps: List<Pair<String, String>> = coreSteps + fitSteps
    val current = steps[step]
    val total = steps.size

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                LanguageManager.t("Hızlı rehber", "Quick guide", context),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${step + 1}/$total",
                color = Color(0xFF8E8E93),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(28.dp))
            Text(
                current.first,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                current.second,
                color = Color(0xFFAEAEB2),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))
            TvActionButton(
                onClick = {
                    if (step < steps.lastIndex) step++ else onDone()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                idle = Color(0xFF3F8CFF)
            ) {
                Text(
                    if (step < steps.lastIndex)
                        LanguageManager.t("İleri", "Next", context)
                    else
                        LanguageManager.t("Başla", "Start", context),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDone, modifier = Modifier.makayFocus(corner = 10.dp)) {
                Text(
                    LanguageManager.t("Atla", "Skip", context),
                    color = Color(0xFF8E8E93)
                )
            }
        }
    }
}

fun markFirstLaunchDone(context: Context) {
    context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("first_launch_done", true)
        .apply()
}

fun isFirstLaunchDone(context: Context): Boolean =
    context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
        .getBoolean("first_launch_done", false)
