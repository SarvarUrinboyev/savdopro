import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../shared/models/order_models.dart';
import '../../../shared/widgets/async_value_view.dart';
import '../../../shared/widgets/empty_state.dart';
import '../order_repository.dart';

/// Mijoz buyurtmalari ro'yxati.
class OrdersScreen extends ConsumerWidget {
  const OrdersScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final orders = ref.watch(myOrdersProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Buyurtmalar')),
      body: AsyncValueView(
        value: orders,
        onRetry: () => ref.invalidate(myOrdersProvider),
        data: (list) {
          if (list.isEmpty) {
            return EmptyState(
              icon: Icons.receipt_long_outlined,
              title: 'Buyurtmalar yo\'q',
              subtitle: 'Birinchi buyurtmangizni bering',
              action: ElevatedButton(
                onPressed: () => context.go('/home'),
                child: const Text('Xaridni boshlash'),
              ),
            );
          }
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(myOrdersProvider),
            child: ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: list.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (_, i) => _OrderCard(order: list[i]),
            ),
          );
        },
      ),
    );
  }
}

class _OrderCard extends StatelessWidget {
  final Order order;
  const _OrderCard({required this.order});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => context.push('/order/${order.id}'),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Buyurtma #${order.id}',
                      style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                  OrderStatusBadge(status: order.status),
                ],
              ),
              const SizedBox(height: 6),
              if (order.createdAt != null)
                Text(Formatters.dateTime(order.createdAt!),
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 13)),
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('${order.items.length} ta mahsulot',
                      style: const TextStyle(color: AppColors.textSecondary)),
                  Text(Formatters.money(order.total),
                      style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.primary)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Buyurtma statusi uchun rangli yorliq.
class OrderStatusBadge extends StatelessWidget {
  final OrderStatus status;
  const OrderStatusBadge({super.key, required this.status});

  Color get _color => switch (status) {
        OrderStatus.delivered => AppColors.success,
        OrderStatus.cancelled => AppColors.danger,
        OrderStatus.onTheWay => AppColors.primary,
        _ => AppColors.warning,
      };

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: _color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        status.label,
        style: TextStyle(color: _color, fontWeight: FontWeight.w700, fontSize: 12),
      ),
    );
  }
}
