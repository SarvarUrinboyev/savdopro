import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../shared/models/order_models.dart';
import '../../../shared/widgets/async_value_view.dart';
import '../order_repository.dart';
import 'orders_screen.dart' show OrderStatusBadge;

/// Buyurtma tafsiloti — status kuzatuvchi + mahsulotlar + summalar.
class OrderDetailScreen extends ConsumerWidget {
  final int orderId;
  const OrderDetailScreen({super.key, required this.orderId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final order = ref.watch(orderDetailProvider(orderId));

    return Scaffold(
      appBar: AppBar(title: Text('Buyurtma #$orderId')),
      body: AsyncValueView(
        value: order,
        onRetry: () => ref.invalidate(orderDetailProvider(orderId)),
        data: (o) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(orderDetailProvider(orderId)),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  if (o.createdAt != null)
                    Text(Formatters.dateTime(o.createdAt!),
                        style: const TextStyle(color: AppColors.textSecondary)),
                  OrderStatusBadge(status: o.status),
                ],
              ),
              const SizedBox(height: 16),
              if (o.status != OrderStatus.cancelled)
                _StatusTracker(status: o.status)
              else
                _CancelledBanner(),
              const SizedBox(height: 20),
              _InfoCard(order: o),
              const SizedBox(height: 16),
              const Text('Mahsulotlar',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
              const SizedBox(height: 8),
              ...o.items.map((it) => _ItemRow(item: it)),
              const Divider(height: 24),
              _kv('Mahsulotlar', Formatters.money(o.itemsTotal)),
              _kv('Yetkazib berish',
                  o.deliveryFee == 0 ? 'Bepul' : Formatters.money(o.deliveryFee)),
              const SizedBox(height: 4),
              _kv('Jami', Formatters.money(o.total), bold: true),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }

  Widget _kv(String k, String v, {bool bold = false}) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(k,
                style: TextStyle(
                    color: bold ? AppColors.textPrimary : AppColors.textSecondary,
                    fontSize: bold ? 17 : 14,
                    fontWeight: bold ? FontWeight.w800 : FontWeight.w400)),
            Text(v,
                style: TextStyle(
                    fontSize: bold ? 17 : 14,
                    fontWeight: bold ? FontWeight.w800 : FontWeight.w600)),
          ],
        ),
      );
}

class _StatusTracker extends StatelessWidget {
  final OrderStatus status;
  const _StatusTracker({required this.status});

  static const _steps = [
    OrderStatus.newOrder,
    OrderStatus.confirmed,
    OrderStatus.assembling,
    OrderStatus.onTheWay,
    OrderStatus.delivered,
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      children: List.generate(_steps.length, (i) {
        final step = _steps[i];
        final done = status.step >= step.step;
        final isLast = i == _steps.length - 1;
        return IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Column(
                children: [
                  Container(
                    width: 26,
                    height: 26,
                    decoration: BoxDecoration(
                      color: done ? AppColors.accent : AppColors.background,
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: done ? AppColors.accent : AppColors.border,
                        width: 2,
                      ),
                    ),
                    child: Icon(
                      done ? Icons.check : Icons.circle,
                      size: 14,
                      color: done ? Colors.white : AppColors.border,
                    ),
                  ),
                  if (!isLast)
                    Expanded(
                      child: Container(
                        width: 2,
                        color: status.step > step.step ? AppColors.accent : AppColors.border,
                      ),
                    ),
                ],
              ),
              const SizedBox(width: 12),
              Padding(
                padding: const EdgeInsets.only(top: 2, bottom: 18),
                child: Text(
                  step.label,
                  style: TextStyle(
                    fontWeight: status == step ? FontWeight.w800 : FontWeight.w500,
                    color: done ? AppColors.textPrimary : AppColors.textSecondary,
                    fontSize: 15,
                  ),
                ),
              ),
            ],
          ),
        );
      }),
    );
  }
}

class _CancelledBanner extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.danger.withOpacity(0.10),
        borderRadius: BorderRadius.circular(14),
      ),
      child: const Row(
        children: [
          Icon(Icons.cancel, color: AppColors.danger),
          SizedBox(width: 10),
          Expanded(
            child: Text('Buyurtma bekor qilingan',
                style: TextStyle(color: AppColors.danger, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
  }
}

class _InfoCard extends StatelessWidget {
  final Order order;
  const _InfoCard({required this.order});

  @override
  Widget build(BuildContext context) {
    final delivery = order.deliveryType == 'PICKUP' ? 'Olib ketish' : 'Yetkazib berish';
    final payment = order.paymentMethod == 'CARD' ? 'Karta' : 'Naqd';
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        children: [
          _line(Icons.local_shipping_outlined, delivery),
          if (order.addressLine != null) _line(Icons.location_on_outlined, order.addressLine!),
          _line(Icons.payments_outlined, payment),
          if (order.comment != null && order.comment!.isNotEmpty)
            _line(Icons.notes_outlined, order.comment!),
        ],
      ),
    );
  }

  Widget _line(IconData icon, String text) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 5),
        child: Row(
          children: [
            Icon(icon, size: 18, color: AppColors.textSecondary),
            const SizedBox(width: 10),
            Expanded(child: Text(text)),
          ],
        ),
      );
}

class _ItemRow extends StatelessWidget {
  final OrderItemModel item;
  const _ItemRow({required this.item});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Text('${item.quantity}×',
              style: const TextStyle(fontWeight: FontWeight.w700, color: AppColors.primary)),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.productName, maxLines: 2, overflow: TextOverflow.ellipsis),
                Text(Formatters.money(item.unitPrice),
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 12)),
              ],
            ),
          ),
          Text(Formatters.money(item.lineTotal),
              style: const TextStyle(fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}
