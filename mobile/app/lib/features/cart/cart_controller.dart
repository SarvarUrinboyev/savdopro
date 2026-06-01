import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/config/app_config.dart';
import '../../shared/models/cart_models.dart';
import '../../shared/models/catalog_models.dart';

/// Mahalliy (client-side) savatcha. Mahsulotlar qurilmada saqlanadi,
/// checkout paytida serverga yuboriladi. Yetkazib berish narxi UI tahmini —
/// haqiqiy summa buyurtma berishda serverda hisoblanadi.
class CartController extends StateNotifier<Cart> {
  CartController() : super(Cart.empty()) {
    // Demo rejim: ilova ochilganda savatni namuna mahsulotlar bilan to'ldiradi.
    // Faqat `--dart-define=DEMO_SEED_CART=true` bilan build qilinganda ishlaydi;
    // production'da hech qanday ta'sir qilmaydi.
    if (const bool.fromEnvironment('DEMO_SEED_CART')) {
      _seedDemo();
    }
  }

  void _seedDemo() {
    setQuantity(productId: 3, name: 'Apelsin', price: 28000, quantity: 2,
        imageUrl: 'https://loremflickr.com/400/300/orange,fruit?lock=3');
    setQuantity(productId: 1, name: 'Olma (Semerenka)', price: 18000, quantity: 1,
        imageUrl: 'https://loremflickr.com/400/300/apple,fruit?lock=1');
    setQuantity(productId: 12, name: 'Sut (2.5%) 1L', price: 13000, quantity: 3,
        imageUrl: 'https://loremflickr.com/400/300/milk,glass?lock=12');
  }

  /// Mahsulotni savatga qo'shadi yoki miqdorini [delta] ga oshiradi.
  void add(ProductSummary product, {int delta = 1}) {
    final current = state.quantityOf(product.id);
    setQuantity(
      productId: product.id,
      name: product.name,
      price: product.price,
      imageUrl: product.imageUrl,
      quantity: current + delta,
    );
  }

  /// ProductDetail'dan qo'shish (tafsilot ekrani uchun).
  void addDetail(ProductDetail product, {int delta = 1}) {
    final current = state.quantityOf(product.id);
    setQuantity(
      productId: product.id,
      name: product.name,
      price: product.price,
      imageUrl: product.imageUrl,
      quantity: current + delta,
    );
  }

  void increment(int productId) {
    final item = _find(productId);
    if (item == null) return;
    setQuantity(
      productId: item.productId,
      name: item.name,
      price: item.price,
      imageUrl: item.imageUrl,
      quantity: item.quantity + 1,
    );
  }

  void decrement(int productId) {
    final item = _find(productId);
    if (item == null) return;
    setQuantity(
      productId: item.productId,
      name: item.name,
      price: item.price,
      imageUrl: item.imageUrl,
      quantity: item.quantity - 1,
    );
  }

  void remove(int productId) {
    final items = state.items.where((i) => i.productId != productId).toList();
    state = _rebuild(items);
  }

  void clear() => state = Cart.empty();

  /// Aniq miqdor o'rnatadi. 0 yoki manfiy bo'lsa elementni o'chiradi.
  void setQuantity({
    required int productId,
    required String name,
    required int price,
    String? imageUrl,
    required int quantity,
  }) {
    final items = [...state.items];
    final idx = items.indexWhere((i) => i.productId == productId);

    if (quantity <= 0) {
      if (idx >= 0) items.removeAt(idx);
    } else {
      final updated = CartItemModel(
        productId: productId,
        name: name,
        price: price,
        quantity: quantity,
        lineTotal: price * quantity,
        imageUrl: imageUrl,
      );
      if (idx >= 0) {
        items[idx] = updated;
      } else {
        items.add(updated);
      }
    }
    state = _rebuild(items);
  }

  CartItemModel? _find(int productId) {
    for (final i in state.items) {
      if (i.productId == productId) return i;
    }
    return null;
  }

  /// Summalarni qayta hisoblab yangi Cart yasaydi.
  Cart _rebuild(List<CartItemModel> items) {
    final subtotal = items.fold<int>(0, (sum, i) => sum + i.lineTotal);
    final itemCount = items.fold<int>(0, (sum, i) => sum + i.quantity);
    final deliveryFee = (subtotal == 0 || subtotal >= AppConfig.freeDeliveryThreshold)
        ? 0
        : AppConfig.baseDeliveryFee;
    return Cart(
      items: items,
      itemCount: itemCount,
      subtotal: subtotal,
      deliveryFee: deliveryFee,
      total: subtotal + deliveryFee,
    );
  }
}

final cartProvider = StateNotifierProvider<CartController, Cart>(
  (ref) => CartController(),
);

/// Bottom-nav badge uchun savatdagi umumiy mahsulot soni.
final cartCountProvider = Provider<int>((ref) => ref.watch(cartProvider).itemCount);
