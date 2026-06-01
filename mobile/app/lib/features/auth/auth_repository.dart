import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../core/network/api_client.dart';
import '../../shared/models/auth_models.dart';

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(ref.watch(apiClientProvider)),
);

class AuthRepository {
  final ApiClient _api;
  AuthRepository(this._api);

  Future<OtpRequestResult> requestOtp(String phone) async {
    final res = await _api.post('/auth/request-otp', data: {'phone': phone});
    return OtpRequestResult.fromJson(res.data as Map<String, dynamic>);
  }

  Future<AuthResult> verifyOtp(String phone, String code) async {
    final res = await _api.post('/auth/verify-otp', data: {'phone': phone, 'code': code});
    return AuthResult.fromJson(res.data as Map<String, dynamic>);
  }

  Future<Customer> me() async {
    final res = await _api.get('/auth/me');
    return Customer.fromJson(res.data as Map<String, dynamic>);
  }

  Future<Customer> updateProfile({String? name, String? email}) async {
    final res = await _api.patch('/auth/me', data: {
      if (name != null) 'name': name,
      if (email != null) 'email': email,
    });
    return Customer.fromJson(res.data as Map<String, dynamic>);
  }
}
