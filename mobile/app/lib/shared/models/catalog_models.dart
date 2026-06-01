/// Katalog modellari: kategoriya, mahsulot, banner, sahifalangan ro'yxat.

class Category {
  final int id;
  final String name;
  final String slug;
  final String? iconUrl;
  final int productCount;

  Category({
    required this.id,
    required this.name,
    required this.slug,
    this.iconUrl,
    required this.productCount,
  });

  factory Category.fromJson(Map<String, dynamic> j) => Category(
        id: j['id'] as int,
        name: j['name'] as String,
        slug: j['slug'] as String,
        iconUrl: j['iconUrl'] as String?,
        productCount: (j['productCount'] as num?)?.toInt() ?? 0,
      );
}

class ProductSummary {
  final int id;
  final String name;
  final int price;
  final int? oldPrice;
  final String unit;
  final String? imageUrl;
  final int? categoryId;
  final bool inStock;
  final int? discountPercent;

  ProductSummary({
    required this.id,
    required this.name,
    required this.price,
    this.oldPrice,
    required this.unit,
    this.imageUrl,
    this.categoryId,
    required this.inStock,
    this.discountPercent,
  });

  factory ProductSummary.fromJson(Map<String, dynamic> j) => ProductSummary(
        id: j['id'] as int,
        name: j['name'] as String,
        price: (j['price'] as num).toInt(),
        oldPrice: (j['oldPrice'] as num?)?.toInt(),
        unit: (j['unit'] as String?) ?? 'dona',
        imageUrl: j['imageUrl'] as String?,
        categoryId: (j['categoryId'] as num?)?.toInt(),
        inStock: (j['inStock'] as bool?) ?? true,
        discountPercent: (j['discountPercent'] as num?)?.toInt(),
      );
}

class ProductDetail {
  final int id;
  final String name;
  final String? description;
  final int price;
  final int? oldPrice;
  final String unit;
  final String? imageUrl;
  final List<String> images;
  final int? categoryId;
  final String? categoryName;
  final bool inStock;
  final int? discountPercent;

  ProductDetail({
    required this.id,
    required this.name,
    this.description,
    required this.price,
    this.oldPrice,
    required this.unit,
    this.imageUrl,
    required this.images,
    this.categoryId,
    this.categoryName,
    required this.inStock,
    this.discountPercent,
  });

  factory ProductDetail.fromJson(Map<String, dynamic> j) => ProductDetail(
        id: j['id'] as int,
        name: j['name'] as String,
        description: j['description'] as String?,
        price: (j['price'] as num).toInt(),
        oldPrice: (j['oldPrice'] as num?)?.toInt(),
        unit: (j['unit'] as String?) ?? 'dona',
        imageUrl: j['imageUrl'] as String?,
        images: (j['images'] as List?)?.map((e) => e as String).toList() ?? const [],
        categoryId: (j['categoryId'] as num?)?.toInt(),
        categoryName: j['categoryName'] as String?,
        inStock: (j['inStock'] as bool?) ?? true,
        discountPercent: (j['discountPercent'] as num?)?.toInt(),
      );
}

class BannerModel {
  final int id;
  final String title;
  final String? subtitle;
  final String? imageUrl;
  final String? actionLink;

  BannerModel({
    required this.id,
    required this.title,
    this.subtitle,
    this.imageUrl,
    this.actionLink,
  });

  factory BannerModel.fromJson(Map<String, dynamic> j) => BannerModel(
        id: j['id'] as int,
        title: j['title'] as String,
        subtitle: j['subtitle'] as String?,
        imageUrl: j['imageUrl'] as String?,
        actionLink: j['actionLink'] as String?,
      );
}

class PagedProducts {
  final List<ProductSummary> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;

  PagedProducts({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  bool get hasMore => page + 1 < totalPages;

  factory PagedProducts.fromJson(Map<String, dynamic> j) => PagedProducts(
        content: (j['content'] as List)
            .map((e) => ProductSummary.fromJson(e as Map<String, dynamic>))
            .toList(),
        page: (j['page'] as num).toInt(),
        size: (j['size'] as num).toInt(),
        totalElements: (j['totalElements'] as num).toInt(),
        totalPages: (j['totalPages'] as num).toInt(),
      );
}
