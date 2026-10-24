package com.makay.wallpaper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.makay.wallpaper.util.LanguageManager
import com.makay.wallpaper.util.PermissionHelper
import com.makay.wallpaper.util.rememberMediaPermissionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val hasPermission = rememberMediaPermissionState()

    LaunchedEffect(hasPermission) {
        if (hasPermission) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        LanguageManager.t("İzinler", "Permissions", context),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TvIconButton(onClick = onDone) {
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
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                LanguageManager.t("Medya erişimi", "Media access", context),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                LanguageManager.t(
                    "Albümlerinize görsel eklemek için fotoğraf / medya izni gerekir.",
                    "Photo / media permission is required to add images to your albums.",
                    context
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            TvActionButton(
                onClick = { PermissionHelper.requestMediaPermission(context) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    LanguageManager.t("İzin ver", "Grant permission", context),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            TvActionButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                idle = MaterialTheme.colorScheme.surfaceVariant,
                idleContent = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(LanguageManager.t("Şimdi değil", "Not now", context))
            }
        }
    }
}
