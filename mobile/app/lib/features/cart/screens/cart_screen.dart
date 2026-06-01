import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/config/app_config.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../shared/models/cart_models.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/network_image_box.dart';
import '../../../shared/widgets/quantity_stepper.dart';
import '../cart_controller.dart';

/// Savatcha ekrani.
class CartScreen extends ConsumerWidget {
  const CartScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cart = ref.watch(cartProvider);
    final controller = ref.read(cartProvider.notifier);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Savatcha'),
        actions: [
          if (!cart.isEmpty)
            TextButton(
              onPressed: () => _confirmClear(context, controller),
              child: const Text('Tozalash'),
            ),
        ],
      ),
      body: cart.isEmpty
          ? EmptyState(
              icon: Icons.shopping_cart_outlined,
              title: 'Savatcha bo\'sh',
              subtitle: 'Mahsulotlarni qo\'shib, xaridni boshlang',
              action: ElevatedButton(
                onPressed: () => context.go('/home'),
                child: const Text('Xaridni boshlash'),
              ),
            )
          : Column(
              children: [
                Expanded(
                  child: ListView.separated(
                    padding: const EdgeInsets.all(16),
                    itemCount: cart.items.length,
                    separatorBuilder: (_, __) => const Divider(height: 24),
                    itemBuilder: (_, i) => _CartRow(item: cart.items[i]),
                  ),
                ),
                _Summary(cart: cart),
              ],
            ),
    );
  }

  void _confirmClear(BuildContext context, CartController controller) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Savatchani tozalash'),
        content: const Text('Barcha mahsulotlar o\'chiriladi. Davom etasizmi?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Bekor')),
          TextButton(
            onPressed: () {
              controller.clear();
              Navigator.pop(ctx);
            },
            child: const Text('Tozalash', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
  }
}

class _CartRow extends ConsumerWidget {
  final CartItemModel item;
  const _CartRow({required this.item});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cart = ref.read(cartProvider.notifier);
    return Row(
      children: [
        SizedBox(
          width: 64,
          height: 64,
          child: NetworkImageBox(imageUrl: item.imageUrl, radius: 12),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                item.name,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 4),
              Text(
                Formatters.money(item.price),
                style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
              ),
              const SizedBox(height: 2),
              Text(
                Formatters.money(item.lineTotal),
                style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.primary),
              ),
            ],
          ),
        ),
        const SizedBox(width: 8),
        QuantityStepper(
          quantity: item.quantity,
          compact: true,
          onIncrement: () => cart.increment(item.productId),
          onDecrement: () => cart.decrement(item.productId),
        ),
      ],
    );
  }
}

class _Summary extends StatelessWidget {
  final Cart cart;
  const _Summary({required this.cart});

  @override
  Widget build(BuildContext context) {
    final freeLeft = AppConfig.freeDeliveryThreshold - cart.subtotal;
    return SafeArea(
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: const BoxDecoration(
          color: AppColors.surface,
          border: Border(top: BorderSide(color: AppColors.border)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (cart.deliveryFee > 0 && freeLeft > 0)
              Container(
                width: double.infinity,
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppColors.accent.withOpacity(0.10),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  'Yana ${Formatters.money(freeLeft)} qo\'shsangiz — yetkazib berish BEPUL',
                  style: const TextStyle(color: AppColors.accentDark, fontSize: 13, fontWeight: FontWeight.w600),
                ),
              ),
            _row('Mahsulotlar', Formatters.money(cart.subtotal)),
            const SizedBox(height: 6),
            _row(
              'Yetkazib berish',
              cart.deliveryFee == 0 ? 'Bepul' : Formatters.money(cart.deliveryFee),
            ),
            const Divider(height: 20),
            _row('Jami', Formatters.money(cart.total), bold: true),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: () => context.push('/checkout'),
              child: Text('Rasmiylashtirish · ${Formatters.money(cart.total)}'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _row(String label, String value, {bool bold = false}) {
    final style = TextStyle(
      fontSize: bold ? 18 : 15,
      fontWeight: bold ? FontWeight.w800 : FontWeight.w500,
      color: bold ? AppColors.textPrimary : AppColors.textSecondary,
    );
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [Text(label, style: style), Text(value, style: style)],
    );
  }
}
