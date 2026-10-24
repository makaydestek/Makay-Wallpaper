# Makay Wallpaper

**Makay Eko sistemi** için geliştirilen, Wallpaper Changer Pro'nun tüm ücretsiz ve profesyonel özelliklerini barındıran duwallpaper değiştirici uygulaması.

### Güncelleme (MakayCleaner ile aynı)
- Kaynak: [GitHub Releases — makaydestek/Makay-Wallpaper](https://github.com/makaydestek/Makay-Wallpaper/releases)
- Açılışta otomatik kontrol → bildirim + diyalog → SHA-256 + imza → kurulum
- Ayarlar → **Güncellemeleri Denetle** (manuel)
- Yayın: `APK-Hazirla.bat` (sürüm artar, `APK\MakayWallpaper_vX.Y.Z.apk` üretir, release yükler, eski sürümleri siler)

---

## 📋 Tam Özellik Listesi (Wallpaper Changer Pro'dan Tüm Özellikler)

1. **Reklam yok, veri toplama yok**  
   Uygulama tamamen reklam içermez ve kullanıcı verileri toplamaz. Gizlilik odaklı tasarım.

2. **Sınırsız fotoğraf ekleme**  
   Telefonun dahili depolamadan sınırsız görüntü albume ekleyebilirsiniz.

3. **Klasör taraması ve otomatik ayarlama**  
   DCIM/Kamera gibi klasörleri seçebilirsiniz. Uygulama klasördeki resimleri otomatik tarar; yeni fotoğraflar eklendiğinde manuel intervention gerekmeden taranır ve duvar kağıdı olarak ayarlanır.

4. **Görüntü kırpma yolu**  
   Görüntü kırpma yolu oluşturulabilir. Uygulama sadece kırpma yolunu kaydeder; orijinal görüntü bozulmaz. Kırpma yolunu istediğiniz zaman değiştirebilirsiniz.

5. **Ekran kilit/gizleme olayları**  
   Ana ekran her göründüğünde veya gizlendiğinde (örneğin ekranı kilitlediğinizde veya bir uygulamayı açtığınızda) duvar kağıdını değiştirme seçeneği.

6. **Çift/Üç dokunma gesture**  
   Ana ekrana iki veya üç kez dokunarak:  
   - Sonraki duvara geçiş  
   - Önceki alba geçiş  

7. **Güçlü zamanlayıcı**  
   Saniye, dakika, saat veya gün cinsinden duvar kağıdının otomatik değişecek aralığı ayarlayabilirsiniz.

8. **Tarih/saat tabanlı program**  
   Belirli saatlerde duvar kağıdını değiştirmek için program oluşturma. Haftanın gününe veya yılın gününe göre tekrar eden programlar.

9. **Albüm geçişi programı**  
   Duwallpaper değişimine ek olarak, albümü değiştirmek için de program oluşturma.

10. **Rastgele fotoğraf seçimi**  
    Bir sonraki duvara geçerken albümden rastgele bir fotoğraf seçme.

11. **Pil optimizasyonu**  
    Pil tüketimini minimize edecek şekilde optimize edilmiş.

12. **Basit kullanıcı arayüzü**  
    Temiz ve directly erişilebilir UI.

13. **Ana ekran widget'ı** (yenilik)  
    Ana ekran widget'ı ile hızlı ve kolay duwallpaper değişimi.

14. **Hata düzeltmeleri ve performans iyileştirmeleri**  
    Düzenli hata düzeltmeleri ve performans optimizasyonları.

---

## 🏗️ Altyapı ve Kullanabilirlik Karşılaştırma (Makay Ekosistemleri)

| App | Teknoloji / Altyapı | Kullanabilirlik Özellikleri |
|-----|----------------------|------------------------------|
| **MakayVideoPlayer (YouTubePlayer)** | Kotlin, Gradle, NewPipe/InnerTube entegrasyonu, tek APK (telefon/tablet/TV). | Kapsay Navigation, tema sistemi, OAuth hesap yönetimi, bildirimler, oynatma kontrolü. Derleme ve yayın otomasyonu (APK-Hazirla.bat). |
| **MakayCleaner** | Benzer Gradle yapısı, temizlik/modüle ayrılmış mimari. | Junk dosya tarama, storage yönetimi, CPU/MEM takibi, kullanıcı onayları. |
| **MakayFileManager** | File/ directory operations, SQLite/Room for history, permission handling. | Hierarchical tree view, batch operations, cloud sync options. |
| **MakayUzaktanBaglantı** | Socket/HTTP server, QR kodu paylaşım, Güvenli bağlantı. | Ağ erişim isteği, bağlantı durumu izleme, kullanıcı davet kodu. |
| **MakayLauncher** | Home screen shortcut/widget generation, intent filtering. | Uygulama kısayolları, özelleştirilebilir ana ekran, gesture destek. |
| **Makay Wallpaper (yeni)** | **Paylaşılan**: Gradle + Kotlin, aynı branding/ikon seti, imza anahtarı (shared keystore). **Yeni**: Wallpaper scheduler, folder monitoring, widget, cropping path, random selection. |

### Altyapı Katkıları
- **Gradle / Kotlin**: Tüm Makay uygulamaları Kotlin 1.9+ ve Gradle 8.x üzerinden derlenir.
- **Imza ve Yayın**: Ortak `signing/keystore.properties` ve `.jks` dosyaları ile imzalanır.
- **Branding**: `branding/` klasörü shared; logo, renk paleti, strings kullanılabilir.
- **Güvenlik**: `network_security_config.xml`, `backup_rules.xml`, `proguard-rules.pro` ortak yapı.
- **Yayın Otomasyonı**: `APK-Hazirla.bat` betikleriyle sürüm Artırma -> Derleme -> GitHub Yükle döngüsü ortak.

### Kullanabilirlik Katkıları
- **Temiz UI**: Tüm Makay uygulamaları minimal ve dark-mode destekli arayüzler içerir.
- **Modüler yapı**: Her özellik `ui/`, `util/`, `data/` alt module'lar olarak ayrılır.
- **Global tema**: `Theme.kt` benzeri dosya ile renk/typozorgamma tutarlılığı.
- **Hata yönetimi**: `CrashLogger.kt`, `BackgroundPermissionHelper.kt` gibi kullanılır.
- **Ayarlar ekranı**: `SettingsScreen.kt` kalıbı uygulanır.

### Makay Wallpaper için Mimari Öneri
- **Paylaşılan Gradle modülü**: `core-common` gibi tem kütüphaneler (theme, preferences, permission helpers).
- **Widget modülü**: Android App Widget Provider, `AppWidgetProvider` alt sınıfı.
- **Scheduler modülü**: `WorkManager` veya `AlarmManager` ile zamanlama.
- **Folder monitor**: `FileObserver` veya periyodik `BroadcastReceiver` ile yeni resim tespiti.
- **Cropping**: `Bitmap` manipulation with saving only crop path in `SharedPreferences`/`Room`.
- **Random selection**: Album index randomization.

---

## 🚀 Geliştirme Roadmap (Sprint'ler)

**Sprint 1 – Temel Altyapı**
- Yeni Gradle projesi oluşturma (`settings.gradle.kts`, `build.gradle.kts`).
- Ortak branding/resources kopya (logo, renkler, strings).
- `AndroidManifest.xml` temel izinler (READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, SET_WALLPAPER).
- Temiz `MainActivity` ve `Theme.kt`.

**Sprint 2 – Fotoğraf Import & Album**
- Storage erişimi ve izin yönetimi.
- Klasör seçici UI (RecyclerView with tree).
- `FileObserver` veya `BroadcastReceiver` ile klasör değişikliklerini dinleme.
- Automatik tarama ve album oluşturma.

**Sprint 3 – Kırpma ve Gesture**
- Kırpma yolu UI (Canvas over ImageView).
- Kırpma yolunu SharedPreferences'a kaydetme.
- Çift/Üç dokunma gesture (OnTouchListener).

**Sprint 4 – Zamanlayıcı ve Program**
- WorkManager/AlarmManager ile saniye/gün bazlı scheduled changes.
- Saat/tarih programlayıcı UI (Material DatePicker/TimePicker).
- Rastgele fotoğraf seçici.

**Sprint 5 – Widget ve Optimizasyon**
- AppWidgetProvider ve provider XML’ı.
- Pil optimizasyonları (Background thread, wakelock yönetimi).
- Hata bildirimleri ve logs.

**Sprint 6 – Test ve Yayın**
- Unit testler (JUnit + Kotlin).
- Release APK oluşturma ve imzalama (`APK-Hazirla.bat` uyarlama).
- GitHub Release otomasyonu.

---

## 📂 Proje Yapısı (Özet)

```
D:\MakayWallpaper\
├─ app/
│  ├─ src/
│  │  ├─ main/
│  │  │  ├─ AndroidManifest.xml
│  │  │  ├─ java/
│  │  │  │  └─ com/makay/wallpaper/
│  │  │  │     └─ MakayWallpaperApp.kt (ana aktivite)
│  │  │  ├─ res/
│  │  │  │  ├─ layout/activity_main.xml
│  │  │  │  ├─ values/strings.xml (tüm metinler)
│  │  │  │  └─ values/themes.xml (tema)
│  │  │  └─ res/drawable (ikonlar, widget)
│  │  └─ AndroidManifest.xml (modül)
│  └─ build.gradle.kts (modül)
├─ settings.gradle.kts
├─ build.gradle.kts (root)
├─ gradle/
│  └─ wrapper/ (Gradle wrapper)
├─ branding/ (shared logo, renkler)
├─ signing/ (keystore.properties, .jks)
├─ APK-Hazirla.bat (yayın otomasyonu)
└─ README.md (bu dosya)
```

---

## 🛠️ Başlangıç İçin Oluşturulan Dosyalar

Aşağıdaki dosyalar projenin temel yapısını oluşturur. İleri aşamada özellikler eklenecektir.

### 1. AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.makay.wallpaper">

    <uses-permission android:name="android.permission.SET_WALLPAPER" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Makay Wallpaper"
        android:theme="@style/Theme.Makay.Wallpaper">

        <activity
            android:name=".MakayWallpaperApp"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- App Widget -->
        <receiver android:name=".widget.WallpaperWidgetProvider"
            android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data android:name="android.appwidget.provider"
                android:resource="@xml/wallpaper_widget_info" />
        </receiver>
    </application>

</manifest>
```

### 2. strings.xml (temel dizeler)

```xml
<resources>
    <string name="app_name">Makay Wallpaper</string>
    <string name="action_next">Sonraki</string>
    <string name="action_prev">Önceki</string>
    <string name="settings">Ayarlar</string>
    <string name="schedule">Zamanlayıcı</string>
    <string name="random">Rastgele</string>
    <string name="crop">Kirp</string>
    <!-- daha fazla string ileride eklenecek -->
</resources>
```

### 3. activity_main.xml (temel layout)

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.drawerlayout.widget.DrawerLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/drawer_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:orientation="vertical"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="?attr/colorPrimary"
            android:title="@string/app_name" />

        <FrameLayout
            android:id="@+id/container"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1" />

        <com.google.android.material.bottomnavigation.BottomNavigationView
            android:id="@+id/navigation"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom"
            app:menu="@menu/nav_main" />

    </LinearLayout>

    <NavigationView
        android:id="@+id/nav_view"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        app:menu="@menu/drawer_items" />

</androidx.drawerlayout.widget.DrawerLayout>
```

### 4. MakayWallpaperApp.kt (ana aktivite - minimal)

```kotlin
package com.makay.wallpaper

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController

class MakayWallpaperApp : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))

        val navController: NavController = NavHostFragment.findNavController(R.id.fragment)
        setupActionBarWithNavController(navController, drawerLayout)

        // Basit toast olarak başlangıç mesajı
        Toast.makeText(this, "Makay Wallpaper Hazır", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavUp(): Boolean {
        return false
    }
}
```

### 5. build.gradle.kts (modül level - temel)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.makay.wallpaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.makay.wallpaper"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // Wallpaper changer bağımlılıkları daha sonra eklenecek
}
```

### 6. settings.gradle.kts (root)

```kotlin
dependencyResolutionManagement {
    // ...
}
rootProject.name = "MakayWallpaper"
include(":app")
```

### 7. APK-Hazirla.bat (yayın otomasyonu - makay stiline göre uyarlanabilir)

```bat
@echo off
setlocal

