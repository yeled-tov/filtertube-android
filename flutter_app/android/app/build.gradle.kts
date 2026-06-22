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

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
