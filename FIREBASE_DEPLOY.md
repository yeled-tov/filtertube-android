# פריסת Firebase ו‑Stripe

הפרויקט מוגדר ל‑Firebase project `filter-tube-52d8e`.

לעולם אין להדביק מפתח Stripe סודי, Signing secret, חשבון שירות או PAT של
GitHub בקוד, בקובץ, ב‑Git או בצ'אט. הסודות נשמרים רק ב‑Firebase Secret
Manager.

## הכנה חד־פעמית

1. ב‑Firebase Console פתח את `filter-tube-52d8e`.
2. ב‑Authentication → Sign-in method הפעל **Email/Password**.
3. ב‑Firestore Database צור מסד במצב **Production**.
4. ב‑Usage and billing ודא שמסלול **Blaze** פעיל ושחשבון החיוב של Google
   Cloud תקין. Cloud Functions דורש זאת.
5. ב‑Stripe הישאר תחילה ב‑**Test mode**. ודא שגם שני ה‑Price IDs שמוגדרים
   לפריסה הם Test prices מאותו חשבון Stripe.

## התחברות לכלי הפריסה

מתיקיית הפרויקט:

```powershell
npm.cmd install -g firebase-tools
firebase login
firebase use filter-tube-52d8e
```

## הגדרת Stripe ללא חלון לא מאובטח

1. צור קובץ מקומי ומוחרג מ‑Git בשם
   `functions/.env.filter-tube-52d8e`, והגדר בו את שני המחירים של אותו מצב:

   ```dotenv
   STRIPE_MONTHLY_PRICE_ID=price_monthly_from_the_current_mode
   STRIPE_YEARLY_PRICE_ID=price_yearly_from_the_current_mode
   ```

   אין ערכי ברירת מחדל בקוד בכוונה: כך אי אפשר לחבר בטעות secret של Live
   למחירים של Test.

2. ב‑Stripe Dashboard → Developers → API keys קח את ה‑Secret key המתאים
   למצב Test והזן אותו ישירות לפקודה המוסתרת:

   ```powershell
   firebase functions:secrets:set STRIPE_SECRET_KEY
   ```

3. עוד לפני הפריסה הראשונה, ב‑Stripe Dashboard → Developers → Webhooks
   צור endpoint לכתובת העתידית:

   ```text
   https://europe-west1-filter-tube-52d8e.cloudfunctions.net/stripeWebhook
   ```

   הגדר את Stripe API version ל‑`2025-06-30.basil` ובחר:

   - `checkout.session.completed`
   - `customer.subscription.created`
   - `customer.subscription.updated`
   - `customer.subscription.deleted`
   - `invoice.paid`
   - `invoice.payment_failed`
   - `invoice.finalization_failed`
   - `invoice.payment_action_required`

   אין צורך שהכתובת כבר תחזיר 200 כדי ליצור את ה‑endpoint.

4. פתח את ה‑endpoint החדש, חשוף את ה‑Signing secret והזן אותו ישירות:

   ```powershell
   firebase functions:secrets:set STRIPE_WEBHOOK_SECRET
   ```

5. רק אחרי ששני הסודות האמיתיים ושני פרמטרי המחיר קיימים, פרוס הכול בפעם אחת:

   ```powershell
   firebase deploy --only functions,firestore:rules,firestore:indexes,hosting
   ```

אסור לפרוס את `stripeWebhook` עם ערך זמני, קבוע או ידוע לציבור.

## הרשאת מנהל

קריאה ואישור של בקשות ערוצים נעשים דרך Functions ודורשים custom claim
בשם `admin`. אין endpoint ציבורי שמאפשר להפוך חשבון למנהל, ואין PAT בתוך
האפליקציה.

אחרי שבעל המאגר יוצר חשבון באפליקציה ומאמת את המייל, מריצים פעם אחת
בסביבה מאובטחת עם Application Default Credentials של הפרויקט. `firebase
login` לבדו אינו מספק הרשאה לסקריפט Admin SDK. במחשב מקומי:

```powershell
gcloud auth application-default login
gcloud config set project filter-tube-52d8e
npm.cmd ci --prefix functions
```

לחלופין אפשר להריץ מתוך Google Cloud Shell כשהפרויקט
`filter-tube-52d8e` נבחר; שם Application Default Credentials כבר זמינים.
לאחר הכנת הסביבה:

```powershell
npm.cmd --prefix functions run set-admin -- owner@example.com
```

הסקריפט ממזג את ההרשאות הקיימות ומוסיף `admin: true` רק לחשבון המאומת
שנמצא לפי המייל. לאחר מכן יש להתנתק ולהתחבר מחדש באפליקציה כדי לרענן את
הטוקן. לעולם אין להפעיל את הסקריפט עם credentials שאינם שייכים לפרויקט.

במקרה של אובדן מכשיר מנהל או חשד לפריצה, מסירים מיד את ההרשאה ומבטלים
את כל ה‑refresh tokens של החשבון:

```powershell
npm.cmd --prefix functions run set-admin -- owner@example.com false
```

עריכת `channels.json` עדיין דורשת PAT של GitHub עם הרשאת Contents למאגר.
מזינים אותו ידנית רק במסך המנהל; הוא מוסתר, נשמר בזיכרון לאותה פתיחה בלבד,
אינו נכתב ל‑SharedPreferences ואינו נשלח ל‑Firebase.

## ניהול וביטול מנוי

ב‑Stripe Dashboard → Settings → Billing → Customer portal הפעל לפחות:

- ביטול מנוי.
- עדכון אמצעי תשלום.
- החלפת מסלול רק בין שני ה‑Price IDs של FilterTube, אם רוצים לאפשר זאת.

אין להוסיף ל‑Portal מוצרים או מחירים אחרים, ויש להשאיר את אמצעי התשלום
המוגדר למנוי ככרטיס.

פרטי הכרטיס נשארים ב‑Stripe ואינם מגיעים לאפליקציה או ל‑Firebase.

## בדיקת Test mode

1. צור חשבון באפליקציה עם אימייל אמיתי וסיסמה של לפחות 6 תווים.
2. אשר את כתובת המייל דרך ההודעה שנשלחה.
3. בחר מסלול Premium ופתח את Stripe Checkout.
4. במצב Test השתמש בכרטיס הבדיקה הרשמי `4242 4242 4242 4242`, תאריך
   עתידי ו‑CVC כלשהו.
5. חזור לאפליקציה ורענן את מצב המנוי. בדוק גם פתיחת Customer portal וביטול.
6. ב‑Stripe וב‑Firebase Functions logs ודא שכל webhook התקבל בהצלחה.

ה‑Checkout מוגבל בכוונה לכרטיס (`card`) כדי ש‑Premium לא יופעל לפני
שהתשלום הושלם. אין להפעיל אמצעי תשלום אסינכרוני בלי להוסיף בדיקת invoice
ששולמה בצד השרת.

במעבר ל‑Live mode יש להשתמש יחד ורק ב‑Secret key, webhook secret ו‑Price
IDs שנוצרו ב‑Live. אסור לערבב אובייקטים של Test ו‑Live.
