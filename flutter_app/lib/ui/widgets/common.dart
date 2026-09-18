import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../theme.dart';

/// כותרת קבוצה במסך ההגדרות.
class GroupHeader extends StatelessWidget {
  final String title;
  const GroupHeader(this.title, {super.key});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(22, 22, 22, 8),
        child: Text(
          title,
          style: TextStyle(
            color: AppTheme.subtext,
            fontSize: 12.5,
            fontWeight: FontWeight.w600,
            letterSpacing: 0.2,
          ),
        ),
      );
}

/// כרטיס שמאחד כמה שורות הגדרה — הגבול מסומן ע"י הכרטיס ולא ע"י קווים.
class GroupCard extends StatelessWidget {
  final List<Widget> children;
  const GroupCard({super.key, required this.children});

  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.symmetric(horizontal: 16),
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: AppTheme.divider),
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(children: children),
      );
}

/// שורה בתוך [GroupCard] — אייקון צבעוני, כותרת, תת-כותרת וחץ.
class GroupRow extends StatelessWidget {
  final IconData icon;
  final Color tint;
  final String title;
  final String? subtitle;
  final bool last;
  final bool locked;
  final Widget? trailing;
  final VoidCallback? onTap;

  const GroupRow({
    super.key,
    required this.icon,
    required this.tint,
    required this.title,
    this.subtitle,
    this.last = false,
    this.locked = false,
    this.trailing,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
            child: Row(
              children: [
                Container(
                  width: 30,
                  height: 30,
                  decoration: BoxDecoration(
                    color: tint.withValues(alpha: 0.16),
                    borderRadius: BorderRadius.circular(9),
                  ),
                  child: Icon(icon, color: tint, size: 18),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Flexible(
                            child: Text(title,
                                style: TextStyle(
                                    color: AppTheme.text,
                                    fontSize: 14.5,
                                    fontWeight: FontWeight.w600)),
                          ),
                          if (locked) ...[
                            const SizedBox(width: 6),
                            Icon(Icons.lock_rounded,
                                size: 13, color: AppTheme.subtext),
                          ],
                        ],
                      ),
                      if (subtitle != null && subtitle!.isNotEmpty) ...[
                        const SizedBox(height: 2),
                        Text(subtitle!,
                            style: TextStyle(
                                color: AppTheme.subtext,
                                fontSize: 11.5,
                                height: 1.35)),
                      ],
                    ],
                  ),
                ),
                trailing ??
                    Icon(Icons.chevron_left_rounded,
                        color: AppTheme.divider, size: 22),
              ],
            ),
          ),
        ),
        if (!last)
          Padding(
            padding: const EdgeInsetsDirectional.only(start: 56),
            child: Divider(height: 1, thickness: 1, color: AppTheme.divider),
          ),
      ],
    );
  }
}

/// מתג עם כותרת ותת-כותרת — הדפוס החוזר בכל גיליונות ההגדרות.
class SettingSwitch extends StatelessWidget {
  final String title;
  final String subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;

  const SettingSwitch({
    super.key,
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: TextStyle(
                        color: AppTheme.text,
                        fontSize: 14,
                        fontWeight: FontWeight.w500)),
                if (subtitle.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text(subtitle,
                        style: TextStyle(
                            color: AppTheme.subtext, fontSize: 11, height: 1.35)),
                  ),
              ],
            ),
          ),
          Switch(value: value, onChanged: onChanged),
        ],
      );
}

/// צ'יפ בחירה — הדפוס של "רמה", "מהירות", "איכות" וכל השאר.
class ChoiceChipBox extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  const ChoiceChipBox(
      {super.key,
      required this.label,
      required this.selected,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          gradient: selected ? AppTheme.accentGradient : null,
          color: selected ? null : AppTheme.bg2,
          borderRadius: BorderRadius.circular(50),
          border: Border.all(color: selected ? Colors.transparent : AppTheme.divider),
        ),
        child: Text(label,
            style: TextStyle(
                color: selected ? Colors.white : AppTheme.subtext2,
                fontSize: 12.5,
                fontWeight: FontWeight.w600)),
      ),
    );
  }
}

