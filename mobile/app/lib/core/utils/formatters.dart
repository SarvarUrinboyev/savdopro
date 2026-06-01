import 'package:intl/intl.dart';

/// Pul va sana formatlovchilar.
class Formatters {
  Formatters._();

  static final NumberFormat _money = NumberFormat('#,###', 'uz');

  /// 55000 → "55 000 so'm"
  static String money(num value) {
    final formatted = _money.format(value).replaceAll(',', ' ');
    return "$formatted so'm";
  }

  /// 55000 → "55 000"
  static String number(num value) {
    return _money.format(value).replaceAll(',', ' ');
  }

  /// ISO sana → "01.06.2026 15:00"
  static String dateTime(DateTime dt) {
    return DateFormat('dd.MM.yyyy HH:mm').format(dt.toLocal());
  }

  static String date(DateTime dt) {
    return DateFormat('dd.MM.yyyy').format(dt.toLocal());
  }
}
