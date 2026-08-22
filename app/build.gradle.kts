import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cem.pusula"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cem.pusula"
        minSdk = 24          // Android 7.0
        targetSdk = 34       // Android 13/14 üzerinde sorunsuz çalışır
        versionCode = 3
        versionName = "1.2"
    }


    // İsteğe bağlı: kendi anahtarınızla imzalı release APK.
    // keystore.properties dosyası yoksa bu blok sessizce atlanır, debug APK yine üretilir.
    val keystorePropsFile = rootProject.file("keystore.properties")
    if (keystorePropsFile.exists()) {
        val keystoreProps = Properties().apply {
            keystorePropsFile.inputStream().use { load(it) }
        }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // Üç şemayla da imzala: bazı OEM ROM'ları yalnızca v2 ile
                // imzalı APK'ları "Uygulama yüklenmedi" diyerek reddedebiliyor.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Bilinçli olarak hiçbir dış bağımlılık yok: sadece Android SDK + Kotlin stdlib.
