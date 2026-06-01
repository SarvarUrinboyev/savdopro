import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../shared/models/catalog_models.dart';
import '../../../shared/widgets/async_value_view.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/network_image_box.dart';
import '../../../shared/widgets/product_card.dart';
import '../category_icon.dart';
import '../catalog_repository.dart';

/// Bosh sahifa: qidiruv, bannerlar, kategoriyalar va mahsulotlar.
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  final _scrollController = ScrollController();
  static const _query = ProductQuery();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 400) {
      ref.read(productListProvider(_query).notifier).loadMore();
    }
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    ref.invalidate(bannersProvider);
    ref.invalidate(categoriesProvider);
    await ref.read(productListProvider(_query).notifier).refresh();
  }

  @override
  Widget build(BuildContext context) {
    final products = ref.watch(productListProvider(_query));

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _refresh,
          child: CustomScrollView(
            controller: _scrollController,
            slivers: [
              const SliverToBoxAdapter(child: _SearchBar()),
              const SliverToBoxAdapter(child: _BannerCarousel()),
              const SliverToBoxAdapter(child: _CategoriesStrip()),
              const SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.fromLTRB(16, 8, 16, 8),
                  child: Text(
                    'Mashhur mahsulotlar',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                  ),
                ),
              ),
              _ProductsGrid(state: products, query: _query),
              const SliverToBoxAdapter(child: SizedBox(height: 24)),
            ],
          ),
        ),
      ),
    );
  }
}

class _SearchBar extends StatelessWidget {
  const _SearchBar();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: GestureDetector(
        onTap: () => context.push('/search'),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.border),
          ),
          child: const Row(
            children: [
              Icon(Icons.search, color: AppColors.textSecondary),
              SizedBox(width: 10),
              Text('Mahsulot qidirish...',
                  style: TextStyle(color: AppColors.textSecondary, fontSize: 15)),
            ],
          ),
        ),
      ),
    );
  }
}

class _BannerCarousel extends ConsumerWidget {
  const _BannerCarousel();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final banners = ref.watch(bannersProvider);
    return banners.maybeWhen(
      data: (list) {
        if (list.isEmpty) return const SizedBox.shrink();
        return SizedBox(
          height: 150,
          child: PageView.builder(
            controller: PageController(viewportFraction: 0.9),
            itemCount: list.length,
            itemBuilder: (_, i) => _BannerCard(banner: list[i]),
          ),
        );
      },
      orElse: () => const SizedBox.shrink(),
    );
  }
}

class _BannerCard extends StatelessWidget {
  final BannerModel banner;
  const _BannerCard({required this.banner});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      child: Stack(
        fit: StackFit.expand,
        children: [
          NetworkImageBox(imageUrl: banner.imageUrl, radius: 16, width: double.infinity),
          Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              gradient: LinearGradient(
                begin: Alignment.centerLeft,
                end: Alignment.centerRight,
                colors: [Colors.black.withOpacity(0.55), Colors.transparent],
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  banner.title,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                if (banner.subtitle != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    banner.subtitle!,
                    style: const TextStyle(color: Colors.white70, fontSize: 13),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _CategoriesStrip extends ConsumerWidget {
  const _CategoriesStrip();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final categories = ref.watch(categoriesProvider);
    return categories.maybeWhen(
      data: (list) {
        if (list.isEmpty) return const SizedBox.shrink();
        return Padding(
          padding: const EdgeInsets.only(top: 8),
          child: SizedBox(
            height: 104,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              itemCount: list.length,
              separatorBuilder: (_, __) => const SizedBox(width: 12),
              itemBuilder: (_, i) {
                final c = list[i];
                return _CategoryChip(
                  category: c,
                  onTap: () => context.push('/category/${c.id}', extra: c.name),
                );
              },
            ),
          ),
        );
      },
      orElse: () => const SizedBox.shrink(),
    );
  }
}

class _CategoryChip extends StatelessWidget {
  final Category category;
  final VoidCallback onTap;
  const _CategoryChip({required this.category, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final visual = categoryVisual(category.slug, category.name);
    final hasIconUrl = category.iconUrl != null;
    return GestureDetector(
      onTap: onTap,
      child: SizedBox(
        width: 76,
        child: Column(
          children: [
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(
                color: hasIconUrl
                    ? AppColors.surface
                    : visual.color.withOpacity(0.12),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                    color: hasIconUrl
                        ? AppColors.border
                        : visual.color.withOpacity(0.25)),
              ),
              clipBehavior: Clip.antiAlias,
              child: hasIconUrl
                  ? NetworkImageBox(imageUrl: category.iconUrl, radius: 18)
                  : Icon(visual.icon, color: visual.color, size: 30),
            ),
            const SizedBox(height: 6),
            Text(
              category.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
            ),
          ],
        ),
      ),
    );
  }
}

/// Mahsulotlar grid'i — infinite scroll holatini ko'rsatadi.
class _ProductsGrid extends ConsumerWidget {
  final ProductListState state;
  final ProductQuery query;
  const _ProductsGrid({required this.state, required this.query});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (state.isLoading) {
      return const SliverToBoxAdapter(
        child: Padding(
          padding: EdgeInsets.symmetric(vertical: 48),
          child: Center(child: CircularProgressIndicator()),
        ),
      );
    }
    if (state.error != null && state.items.isEmpty) {
      return SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 32),
          child: ErrorRetry(
            message: state.error.toString(),
            onRetry: () => ref.read(productListProvider(query).notifier).refresh(),
          ),
        ),
      );
    }
    if (state.items.isEmpty) {
      return const SliverToBoxAdapter(
        child: Padding(
          padding: EdgeInsets.symmetric(vertical: 32),
          child: EmptyState(
            icon: Icons.inventory_2_outlined,
            title: 'Mahsulot topilmadi',
          ),
        ),
      );
    }

    return SliverPadding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      sliver: SliverGrid(
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 2,
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 0.62,
        ),
        delegate: SliverChildBuilderDelegate(
          (_, i) => ProductCard(product: state.items[i]),
          childCount: state.items.length,
        ),
      ),
    );
  }
}
