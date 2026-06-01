import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_client.dart';
import '../../core/providers.dart';
import '../../shared/models/address_model.dart';

/// Yetkazib berish manzillari API.
class AddressRepository {
  final ApiClient _api;
  AddressRepository(this._api);

  Future<List<Address>> list() async {
    final res = await _api.get('/addresses');
    final data = res.data as List;
    return data.map((e) => Address.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<Address> create({
    String? label,
    required String addressLine,
    double? lat,
    double? lng,
    String? comment,
  }) async {
    final res = await _api.post('/addresses', data: {
      if (label != null) 'label': label,
      'addressLine': addressLine,
      if (lat != null) 'lat': lat,
      if (lng != null) 'lng': lng,
      if (comment != null) 'comment': comment,
    });
    return Address.fromJson(res.data as Map<String, dynamic>);
  }

  Future<Address> update(
    int id, {
    String? label,
    required String addressLine,
    double? lat,
    double? lng,
    String? comment,
  }) async {
    final res = await _api.put('/addresses/$id', data: {
      if (label != null) 'label': label,
      'addressLine': addressLine,
      if (lat != null) 'lat': lat,
      if (lng != null) 'lng': lng,
      if (comment != null) 'comment': comment,
    });
    return Address.fromJson(res.data as Map<String, dynamic>);
  }

  Future<void> delete(int id) async {
    await _api.delete('/addresses/$id');
  }
}

final addressRepositoryProvider = Provider<AddressRepository>(
  (ref) => AddressRepository(ref.watch(apiClientProvider)),
);

final addressesProvider = FutureProvider.autoDispose<List<Address>>(
  (ref) => ref.watch(addressRepositoryProvider).list(),
);

/// Checkout uchun tanlangan manzil (id). UI shu provayderni o'zgartiradi.
final selectedAddressIdProvider = StateProvider<int?>((ref) => null);
