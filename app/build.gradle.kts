import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The release key lives outside the repository; without it, release builds come out unsigned.
val signingFile =
    file(
        System.getenv("SAVEPOINT_SIGNING")
            ?: "${System.getProperty("user.home")}/.local/share/savepoint-signing/keystore.properties"
    )
val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use(::load) }

android {
    namespace = "com.cmyksoda.savepoint"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "com.cmyksoda.savepoint"
        minSdk = 26
        targetSdk = 37
        versionCode = 36
        versionName = "0.7.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (signingFile.exists())
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            // F-Droid rebuilds from the tag and must match the published APK byte for byte.
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Sideloaded installs never get Play's cloud profiles, so the app installs its own.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    // Glance brings WorkManager 2.7, whose shrinker rules drop a constructor the widget needs.
    implementation("androidx.work:work-runtime:2.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
