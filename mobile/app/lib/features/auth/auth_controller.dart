import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../core/providers.dart';
import '../../shared/models/auth_models.dart';
import 'auth_repository.dart';

/// Sessiya holati. Router shu holatga qarab login/asosiy ekranni tanlaydi.
enum AuthStatus { unknown, authenticated, unauthenticated }

class AuthState {
  final AuthStatus status;
  final Customer? customer;

  const AuthState({required this.status, this.customer});

  const AuthState.unknown() : status = AuthStatus.unknown, customer = null;

  AuthState copyWith({AuthStatus? status, Customer? customer, bool clearCustomer = false}) {
    return AuthState(
      status: status ?? this.status,
      customer: clearCustomer ? null : (customer ?? this.customer),
    );
  }

  bool get isAuthenticated => status == AuthStatus.authenticated;
}

/// Auth sessiyasini boshqaradi: ilova ochilganda tokenni tekshiradi,
/// OTP orqali kirish, chiqish va profil yangilash.
class AuthController extends StateNotifier<AuthState> {
  final Ref _ref;

  AuthController(this._ref) : super(const AuthState.unknown()) {
    // 401 yuz berganda avtomatik chiqish (router login'ga yo'naltiradi).
    _ref.read(apiClientProvider).onUnauthorized = () {
      state = const AuthState(status: AuthStatus.unauthenticated);
    };
    _bootstrap();
  }

  AuthRepository get _repo => _ref.read(authRepositoryProvider);

  /// Ilova ishga tushganda: saqlangan token bo'lsa, /auth/me bilan tekshiramiz.
  Future<void> _bootstrap() async {
    final token = await _ref.read(tokenStorageProvider).read();
    if (token == null || token.isEmpty) {
      state = const AuthState(status: AuthStatus.unauthenticated);
      return;
    }
    try {
      final customer = await _repo.me();
      state = AuthState(status: AuthStatus.authenticated, customer: customer);
    } catch (_) {
      // Token yaroqsiz — tozalaymiz.
      await _ref.read(tokenStorageProvider).clear();
      state = const AuthState(status: AuthStatus.unauthenticated);
    }
  }

  /// Telefon raqamiga OTP yuborish. Natijani (dev kodi bilan) qaytaradi.
  Future<OtpRequestResult> requestOtp(String phone) {
    return _repo.requestOtp(phone);
  }

  /// OTP kodni tasdiqlash. Muvaffaqiyatli bo'lsa tokenni saqlaydi.
  Future<void> verifyOtp(String phone, String code) async {
    final result = await _repo.verifyOtp(phone, code);
    await _ref.read(tokenStorageProvider).write(result.token);
    state = AuthState(status: AuthStatus.authenticated, customer: result.customer);
  }

  Future<void> logout() async {
    await _ref.read(tokenStorageProvider).clear();
    state = const AuthState(status: AuthStatus.unauthenticated);
  }

  /// Profilni yangilaydi (ism/email) va holatdagi customer'ni almashtiradi.
  Future<void> updateProfile({String? name, String? email}) async {
    final updated = await _repo.updateProfile(name: name, email: email);
    state = state.copyWith(customer: updated);
  }

  /// UI uchun qulay: xatoni o'qiladigan matnga aylantiradi.
  String describeError(Object e) =>
      e is ApiException ? e.message : 'Xatolik yuz berdi. Qaytadan urinib ko\'ring.';
}

final authControllerProvider =
    StateNotifierProvider<AuthController, AuthState>((ref) => AuthController(ref));

/// Joriy mijoz (login qilingan bo'lsa).
final currentCustomerProvider = Provider<Customer?>(
  (ref) => ref.watch(authControllerProvider).customer,
);
