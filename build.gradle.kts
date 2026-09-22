plugins {
    // compileSdk 36 en az AGP 8.9 istiyor; 8.7.3 ile "compileSdk 36 requires
    // Android Gradle plugin 8.9.0 or higher" diyerek durur. 8.9 mevcut Gradle
    // 8.11.1 ile çalışıyor, yani sarmalayıcıya dokunmak gerekmiyor.
    id("com.android.application") version "8.9.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
