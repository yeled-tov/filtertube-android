import 'dart:math';

import 'package:flutter/material.dart';

import '../../theme.dart';

/// פס ההתקדמות של הנגן — ישר, גלי או מזוגזג, בעובי ובזוהר שנבחרו
/// בהגדרות. שלושת המצבים קיימים גם באפליקציה הראשית.
class SeekBar extends StatelessWidget {
  final double value;
  final int shape; // 0 ישר · 1 גלי · 2 מזוגזג
  final double thickness;
  final bool glow;
  final ValueChanged<double> onChanged;
  final ValueChanged<double> onChangeEnd;

  const SeekBar({
    super.key,
    required this.value,
    required this.onChanged,
    required this.onChangeEnd,
    this.shape = 0,
    this.thickness = 3,
    this.glow = true,
  });

  @override
  Widget build(BuildContext context) {
    return SliderTheme(
      data: SliderTheme.of(context).copyWith(
        trackHeight: thickness,
        trackShape: shape == 0
            ? null
            : _WavyTrackShape(zigzag: shape == 2, glow: glow, accent: AppTheme.accent),
        thumbShape: RoundSliderThumbShape(enabledThumbRadius: thickness + 3),
        overlayShape: RoundSliderOverlayShape(overlayRadius: thickness + 11),
        activeTrackColor: AppTheme.accent,
        inactiveTrackColor: AppTheme.divider,
        thumbColor: AppTheme.accent,
        overlayColor: AppTheme.accent.withValues(alpha: 0.18),
      ),
      child: Slider(
        value: value.clamp(0.0, 1.0),
        onChanged: onChanged,
        onChangeEnd: onChangeEnd,
      ),
    );
  }
}

/// הצורה הלא-ישרה. היא מצוירת ידנית כי Slider לא מאפשר החלפת הנתיב של
/// המסלול — רק של הציור שלו.
class _WavyTrackShape extends SliderTrackShape {
  final bool zigzag;
  final bool glow;
  final Color accent;

  const _WavyTrackShape(
      {required this.zigzag, required this.glow, required this.accent});

  @override
  Rect getPreferredRect({
    required RenderBox parentBox,
    Offset offset = Offset.zero,
    required SliderThemeData sliderTheme,
    bool isEnabled = false,
    bool isDiscrete = false,
  }) {
    final height = sliderTheme.trackHeight ?? 3;
    final width = parentBox.size.width;
    return Rect.fromLTWH(
      offset.dx,
      offset.dy + (parentBox.size.height - height) / 2,
      width,
      height,
    );
  }

  @override
  void paint(
    PaintingContext context,
    Offset offset, {
    required RenderBox parentBox,
    required SliderThemeData sliderTheme,
    required Animation<double> enableAnimation,
    required Offset thumbCenter,
    Offset? secondaryOffset,
    bool isEnabled = false,
    bool isDiscrete = false,
    required TextDirection textDirection,
  }) {
    final rect = getPreferredRect(
      parentBox: parentBox,
      offset: offset,
      sliderTheme: sliderTheme,
    );
    final canvas = context.canvas;
    final centerY = rect.center.dy;
    final stroke = sliderTheme.trackHeight ?? 3;

    Path build(double fromX, double toX) {
      final path = Path()..moveTo(fromX, centerY);
      final amplitude = stroke * 1.6;
      final period = zigzag ? 10.0 : 14.0;
      for (var x = fromX; x < toX; x += period) {
        final next = min(x + period, toX);
        if (zigzag) {
          path.lineTo((x + next) / 2,
              centerY + (((x ~/ period) % 2 == 0) ? -amplitude : amplitude));
          path.lineTo(next, centerY);
        } else {
          path.quadraticBezierTo(
            (x + next) / 2,
            centerY + (((x ~/ period) % 2 == 0) ? -amplitude : amplitude),
            next,
            centerY,
          );
        }
      }
      return path;
    }

    final inactive = Paint()
      ..color = sliderTheme.inactiveTrackColor ?? Colors.grey
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round;
    canvas.drawPath(build(rect.left, rect.right), inactive);

    final active = Paint()
      ..color = sliderTheme.activeTrackColor ?? accent
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round;
    // בכיוון ימין-לשמאל האגודל נע מימין; הציור עדיין בקואורדינטות מסך,
    // ולכן די להשתמש במיקום האגודל עצמו כגבול.
    final start = textDirection == TextDirection.rtl ? thumbCenter.dx : rect.left;
    final end = textDirection == TextDirection.rtl ? rect.right : thumbCenter.dx;
    if (glow) {
      canvas.drawPath(
        build(start, end),
        Paint()
          ..color = accent.withValues(alpha: 0.35)
          ..style = PaintingStyle.stroke
          ..strokeWidth = stroke + 4
          ..strokeCap = StrokeCap.round
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 4),
      );
    }
    canvas.drawPath(build(start, end), active);
  }
}
