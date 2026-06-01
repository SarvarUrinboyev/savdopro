import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../shared/widgets/async_value_view.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/network_image_box.dart';
import '../category_icon.dart';
import '../catalog_repository.dart';

/// Barcha kategoriyalar ro'yxati (bottom-nav "Katalog" tabi).
class CategoriesScreen extends ConsumerWidget {
  const CategoriesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final categories = ref.watch(categoriesProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Katalog')),
      body: AsyncValueView(
        value: categories,
        onRetry: () => ref.invalidate(categoriesProvider),
        data: (list) {
          if (list.isEmpty) {
            return const EmptyState(
              icon: Icons.category_outlined,
              title: 'Kategoriyalar yo\'q',
            );
          }
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(categoriesProvider),
            child: ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: list.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (_, i) {
                final c = list[i];
                final visual = categoryVisual(c.slug, c.name);
                return Card(
                  margin: EdgeInsets.zero,
                  child: ListTile(
                    contentPadding: const EdgeInsets.all(10),
                    leading: SizedBox(
                      width: 52,
                      height: 52,
                      child: c.iconUrl != null
                          ? NetworkImageBox(imageUrl: c.iconUrl, radius: 12)
                          : Container(
                              decoration: BoxDecoration(
                                color: visual.color.withOpacity(0.12),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Icon(visual.icon,
                                  color: visual.color, size: 28),
                            ),
                    ),
                    title: Text(c.name,
                        style: const TextStyle(fontWeight: FontWeight.w700)),
                    subtitle: Text('${c.productCount} ta mahsulot'),
                    trailing: const Icon(Icons.chevron_right, color: AppColors.textSecondary),
                    onTap: () => context.push('/category/${c.id}', extra: c.name),
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }
}
