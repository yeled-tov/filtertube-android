import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:in_app_purchase/in_app_purchase.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config.dart';

final appBilling = Billing();

/// מנוי FilterTube Premium — דרך מערכת החיוב של החנות בלבד.
///
/// ## למה לא תשלום חיצוני
/// Google Play ו-App Store מחייבות שמנוי לתוכן דיגיטלי בתוך האפליקציה
/// ייגבה דרכן. מסך שמפנה למייל, לקישור או ל-Stripe הוא עילת דחייה
/// מפורשת — ולכן כאן יש רק `in_app_purchase`, התוסף הרשמי שעוטף את
/// Google Play Billing ואת StoreKit.
///
/// ## למה הזכאות נשמרת גם מקומית
/// החנות היא מקור האמת, אבל היא נשאלת ברשת. משתמש שפתח את האפליקציה בלי
/// חיבור לא אמור לגלות שהמנוי שלו "נעלם", ולכן המצב האחרון שנודע נשמר
/// במכשיר ומשמש עד שהחנות עונה.
class Billing extends ChangeNotifier {
  static const String _kActive = 'premium_active';
  static const String _kProduct = 'premium_product';

  final InAppPurchase _iap = InAppPurchase.instance;
  StreamSubscription<List<PurchaseDetails>>? _sub;

  bool _available = false;
  bool _active = false;
  String _productId = '';
  List<ProductDetails> _products = const [];
  String lastError = '';
  bool restoring = false;

  /// האם חנות התשלומים בכלל זמינה במכשיר הזה.
  bool get storeAvailable => _available;

  /// האם המנוי פעיל.
  bool get premiumActive => _active;

  String get activeProductId => _productId;
  List<ProductDetails> get products => _products;

  ProductDetails? get monthly => _byId(AppConfig.premiumMonthlyId);
  ProductDetails? get yearly => _byId(AppConfig.premiumYearlyId);

  ProductDetails? _byId(String id) {
    for (final p in _products) {
      if (p.id == id) return p;
    }
    return null;
  }

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    _active = prefs.getBool(_kActive) ?? false;
    _productId = prefs.getString(_kProduct) ?? '';
    notifyListeners();

    try {
      _available = await _iap.isAvailable();
    } catch (_) {
      _available = false;
    }
    if (!_available) {
      notifyListeners();
      return;
    }

    _sub = _iap.purchaseStream.listen(
      _onPurchases,
      onError: (Object error) => lastError = 'שגיאה בחיבור לחנות',
    );
    await _loadProducts();
    // שחזור בעלייה: מנוי שנרכש במכשיר אחר, או התקנה מחדש, צריכים לחזור
    // בלי שהמשתמש יצטרך לחפש כפתור.
    await restore(silent: true);
  }

  Future<void> _loadProducts() async {
    try {
      final response = await _iap.queryProductDetails(AppConfig.premiumProductIds);
      _products = response.productDetails;
      if (_products.isEmpty) {
        lastError = 'לא נמצאו מסלולי מנוי. ודא שהמוצרים מוגדרים בחנות.';
      }
    } catch (_) {
      lastError = 'לא ניתן לטעון את מסלולי המנוי כרגע';
    }
    notifyListeners();
  }

  Future<void> _onPurchases(List<PurchaseDetails> purchases) async {
    var changed = false;
    for (final purchase in purchases) {
      if (purchase.status == PurchaseStatus.error) {
        lastError = purchase.error?.message ?? 'הרכישה נכשלה';
      }
      if (purchase.status == PurchaseStatus.purchased ||
          purchase.status == PurchaseStatus.restored) {
        if (AppConfig.premiumProductIds.contains(purchase.productID)) {
          _active = true;
          _productId = purchase.productID;
          changed = true;
        }
      }
      // חובה: בלי completePurchase החנות חוזרת על אותה רכישה בכל פתיחה,
      // ובאנדרואיד היא אף מבוטלת אוטומטית אחרי שלושה ימים.
      if (purchase.pendingCompletePurchase) {
        await _iap.completePurchase(purchase);
      }
    }
    if (changed) await _persist();
    restoring = false;
    notifyListeners();
  }

  Future<void> _persist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_kActive, _active);
    await prefs.setString(_kProduct, _productId);
  }

  Future<bool> buy(ProductDetails product) async {
    lastError = '';
    if (!_available) {
      lastError = 'חנות התשלומים אינה זמינה במכשיר הזה';
      notifyListeners();
      return false;
    }
    try {
      return await _iap.buyNonConsumable(
        purchaseParam: PurchaseParam(productDetails: product),
      );
    } catch (_) {
      lastError = 'לא ניתן לפתוח את מסך התשלום';
      notifyListeners();
      return false;
    }
  }

  /// שחזור רכישות — שתי החנויות דורשות שהכפתור הזה יהיה נגיש במסך המנוי.
  Future<void> restore({bool silent = false}) async {
    if (!_available) return;
    if (!silent) {
      restoring = true;
      lastError = '';
      notifyListeners();
    }
    try {
      await _iap.restorePurchases();
    } catch (_) {
      restoring = false;
      if (!silent) {
        lastError = 'השחזור נכשל. נסה שוב בעוד רגע.';
        notifyListeners();
      }
    }
  }

  String get manageUrl => Platform.isIOS
      ? AppConfig.manageSubscriptionApple
      : AppConfig.manageSubscriptionAndroid;

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }
}
