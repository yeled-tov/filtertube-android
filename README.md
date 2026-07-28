# FilterTube Android

אפליקציית YouTube מסוננת לאנדרואיד שמציגה תוכן מערוצים מאושרים.

במאגר קיימות שתי גרסאות נפרדות:

- אפליקציית Android הראשית בתיקיית השורש — הגרסה המסוננת המבוססת על
  NewPipe/InnerTube ומיועדת להפצה ישירה כ‑APK.
- `flutter_app/` — גרסת החנות הנפרדת, המבוססת על ה‑API הרשמי.

## הורדה והתקנה

1. פתח את [Releases](https://github.com/yeled-tov/filtertube-android/releases).
2. בחר את הגרסה האחרונה והורד `FilterTube.apk`.
3. העבר את הקובץ לטלפון ואפשר התקנה מהמקור שממנו פתחת אותו.

## חשבון, גיבוי ו‑Premium

- הכניסה מתבצעת עם Firebase Email/Password ודורשת אימות כתובת המייל.
- סיסמת החשבון משמשת גם כסיסמת ההורים. היא אינה נשמרת כטקסט גלוי במכשיר
  או ב‑Firestore.
- הפרופיל, היסטוריות, לייקים, הורדות, מנויים ורשימות נשמרים ב‑Firestore
  ומשוחזרים לאחר התחברות.
- תשלום Premium מתבצע ב‑Stripe Checkout. רק Firebase Functions מחזיק את
  מפתחות Stripe; פרטי כרטיס אינם מגיעים לאפליקציה.
- הוראות פריסה מאובטחות נמצאות ב‑[`FIREBASE_DEPLOY.md`](FIREBASE_DEPLOY.md).

## בנייה אוטומטית

GitHub Actions בודק את Firebase Functions ובונה APK חתום מסוג Release בכל
push ל‑`main` או לענף `codex/**`.

- ענף `codex/**` יוצר Artifact לבדיקה בלבד.
- `main` יוצר גם GitHub Release עם `FilterTube.apk`.
- לוג של בנייה שנכשלה נשמר כ‑Artifact ואינו נכתב חזרה למאגר.

מטעמי אבטחה אין PAT של GitHub או מפתח Stripe בתוך ה‑APK.

## טכנולוגיות

- Kotlin 2.0 ו‑Jetpack Compose
- Media3 / ExoPlayer
- NewPipeExtractor ו‑InnerTube
- Firebase Authentication ו‑Cloud Firestore
- Firebase Functions ו‑Stripe Checkout
- minSdk 24, targetSdk 34
