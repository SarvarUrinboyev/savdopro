import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_client.dart';
import '../../core/providers.dart';
import '../../shared/models/cart_models.dart';
import '../../shared/models/order_models.dart';

/// Checkout uchun yuboriladigan payload.
class CheckoutPayload {
  final String deliveryType; // DELIVERY | PICKUP
  final String paymentMethod; // CASH | CARD
  final int? addressId;
  final DateTime? deliverySlot;
  final String? comment;
  final List<CartItemModel> items;

  CheckoutPayload({
    required this.deliveryType,
    required this.paymentMethod,
    this.addressId,
    this.deliverySlot,
    this.comment,
    required this.items,
  });

  Map<String, dynamic> toJson() => {
        'deliveryType': deliveryType,
        'paymentMethod': paymentMethod,
        if (addressId != null) 'addressId': addressId,
        if (deliverySlot != null) 'deliverySlot': deliverySlot!.toUtc().toIso8601String(),
        if (comment != null && comment!.trim().isNotEmpty) 'comment': comment!.trim(),
        'items': items
            .map((i) => {'productId': i.productId, 'quantity': i.quantity})
            .toList(),
      };
}

/// Buyurtmalar API: yaratish, ro'yxat, tafsilot, bekor qilish.
class OrderRepository {
  final ApiClient _api;
  OrderRepository(this._api);

  Future<Order> placeOrder(CheckoutPayload payload) async {
    final res = await _api.post('/orders', data: payload.toJson());
    return Order.fromJson(res.data as Map<String, dynamic>);
  }

  Future<List<Order>> myOrders() async {
    final res = await _api.get('/orders');
    final list = res.data as List;
    return list.map((e) => Order.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<Order> order(int id) async {
    final res = await _api.get('/orders/$id');
    return Order.fromJson(res.data as Map<String, dynamic>);
  }

  Future<Order> cancel(int id) async {
    final res = await _api.post('/orders/$id/cancel');
    return Order.fromJson(res.data as Map<String, dynamic>);
  }
}

final orderRepositoryProvider = Provider<OrderRepository>(
  (ref) => OrderRepository(ref.watch(apiClientProvider)),
);

/// Mijozning buyurtmalari ro'yxati.
final myOrdersProvider = FutureProvider.autoDispose<List<Order>>(
  (ref) => ref.watch(orderRepositoryProvider).myOrders(),
);

/// Bitta buyurtma tafsiloti (status kuzatuvi uchun).
final orderDetailProvider = FutureProvider.autoDispose.family<Order, int>(
  (ref, id) => ref.watch(orderRepositoryProvider).order(id),
);
