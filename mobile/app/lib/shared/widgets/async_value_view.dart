import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../core/theme/app_colors.dart';

/// AsyncValue uchun yagona loading/error/data ko'rsatuvchi.
/// Xato holatida "Qayta urinish" tugmasi bilan [onRetry] ni chaqiradi.
class AsyncValueView<T> extends StatelessWidget {
  final AsyncValue<T> value;
  final Widget Function(T data) data;
  final VoidCallback? onRetry;
  final Widget? loading;

  const AsyncValueView({
    super.key,
    required this.value,
    required this.data,
    this.onRetry,
    this.loading,
  });

  @override
  Widget build(BuildContext context) {
    return value.when(
      skipLoadingOnRefresh: false,
      data: data,
      loading: () => loading ?? const Center(child: CircularProgressIndicator()),
      error: (e, _) => ErrorRetry(message: _msg(e), onRetry: onRetry),
    );
  }

  String _msg(Object e) =>
      e is ApiException ? e.message : 'Xatolik yuz berdi. Qaytadan urinib ko\'ring.';
}

/// Xato + qayta urinish bloki.
class ErrorRetry extends StatelessWidget {
  final String message;
  final VoidCallback? onRetry;
  const ErrorRetry({super.key, required this.message, this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_rounded, size: 56, color: AppColors.textSecondary),
            const SizedBox(height: 12),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.textSecondary, fontSize: 15),
            ),
            if (onRetry != null) ...[
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh),
                label: const Text('Qayta urinish'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
