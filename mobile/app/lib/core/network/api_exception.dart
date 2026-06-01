import 'package:dio/dio.dart';

/// Foydalanuvchiga ko'rsatish uchun tushunarli xato.
class ApiException implements Exception {
  final String message;
  final int? statusCode;

  ApiException(this.message, {this.statusCode});

  bool get isUnauthorized => statusCode == 401;

  /// Dio xatosini foydalanuvchi tilidagi xabarga aylantiradi.
  factory ApiException.fromDio(DioException e) {
    final code = e.response?.statusCode;

    // Server "message" maydonini qaytaradi (GlobalExceptionHandler)
    final data = e.response?.data;
    if (data is Map && data['message'] is String &&
        (data['message'] as String).isNotEmpty) {
      return ApiException(data['message'] as String, statusCode: code);
    }

    final message = switch (e.type) {
      DioExceptionType.connectionTimeout ||
      DioExceptionType.sendTimeout ||
      DioExceptionType.receiveTimeout =>
        'Server javob bermayapti. Internetni tekshiring.',
      DioExceptionType.connectionError =>
        'Serverga ulanib bo\'lmadi. Internetni tekshiring.',
      _ => switch (code ?? 0) {
          401 => 'Avtorizatsiya muddati tugadi. Qaytadan kiring.',
          403 => 'Ruxsat yo\'q.',
          404 => 'Topilmadi.',
          >= 500 => 'Serverda xatolik. Birozdan keyin urinib ko\'ring.',
          _ => 'Noma\'lum xatolik yuz berdi.',
        },
    };
    return ApiException(message, statusCode: code);
  }

  @override
  String toString() => message;
}
