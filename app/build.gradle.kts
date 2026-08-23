import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aripd.compass"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aripd.compass"
        minSdk = 24          // Android 7.0
        // Android 15. Play Store yeni sürümler için 35 istiyor; ayrıca bu
        // seviyeden itibaren kenardan kenara çizim zorunlu ve
        // setDecorFitsSystemWindows(true) yok sayılıyor — pencere boşluklarını
        // uygulama kendisi bırakmak zorunda (bkz. MainActivity.applyInsets).
        targetSdk = 35
        versionCode = 33
        versionName = "4.2"
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
            // R8: kullanılmayan kodu ve kaynakları atar, kalanı küçültür.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

// Uygulama tarafında bilinçli olarak hiçbir dış bağımlılık yok: sadece Android
// SDK + Kotlin stdlib. Aşağıdaki yalnızca testlerde kullanılır ve APK'ya girmez.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