:: Sürüm numarası otomatik artırma
call :bump_version
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

:: Derleme
call gradlew.bat assembleRelease

:: SHA-256 hesaplama
cert -use-jceks -verify -file signing\%PROJECT_NAME%-release.jks >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo İmza doğrulaması başarısız!
    exit /b 1
)

:: APK'ı APK klasörüne kopyala
copy app\build\outputs\apk\release\%PROJECT_NAME%-release.apk APK\%PROJECT_NAME%-%VERSION%.apk

:: SHA-256 dosyası oluştur
cert -use-jceks -verify -file signing\%PROJECT_NAME%-release.jks ^
    -keystore signing\%PROJECT_NAME%-release.jks ^
    -storepass %KEYSTORE_PASSWORD% ^
    -keypass %KEY_PASSWORD% > nul
echo %PROJECT_NAME%-%VERSION%.apk > sha256.tmp
%java_executable_path%\bin\jar.exe -jar ...\hash.jar APK\%PROJECT_NAME%-%VERSION%.apk > sha256.txt

:: GitHub'a yükleme (isteğe bağlı)
if %SKIP_PUBLISH% neq 1 (
    gh release create %VERSION% APK\%PROJECT_NAME%-%VERSION%.apk --notes "Makay Wallpaper %VERSION%"
)

:end
echo Yayın tamam.
exit /b 0

:: Sürüm bump fonksiyonu
:bump_version
set /a NEW_VERSION=%VERSION%+1
set VERSION=%NEW_VERSION%
exit /b 0
```

---

## 📌 Sonraki Adımlar

1. Yukarıdaki dosyaları `D:\MakayWallpaper` dizinine yerleştirin.
2. Gradle sync yapın (`gradlew.bat sync` veya Android Studio).
3. İzin isteklerini (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` vb.) anında yönetin.
4. Feature'leri birer sprintte implement edin (özellik listesi README'de).
5. Branding/resources'ı `branding/` klasöründen alın ve `app/src/main/res` altına kopyalayın.
6. `APK-Hazirla.bat` ile release hazırlayın ve GitHub'a yükleyin.

---
*Bu belge **Makay Eko sistemi**'nin bir parçası olarak oluşturulmuştur. Tüm Makay uygulamalarıyla tutarlı tasarım, altyapı ve yayın süreçleri sağlanmıştır.*