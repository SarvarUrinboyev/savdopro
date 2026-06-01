import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../shared/widgets/async_value_view.dart';
import '../../address/address_repository.dart';
import '../../cart/cart_controller.dart';
import '../order_repository.dart';

/// Buyurtmani rasmiylashtirish ekrani.
class CheckoutScreen extends ConsumerStatefulWidget {
  const CheckoutScreen({super.key});

  @override
  ConsumerState<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends ConsumerState<CheckoutScreen> {
  String _deliveryType = 'DELIVERY';
  String _paymentMethod = 'CASH';
  final _commentController = TextEditingController();
  bool _placing = false;

  @override
  void dispose() {
    _commentController.dispose();
    super.dispose();
  }

  Future<void> _placeOrder() async {
    final cart = ref.read(cartProvider);
    final addressId = ref.read(selectedAddressIdProvider);

    if (cart.isEmpty) return;
    if (_deliveryType == 'DELIVERY' && addressId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Yetkazib berish manzilini tanlang')),
      );
      return;
    }

    setState(() => _placing = true);
    try {
      final order = await ref.read(orderRepositoryProvider).placeOrder(
            CheckoutPayload(
              deliveryType: _deliveryType,
              paymentMethod: _paymentMethod,
              addressId: _deliveryType == 'DELIVERY' ? addressId : null,
              comment: _commentController.text,
              items: cart.items,
            ),
          );
      ref.read(cartProvider.notifier).clear();
      ref.invalidate(myOrdersProvider);
      if (!mounted) return;
      // Buyurtma tafsilotiga o'tamiz (savatga qaytmaslik uchun replace).
      context.go('/orders');
      context.push('/order/${order.id}');
    } catch (e) {
      if (!mounted) return;
      final msg = e is Exception ? e.toString() : 'Buyurtma berishda xatolik';
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    } finally {
      if (mounted) setState(() => _placing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cart = ref.watch(cartProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Rasmiylashtirish')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _SectionTitle('Yetkazib berish turi'),
          _ChoiceTiles(
            value: _deliveryType,
            options: const {'DELIVERY': 'Yetkazib berish', 'PICKUP': 'Olib ketish'},
            icons: const {'DELIVERY': Icons.delivery_dining, 'PICKUP': Icons.storefront},
            onChanged: (v) => setState(() => _deliveryType = v),
          ),
          if (_deliveryType == 'DELIVERY') ...[
            const SizedBox(height: 20),
            _SectionTitle('Manzil'),
            const _AddressPicker(),
          ],
          const SizedBox(height: 20),
          _SectionTitle('To\'lov usuli'),
          _ChoiceTiles(
            value: _paymentMethod,
            options: const {'CASH': 'Naqd', 'CARD': 'Karta'},
            icons: const {'CASH': Icons.payments_outlined, 'CARD': Icons.credit_card},
            onChanged: (v) => setState(() => _paymentMethod = v),
          ),
          const SizedBox(height: 20),
          _SectionTitle('Izoh (ixtiyoriy)'),
          TextField(
            controller: _commentController,
            maxLines: 3,
            decoration: const InputDecoration(
              hintText: 'Kuryer uchun eslatma, qavat, domofon...',
            ),
          ),
          const SizedBox(height: 20),
          _SectionTitle('Buyurtma'),
          _OrderSummary(),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: ElevatedButton(
            onPressed: _placing || cart.isEmpty ? null : _placeOrder,
            child: _placing
                ? const SizedBox(
                    width: 22,
                    height: 22,
                    child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5),
                  )
                : Text('Buyurtma berish · ${Formatters.money(cart.total)}'),
          ),
        ),
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Text(text, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      );
}

class _ChoiceTiles extends StatelessWidget {
  final String value;
  final Map<String, String> options;
  final Map<String, IconData> icons;
  final ValueChanged<String> onChanged;

  const _ChoiceTiles({
    required this.value,
    required this.options,
    required this.icons,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: options.entries.map((e) {
        final selected = e.key == value;
        return Expanded(
          child: Padding(
            padding: const EdgeInsets.only(right: 10),
            child: InkWell(
              onTap: () => onChanged(e.key),
              borderRadius: BorderRadius.circular(14),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 16),
                decoration: BoxDecoration(
                  color: selected ? AppColors.primary.withOpacity(0.08) : AppColors.surface,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(
                    color: selected ? AppColors.primary : AppColors.border,
                    width: selected ? 1.5 : 1,
                  ),
                ),
                child: Column(
                  children: [
                    Icon(icons[e.key],
                        color: selected ? AppColors.primary : AppColors.textSecondary),
                    const SizedBox(height: 6),
                    Text(
                      e.value,
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        color: selected ? AppColors.primary : AppColors.textPrimary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      }).toList(),
    );
  }
}

class _AddressPicker extends ConsumerWidget {
  const _AddressPicker();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final addresses = ref.watch(addressesProvider);
    final selectedId = ref.watch(selectedAddressIdProvider);

    return AsyncValueView(
      value: addresses,
      onRetry: () => ref.invalidate(addressesProvider),
      loading: const Padding(
        padding: EdgeInsets.all(8),
        child: Center(child: CircularProgressIndicator()),
      ),
      data: (list) {
        // Birinchi manzilni standart sifatida avtomatik tanlaymiz.
        if (selectedId == null && list.isNotEmpty) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (ref.read(selectedAddressIdProvider) == null) {
              ref.read(selectedAddressIdProvider.notifier).state = list.first.id;
            }
          });
        }
        if (list.isEmpty) {
          return OutlinedButton.icon(
            onPressed: () => context.push('/address/edit'),
            icon: const Icon(Icons.add_location_alt_outlined),
            label: const Text('Manzil qo\'shish'),
          );
        }
        return Column(
          children: [
            ...list.map((a) {
              final selected = a.id == selectedId;
              return Card(
                margin: const EdgeInsets.only(bottom: 8),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                  side: BorderSide(
                    color: selected ? AppColors.primary : AppColors.border,
                    width: selected ? 1.5 : 1,
                  ),
                ),
                child: RadioListTile<int>(
                  value: a.id,
                  groupValue: selectedId,
                  onChanged: (v) =>
                      ref.read(selectedAddressIdProvider.notifier).state = v,
                  title: Text(a.label ?? 'Manzil',
                      style: const TextStyle(fontWeight: FontWeight.w700)),
                  subtitle: Text(a.addressLine),
                ),
              );
            }),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: () => context.push('/address/edit'),
                icon: const Icon(Icons.add),
                label: const Text('Yangi manzil'),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _OrderSummary extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cart = ref.watch(cartProvider);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        children: [
          ...cart.items.map((i) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  children: [
                    Text('${i.quantity}×',
                        style: const TextStyle(fontWeight: FontWeight.w700, color: AppColors.primary)),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(i.name,
                          maxLines: 1, overflow: TextOverflow.ellipsis),
                    ),
                    Text(Formatters.money(i.lineTotal)),
                  ],
                ),
              )),
          const Divider(),
          _kv('Mahsulotlar', Formatters.money(cart.subtotal)),
          _kv('Yetkazib berish',
              cart.deliveryFee == 0 ? 'Bepul' : Formatters.money(cart.deliveryFee)),
          const Divider(),
          _kv('Jami', Formatters.money(cart.total), bold: true),
        ],
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
                    fontWeight: bold ? FontWeight.w800 : FontWeight.w400,
                    fontSize: bold ? 16 : 14)),
            Text(v,
                style: TextStyle(
                    fontWeight: bold ? FontWeight.w800 : FontWeight.w600,
                    fontSize: bold ? 16 : 14)),
          ],
        ),
      );
}
