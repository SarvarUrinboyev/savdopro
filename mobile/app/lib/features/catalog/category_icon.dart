import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';

/// Kategoriya uchun ikonka va rang.
class CategoryVisual {
  final IconData icon;
  final Color color;
  const CategoryVisual(this.icon, this.color);
}

/// Kategoriya `slug`iga (yoki nomiga) mos ikonka va rang qaytaradi.
///
/// Backend `iconUrl` bermaganda (hozir hammasi `null`) UI shu mahalliy
/// Material ikonkalarni ishlatadi — har bir toifa o'ziga xos ko'rinadi.
CategoryVisual categoryVisual(String slug, [String? name]) {
  switch (slug) {
    case 'sut': // Sut mahsulotlari
      return const CategoryVisual(Icons.water_drop, Color(0xFF3B82F6));
    case 'non': // Non va shirinliklar
      return const CategoryVisual(Icons.bakery_dining, Color(0xFFD97706));
    case 'ichimliklar': // Ichimliklar
      return const CategoryVisual(Icons.local_drink, Color(0xFF06B6D4));
    case 'meva-sabzavot': // Meva va sabzavot
      return const CategoryVisual(Icons.park, Color(0xFF22C55E));
    case 'gosht': // Go'sht va baliq
      return const CategoryVisual(Icons.set_meal, Color(0xFFEF4444));
    case 'bakaleya': // Bakaleya (un, yog', makaron...)
      return const CategoryVisual(Icons.grain, Color(0xFFCA8A04));
    case 'uy-rozgor': // Uy-ro'zg'or
      return const CategoryVisual(Icons.cleaning_services, Color(0xFF14B8A6));
  }
  // Slug mos kelmasa — nom bo'yicha taxmin, bo'lmasa umumiy ikonka.
  return _byName(name) ??
      const CategoryVisual(Icons.category_outlined, AppColors.primary);
}

/// Slug noma'lum bo'lsa, kategoriya nomidagi kalit so'zlar bo'yicha taxmin.
CategoryVisual? _byName(String? name) {
  if (name == null) return null;
  final n = name.toLowerCase();
  if (n.contains('sut')) {
    return const CategoryVisual(Icons.water_drop, Color(0xFF3B82F6));
  }
  if (n.contains('non') || n.contains('shirin')) {
    return const CategoryVisual(Icons.bakery_dining, Color(0xFFD97706));
  }
  if (n.contains('ichim')) {
    return const CategoryVisual(Icons.local_drink, Color(0xFF06B6D4));
  }
  if (n.contains('meva') || n.contains('sabzavot')) {
    return const CategoryVisual(Icons.park, Color(0xFF22C55E));
  }
  if (n.contains('sht') || n.contains('baliq') || n.contains('gosht')) {
    return const CategoryVisual(Icons.set_meal, Color(0xFFEF4444));
  }
  if (n.contains('bakaleya')) {
    return const CategoryVisual(Icons.grain, Color(0xFFCA8A04));
  }
  if (n.contains('zg') || n.contains('rozgor') || n.contains('uy')) {
    return const CategoryVisual(Icons.cleaning_services, Color(0xFF14B8A6));
  }
  return null;
}
