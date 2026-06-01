import 'package:flutter/material.dart';

/// Barakat brend ranglari (logodan olingan: chuqur ko'k gradient + yashil aksent).
class AppColors {
  AppColors._();

  // Primary — chuqur ko'k
  static const Color primary = Color(0xFF33608F);
  static const Color primaryDark = Color(0xFF15293F);
  static const Color primaryLight = Color(0xFF5B85B5);

  // Accent — yashil (savat strelkasi rangidan)
  static const Color accent = Color(0xFF22C55E);
  static const Color accentDark = Color(0xFF16A34A);

  // Neytral
  static const Color background = Color(0xFFF6F8FB);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color textPrimary = Color(0xFF1A2433);
  static const Color textSecondary = Color(0xFF6B7787);
  static const Color border = Color(0xFFE3E8EF);

  // Holat ranglari
  static const Color danger = Color(0xFFEF4444);
  static const Color warning = Color(0xFFF59E0B);
  static const Color success = Color(0xFF22C55E);

  static const LinearGradient primaryGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [primary, primaryDark],
  );
}
