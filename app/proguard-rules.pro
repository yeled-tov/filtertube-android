# NewPipeExtractor + Rhino (JS interpreter) — שומר על reflection וקריאות native
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.tools.**
-dontwarn org.mozilla.javascript.xml.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp (warning suppressions)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Firebase / Google Play Services ──────────────────────────────────────
# Firestore ו-Auth ממפים JSON למחלקות דרך reflection. ערבול השדות שובר
# את המיפוי בשקט: הקריאה מצליחה, והאובייקט חוזר ריק.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# מודלי הנתונים של FilterTube עוברים סריאליזציה לפי שמות השדות —
# ב-channels.json, בגיבוי לענן ובקאש המקומי. שם שדה מעורבל = קובץ שנשמר
# בגרסה אחת ולא נקרא בגרסה הבאה.
-keep class com.filtertube.app.data.Video { *; }
-keep class com.filtertube.app.data.Channel { *; }
-keepclassmembers class com.filtertube.app.data.** {
    @kotlinx.serialization.SerialName <fields>;
}

# Kotlin serialization: ה-serializer מיוצר כמחלקה מקוננת ונמצא ב-reflection.
-keepclassmembers class ** {
    public static ** INSTANCE;
}
-keepclasseswithmembers class com.filtertube.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose
-dontwarn androidx.compose.**

# Coil
-dontwarn coil.**

# שמות מחלקות אמיתיים לא נשמרים ב-stack trace אחרי ערבול. המיפוי נשמר
# בקובץ mapping.txt של הבנייה, וזה מה שמאפשר לפענח דוח קריסה.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ── Rhino (מנוע ה-JS של NewPipe) ─────────────────────────────────────────
# Rhino נבנה לשרת ומתייחס למחלקות JDK שאינן קיימות באנדרואיד: javax.script
# (ממשק ScriptEngine) ו-jdk.dynalink. הן לעולם לא נטענות בזמן ריצה, אבל
# R8 רואה הפניה למחלקה חסרה מתוך מחלקה ששמורה — ונעצר.
#
# זה בדיוק מה ש-missing_rules.txt של AGP ביקש.
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.lang.invoke.**
-dontwarn org.mozilla.javascript.engine.**
-dontwarn org.mozilla.javascript.jdk18.**
-dontwarn org.mozilla.javascript.tools.**
-dontwarn sun.misc.**

# ── ספריות נוספות ────────────────────────────────────────────────────────
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn javax.naming.**
-dontwarn java.beans.**
