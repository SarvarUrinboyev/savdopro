import 'package:dio/dio.dart';

import '../config/app_config.dart';
import '../storage/token_storage.dart';
import 'api_exception.dart';

/// Dio asosidagi HTTP klient. Har bir so'rovga JWT qo'shadi,
/// 401 bo'lsa tokenni tozalaydi va onUnauthorized callback'ni chaqiradi.
class ApiClient {
  final Dio dio;
  final TokenStorage _tokenStorage;

  /// 401 yuz berganda chaqiriladi (masalan login ekraniga yo'naltirish).
  void Function()? onUnauthorized;

  ApiClient(this._tokenStorage)
      : dio = Dio(BaseOptions(
          baseUrl: AppConfig.apiBaseUrl,
          connectTimeout: AppConfig.connectTimeout,
          receiveTimeout: AppConfig.receiveTimeout,
          contentType: 'application/json',
        )) {
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final token = await _tokenStorage.read();
        if (token != null && token.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
      onError: (e, handler) async {
        if (e.response?.statusCode == 401) {
          await _tokenStorage.clear();
          onUnauthorized?.call();
        }
        handler.next(e);
      },
    ));
  }

  Future<Response<T>> get<T>(String path, {Map<String, dynamic>? query}) =>
      _wrap(() => dio.get<T>(path, queryParameters: query));

  Future<Response<T>> post<T>(String path, {Object? data}) =>
      _wrap(() => dio.post<T>(path, data: data));

  Future<Response<T>> put<T>(String path, {Object? data}) =>
      _wrap(() => dio.put<T>(path, data: data));

  Future<Response<T>> patch<T>(String path, {Object? data}) =>
      _wrap(() => dio.patch<T>(path, data: data));

  Future<Response<T>> delete<T>(String path) =>
      _wrap(() => dio.delete<T>(path));

  Future<Response<T>> _wrap<T>(Future<Response<T>> Function() call) async {
    try {
      return await call();
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
