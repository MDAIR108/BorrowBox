# Course Coverage

Track which course topics are implemented in the BorrowBox project.

| Topic | Implementation location | Class/file | How it works | How to demo | Status |
|-------|------------------------|------------|--------------|-------------|--------|
| View Binding | `app/build.gradle`, Activities & Adapters | `MainActivity.java`, `WelcomeActivity.java`, `ProductAdapter.java` | Generates compile-time type-safe bindings for XML views avoiding `findViewById`. | Launch app, interact with buttons, RecyclerView items, and search bar. | Completed |
| RecyclerView & Custom Adapter | Main marketplace screen | `ProductAdapter.java`, `item_product_card.xml`, `MainActivity.java` | Renders dynamic list of product cards with photo, pricing, availability badge, and detail callbacks. | Scroll the product listings on Home screen; observe smooth rendering and badge updates. | Completed |
| Material Navigation & Floating Action Button | Bottom navigation bar | `activity_main.xml`, `MainActivity.java` | `CoordinatorLayout` + `BottomAppBar` with center cradle and elevated central `FloatingActionButton` for adding items. | Tap bottom nav tabs (Home, Search, Rentals, Profile) or central `+` FAB. | Completed |
| Custom Themes, Colors & Rounded Shapes | Resources | `colors.xml`, `themes.xml` | Configures KUET brand palette (`#312C51`, `#48426D`, `#F0C38E`, `#F1A9A9`) and rounded card/pill shapes everywhere. | Observe rounded cards, pill buttons, floating search bar, and custom branded status bar. | Completed |
