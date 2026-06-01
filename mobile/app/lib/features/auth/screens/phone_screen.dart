import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../auth_controller.dart';

/// Telefon raqami kiritish va OTP so'rash ekrani.
class PhoneScreen extends ConsumerStatefulWidget {
  const PhoneScreen({super.key});

  @override
  ConsumerState<PhoneScreen> createState() => _PhoneScreenState();
}

class _PhoneScreenState extends ConsumerState<PhoneScreen> {
  final _controller = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  /// "+998 90 123 45 67" → "+998901234567"
  String get _normalizedPhone {
    final digits = _controller.text.replaceAll(RegExp(r'[^0-9]'), '');
    return '+$digits';
  }

  bool get _isValid {
    final digits = _controller.text.replaceAll(RegExp(r'[^0-9]'), '');
    return digits.length >= 12; // 998 + 9 raqam
  }

  Future<void> _submit() async {
    if (!_isValid) {
      setState(() => _error = 'To\'liq telefon raqamini kiriting');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    final phone = _normalizedPhone;
    try {
      final result = await ref.read(authControllerProvider.notifier).requestOtp(phone);
      if (!mounted) return;
      context.push('/otp', extra: {
        'phone': result.phone,
        'devCode': result.devCode,
        'expiresIn': result.expiresInSeconds,
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = ref.read(authControllerProvider.notifier).describeError(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 40),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  gradient: AppColors.primaryGradient,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: const Icon(Icons.shopping_basket_rounded,
                    color: Colors.white, size: 40),
              ),
              const SizedBox(height: 24),
              const Text(
                'Xush kelibsiz!',
                style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 8),
              const Text(
                'Davom etish uchun telefon raqamingizni kiriting. '
                'Sizga tasdiqlash kodi yuboramiz.',
                style: TextStyle(color: AppColors.textSecondary, fontSize: 15, height: 1.4),
              ),
              const SizedBox(height: 32),
              TextField(
                controller: _controller,
                keyboardType: TextInputType.phone,
                autofocus: true,
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'[0-9 +]')),
                  LengthLimitingTextInputFormatter(20),
                ],
                onSubmitted: (_) => _submit(),
                decoration: const InputDecoration(
                  labelText: 'Telefon raqami',
                  hintText: '+998 90 123 45 67',
                  prefixIcon: Icon(Icons.phone_outlined),
                ),
              ),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!, style: const TextStyle(color: AppColors.danger)),
              ],
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: _loading ? null : _submit,
                child: _loading
                    ? const SizedBox(
                        width: 22,
                        height: 22,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5),
                      )
                    : const Text('Kodni olish'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
