import 'package:flutter/material.dart';
import 'package:in_app_purchase/in_app_purchase.dart';
import 'package:url_launcher/url_launcher.dart';

import '../config.dart';
import '../data/billing.dart';
import '../theme.dart';
import 'widgets/common.dart';

/// FilterTube Premium — מנוי דרך מערכת החיוב של החנות.
///
/// ## מה המסך הזה חייב להכיל
/// שתי החנויות בודקות בדיוק את זה: מה בדיוק נמכר, מה המחיר ובאיזו תדירות,
/// שהמנוי מתחדש מאליו ואיך מבטלים אותו, כפתור "שחזור רכישות", וקישורים
/// לתנאי השימוש ולמדיניות הפרטיות. מסך מנוי שחסר אחד מאלה נדחה — ולכן
/// כולם כאן, ולא באותיות הקטנות.
class PremiumScreen extends StatefulWidget {
  const PremiumScreen({super.key});

  @override
  State<PremiumScreen> createState() => _PremiumScreenState();
}

class _PremiumScreenState extends State<PremiumScreen> {
  bool _buying = false;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appBilling,
      builder: (context, _) => Scaffold(
        backgroundColor: AppTheme.bg,
        appBar: const DetailTopBar('FilterTube Premium'),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(20, 10, 20, 40),
          children: [
            _hero(),
            const SizedBox(height: 22),
            if (appBilling.premiumActive) ..._subscribed() else ..._offer(),
            const SizedBox(height: 26),
            _legal(),
          ],
        ),
      ),
    );
  }

  Widget _hero() => Column(
        children: [
          Container(
            width: 66,
            height: 66,
            decoration: BoxDecoration(
              gradient: AppTheme.accentGradient,
              borderRadius: BorderRadius.circular(20),
            ),
            child: const Icon(Icons.workspace_premium,
                color: Colors.white, size: 34),
          ),
          const SizedBox(height: 14),
          Text(
            appBilling.premiumActive
                ? 'אתה מנוי Premium 🎉'
                : 'פתח את כל היכולות של FilterTube',
            textAlign: TextAlign.center,
            style: TextStyle(
                color: AppTheme.text, fontSize: 19, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 18),
          _benefit(Icons.cloud_done, 'סנכרון וגיבוי בענן',
              'הספרייה, ההיסטוריה והאלבומים נשמרים בחשבון ומשוחזרים בכל מכשיר.'),
          _benefit(Icons.playlist_add_check, 'אלבומים ללא הגבלה',
              'בחינם אפשר ליצור עד ${AppConfig.freePlaylistLimit} אלבומים.'),
          _benefit(Icons.bolt, 'בקשות ערוץ בעדיפות',
              'בקשות של מנויים נבדקות ראשונות.'),
          _benefit(Icons.favorite, 'תמיכה בפרויקט',
              'המנוי הוא מה שמאפשר להמשיך לבדוק ולאשר ערוצים ידנית.'),
        ],
      );

  Widget _benefit(IconData icon, String title, String body) => Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: AppTheme.accentSoft,
                borderRadius: BorderRadius.circular(11),
              ),
              child: Icon(icon, color: AppTheme.accent, size: 19),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title,
                      style: TextStyle(
                          color: AppTheme.text,
                          fontSize: 14,
                          fontWeight: FontWeight.w700)),
                  const SizedBox(height: 2),
                  Text(body,
                      style: TextStyle(
                          color: AppTheme.subtext, fontSize: 12, height: 1.45)),
                ],
              ),
            ),
          ],
        ),
      );

  List<Widget> _subscribed() => [
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppTheme.card,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.tintGreen.withValues(alpha: 0.5)),
          ),
          child: Row(
            children: [
              const Icon(Icons.verified, color: AppTheme.tintGreen, size: 26),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  'המנוי פעיל. הוא מתחדש מאליו עד שמבטלים אותו במסך המנויים '
                  'של החנות.',
                  style: TextStyle(
                      color: AppTheme.subtext2, fontSize: 12.5, height: 1.5),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        OutlinedButton.icon(
          style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(46)),
          onPressed: () => launchUrl(Uri.parse(appBilling.manageUrl),
              mode: LaunchMode.externalApplication),
          icon: const Icon(Icons.open_in_new, size: 18),
          label: const Text('ניהול או ביטול המנוי'),
        ),
      ];

  List<Widget> _offer() {
    if (!appBilling.storeAvailable) {
      return [
        _notice('חנות התשלומים אינה זמינה במכשיר הזה כרגע. '
            'בדוק שאתה מחובר לחשבון החנות ונסה שוב.'),
      ];
    }
    final monthly = appBilling.monthly;
    final yearly = appBilling.yearly;
    if (monthly == null && yearly == null) {
      return [
        _notice(appBilling.lastError.isNotEmpty
            ? appBilling.lastError
            : 'טוען מסלולים…'),
      ];
    }
    return [
      if (yearly != null)
        _plan(yearly, 'שנתי', 'לשנה', badge: 'החיסכון הטוב ביותר'),
      if (monthly != null) _plan(monthly, 'חודשי', 'לחודש'),
      const SizedBox(height: 10),
      TextButton.icon(
        onPressed: appBilling.restoring ? null : () => appBilling.restore(),
        icon: Icon(Icons.restore, size: 17, color: AppTheme.subtext2),
        label: Text(appBilling.restoring ? 'משחזר…' : 'שחזור רכישות',
            style: TextStyle(color: AppTheme.subtext2, fontSize: 13)),
      ),
      if (appBilling.lastError.isNotEmpty)
        Padding(
          padding: const EdgeInsets.only(top: 6),
          child: Text(appBilling.lastError,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppTheme.tintRed, fontSize: 12)),
        ),
    ];
  }

  Widget _plan(ProductDetails product, String title, String period,
      {String badge = ''}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
              color: badge.isEmpty ? AppTheme.divider : AppTheme.accent,
              width: badge.isEmpty ? 1 : 2),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(title,
                    style: TextStyle(
                        color: AppTheme.text,
                        fontSize: 15,
                        fontWeight: FontWeight.w800)),
                const SizedBox(width: 8),
                if (badge.isNotEmpty)
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppTheme.accentSoft,
                      borderRadius: BorderRadius.circular(50),
                    ),
                    child: Text(badge,
                        style:
                            TextStyle(color: AppTheme.accent, fontSize: 10.5)),
                  ),
                const Spacer(),
                Text('${product.price} $period',
                    style: TextStyle(
                        color: AppTheme.text,
                        fontSize: 14,
                        fontWeight: FontWeight.w700)),
              ],
            ),
            const SizedBox(height: 10),
            FilledButton(
              style: FilledButton.styleFrom(
                  backgroundColor: AppTheme.accent,
                  minimumSize: const Size.fromHeight(44)),
              onPressed: _buying
                  ? null
                  : () async {
                      setState(() => _buying = true);
                      await appBilling.buy(product);
                      if (mounted) setState(() => _buying = false);
                    },
              child: Text(_buying ? 'פותח תשלום…' : 'הצטרף — ${product.price}'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _notice(String text) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppTheme.bg2,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppTheme.divider),
        ),
        child: Text(text,
            style: TextStyle(
                color: AppTheme.subtext2, fontSize: 12.5, height: 1.5)),
      );

  Widget _legal() => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'המנוי מתחדש מאליו בסוף כל תקופה, אלא אם מבטלים אותו לפחות 24 שעות '
            'לפני מועד החידוש. החיוב נעשה דרך חשבון החנות שלך, והביטול נעשה '
            'במסך המנויים של החנות — לא באפליקציה.',
            style:
                TextStyle(color: AppTheme.subtext, fontSize: 11.5, height: 1.6),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              _link('תנאי שימוש', AppConfig.termsUrl),
              Text(' · ', style: TextStyle(color: AppTheme.subtext)),
              _link('מדיניות פרטיות', AppConfig.privacyPolicyUrl),
            ],
          ),
        ],
      );

  Widget _link(String label, String url) => InkWell(
        onTap: () =>
            launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication),
        child: Text(label,
            style: TextStyle(color: AppTheme.accent, fontSize: 12)),
      );
}
