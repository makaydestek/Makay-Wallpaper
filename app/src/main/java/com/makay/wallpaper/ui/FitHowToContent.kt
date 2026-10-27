package com.makay.wallpaper.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makay.wallpaper.util.LanguageManager

data class HowToStep(
    val titleTr: String,
    val titleEn: String,
    val bodyTr: String,
    val bodyEn: String
)

/** “Nasıl kullanılır” — ekrana sığdırma rehberi (tüm maddeler). */
fun fitHowToSteps(): List<HowToStep> = listOf(
    HowToStep(
        "Ayarlar → Ekrana sığdırma",
        "Settings → Fit to screen",
        "Ana menüden Ayarlar’ı açın. “Ekrana sığdırma” bölümünden görsellerin telefona nasıl oturacağını yönetin.",
        "Open Settings from the main menu. Use “Fit to screen” to control how images fill the phone."
    ),
    HowToStep(
        "Sığdırma modu",
        "Fit mode",
        "• Doldur (önerilen): Ekranı tamamen kaplar; fazla kenarlar kırpılabilir.\n• Sığdır: Tüm görsel görünür; kenarlarda şerit olabilir.\n• Esnet: Oran bozulabilir; görsel zorla ekran boyutuna çekilir.",
        "• Fill (recommended): Covers the screen; edges may be cropped.\n• Fit: Whole image visible; bars may appear.\n• Stretch: May distort; forces image to screen size."
    ),
    HowToStep(
        "Hedef çözünürlük → Özel (Custom)",
        "Target resolution → Custom",
        "“Özel (Custom)” seçin. Telefonunuz için hazır kısayol: 1220×2712 (dikey). Yatay otomatik olarak 2712×1220 olur. İsterseniz “Cihazdan al” ile anlık ekran boyutunu yazın veya değerleri elle girin.",
        "Select “Custom”. Preset for your phone: 1220×2712 (portrait). Landscape becomes 2712×1220 automatically. Use “From device” or type values manually."
    ),
    HowToStep(
        "Ekran oranına kırp",
        "Crop to screen ratio",
        "Albümde bir görsele dokunup Kırp ekranına girin. “Ekran oranına kırp” ile kırpma kutusunu hedef çözünürlük oranına kilitleyin; kaydedin. Sonra duvar kağıdı olarak uygulayın.",
        "Open an image from an album into Crop. Tap “Crop to screen ratio” to lock the box to your target aspect, save, then apply as wallpaper."
    )
)

@Composable
fun FitHowToGuideCard(context: Context) {
    SettingsHelpSection(
        titleTr = "Nasıl kullanılır",
        titleEn = "How to use",
        initiallyExpanded = false
    ) {
        fitHowToSteps().forEachIndexed { index, step ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${index + 1}.",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        LanguageManager.t(step.titleTr, step.titleEn, context),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        LanguageManager.t(step.bodyTr, step.bodyEn, context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
