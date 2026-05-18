# FilterTube — Android

אפליקציית YouTube מסונן לאנדרואיד, מציגה רק תוכן מערוצים מאושרים.

> 🚧 **גרסה 0.1.0 (MVP)** — בשלב זה רק "Hello World" שמוודא שהבנייה האוטומטית עובדת. הפיצ'רים האמיתיים מגיעים בגרסאות הבאות.

## איך מורידים את ה-APK

1. עבור ל-[Releases](https://github.com/yeled-tov/filtertube-android/releases)
2. בחר את הגרסה האחרונה
3. הורד את `FilterTube-debug.apk`

## איך מתקינים על הטלפון

1. העבר את ה-APK לטלפון (USB / Gmail / Drive / Telegram וכו')
2. בטלפון: הגדרות → אבטחה → אפשר התקנה ממקורות לא ידועים (לאפליקציה שמורידה את הקובץ)
3. פתח את הקובץ במנהל הקבצים ולחץ "התקן"

## פיתוח

האפליקציה נבנית אוטומטית ע"י GitHub Actions בכל push ל-main. אין צורך להתקין Android Studio מקומית.

```
git push origin main → GitHub Actions בונה APK → APK זמין ב-Releases
```

## טכנולוגיות

- Kotlin 2.0
- Jetpack Compose (Material 3)
- ExoPlayer / Media3 (לנגן וידאו)
- minSdk 24 (Android 7.0) → tomers 99% מהמכשירים
- targetSdk 34 (Android 14)

## רוד מאפ

- [x] שלב 1: MVP "Hello World" + CI build
- [ ] שלב 2: חיבור ל-Supabase + רשימת ערוצים
- [ ] שלב 3: NewPipeExtractor + נגן וידאו
- [ ] שלב 4: הורדות + ניגון ברקע
- [ ] שלב 5: חיפוש + Profile + Shorts
- [ ] שלב 6: שחרור ב-F-Droid
