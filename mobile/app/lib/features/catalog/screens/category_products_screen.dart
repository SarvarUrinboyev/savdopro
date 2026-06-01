import 'package:flutter/material.dart';

import '../../../shared/widgets/product_grid_view.dart';
import '../catalog_repository.dart';

/// Bitta kategoriya mahsulotlari.
class CategoryProductsScreen extends StatelessWidget {
  final int categoryId;
  final String? categoryName;

  const CategoryProductsScreen({
    super.key,
    required this.categoryId,
    this.categoryName,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(categoryName ?? 'Kategoriya')),
      body: ProductGridView(query: ProductQuery(categoryId: categoryId)),
    );
  }
}
