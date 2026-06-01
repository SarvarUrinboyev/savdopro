/// Yetkazib berish manzili modeli.
class Address {
  final int id;
  final String? label;
  final String addressLine;
  final double? lat;
  final double? lng;
  final String? comment;

  Address({
    required this.id,
    this.label,
    required this.addressLine,
    this.lat,
    this.lng,
    this.comment,
  });

  factory Address.fromJson(Map<String, dynamic> j) => Address(
        id: j['id'] as int,
        label: j['label'] as String?,
        addressLine: j['addressLine'] as String,
        lat: (j['lat'] as num?)?.toDouble(),
        lng: (j['lng'] as num?)?.toDouble(),
        comment: j['comment'] as String?,
      );
}
