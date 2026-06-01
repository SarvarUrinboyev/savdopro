import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../shared/models/address_model.dart';
import '../../../shared/widgets/async_value_view.dart';
import '../../../shared/widgets/empty_state.dart';
import '../address_repository.dart';

/// Saqlangan manzillar ro'yxati.
class AddressesScreen extends ConsumerWidget {
  const AddressesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final addresses = ref.watch(addressesProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Manzillarim')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/address/edit'),
        icon: const Icon(Icons.add),
        label: const Text('Qo\'shish'),
      ),
      body: AsyncValueView(
        value: addresses,
        onRetry: () => ref.invalidate(addressesProvider),
        data: (list) {
          if (list.isEmpty) {
            return const EmptyState(
              icon: Icons.location_off_outlined,
              title: 'Manzil yo\'q',
              subtitle: 'Yetkazib berish uchun manzil qo\'shing',
            );
          }
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(addressesProvider),
            child: ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 90),
              itemCount: list.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (_, i) => _AddressTile(address: list[i]),
            ),
          );
        },
      ),
    );
  }
}

class _AddressTile extends ConsumerWidget {
  final Address address;
  const _AddressTile({required this.address});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      margin: EdgeInsets.zero,
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        leading: const Icon(Icons.location_on, color: AppColors.primary),
        title: Text(address.label ?? 'Manzil',
            style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Text(address.addressLine),
        trailing: PopupMenuButton<String>(
          onSelected: (v) async {
            if (v == 'edit') {
              context.push('/address/edit', extra: address);
            } else if (v == 'delete') {
              await _confirmDelete(context, ref);
            }
          },
          itemBuilder: (_) => const [
            PopupMenuItem(value: 'edit', child: Text('Tahrirlash')),
            PopupMenuItem(value: 'delete', child: Text('O\'chirish')),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmDelete(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Manzilni o\'chirish'),
        content: Text('${address.label ?? address.addressLine} o\'chirilsinmi?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Bekor')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('O\'chirish', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(addressRepositoryProvider).delete(address.id);
      ref.invalidate(addressesProvider);
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }
}
