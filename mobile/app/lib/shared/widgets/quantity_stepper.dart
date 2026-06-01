import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';

/// Miqdor boshqaruvi. Miqdor 0 bo'lsa "Savatga" tugmasi,
/// aks holda [-] miqdor [+] ko'rinishi.
class QuantityStepper extends StatelessWidget {
  final int quantity;
  final VoidCallback onIncrement;
  final VoidCallback onDecrement;
  final bool enabled;
  final bool compact;

  const QuantityStepper({
    super.key,
    required this.quantity,
    required this.onIncrement,
    required this.onDecrement,
    this.enabled = true,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    if (quantity <= 0) {
      return SizedBox(
        height: compact ? 36 : 44,
        child: ElevatedButton(
          onPressed: enabled ? onIncrement : null,
          style: ElevatedButton.styleFrom(
            minimumSize: Size.zero,
            padding: EdgeInsets.symmetric(horizontal: compact ? 12 : 16),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.add_shopping_cart_rounded, size: 18),
              if (!compact) ...[const SizedBox(width: 6), const Text('Savatga')],
            ],
          ),
        ),
      );
    }

    return Container(
      height: compact ? 36 : 44,
      decoration: BoxDecoration(
        color: AppColors.accent,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _btn(Icons.remove, onDecrement),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Text(
              '$quantity',
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w700,
                fontSize: 16,
              ),
            ),
          ),
          _btn(Icons.add, onIncrement),
        ],
      ),
    );
  }

  Widget _btn(IconData icon, VoidCallback onTap) {
    return InkWell(
      onTap: enabled ? onTap : null,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: EdgeInsets.all(compact ? 6 : 8),
        child: Icon(icon, color: Colors.white, size: compact ? 18 : 20),
      ),
    );
  }
}
