plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.filtertube.filtertube"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.filtertube.filtertube"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    // חתימה קבועה (אותו keystore כמו אפליקציית האנדרואיד) → אותה חתימה בכל בנייה,
    // כך שעדכון לא נכשל ב"מתנגש". הסיסמה מגיעה מ-secret של CI (לא נשמרת בקוד).
    val ksPassword: String? = System.getenv("FT_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
    signingConfigs {
        create("shared") {
            storeFile = file("../../../filtertube.keystore")
            storePassword = ksPassword ?: ""
            keyAlias = "filtertube"
            keyPassword = ksPassword ?: ""
        }
    }

    buildTypes {
        release {
            // אם ה-secret קיים — חותמים בחתימה הקבועה; אחרת נופלים ל-debug (זמני)
            signingConfig = if (ksPassword != null)
                signingConfigs.getByName("shared")
            else
                signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
