/// Buyurtma modellari va holat enum'i.

enum OrderStatus {
  newOrder('NEW', 'Qabul qilindi'),
  confirmed('CONFIRMED', 'Tasdiqlandi'),
  assembling('ASSEMBLING', 'Yig\'ilmoqda'),
  onTheWay('ON_THE_WAY', 'Yo\'lda'),
  delivered('DELIVERED', 'Yetkazildi'),
  cancelled('CANCELLED', 'Bekor qilindi');

  final String code;
  final String label;
  const OrderStatus(this.code, this.label);

  static OrderStatus fromCode(String code) =>
      OrderStatus.values.firstWhere((s) => s.code == code, orElse: () => OrderStatus.newOrder);

  /// Progress kuzatuvi uchun tartib (CANCELLED bundan tashqari).
  int get step => switch (this) {
        OrderStatus.newOrder => 0,
        OrderStatus.confirmed => 1,
        OrderStatus.assembling => 2,
        OrderStatus.onTheWay => 3,
        OrderStatus.delivered => 4,
        OrderStatus.cancelled => -1,
      };
}

class OrderItemModel {
  final int? productId;
  final String productName;
  final int unitPrice;
  final int quantity;
  final int lineTotal;

  OrderItemModel({
    this.productId,
    required this.productName,
    required this.unitPrice,
    required this.quantity,
    required this.lineTotal,
  });

  factory OrderItemModel.fromJson(Map<String, dynamic> j) => OrderItemModel(
        productId: (j['productId'] as num?)?.toInt(),
        productName: j['productName'] as String,
        unitPrice: (j['unitPrice'] as num).toInt(),
        quantity: (j['quantity'] as num).toInt(),
        lineTotal: (j['lineTotal'] as num).toInt(),
      );
}

class Order {
  final int id;
  final OrderStatus status;
  final String deliveryType;
  final String paymentMethod;
  final String? addressLine;
  final DateTime? deliverySlot;
  final String? comment;
  final int itemsTotal;
  final int deliveryFee;
  final int total;
  final DateTime? createdAt;
  final List<OrderItemModel> items;

  Order({
    required this.id,
    required this.status,
    required this.deliveryType,
    required this.paymentMethod,
    this.addressLine,
    this.deliverySlot,
    this.comment,
    required this.itemsTotal,
    required this.deliveryFee,
    required this.total,
    this.createdAt,
    required this.items,
  });

  factory Order.fromJson(Map<String, dynamic> j) => Order(
        id: j['id'] as int,
        status: OrderStatus.fromCode(j['status'] as String),
        deliveryType: (j['deliveryType'] as String?) ?? 'DELIVERY',
        paymentMethod: (j['paymentMethod'] as String?) ?? 'CASH',
        addressLine: j['addressLine'] as String?,
        deliverySlot: j['deliverySlot'] == null
            ? null
            : DateTime.tryParse(j['deliverySlot'] as String),
        comment: j['comment'] as String?,
        itemsTotal: (j['itemsTotal'] as num).toInt(),
        deliveryFee: (j['deliveryFee'] as num).toInt(),
        total: (j['total'] as num).toInt(),
        createdAt: j['createdAt'] == null
            ? null
            : DateTime.tryParse(j['createdAt'] as String),
        items: (j['items'] as List? ?? [])
            .map((e) => OrderItemModel.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
