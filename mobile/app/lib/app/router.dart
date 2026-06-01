import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/address/screens/address_edit_screen.dart';
import '../features/address/screens/addresses_screen.dart';
import '../features/auth/auth_controller.dart';
import '../features/auth/screens/otp_screen.dart';
import '../features/auth/screens/phone_screen.dart';
import '../features/catalog/screens/categories_screen.dart';
import '../features/catalog/screens/category_products_screen.dart';
import '../features/catalog/screens/home_screen.dart';
import '../features/catalog/screens/product_detail_screen.dart';
import '../features/catalog/screens/search_screen.dart';
import '../features/cart/screens/cart_screen.dart';
import '../features/orders/screens/checkout_screen.dart';
import '../features/orders/screens/order_detail_screen.dart';
import '../features/orders/screens/orders_screen.dart';
import '../features/profile/screens/profile_edit_screen.dart';
import '../features/profile/screens/profile_screen.dart';
import '../shared/models/address_model.dart';
import 'scaffold_with_nav.dart';
import 'splash_screen.dart';

final _rootKey = GlobalKey<NavigatorState>();

/// Auth holatiga bog'langan GoRouter. Holat o'zgarganda qayta yo'naltiradi.
final routerProvider = Provider<GoRouter>((ref) {
  // Auth holati o'zgarganda router'ni qayta baholaydi.
  final refresh = ValueNotifier<int>(0);
  ref.onDispose(refresh.dispose);
  ref.listen(authControllerProvider, (_, __) => refresh.value++);

  return GoRouter(
    navigatorKey: _rootKey,
    initialLocation: '/splash',
    refreshListenable: refresh,
    redirect: (context, state) {
      final status = ref.read(authControllerProvider).status;
      final loc = state.matchedLocation;
      const authLocations = {'/login', '/otp'};

      if (status == AuthStatus.unknown) {
        return loc == '/splash' ? null : '/splash';
      }
      if (status == AuthStatus.unauthenticated) {
        return authLocations.contains(loc) ? null : '/login';
      }
      // authenticated — splash/login/otp'da bo'lsa asosiy ekranga.
      if (loc == '/splash' || authLocations.contains(loc)) return '/home';
      return null;
    },
    routes: [
      GoRoute(path: '/splash', builder: (_, __) => const SplashScreen()),
      GoRoute(path: '/login', builder: (_, __) => const PhoneScreen()),
      GoRoute(
        path: '/otp',
        builder: (_, state) {
          final extra = (state.extra as Map?) ?? const {};
          return OtpScreen(
            phone: extra['phone'] as String? ?? '',
            devCode: extra['devCode'] as String?,
            expiresInSeconds: (extra['expiresIn'] as int?) ?? 120,
          );
        },
      ),

      // Bottom-nav shell (5 tab).
      StatefulShellRoute.indexedStack(
        builder: (_, __, shell) => ScaffoldWithNavBar(navigationShell: shell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(path: '/home', builder: (_, __) => const HomeScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/categories', builder: (_, __) => const CategoriesScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/cart', builder: (_, __) => const CartScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/orders', builder: (_, __) => const OrdersScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/profile', builder: (_, __) => const ProfileScreen()),
          ]),
        ],
      ),

      // Full-screen (nav bar'siz) yo'llar — root navigator'da.
      GoRoute(
        path: '/product/:id',
        parentNavigatorKey: _rootKey,
        builder: (_, state) =>
            ProductDetailScreen(productId: int.parse(state.pathParameters['id']!)),
      ),
      GoRoute(
        path: '/category/:id',
        parentNavigatorKey: _rootKey,
        builder: (_, state) => CategoryProductsScreen(
          categoryId: int.parse(state.pathParameters['id']!),
          categoryName: state.extra as String?,
        ),
      ),
      GoRoute(
        path: '/search',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const SearchScreen(),
      ),
      GoRoute(
        path: '/checkout',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const CheckoutScreen(),
      ),
      GoRoute(
        path: '/order/:id',
        parentNavigatorKey: _rootKey,
        builder: (_, state) =>
            OrderDetailScreen(orderId: int.parse(state.pathParameters['id']!)),
      ),
      GoRoute(
        path: '/addresses',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const AddressesScreen(),
      ),
      GoRoute(
        path: '/address/edit',
        parentNavigatorKey: _rootKey,
        builder: (_, state) => AddressEditScreen(existing: state.extra as Address?),
      ),
      GoRoute(
        path: '/profile/edit',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const ProfileEditScreen(),
      ),
    ],
  );
});
