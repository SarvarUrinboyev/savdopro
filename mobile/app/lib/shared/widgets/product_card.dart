import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/app_colors.dart';
import '../../core/utils/formatters.dart';
import '../../features/cart/cart_controller.dart';
import '../models/catalog_models.dart';
import 'network_image_box.dart';
import 'quantity_stepper.dart';

/// Grid'dagi mahsulot kartochkasi. Bosilganda tafsilotga o'tadi,
/// stepper orqali savatga qo'shadi.
class ProductCard extends ConsumerWidget {
  final ProductSummary product;
  const ProductCard({super.key, required this.product});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final qty = ref.watch(cartProvider.select((c) => c.quantityOf(product.id)));
    final cart = ref.read(cartProvider.notifier);
    final hasDiscount = product.oldPrice != null && product.oldPrice! > product.price;

    return InkWell(
      onTap: () => context.push('/product/${product.id}'),
      borderRadius: BorderRadius.circular(16),
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.border),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Stack(
              children: [
                AspectRatio(
                  aspectRatio: 1.2,
                  child: NetworkImageBox(
                    imageUrl: product.imageUrl,
                    width: double.infinity,
                    radius: 16,
                  ),
                ),
                if (hasDiscount)
                  Positioned(
                    top: 8,
                    left: 8,
                    child: _Badge(
                      text: '-${product.discountPercent ?? _percent(product)}%',
                      color: AppColors.danger,
                    ),
                  ),
                if (!product.inStock)
                  Positioned(
                    top: 8,
                    right: 8,
                    child: _Badge(text: 'Tugagan', color: AppColors.textSecondary),
                  ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 8, 10, 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    product.name,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                      height: 1.2,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    product.unit,
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 12),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    Formatters.money(product.price),
                    style: const TextStyle(
                      fontWeight: FontWeight.w800,
                      fontSize: 15,
                      color: AppColors.primary,
                    ),
                  ),
                  if (hasDiscount)
                    Text(
                      Formatters.money(product.oldPrice!),
                      style: const TextStyle(
                        decoration: TextDecoration.lineThrough,
                        color: AppColors.textSecondary,
                        fontSize: 12,
                      ),
                    ),
                  const SizedBox(height: 8),
                  Align(
                    alignment: Alignment.centerRight,
                    child: QuantityStepper(
                      quantity: qty,
                      enabled: product.inStock,
                      compact: true,
                      onIncrement: () => cart.add(product),
                      onDecrement: () => cart.decrement(product.id),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  int _percent(ProductSummary p) {
    if (p.oldPrice == null || p.oldPrice == 0) return 0;
    return (100 * (p.oldPrice! - p.price) / p.oldPrice!).round();
  }
}

class _Badge extends StatelessWidget {
  final String text;
  final Color color;
  const _Badge({required this.text, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(8)),
      child: Text(
        text,
        style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w700),
      ),
    );
  }
}
