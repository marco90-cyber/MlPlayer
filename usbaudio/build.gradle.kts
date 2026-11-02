// Driver USB Audio Class (usbdevfs, trasferimenti isocroni) di decent-player, licenza MIT
// (vedi LICENSE in questa cartella). Preso da LastWave (audio/decent-usb-audio-driver).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.decent.usbaudio"
    compileSdk = 35
    System.getenv("ANDROID_NDK_VERSION")?.takeIf { it.isNotBlank() }?.let { ndkVersion = it }

    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild { cmake { cppFlags("-fno-fast-math") } }
    }

    externalNativeBuild {
        cmake { path("src/main/jni/CMakeLists.txt") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
