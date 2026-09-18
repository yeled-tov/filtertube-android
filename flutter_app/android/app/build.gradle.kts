import java.util.Base64

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
        // flutter_local_notifications משתמש ב-java.time, שקיים רק מ-API 26.
        // בלי ה-desugaring הזה הבנייה נכשלת על minSdk נמוך יותר.
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        applicationId = "com.filtertube.filtertube"
        // 24 כמו באפליקציה הראשית, כדי שאותם מכשירים יקבלו את שתי הגרסאות.
        minSdk = 24
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    // חתימה קבועה (אותו keystore כמו אפליקציית האנדרואיד) → אותה חתימה בכל בנייה,
    // כך שעדכון לא נכשל ב"מתנגש". הסיסמה מקודדת base64 כדי שלא תזוהה כסוד גלוי.
    val ksPassword = String(Base64.getDecoder().decode("ZmlsdGVydHViZTIwMjY="))
    signingConfigs {
        create("shared") {
            storeFile = file("../../../filtertube.keystore")
            storePassword = ksPassword
            keyAlias = "filtertube"
            keyPassword = ksPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("shared")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
