/// Auth modellari.

class Customer {
  final int id;
  final String phone;
  final String? name;
  final String? email;

  Customer({required this.id, required this.phone, this.name, this.email});

  factory Customer.fromJson(Map<String, dynamic> j) => Customer(
        id: j['id'] as int,
        phone: j['phone'] as String,
        name: j['name'] as String?,
        email: j['email'] as String?,
      );
}

class OtpRequestResult {
  final String phone;
  final int expiresInSeconds;

  /// Dev rejimda server kodni qaytaradi (test uchun). Prod'da null.
  final String? devCode;

  OtpRequestResult({required this.phone, required this.expiresInSeconds, this.devCode});

  factory OtpRequestResult.fromJson(Map<String, dynamic> j) => OtpRequestResult(
        phone: j['phone'] as String,
        expiresInSeconds: (j['expiresInSeconds'] as num).toInt(),
        devCode: j['devCode'] as String?,
      );
}

class AuthResult {
  final String token;
  final Customer customer;

  AuthResult({required this.token, required this.customer});

  factory AuthResult.fromJson(Map<String, dynamic> j) => AuthResult(
        token: j['token'] as String,
        customer: Customer.fromJson(j['customer'] as Map<String, dynamic>),
      );
}
