/// Savatcha modellari.

class CartItemModel {
  final int productId;
  final String name;
  final int price;
  final int quantity;
  final int lineTotal;
  final String? imageUrl;

  CartItemModel({
    required this.productId,
    required this.name,
    required this.price,
    required this.quantity,
    required this.lineTotal,
    this.imageUrl,
  });

  factory CartItemModel.fromJson(Map<String, dynamic> j) => CartItemModel(
        productId: j['productId'] as int,
        name: j['name'] as String,
        price: (j['price'] as num).toInt(),
        quantity: (j['quantity'] as num).toInt(),
        lineTotal: (j['lineTotal'] as num).toInt(),
        imageUrl: j['imageUrl'] as String?,
      );
}

class Cart {
  final List<CartItemModel> items;
  final int itemCount;
  final int subtotal;
  final int deliveryFee;
  final int total;

  Cart({
    required this.items,
    required this.itemCount,
    required this.subtotal,
    required this.deliveryFee,
    required this.total,
  });

  bool get isEmpty => items.isEmpty;

  static Cart empty() =>
      Cart(items: const [], itemCount: 0, subtotal: 0, deliveryFee: 0, total: 0);

  /// Mahsulot uchun savatdagi miqdor (yo'q bo'lsa 0).
  int quantityOf(int productId) {
    for (final i in items) {
      if (i.productId == productId) return i.quantity;
    }
    return 0;
  }

  factory Cart.fromJson(Map<String, dynamic> j) => Cart(
        items: (j['items'] as List)
            .map((e) => CartItemModel.fromJson(e as Map<String, dynamic>))
            .toList(),
        itemCount: (j['itemCount'] as num).toInt(),
        subtotal: (j['subtotal'] as num).toInt(),
        deliveryFee: (j['deliveryFee'] as num).toInt(),
        total: (j['total'] as num).toInt(),
      );
}
