import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aripd.kerteriz"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aripd.kerteriz"
        minSdk = 24          // Android 7.0
        // Android 15. Play Store yeni sürümler için 35 istiyor; ayrıca bu
        // seviyeden itibaren kenardan kenara çizim zorunlu ve
        // setDecorFitsSystemWindows(true) yok sayılıyor — pencere boşluklarını
        // uygulama kendisi bırakmak zorunda (bkz. MainActivity.applyInsets).
        targetSdk = 35
        versionCode = 35
        versionName = "5.0"
    }


    /**
     * İsteğe bağlı: kendi anahtarınızla imzalı release APK.
     *
     * Bilgiler iki yerden gelebilir. Yerelde kök dizindeki `keystore.properties`
     * okunur. CI'da o dosya yoktur (anahtar da parola da depoya girmez), orada
     * ortam değişkenleri kullanılır. İkisi de yoksa blok sessizce atlanır:
     * release APK imzasız çıkar, debug APK yine üretilir.
     *
     * CI'da dosya yerine ortam değişkeni okunmasının sebebi `.properties`
     * biçiminin kendisi: ters bölü orada kaçış karakteridir ve iki nokta ile
     * eşittir anahtarı bitirir. İçinde bunlardan biri geçen bir parola dosyaya
     * yazıldığında sessizce başka bir parolaya dönüşür ve imzalama "parola
     * yanlış" diyerek kırılır. Ortam değişkeninde böyle bir yorumlama yok.
     */
    val signingValues: Map<String, String>? = run {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) {
            val props = Properties().apply { propsFile.inputStream().use { load(it) } }
            props.getProperty("storeFile")?.let { store ->
                mapOf(
                    "storeFile" to store,
                    "storePassword" to props.getProperty("storePassword").orEmpty(),
                    "keyAlias" to props.getProperty("keyAlias").orEmpty(),
                    "keyPassword" to props.getProperty("keyPassword").orEmpty()
                )
            }
        } else {
            System.getenv("COMPASS_KEYSTORE_FILE")?.let { store ->
                mapOf(
                    "storeFile" to store,
                    "storePassword" to System.getenv("COMPASS_KEYSTORE_PASSWORD").orEmpty(),
                    "keyAlias" to System.getenv("COMPASS_KEY_ALIAS").orEmpty(),
                    "keyPassword" to System.getenv("COMPASS_KEY_PASSWORD").orEmpty()
                )
            }
        }
    }

    if (signingValues != null) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(signingValues["storeFile"]!!)
                storePassword = signingValues["storePassword"]
                keyAlias = signingValues["keyAlias"]
                keyPassword = signingValues["keyPassword"]
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
            if (signingValues != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    /**
     * Play'e yüklenen paket (AAB) dil başına parçalara ayrılır: kullanıcı yirmi
     * sekiz dilin metnini değil yalnızca kendi dilininkini indirir. Üçü de AGP'nin
     * varsayılanı; burada açıkça yazılmalarının sebebi bunun bilinçli bir tercih
     * olduğunu belgelemek. 10. bölümdeki ölçümle birlikte okunmalı: tek parça APK
     * 300 KB, bunun dörtte üçü metin.
     *
     * Doğrudan dağıtılan APK'yı etkilemez; orada bütün diller birliktedir.
     */
    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
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
