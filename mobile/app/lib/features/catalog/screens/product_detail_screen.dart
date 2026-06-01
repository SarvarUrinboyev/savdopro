import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../shared/models/catalog_models.dart';
import '../../../shared/widgets/async_value_view.dart';
import '../../../shared/widgets/network_image_box.dart';
import '../../../shared/widgets/quantity_stepper.dart';
import '../../cart/cart_controller.dart';
import '../catalog_repository.dart';

/// Mahsulot tafsiloti ekrani.
class ProductDetailScreen extends ConsumerWidget {
  final int productId;
  const ProductDetailScreen({super.key, required this.productId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(productDetailProvider(productId));

    return Scaffold(
      appBar: AppBar(title: const Text('Mahsulot')),
      body: AsyncValueView(
        value: detail,
        onRetry: () => ref.invalidate(productDetailProvider(productId)),
        data: (p) => _Body(product: p),
      ),
      bottomNavigationBar: detail.maybeWhen(
        data: (p) => _BottomBar(product: p),
        orElse: () => null,
      ),
    );
  }
}

class _Body extends StatelessWidget {
  final ProductDetail product;
  const _Body({required this.product});

  @override
  Widget build(BuildContext context) {
    final hasDiscount = product.oldPrice != null && product.oldPrice! > product.price;
    final images = product.images.isNotEmpty
        ? product.images
        : (product.imageUrl != null ? [product.imageUrl!] : <String>[]);

    return ListView(
      children: [
        SizedBox(
          height: 280,
          child: images.isEmpty
              ? const NetworkImageBox(imageUrl: null, radius: 0, width: double.infinity)
              : PageView.builder(
                  itemCount: images.length,
                  itemBuilder: (_, i) => NetworkImageBox(
                    imageUrl: images[i],
                    radius: 0,
                    width: double.infinity,
                  ),
                ),
        ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (product.categoryName != null)
                Text(
                  product.categoryName!.toUpperCase(),
                  style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.5,
                  ),
                ),
              const SizedBox(height: 6),
              Text(
                product.name,
                style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 4),
              Text(product.unit,
                  style: const TextStyle(color: AppColors.textSecondary, fontSize: 14)),
              const SizedBox(height: 16),
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    Formatters.money(product.price),
                    style: const TextStyle(
                      fontSize: 26,
                      fontWeight: FontWeight.w800,
                      color: AppColors.primary,
                    ),
                  ),
                  const SizedBox(width: 10),
                  if (hasDiscount)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 4),
                      child: Text(
                        Formatters.money(product.oldPrice!),
                        style: const TextStyle(
                          decoration: TextDecoration.lineThrough,
                          color: AppColors.textSecondary,
                          fontSize: 16,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              _StockChip(inStock: product.inStock),
              if (product.description != null &&
                  product.description!.trim().isNotEmpty) ...[
                const SizedBox(height: 20),
                const Text('Tavsif',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                const SizedBox(height: 8),
                Text(
                  product.description!,
                  style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 14,
                    height: 1.5,
                  ),
                ),
              ],
              const SizedBox(height: 24),
            ],
          ),
        ),
      ],
    );
  }
}

class _StockChip extends StatelessWidget {
  final bool inStock;
  const _StockChip({required this.inStock});

  @override
  Widget build(BuildContext context) {
    final color = inStock ? AppColors.success : AppColors.danger;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(inStock ? Icons.check_circle : Icons.cancel, size: 16, color: color),
          const SizedBox(width: 6),
          Text(
            inStock ? 'Sotuvda bor' : 'Tugagan',
            style: TextStyle(color: color, fontWeight: FontWeight.w600, fontSize: 13),
          ),
        ],
      ),
    );
  }
}

class _BottomBar extends ConsumerWidget {
  final ProductDetail product;
  const _BottomBar({required this.product});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final qty = ref.watch(cartProvider.select((c) => c.quantityOf(product.id)));
    final cart = ref.read(cartProvider.notifier);

    return SafeArea(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: const BoxDecoration(
          color: AppColors.surface,
          border: Border(top: BorderSide(color: AppColors.border)),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('Narxi', style: TextStyle(color: AppColors.textSecondary, fontSize: 12)),
                  Text(
                    Formatters.money(product.price * (qty == 0 ? 1 : qty)),
                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                  ),
                ],
              ),
            ),
            SizedBox(
              width: qty > 0 ? 150 : 180,
              child: QuantityStepper(
                quantity: qty,
                enabled: product.inStock,
                onIncrement: () => cart.addDetail(product),
                onDecrement: () => cart.decrement(product.id),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
