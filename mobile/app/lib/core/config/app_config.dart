/// Ilova konfiguratsiyasi — API manzili va konstantalar.
///
/// API base url'ni build vaqtida o'zgartirish mumkin:
///   flutter run --dart-define=API_BASE_URL=http://192.168.1.10:8090/api
class AppConfig {
  AppConfig._();

  /// Android emulyator host mashinaga `10.0.2.2` orqali ulanadi.
  /// iOS simulyator yoki web uchun `localhost` ishlatiladi.
  /// Real qurilmada kompyuteringizning LAN IP manzilini bering.
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8090/api',
  );

  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 20);

  static const String appName = 'Barakat Market';

  /// Bepul yetkazib berish chegarasi (faqat UI ko'rsatish uchun; haqiqiy hisob serverda).
  static const int freeDeliveryThreshold = 300000;

  /// Standart yetkazib berish narxi (UI tahmini; chegaradan yuqorida bepul).
  static const int baseDeliveryFee = 15000;
}
