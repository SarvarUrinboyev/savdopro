import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/catalog/catalog_repository.dart';
import 'async_value_view.dart';
import 'empty_state.dart';
import 'product_card.dart';

/// Berilgan [query] (kategoriya/qidiruv) bo'yicha mahsulotlar grid'i.
/// Infinite-scroll, refresh, bo'sh/xato holatlarini o'zi boshqaradi.
class ProductGridView extends ConsumerStatefulWidget {
  final ProductQuery query;
  final String emptyTitle;

  const ProductGridView({
    super.key,
    required this.query,
    this.emptyTitle = 'Mahsulot topilmadi',
  });

  @override
  ConsumerState<ProductGridView> createState() => _ProductGridViewState();
}

class _ProductGridViewState extends ConsumerState<ProductGridView> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 400) {
      ref.read(productListProvider(widget.query).notifier).loadMore();
    }
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(productListProvider(widget.query));
    final notifier = ref.read(productListProvider(widget.query).notifier);

    if (state.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state.error != null && state.items.isEmpty) {
      return ErrorRetry(message: state.error.toString(), onRetry: notifier.refresh);
    }
    if (state.items.isEmpty) {
      return EmptyState(icon: Icons.inventory_2_outlined, title: widget.emptyTitle);
    }

    return RefreshIndicator(
      onRefresh: notifier.refresh,
      child: GridView.builder(
        controller: _scrollController,
        padding: const EdgeInsets.all(16),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 2,
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 0.62,
        ),
        itemCount: state.items.length + (state.isLoadingMore ? 2 : 0),
        itemBuilder: (_, i) {
          if (i >= state.items.length) {
            return const Center(child: Padding(
              padding: EdgeInsets.all(8),
              child: CircularProgressIndicator(strokeWidth: 2),
            ));
          }
          return ProductCard(product: state.items[i]);
        },
      ),
    );
  }
}
