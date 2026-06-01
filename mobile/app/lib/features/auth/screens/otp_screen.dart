import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../auth_controller.dart';

/// OTP kodni kiritish va tasdiqlash. Muvaffaqiyatda router avtomatik
/// asosiy ekranga o'tkazadi (auth holati o'zgaradi).
class OtpScreen extends ConsumerStatefulWidget {
  final String phone;
  final String? devCode;
  final int expiresInSeconds;

  const OtpScreen({
    super.key,
    required this.phone,
    this.devCode,
    required this.expiresInSeconds,
  });

  @override
  ConsumerState<OtpScreen> createState() => _OtpScreenState();
}

class _OtpScreenState extends ConsumerState<OtpScreen> {
  final _controller = TextEditingController();
  bool _loading = false;
  String? _error;
  late int _remaining;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    // Dev rejimda kod avtomatik to'ldiriladi (test qulayligi uchun).
    if (widget.devCode != null && widget.devCode!.isNotEmpty) {
      _controller.text = widget.devCode!;
    }
    _remaining = widget.expiresInSeconds;
    _startTimer();
  }

  void _startTimer() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (_remaining <= 0) {
        t.cancel();
      } else {
        setState(() => _remaining--);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  Future<void> _verify() async {
    final code = _controller.text.trim();
    if (code.length < 4) {
      setState(() => _error = 'Kodni to\'liq kiriting');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).verifyOtp(widget.phone, code);
      // Muvaffaqiyat → router redirect ishlaydi, qo'lda navigatsiya shart emas.
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = ref.read(authControllerProvider.notifier).describeError(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _resend() async {
    setState(() => _error = null);
    try {
      final res = await ref.read(authControllerProvider.notifier).requestOtp(widget.phone);
      if (res.devCode != null && res.devCode!.isNotEmpty) {
        _controller.text = res.devCode!;
      }
      setState(() => _remaining = res.expiresInSeconds);
      _startTimer();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Yangi kod yuborildi')),
        );
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = ref.read(authControllerProvider.notifier).describeError(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Tasdiqlash')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 8),
              Text(
                'Kodni kiriting',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
              ),
              const SizedBox(height: 8),
              Text.rich(
                TextSpan(
                  text: 'Tasdiqlash kodi ',
                  style: const TextStyle(color: AppColors.textSecondary, fontSize: 15),
                  children: [
                    TextSpan(
                      text: widget.phone,
                      style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const TextSpan(text: ' raqamiga yuborildi'),
                  ],
                ),
              ),
              const SizedBox(height: 28),
              TextField(
                controller: _controller,
                keyboardType: TextInputType.number,
                autofocus: true,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 28,
                  letterSpacing: 12,
                  fontWeight: FontWeight.w700,
                ),
                inputFormatters: [
                  FilteringTextInputFormatter.digitsOnly,
                  LengthLimitingTextInputFormatter(6),
                ],
                onSubmitted: (_) => _verify(),
                decoration: const InputDecoration(hintText: '••••••'),
              ),
              if (widget.devCode != null && widget.devCode!.isNotEmpty) ...[
                const SizedBox(height: 10),
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppColors.warning.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    'DEV rejim: kod ${widget.devCode}',
                    style: const TextStyle(color: AppColors.warning, fontWeight: FontWeight.w600),
                  ),
                ),
              ],
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!, style: const TextStyle(color: AppColors.danger)),
              ],
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: _loading ? null : _verify,
                child: _loading
                    ? const SizedBox(
                        width: 22,
                        height: 22,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5),
                      )
                    : const Text('Tasdiqlash'),
              ),
              const SizedBox(height: 16),
              Center(
                child: _remaining > 0
                    ? Text(
                        'Qayta yuborish ${_remaining}s dan keyin',
                        style: const TextStyle(color: AppColors.textSecondary),
                      )
                    : TextButton(
                        onPressed: _resend,
                        child: const Text('Kodni qayta yuborish'),
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
