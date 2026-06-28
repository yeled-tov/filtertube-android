plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.filtertube.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.filtertube.app"
        minSdk = 24            // Android 7.0 — תומך ב-99% מהמכשירים
        targetSdk = 34         // Android 14

        // versionCode נקבע ממספר ה-build של GitHub Actions (להשוואת עדכונים).
        // מקומית ברירת המחדל 2.
        val buildNum = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 2
        versionCode = buildNum
        versionName = "0.2.$buildNum"

        // טוקן דיווח-באגים — מוזרק מ-secret של CI בזמן בנייה (לא נשמר בקוד, לכן לא
        // מבוטל ע"י סריקת הסודות של GitHub). מאפשר לכל לקוח לשלוח דוח באג אוטומטית.
        val bugToken = (project.findProperty("bugToken") as String?) ?: ""
        buildConfigField("String", "BUG_REPORT_TOKEN", "\"$bugToken\"")

        // RTL support
        resourceConfigurations += listOf("en", "iw")
    }

    // חתימה קבועה — מאפשרת עדכוני OTA חלקים (אותו signature תמיד) ו-Google OAuth
    signingConfigs {
        create("shared") {
            storeFile = file("../filtertube.keystore")
            storePassword = "filtertube2026"
            keyAlias = "filtertube"
            keyPassword = "filtertube2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            // ללא applicationIdSuffix — package קבוע com.filtertube.app
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // מביא APIs חדשים (URLDecoder.decode(String,Charset), java.time) לאנדרואיד ישן —
        // בלי זה NewPipe קורס באנדרואיד < 13 עם NoSuchMethodError
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core library desugaring — תיקון קריסות NewPipe באנדרואיד < 13
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Compose BOM — מנהל גרסאות של כל ספריות Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // ExoPlayer (Media3) — נגן וידאו
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")   // שידורים חיים (HLS)
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    // OkHttp + Kotlin coroutines (לקריאות HTTP ל-Supabase + YouTube)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coil — טעינת תמונות אסינכרונית מ-URLs (thumbnails)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // NewPipeExtractor — חילוץ stream URLs מ-YouTube ישירות מהטלפון
implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")

    // Google Sign-In — התחברות לחשבון יוטיוב לייבוא "אהבתי"
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // WorkManager — בדיקת רקע תקופתית להתראות על סרטון חדש
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Firebase — סנכרון ענן/פרופיל
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    testImplementation("junit:junit:4.13.2")
}
