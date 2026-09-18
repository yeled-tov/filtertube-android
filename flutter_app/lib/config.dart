/// כל מה שמשתנה בין סביבות — מפתחות, כתובות ומזהים — יושב כאן ולא פזור
/// בין הקבצים. מי שמכין את האפליקציה לפרסום צריך לקרוא קובץ אחד בלבד.
library;

class AppConfig {
  AppConfig._();

  // ── YouTube Data API v3 ────────────────────────────────────────────────
  /// המפתח הרשמי. משמש לחיפוש ולהעשרת מטא-דאטה בלבד — לא לניגון.
  ///
  /// לפני פרסום בחנות יש להגביל אותו ב-Google Cloud Console לחבילה
  /// ולחתימה של האפליקציה (Android) ולמזהה החבילה (iOS), אחרת כל מי
  /// שיפתח את ה-APK יוכל לשרוף את המכסה.
  static const String youtubeApiKey = 'AIzaSyDLAo5cUv4lt1Tsad50aMGFE0jl-mfRtOk';

  // ── Firebase (דרך REST, בלי SDK מקורי) ─────────────────────────────────
  //
  // ## למה REST ולא firebase_auth
  // ה-SDK המקורי דורש רישום נפרד של אפליקציית Android ואפליקציית iOS
  // בקונסולה, וקבצי google-services.json / GoogleService-Info.plist לכל
  // אחת. REST עובד מול אותו פרויקט עם מפתח Web יחיד, זהה בשתי המערכות,
  // ולכן אותה בנייה עובדת בשתיהן בלי קבצי הגדרה כלל.
  static const String firebaseProjectId = 'filter-tube-52d8e';

  /// מפתח ה-Web של הפרויקט (Project settings → General → Web API Key).
  ///
  /// אם הוא ריק או מוגבל, מסך החשבון יסביר זאת במקום להיכשל בשקט.
  static const String firebaseWebApiKey = 'AIzaSyAgDoBTOHhb0RaPL0vkQtFmVwkr126OpNw';

  static const String functionsBase =
      'https://europe-west1-filter-tube-52d8e.cloudfunctions.net';

  static const String firestoreBase =
      'https://firestore.googleapis.com/v1/projects/$firebaseProjectId/databases/(default)/documents';

  // ── רשימת הערוצים המאושרים ─────────────────────────────────────────────
  static const String channelsHostingUrl =
      'https://filter-tube-52d8e.web.app/channels.json';
  static const String channelsGithubUrl =
      'https://raw.githubusercontent.com/yeled-tov/filtertube-android/main/channels.json';
  static const String channelsAsset = 'assets/data/channels.json';

  // ── חיבור חשבון YouTube (אופציונלי) ────────────────────────────────────
  //
  // מזהה הלקוח מסוג Web של הפרויקט ב-Google Cloud. בלעדיו כפתור החיבור
  // מסביר שהיכולת לא הוגדרה במקום לפתוח מסך שייכשל.
  static const String googleServerClientId = '';

  /// מזהה לקוח iOS (Google Cloud → Credentials → iOS OAuth client).
  static const String googleIosClientId = '';

  static bool get googleSignInConfigured => googleServerClientId.isNotEmpty;

  // ── מי מנהל ────────────────────────────────────────────────────────────
  static const String adminEmail = 'ywldyld@gmail.com';
  static const String supportEmail = 'ywldyld@gmail.com';
  static const String website = 'https://filterphone.com';
  static const String privacyPolicyUrl = 'https://filterphone.com/privacy';
  static const String termsUrl = 'https://filterphone.com/terms';

  /// דף האפליקציה בחנות — ליעד של "שתף" ושל "דרג אותנו".
  static const String storeListingUrl =
      'https://play.google.com/store/apps/details?id=com.filtertube.filtertube';
  static const String appleListingUrl = 'https://apps.apple.com/app/filtertube';

  // ── קישורים שהחנויות דורשות שיהיו נגישים מתוך האפליקציה ───────────────
  static const String youtubeTermsUrl = 'https://www.youtube.com/t/terms';
  static const String googlePrivacyUrl = 'https://policies.google.com/privacy';
}
