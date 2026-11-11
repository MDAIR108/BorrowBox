package com.borrowbox.kuet;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.borrowbox.kuet.adapters.ProductAdapter;
import com.borrowbox.kuet.auth.AuthActivity;
import com.borrowbox.kuet.auth.WelcomeActivity;
import com.borrowbox.kuet.base.BaseActivity;
import com.borrowbox.kuet.bookings.RentalRequestReviewActivity;
import com.borrowbox.kuet.databinding.ActivityMainBinding;
import com.borrowbox.kuet.items.ProductDetailsActivity;
import com.borrowbox.kuet.models.Product;
import com.borrowbox.kuet.models.RentalRequest;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.borrowbox.kuet.profile.ProfileActivity;
import com.borrowbox.kuet.search.SearchActivity;
import com.borrowbox.kuet.utils.AppConfig;
import com.borrowbox.kuet.verification.VerificationActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private final List<Product> allProducts = new ArrayList<>();
    private final List<Product> displayedProducts = new ArrayList<>();
    private ProductAdapter adapter;
    private SessionManager sessionManager;
    private ApiClient apiClient;
    private boolean isGuest = false;

    private final android.os.Handler notifHandler = new android.os.Handler();
    private final Runnable notifPollRunnable = this::pollUnreadNotifications;
    private com.google.firebase.database.DatabaseReference unreadRtdbRef;
    private com.google.firebase.database.ValueEventListener unreadRtdbListener;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    syncFcmTokenWithServer();
                }
                checkBatteryOptimization();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Edge-to-edge: let our content draw behind status bar AND nav bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Make nav bar transparent so bottom bar background fills the gap
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        // Keep nav bar icons light (dark background)
        WindowInsetsControllerCompat wic = WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        wic.setAppearanceLightNavigationBars(false);

        sessionManager = new SessionManager(this);
        apiClient = ApiClient.getInstance(this);

        isGuest = getIntent().getBooleanExtra(WelcomeActivity.EXTRA_IS_GUEST, false) || sessionManager.isGuest();

        setupSafeAreas();
        initSampleData();
        setupRecyclerView();
        setupSearchPill();
        setupBottomNav();
        setupVerificationBanner();
        updateUserStatusBadge();
        playHeaderAnimation();
        setupBellIcon();
        checkNotificationPermission();
        fetchProductsFromBackend();
        handleNotificationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null || intent.getExtras() == null) return;
        String type = intent.getStringExtra("type");
        String rentalId = intent.getStringExtra("rentalId");
        if (rentalId == null) rentalId = intent.getStringExtra("rental_id");

        if ("rent_request".equals(type) && rentalId != null && !rentalId.trim().isEmpty()) {
            Intent detailsIntent = new Intent(this, com.borrowbox.kuet.rentals.RentalRequestDetailsActivity.class);
            detailsIntent.putExtra(com.borrowbox.kuet.rentals.RentalRequestDetailsActivity.EXTRA_RENTAL_ID, rentalId);
            detailsIntent.putExtra("rentalId", rentalId);
            detailsIntent.putExtra("rental_id", rentalId);
            startActivity(detailsIntent);

            intent.removeExtra("type");
            intent.removeExtra("rentalId");
            intent.removeExtra("rental_id");
        }
    }

    /**
     * ISSUE 1 (bottom nav gap fix) + TASK 1 (top safe area) + TASK 2 (bottom safe area).
     *
     * Key fix for bottom gap:
     *  - WindowCompat.setDecorFitsSystemWindows(false) is called in onCreate BEFORE setContentView.
     *  - window.setNavigationBarColor(TRANSPARENT) makes the nav area see-through.
     *  - We apply navBarInsets.bottom as INTERNAL paddingBottom on BottomAppBar itself, NOT as a
     *    layout margin. This means the bar's own opaque background fills the gap all the way to the
     *    physical screen edge. No transparent strip, no "peeking" cards.
     *  - The CoordinatorLayout's FAB anchor still centres the FAB over the bar automatically.
     *  - RecyclerView gets extra bottom padding so the last card clears the bar + FAB.
     */
    private void setupSafeAreas() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorRoot, (v, windowInsets) -> {
            Insets statusBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());

            // TASK 1: Header background extends behind status bar; content padded below cutout
            int topPadding = statusBarInsets.top + (int) (16 * getResources().getDisplayMetrics().density);
            binding.homeHeaderPanel.setPadding(
                    binding.homeHeaderPanel.getPaddingLeft(),
                    topPadding,
                    binding.homeHeaderPanel.getPaddingRight(),
                    (int) (12 * getResources().getDisplayMetrics().density)
            );

            // ISSUE 1 FIX: Apply bottom inset as INTERNAL padding so bar background fills
            // the full height including the system nav area. Do NOT use bottomMargin.
            int barBottomPad = navBarInsets.bottom;
            binding.bottomAppBar.setPaddingRelative(
                    binding.bottomAppBar.getPaddingStart(),
                    binding.bottomAppBar.getPaddingTop(),
                    binding.bottomAppBar.getPaddingEnd(),
                    barBottomPad
            );

            // RecyclerView: pad enough so the last card scrolls above bar+FAB
            // We post so that bottomAppBar.getHeight() reflects the inset-expanded height.
            final int navBottom = navBarInsets.bottom;
            binding.bottomAppBar.post(() -> {
                int barH = binding.bottomAppBar.getHeight();
                // bar height already includes internal navBottom padding after the call above
                int fabExtraRoom = (int) (36 * getResources().getDisplayMetrics().density);
                int totalBottomPadding = barH + fabExtraRoom;

                binding.fixedTopContainer.post(() -> {
                    int topH = binding.fixedTopContainer.getHeight();
                    binding.recyclerViewProducts.setPadding(
                            0,
                            topH + (int) (6 * getResources().getDisplayMetrics().density),
                            0,
                            totalBottomPadding
                    );
                });
            });

            return windowInsets;
        });
    }

    private void updateUserStatusBadge() {
        if (binding.tvWelcomeGreeting != null) {
            String name = sessionManager.getUserName();
            if (!isGuest && name != null && !name.trim().isEmpty() && !name.equalsIgnoreCase("KUETian")) {
                binding.tvWelcomeGreeting.setText("Welcome, " + name.trim() + "! ✨");
            } else {
                binding.tvWelcomeGreeting.setText("Welcome, KUETian! ✨");
            }
        }

        if (binding.cardUserAvatar != null) {
            if (isGuest) {
                com.borrowbox.kuet.rentals.RentalImageUtils.loadCircularImage(binding.ivUserAvatar, binding.tvAvatarInitial, "", "G");
                binding.cardUserAvatar.setOnClickListener(v -> showGuestRedirectDialog("view your profile"));
            } else {
                String photo = sessionManager.getProfilePhoto();
                String name = sessionManager.getUserName();
                com.borrowbox.kuet.rentals.RentalImageUtils.loadCircularImage(binding.ivUserAvatar, binding.tvAvatarInitial, photo, name);
                binding.cardUserAvatar.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                    intent.putExtra(WelcomeActivity.EXTRA_IS_GUEST, false);
                    startActivity(intent);
                });
            }
        }

        if (isGuest) {
            binding.tvUserStatus.setText("Guest Mode");
            binding.headerBadge.setOnClickListener(v -> showGuestRedirectDialog("manage your account"));
        } else {
            String roll = sessionManager.getUserRoll();
            if (!roll.isEmpty()) {
                binding.tvUserStatus.setText(sessionManager.isEmailVerified() ? "✓ Roll: " + roll : "⚠️ Roll: " + roll);
            } else {
                binding.tvUserStatus.setText("KUET Student");
            }
            binding.headerBadge.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                intent.putExtra(WelcomeActivity.EXTRA_IS_GUEST, false);
                startActivity(intent);
            });
        }
    }

    /** TASK 6: Banner on Home for unverified users */
    private void setupVerificationBanner() {
        if (!isGuest && sessionManager.isLoggedIn() && !sessionManager.isEmailVerified()) {
            binding.cardVerificationBanner.setVisibility(View.VISIBLE);
            binding.cardVerificationBanner.setOnClickListener(v -> {
                Intent intent = new Intent(this, VerificationActivity.class);
                intent.putExtra("email", sessionManager.getUserEmail());
                startActivity(intent);
            });
            binding.btnBannerVerify.setOnClickListener(v -> {
                Intent intent = new Intent(this, VerificationActivity.class);
                intent.putExtra("email", sessionManager.getUserEmail());
                startActivity(intent);
            });
        } else {
            binding.cardVerificationBanner.setVisibility(View.GONE);
        }

        // Adjust RecyclerView top padding when banner visibility changes
        binding.fixedTopContainer.post(() -> {
            int topH = binding.fixedTopContainer.getHeight();
            binding.recyclerViewProducts.setPadding(
                    0,
                    topH + (int) (6 * getResources().getDisplayMetrics().density),
                    0,
                    binding.recyclerViewProducts.getPaddingBottom()
            );
        });
    }

    /** Fade + slide-down entrance animation for the header and search pill */
    private void playHeaderAnimation() {
        binding.homeHeaderPanel.setAlpha(0f);
        binding.homeHeaderPanel.setTranslationY(-24f);
        binding.cardSearchBar.setAlpha(0f);
        binding.cardSearchBar.setTranslationY(-12f);

        ObjectAnimator headerFade = ObjectAnimator.ofFloat(binding.homeHeaderPanel, "alpha", 0f, 1f);
        ObjectAnimator headerSlide = ObjectAnimator.ofFloat(binding.homeHeaderPanel, "translationY", -24f, 0f);
        ObjectAnimator searchFade = ObjectAnimator.ofFloat(binding.cardSearchBar, "alpha", 0f, 1f);
        ObjectAnimator searchSlide = ObjectAnimator.ofFloat(binding.cardSearchBar, "translationY", -12f, 0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(headerFade, headerSlide);
        set.setDuration(360);

        AnimatorSet set2 = new AnimatorSet();
        set2.playTogether(searchFade, searchSlide);
        set2.setDuration(260);
        set2.setStartDelay(180);

        AnimatorSet all = new AnimatorSet();
        all.playSequentially(set, set2);
        all.start();
    }

    private void initSampleData() {
        allProducts.clear();
        allProducts.add(new Product("1", "Casio fx-991EX ClassWiz", 20.0, false, true,
                "Tools", "CSE Dept", "Amar Ekushey Hall", "Tanvir Ahmed",
                "Original scientific calculator with natural textbook display. Essential for calculus and exam hall.",
                "+8801700000001", "🧮"));

        allProducts.add(new Product("2", "Rotary Mini Drafter + Sheet Clamps", 15.0, false, true,
                "Tools", "ME Dept", "Fazlul Haque Hall", "Shadman Sakib",
                "Full precision engineering drawing drafter set with scale and clamp holder.",
                "+8801700000002", "📐"));

        allProducts.add(new Product("3", "Cotton White Lab Coat (Clean, Size L)", 0.0, true, true,
                "Labs", "ChE Dept", "Lalan Shah Hall", "Mahir Faysal",
                "Pure cotton protective lab coat. Freshly cleaned, suitable for chemistry and workshop labs.",
                "+8801700000003", "🥼"));

        allProducts.add(new Product("4", "Arduino Mega 2560 & Sensor Kit", 35.0, false, true,
                "Gadgets", "EEE Dept", "Bangabandhu Hall", "Rifat Hasan",
                "Microcontroller board complete with breadboard, jumper wires, ultrasonic and temperature sensors.",
                "+8801700000004", "⚡"));

        allProducts.add(new Product("5", "Higher Engineering Mathematics - B.S. Grewal", 10.0, false, false,
                "Books", "Math Dept", "Khan Jahan Ali Hall", "Nafis Imtiaz",
                "44th edition, clean textbook with all solved university question sets. Currently on loan until Friday.",
                "+8801700000005", "📚"));

        allProducts.add(new Product("6", "Phoenix Hero 26\" Campus Bicycle", 50.0, false, true,
                "Cycles", "CE Dept", "Rokeya Hall", "Sadia Noor",
                "Smooth city ride bicycle with front basket and lock.",
                "+8801700000006", "🚴"));

        displayedProducts.clear();
        displayedProducts.addAll(allProducts);
    }

    private void setupRecyclerView() {
        // TASK 5: Clicking "View Details" navigates to ProductDetailsActivity as a full separate page (not a popup)
        adapter = new ProductAdapter(displayedProducts, this::openProductDetailsPage);
        binding.recyclerViewProducts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewProducts.setAdapter(adapter);
    }

    /** TASK 5: Full separate page for product details */
    private void openProductDetailsPage(Product product) {
        Intent intent = new Intent(this, ProductDetailsActivity.class);
        intent.putExtra(AppConfig.EXTRA_PRODUCT, product);
        startActivity(intent);
    }

    /** Static search pill: tapping any part opens SearchActivity */
    private void setupSearchPill() {
        View.OnClickListener openSearch = v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra(WelcomeActivity.EXTRA_IS_GUEST, isGuest);
            startActivity(intent);
        };
        binding.cardSearchBar.setOnClickListener(openSearch);
        binding.tvItemCount.setText(allProducts.size() + " items");
    }

    private void filterProducts(String query) {
        displayedProducts.clear();
        String lowerQuery = query.toLowerCase().trim();

        if (lowerQuery.isEmpty()) {
            displayedProducts.addAll(allProducts);
        } else {
            for (Product product : allProducts) {
                if (product.getName().toLowerCase().contains(lowerQuery)
                        || product.getDepartment().toLowerCase().contains(lowerQuery)
                        || product.getHallLocation().toLowerCase().contains(lowerQuery)
                        || product.getCategory().toLowerCase().contains(lowerQuery)) {
                    displayedProducts.add(product);
                }
            }
        }

        binding.tvItemCount.setText(displayedProducts.size() + " items");
        adapter.notifyDataSetChanged();
    }

    /** Fetch live products from MySQL backend API (TASK 3) */
    private void fetchProductsFromBackend() {
        String token = sessionManager.getToken();
        apiClient.getProducts("", token, new ApiClient.ApiCallback<JSONArray>() {
            @Override
            public void onSuccess(JSONArray array) {
                if (array != null && array.length() > 0) {
                    allProducts.clear();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.optJSONObject(i);
                        if (obj != null) {
                            String id = obj.optString("id", String.valueOf(i + 1));
                            String name = obj.optString("name", "Product");
                            double price = obj.optDouble("pricePerDay", 0.0);
                            boolean isFree = obj.optBoolean("isFree", price == 0);
                            boolean isAvailable = obj.optBoolean("isAvailable", true);
                            String category = obj.optString("category", "General");
                            String dept = obj.optString("department", "KUET");
                            String hall = obj.optString("hallLocation", "Campus");
                            String ownerName = obj.optString("ownerName", "KUETian");
                            String ownerId = obj.optString("ownerId", "owner_" + id);
                            String whatsapp = obj.optString("whatsappNumber", "");
                            String desc = obj.optString("description", "");
                            String emoji = obj.optString("emoji", "📦");
                            String imageUrl = obj.optString("imageUrl", obj.optString("image_url", obj.optString("photoUrl", "")));

                            allProducts.add(new Product(id, name, price, isFree, isAvailable,
                                    category, dept, hall, ownerName, ownerId, desc, whatsapp, emoji, imageUrl));
                        }
                    }
                    displayedProducts.clear();
                    displayedProducts.addAll(allProducts);
                    binding.tvItemCount.setText(allProducts.size() + " items");
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onError(String errorMessage) {
                // If backend is offline or starting up, keep local sample products
            }
        });
    }

    private void setupBottomNav() {
        com.borrowbox.kuet.utils.BottomNavHelper.setupBottomNav(this, binding.bottomNavigation, binding.fabAddItem, R.id.nav_home);
    }

    private void showRentalsHubDialog() {
        String token = sessionManager.getToken();
        apiClient.getMyRentals(token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray myRentals = response.optJSONArray("myRentals");
                JSONArray incoming = response.optJSONArray("incomingRequests");

                int myCount = myRentals != null ? myRentals.length() : 0;
                int incomingCount = incoming != null ? incoming.length() : 0;

                String[] options = {
                        "Incoming Requests for My Items (" + incomingCount + ")",
                        "My Submitted Borrow Requests (" + myCount + ")"
                };

                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("Rentals & Borrowing Hub")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                showIncomingRequestsDialog(incoming);
                            } else {
                                showMyBorrowRequestsDialog(myRentals);
                            }
                        })
                        .setNegativeButton("Close", null)
                        .show();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(MainActivity.this, "Rentals: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showIncomingRequestsDialog(JSONArray incoming) {
        if (incoming == null || incoming.length() == 0) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Incoming Requests")
                    .setMessage("No incoming requests for your listed items yet.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < incoming.length(); i++) {
            JSONObject r = incoming.optJSONObject(i);
            if (r != null) {
                sb.append("• ").append(r.optString("productName"))
                        .append(" from ").append(r.optString("renterName"))
                        .append(" (").append(r.optString("renterRoll")).append(")\n")
                        .append("  Dates: ").append(r.optString("startDate")).append(" to ").append(r.optString("endDate"))
                        .append("\n  Status: ").append(r.optString("status").toUpperCase())
                        .append("\n\n");
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Incoming Rental Requests")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showMyBorrowRequestsDialog(JSONArray myRentals) {
        if (myRentals == null || myRentals.length() == 0) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Your Borrow Requests")
                    .setMessage("You haven't submitted any borrow requests yet. Tap 'View Details' on any item and submit a rental request!")
                    .setPositiveButton("Browse Items", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < myRentals.length(); i++) {
            JSONObject r = myRentals.optJSONObject(i);
            if (r != null) {
                sb.append("• ").append(r.optString("productName"))
                        .append("\n  Owner: ").append(r.optString("ownerName"))
                        .append("\n  Dates: ").append(r.optString("startDate")).append(" to ").append(r.optString("endDate"))
                        .append("\n  Total: ৳").append(r.optDouble("totalPrice", 0.0))
                        .append("\n  Status: ").append(r.optString("status").toUpperCase())
                        .append("\n\n");
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("My Borrow Requests")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showGuestRedirectDialog(String actionDescription) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Account Required")
                .setMessage("Guests can browse and view details, but you must log in or register with your KUET student email to " + actionDescription + ".")
                .setPositiveButton("Log In / Register", (dialog, which) -> {
                    Intent intent = new Intent(MainActivity.this, AuthActivity.class);
                    intent.putExtra(AppConfig.EXTRA_AUTH_TAB, "login");
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showVerificationRedirectDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Verification Required")
                .setMessage("Please verify your KUET student email before listing or renting items.")
                .setPositiveButton("Verify Now", (dialog, which) -> {
                    Intent intent = new Intent(MainActivity.this, VerificationActivity.class);
                    intent.putExtra("email", sessionManager.getUserEmail());
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupBellIcon() {
        if (binding.layoutBellIcon != null) {
            binding.layoutBellIcon.setOnClickListener(v -> {
                if (isGuest) {
                    showGuestRedirectDialog("view notifications");
                    return;
                }
                Intent intent = new Intent(MainActivity.this, com.borrowbox.kuet.notifications.NotificationsActivity.class);
                startActivity(intent);
            });
        }
    }

    private void checkNotificationPermission() {
        if (isGuest || !sessionManager.isLoggedIn()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Stay Updated on Rentals")
                            .setMessage("BorrowBox needs notification permission to send instant heads-up alerts with sound when someone requests your items or updates a rental status.")
                            .setPositiveButton("Allow", (d, w) -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                            .setNegativeButton("Not Now", (d, w) -> checkBatteryOptimization())
                            .show();
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
                return;
            }
        }
        syncFcmTokenWithServer();
        checkBatteryOptimization();
    }

    private void checkBatteryOptimization() {
        if (isGuest || !sessionManager.isLoggedIn()) return;
        if (sessionManager.hasShownBatteryDialog()) return;

        sessionManager.setShownBatteryDialog(true);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Background Alerts")
                .setMessage("To ensure you never miss rental requests while BorrowBox is closed, please enable background activity or disable battery optimization for BorrowBox.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    try {
                        Intent intent = new Intent();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            intent.setAction(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        } else {
                            intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                        }
                        startActivity(intent);
                    } catch (Exception e) {
                        try {
                            Intent fallback = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            fallback.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(fallback);
                        } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("Got It", null)
                .show();
    }

    private void syncFcmTokenWithServer() {
        if (isGuest || !sessionManager.isLoggedIn()) return;
        com.borrowbox.kuet.utils.FcmHelper.registerFcmToken(this);
    }

    private void pollUnreadNotifications() {
        if (sessionManager.isLoggedIn() && !sessionManager.isGuest()) {
            String token = sessionManager.getToken();
            apiClient.getUnreadNotificationCount(token, new ApiClient.ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject result) {
                    int count = result.optInt("count", 0);
                    if (binding.tvNotifBadge != null) {
                        if (count > 0) {
                            binding.tvNotifBadge.setVisibility(View.VISIBLE);
                            binding.tvNotifBadge.setText(count > 9 ? "9+" : String.valueOf(count));
                        } else {
                            binding.tvNotifBadge.setVisibility(View.GONE);
                        }
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    // Fail silently on background poll
                }
            });
        }

        notifHandler.postDelayed(notifPollRunnable, 20000);
    }

    private void setupRealtimeDbUnreadListener() {
        String userId = sessionManager.getUserId();
        if (userId == null || userId.trim().isEmpty() || sessionManager.isGuest()) return;

        // (a) Online presence tracking in RTDB under /presence/<userId>
        BorrowBoxApp.setupUserPresence(userId);

        // (b) Live unread notification counter listener under /unread/<userId>
        try {
            com.google.firebase.database.FirebaseDatabase rtdb = com.google.firebase.database.FirebaseDatabase.getInstance();
            unreadRtdbRef = rtdb.getReference("unread").child(userId);
            unreadRtdbListener = new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                    Long countVal = snapshot.getValue(Long.class);
                    int count = countVal != null ? countVal.intValue() : 0;
                    runOnUiThread(() -> {
                        if (binding.tvNotifBadge != null) {
                            if (count > 0) {
                                binding.tvNotifBadge.setVisibility(View.VISIBLE);
                                binding.tvNotifBadge.setText(count > 9 ? "9+" : String.valueOf(count));
                            } else {
                                binding.tvNotifBadge.setVisibility(View.GONE);
                            }
                        }
                    });
                }

                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {
                    // Fall back to polling
                }
            };
            unreadRtdbRef.addValueEventListener(unreadRtdbListener);
        } catch (Exception e) {
            android.util.Log.w("MainActivity", "RTDB unread listener not active: " + e.getMessage());
        }
    }

    private void stopRealtimeDbUnreadListener() {
        if (unreadRtdbRef != null && unreadRtdbListener != null) {
            try {
                unreadRtdbRef.removeEventListener(unreadRtdbListener);
            } catch (Exception ignored) {}
            unreadRtdbListener = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isGuest = sessionManager.isGuest();
        if (binding.bottomNavigation != null) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        }
        updateUserStatusBadge();
        setupVerificationBanner();
        fetchProductsFromBackend();

        setupRealtimeDbUnreadListener();

        notifHandler.removeCallbacks(notifPollRunnable);
        pollUnreadNotifications();
    }

    @Override
    protected void onPause() {
        super.onPause();
        notifHandler.removeCallbacks(notifPollRunnable);
        stopRealtimeDbUnreadListener();
    }
}
