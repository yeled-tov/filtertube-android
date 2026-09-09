import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
}

// נקרא ברמת הקובץ ולא בתוך defaultConfig: שם קיים מאפיין בשם `java`
// שמסתיר את *החבילה* java, ו-java.util.Properties לא מתקמפל שם.
val appVersionName: String = Properties().let { props ->
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
    props.getProperty("versionName")?.trim()?.takeIf { it.isNotEmpty() } ?: "1.0.0"
}

android {
    namespace = "com.filtertube.app"
    compileSdk = 36  // נדרש ע"י media3 ו-androidx החדשים; AGP 8.13 תומך

    defaultConfig {
        applicationId = "com.filtertube.app"
        minSdk = 24            // Android 7.0 — תומך ב-99% מהמכשירים
        targetSdk = 34         // Android 14

        // versionCode = מספר הריצה של GitHub Actions. הוא משותף לבניות טסט
        // ולבניות יציבות (אותו workflow), כך שהוא תמיד עולה ולעולם לא קורה
        // מצב שבו גרסת טסט חדשה נראית "ישנה" מול גרסה יציבה. מקומית: 3.
        val buildNum = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 3
        versionCode = buildNum

        // versionName = הגרסה השיווקית, מתוך version.properties בשורש הפרויקט.
        versionName = appVersionName

        // RTL support
        resourceConfigurations += listOf("en", "iw")
    }

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
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        lintConfig = file("lint.xml")
    }
}

// תקרת השדרוג כאן היא compileSdk 36 / AGP 8.13, לא "הגרסה האחרונה שקיימת".
// core-ktx 1.18+, lifecycle 2.10+, activity-compose 1.12+, navigation 2.10+
// ו-work 2.11+ כולם מצהירים minCompileSdk 37 ודורשים AGP 9.1 ומעלה — הבנייה
// נכשלת עליהם ב-CheckAarMetadata לפני שהמהדר בכלל רץ. המעבר ל-AGP 9 הוא שינוי
// נפרד בפני עצמו (Gradle 9, שינויי DSL), ולכן הוא לא מעורבב עם השדרוג הזה.
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.9.5")

    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    // בלי מספר גרסה בכוונה: ההצהרה רק מוודאת ש-protobuf-javalite נמצא על
    // ה-classpath של זמן הריצה, והגרסה נקבעת ממה ש-Firestore עצמו דורש. בעבר
    // היה כאן strictly("3.22.3") — פין שהתאים ל-Firestore 25, ומול firebase-bom
    // 34 הוא היה כופה שדרוג-לאחור אל מתחת למה שהספרייה מצפה לו.
    implementation("com.google.protobuf:protobuf-javalite") {
        because("Firestore requires the protobuf lite runtime at runtime")
    }
    implementation("com.google.firebase:firebase-firestore")

    testImplementation("junit:junit:4.13.2")
}
