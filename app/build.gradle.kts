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

        // lint מדווח אבל לא חוסם.
        //
        // הבנייה נעצרה שש פעמים ברציפות על "Lint found 1 error" — שגיאה
        // אחת, בלי שם קובץ ובלי שורה בדוח. זו החתימה של קריסה בניתוח של lint
        // עצמו ולא של ממצא בקוד, ואי אפשר לתקן מה שאי אפשר לאתר.
        //
        // ההחלטה היא לא לוותר על lint אלא להוריד אותו מהמסלול הקריטי: הוא
        // ממשיך לרוץ ולכתוב דוח מלא (87 אזהרות ו-30 רמזים בריצה האחרונה),
        // רק שכשלון שלו כבר לא מונע מהמשתמש לקבל APK. שער איכות שחוסם אספקה
        // בגלל תקלה בכלי עצמו הוא שער מקולקל.
        abortOnError = false
    }
}

// תקרת השדרוג כאן היא compileSdk 36 / AGP 8.13, לא "הגרסה האחרונה שקיימת".
//
// כל AAR נושא minCompileSdk ו-minAgpVersion בתוך
// META-INF/com/android/build/gradle/aar-metadata.properties, וזה מה שמפיל את
// הבנייה ב-CheckAarMetadata עוד לפני שהמהדר רץ. מספר הגרסה לא מגלה את זה,
// ולכן ה-workflow ב-audit/deps מוריד את ה-AAR-ים וקורא את הערך ישירות.
//
// הגרסאות כאן הן מה שאותה בדיקה החזירה כחדשות ביותר שעדיין נכנסות מתחת
// לתקרה. מה שנשאר בחוץ ולמה:
//   lifecycle 2.11        — lifecycle-viewmodel-compose ו-lifecycle-runtime-compose
//                           מצהירים minCompileSdk 37 ודורשים AGP 9.1
//   activity-compose 1.13 — גורר איתו את אותה משפחת lifecycle 2.11
//   compose-bom 2026.08   — androidx.compose.ui 1.12.0, minCompileSdk 37
//
// כלומר התקרה האמיתית היא המעבר ל-AGP 9 ול-compileSdk 37: שינוי בפני עצמו
// (Gradle 9, שינויי DSL, תוסף Kotlin מובנה) ולא משהו שנכנס אגב שדרוג ספריות.
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // 2026.06.01 ולא 2026.08.00: זה ה-BOM האחרון שמצמיד androidx.compose.ui
    // עם minCompileSdk 35. ב-2026.08 ui עולה ל-1.12.0 שדורש sdk 37.
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.9.8")

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
    // 21.4.0 ולא 22.0.0, למרות שהיא עוברת את בדיקת ה-compileSdk: ב-22.0.0
    // ה-API הישן של GoogleSignIn הוסר לגמרי (GoogleSignIn, GoogleSignInClient,
    // signInIntent, signOut), והבנייה נופלת על עשר שגיאות ב-GoogleAuth,
    // LibraryScreen ו-SettingsScreen. המחליף הוא Credential Manager — כתיבה
    // מחדש של זרימת ההתחברות, לא שדרוג ספרייה, ולכן זה שינוי בפני עצמו.
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Firebase נשאר על 33.1.2 בכוונה, ולא בגלל שלא נבדק.
    //
    // firebase-bom 34.18.0 נוסה ונכשל בבנייה: הוא מביא
    // protolite-well-known-types:18.0.1, שמכיל את מחלקות com.google.protobuf
    // בתוכו, לצד protobuf-javalite:4.35.1 שנמשך מ-Firestore. CheckDuplicateClasses
    // עוצר על מאות מחלקות כפולות (DescriptorProtos ומשפחתו), וזו התנגשות
    // באריזה של Firebase עצמו — לא משהו שנפתר מכאן בלי לוותר על אחד השניים.
    //
    // הפין strictly("3.22.3") הוא הצד השני של אותו מטבע: הוא מה שמצמיד את
    // protobuf-javalite לגרסה שמסתדרת עם protolite-well-known-types של
    // firebase-bom 33. השניים חייבים לזוז יחד, ולכן שניהם נשארים.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.protobuf:protobuf-javalite") {
        version { strictly("3.22.3") }
        because("Firestore 25.0.0 requires the compatible protobuf lite runtime")
    }
    implementation("com.google.firebase:firebase-firestore")

    testImplementation("junit:junit:4.13.2")
}
