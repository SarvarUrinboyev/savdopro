import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_client.dart';
import '../../core/providers.dart';
import '../../shared/models/catalog_models.dart';

/// Katalog API: kategoriyalar, bannerlar, mahsulotlar (sahifalangan), tafsilot.
class CatalogRepository {
  final ApiClient _api;
  CatalogRepository(this._api);

  Future<List<Category>> categories() async {
    final res = await _api.get('/categories');
    final list = res.data as List;
    return list.map((e) => Category.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<BannerModel>> banners() async {
    final res = await _api.get('/banners');
    final list = res.data as List;
    return list.map((e) => BannerModel.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Mahsulotlar ro'yxati. [categoryId] / [search] ixtiyoriy filtrlar.
  Future<PagedProducts> products({
    int page = 0,
    int size = 20,
    int? categoryId,
    String? search,
  }) async {
    final res = await _api.get('/products', query: {
      'page': page,
      'size': size,
      if (categoryId != null) 'categoryId': categoryId,
      if (search != null && search.trim().isNotEmpty) 'search': search.trim(),
    });
    return PagedProducts.fromJson(res.data as Map<String, dynamic>);
  }

  Future<ProductDetail> product(int id) async {
    final res = await _api.get('/products/$id');
    return ProductDetail.fromJson(res.data as Map<String, dynamic>);
  }
}

final catalogRepositoryProvider = Provider<CatalogRepository>(
  (ref) => CatalogRepository(ref.watch(apiClientProvider)),
);

/// Bosh sahifa uchun kategoriyalar.
final categoriesProvider = FutureProvider<List<Category>>(
  (ref) => ref.watch(catalogRepositoryProvider).categories(),
);

/// Bosh sahifa karuseli uchun bannerlar.
final bannersProvider = FutureProvider<List<BannerModel>>(
  (ref) => ref.watch(catalogRepositoryProvider).banners(),
);

/// Bitta mahsulot tafsiloti.
final productDetailProvider =
    FutureProvider.family<ProductDetail, int>((ref, id) {
  return ref.watch(catalogRepositoryProvider).product(id);
});

/// Mahsulotlar ro'yxati uchun filtr (kategoriya + qidiruv).
class ProductQuery {
  final int? categoryId;
  final String? search;
  const ProductQuery({this.categoryId, this.search});

  @override
  bool operator ==(Object other) =>
      other is ProductQuery &&
      other.categoryId == categoryId &&
      other.search == search;

  @override
  int get hashCode => Object.hash(categoryId, search);
}

/// Infinite-scroll holati.
class ProductListState {
  final List<ProductSummary> items;
  final bool isLoading; // birinchi yuklash
  final bool isLoadingMore; // keyingi sahifa
  final bool hasMore;
  final Object? error;
  final int page;

  const ProductListState({
    this.items = const [],
    this.isLoading = true,
    this.isLoadingMore = false,
    this.hasMore = false,
    this.error,
    this.page = 0,
  });

  ProductListState copyWith({
    List<ProductSummary>? items,
    bool? isLoading,
    bool? isLoadingMore,
    bool? hasMore,
    Object? error,
    bool clearError = false,
    int? page,
  }) {
    return ProductListState(
      items: items ?? this.items,
      isLoading: isLoading ?? this.isLoading,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      hasMore: hasMore ?? this.hasMore,
      error: clearError ? null : (error ?? this.error),
      page: page ?? this.page,
    );
  }
}

/// Sahifalangan mahsulot ro'yxatini boshqaradi (kategoriya/qidiruv bo'yicha).
class ProductListController extends StateNotifier<ProductListState> {
  final CatalogRepository _repo;
  final ProductQuery query;
  static const _pageSize = 20;

  ProductListController(this._repo, this.query) : super(const ProductListState()) {
    refresh();
  }

  Future<void> refresh() async {
    state = const ProductListState(isLoading: true);
    try {
      final res = await _repo.products(
        page: 0,
        size: _pageSize,
        categoryId: query.categoryId,
        search: query.search,
      );
      state = ProductListState(
        items: res.content,
        isLoading: false,
        hasMore: res.hasMore,
        page: 0,
      );
    } catch (e) {
      state = ProductListState(isLoading: false, error: e, hasMore: false);
    }
  }

  Future<void> loadMore() async {
    if (state.isLoading || state.isLoadingMore || !state.hasMore) return;
    state = state.copyWith(isLoadingMore: true, clearError: true);
    final next = state.page + 1;
    try {
      final res = await _repo.products(
        page: next,
        size: _pageSize,
        categoryId: query.categoryId,
        search: query.search,
      );
      state = state.copyWith(
        items: [...state.items, ...res.content],
        isLoadingMore: false,
        hasMore: res.hasMore,
        page: next,
      );
    } catch (e) {
      state = state.copyWith(isLoadingMore: false, error: e);
    }
  }
}

final productListProvider = StateNotifierProvider.family<ProductListController,
    ProductListState, ProductQuery>((ref, query) {
  return ProductListController(ref.watch(catalogRepositoryProvider), query);
});
