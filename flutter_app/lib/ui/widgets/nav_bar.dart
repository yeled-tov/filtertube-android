import 'package:flutter/material.dart';

import '../../theme.dart';

class NavItem {
  final String label;
  final IconData icon;
  const NavItem(this.label, this.icon);
}

/// סרגל ניווט צף — גלולה עם גרדיאנט לפריט הנבחר. אותה שפה עיצובית של
/// האפליקציה הראשית: "הגדרות" הוא לשונית קבועה ולא פריט חבוי בתפריט.
class NavBar extends StatelessWidget {
  final List<NavItem> items;
  final int index;
  final double height;
  final double margin;
  final ValueChanged<int> onTap;

  const NavBar({
    super.key,
    required this.items,
    required this.index,
    required this.onTap,
    this.height = 62,
    this.margin = 16,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(12, 0, 12, margin),
      child: Container(
        height: height,
        decoration: BoxDecoration(
          color: AppTheme.surface.withValues(alpha: 0.96),
          borderRadius: BorderRadius.circular(22),
          border: Border.all(color: AppTheme.divider),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: AppTheme.dark ? 0.45 : 0.12),
              blurRadius: 22,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: List.generate(items.length, (i) {
            final selected = i == index;
            return Expanded(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => onTap(i),
                child: Center(
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 220),
                    curve: Curves.easeOut,
                    padding: EdgeInsets.symmetric(
                        horizontal: selected ? 11 : 8, vertical: 9),
                    decoration: BoxDecoration(
                      gradient: selected ? AppTheme.accentGradient : null,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(items[i].icon,
                            color: selected ? Colors.white : AppTheme.subtext,
                            size: 21),
                        // התווית מופיעה רק על הפריט הנבחר: שש תוויות קבועות
                        // לא נכנסות לרוחב מסך טלפון בלי לקצץ אותן.
                        if (selected) ...[
                          const SizedBox(width: 6),
                          Text(items[i].label,
                              style: const TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w700,
                                  fontSize: 12.5)),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    );
  }
}