/// מסך טעינה במרכז — טקסט אחיד לכל המסכים.
class CenteredLoading extends StatelessWidget {
  final String text;
  const CenteredLoading(this.text, {super.key});

  @override
  Widget build(BuildContext context) => Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(color: AppTheme.accent),
            const SizedBox(height: 16),
            Text(text,
                style: TextStyle(color: AppTheme.subtext2, fontSize: 14)),
          ],
        ),
      );
}

class CenteredError extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;
  const CenteredError(this.message, {super.key, required this.onRetry});

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.warning_rounded, color: AppTheme.accent, size: 48),
              const SizedBox(height: 16),
              Text('שגיאה בטעינה',
                  style: TextStyle(
                      color: AppTheme.text,
                      fontSize: 18,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text(message,
                  textAlign: TextAlign.center,
                  style: TextStyle(color: AppTheme.subtext2, fontSize: 13)),
              const SizedBox(height: 24),
              FilledButton(
                onPressed: onRetry,
                style: FilledButton.styleFrom(backgroundColor: AppTheme.accent),
                child: const Text('נסה שוב'),
              ),
            ],
          ),
        ),
      );
}

/// מצב ריק — אייקון, כותרת והסבר. עדיף על מסך לבן בלי הסבר.
class EmptyState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String body;
  final Widget? action;

  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    this.body = '',
    this.action,
  });

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, color: AppTheme.subtext, size: 44),
              const SizedBox(height: 14),
              Text(title,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                      color: AppTheme.text,
                      fontSize: 15,
                      fontWeight: FontWeight.bold)),
              if (body.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(body,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                        color: AppTheme.subtext, fontSize: 12.5, height: 1.5)),
              ],
              if (action != null) ...[const SizedBox(height: 18), action!],
            ],
          ),
        ),
      );
}

/// סרגל עליון של מסך משני — כותרת וחץ חזרה, בלי צל.
class DetailTopBar extends StatelessWidget implements PreferredSizeWidget {
  final String title;
  final List<Widget> actions;
  const DetailTopBar(this.title, {super.key, this.actions = const []});

  @override
  Size get preferredSize => const Size.fromHeight(56);

  @override
  Widget build(BuildContext context) => AppBar(
        backgroundColor: AppTheme.bg,
        surfaceTintColor: Colors.transparent,
        title: Text(title),
        actions: actions,
      );
}

/// אווטאר ערוץ — התמונה האמיתית אם נטענה, אחרת גרדיאנט עם אייקון.
///
/// אות בודדת לא מזהה כלום, ובשם עברי היא גם נראית כמו תקלה — ולכן
/// ברירת המחדל היא אייקון ולא אות.
class ChannelAvatar extends StatelessWidget {
  final String name;
  final String? imageUrl;
  final double size;

  const ChannelAvatar(
      {super.key, required this.name, this.imageUrl, this.size = 34});

  @override
  Widget build(BuildContext context) {
    final url = imageUrl;
    return Container(
      width: size,
      height: size,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        gradient: AppTheme.accentGradient,
        shape: BoxShape.circle,
      ),
      child: url == null || url.isEmpty
          ? Icon(Icons.person_rounded, color: Colors.white, size: size * 0.55)
          : CachedNetworkImage(
              imageUrl: url,
              fit: BoxFit.cover,
              errorWidget: (_, __, ___) => Icon(Icons.person_rounded,
                  color: Colors.white, size: size * 0.55),
            ),
    );
  }
}

/// מציג הודעה קצרה. ריכוז במקום אחד כדי שכל ההודעות ייראו אותו דבר.
void showToast(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(
      content: Text(message),
      duration: const Duration(seconds: 2),
    ));
}
