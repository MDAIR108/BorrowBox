package com.borrowbox.kuet;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.borrowbox.kuet.adapters.ProductAdapter;
import com.borrowbox.kuet.auth.WelcomeActivity;
import com.borrowbox.kuet.bookings.RentActivity;
import com.borrowbox.kuet.bookings.RentalRequestReviewActivity;
import com.borrowbox.kuet.databinding.ActivityMainBinding;
import com.borrowbox.kuet.models.Product;
import com.borrowbox.kuet.models.RentalRequest;
import com.borrowbox.kuet.profile.ProfileActivity;
import com.borrowbox.kuet.repositories.RentalRepository;
import com.borrowbox.kuet.repositories.UserRepository;
import com.borrowbox.kuet.search.SearchActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final List<Product> allProducts = new ArrayList<>();
    private final List<Product> displayedProducts = new ArrayList<>();
    private ProductAdapter adapter;
    private boolean isGuest = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        isGuest = getIntent().getBooleanExtra(WelcomeActivity.EXTRA_IS_GUEST, false);

        // Handle window insets so header never sits under the status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.fixedTopContainer, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), statusBarHeight, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        if (isGuest) {
            binding.tvUserStatus.setText("Guest Mode");
        }

        initSampleData();
        setupRecyclerView();
        setupSearchPill();
        setupBottomNav();
        playHeaderAnimation();
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
        adapter = new ProductAdapter(displayedProducts, this::showProductDetailsDialog);
        binding.recyclerViewProducts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewProducts.setAdapter(adapter);
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

    private void showProductDetailsDialog(Product product) {
        String statusNote = product.isAvailable() ? "● Available for Immediate Pickup" : "○ Currently Rented (Unavailable)";
        String message = "Rate: " + product.getFormattedPrice() + "\n" +
                "Status: " + statusNote + "\n" +
                "Owner: " + product.getOwnerName() + " (" + product.getDepartment() + ")\n" +
                "Pickup Location: " + product.getHallLocation() + "\n\n" +
                "Description:\n" + product.getDescription();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(product.getName())
                .setMessage(message)
                .setNegativeButton("Close", null);

        if (product.isAvailable()) {
            builder.setPositiveButton("Rent Product", (dialog, which) -> {
                if (isGuest) {
                    showGuestRedirectDialog("rent products");
                } else {
                    Intent intent = new RentActivity.Builder(this)
                            .setProduct(product)
                            .build();
                    startActivity(intent);
                }
            });
        }

        builder.show();
    }

    private void setupBottomNav() {
        // Distinct Central + Add Item Button
        binding.fabAddItem.setOnClickListener(v -> {
            if (isGuest) {
                showGuestRedirectDialog("post and rent items");
            } else {
                Toast.makeText(this, "Add Item – coming in Phase 4 (Product listings).", Toast.LENGTH_SHORT).show();
            }
        });

        // Bottom Navigation Tabs
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                binding.recyclerViewProducts.smoothScrollToPosition(0);
                filterProducts("");
                return true;
            } else if (id == R.id.nav_search) {
                Intent searchIntent = new Intent(MainActivity.this, SearchActivity.class);
                searchIntent.putExtra(WelcomeActivity.EXTRA_IS_GUEST, isGuest);
                startActivity(searchIntent);
                return true;
            } else if (id == R.id.nav_rentals) {
                if (isGuest) {
                    showGuestRedirectDialog("view your rentals");
                } else {
                    showRentalsHubDialog();
                }
                return true;
            } else if (id == R.id.nav_profile) {
                Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                intent.putExtra(WelcomeActivity.EXTRA_IS_GUEST, isGuest);
                startActivity(intent);
                return true;
            }
            return false;
        });
    }

    private void showRentalsHubDialog() {
        RentalRepository rentalRepo = new RentalRepository(this);
        UserRepository userRepo = new UserRepository(this);
        String currentUid = userRepo.getCurrentUserId();

        rentalRepo.getRenterRequests(currentUid, new RentalRepository.DataCallback<List<RentalRequest>>() {
            @Override
            public void onSuccess(List<RentalRequest> requests) {
                String[] options = {
                    "Incoming Requests for My Items (Owner Review)",
                    "My Submitted Borrow Requests (" + requests.size() + ")"
                };

                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("Rentals & Borrowing Hub")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                showOwnerIncomingRequests(rentalRepo, currentUid);
                            } else {
                                showMyBorrowRequests(requests);
                            }
                        })
                        .setNegativeButton("Close", null)
                        .show();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(MainActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showOwnerIncomingRequests(RentalRepository rentalRepo, String currentUid) {
        rentalRepo.getOwnerRequests(currentUid, new RentalRepository.DataCallback<List<RentalRequest>>() {
            @Override
            public void onSuccess(List<RentalRequest> requests) {
                if (requests.isEmpty()) {
                    RentalRequest sample = new RentalRequest();
                    sample.setId("req_sample_review");
                    sample.setProductId("1");
                    sample.setProductName("Casio fx-991EX ClassWiz");
                    sample.setProductPricePerDay(20.0);
                    sample.setProductEmoji("🧮");
                    sample.setPickupLocation("Amar Ekushey Hall");
                    sample.setOwnerId(currentUid);
                    sample.setOwnerName("You (Listing Owner)");
                    sample.setRenterId("sample_borrower_99");
                    sample.setRenterName("Sadia Noor");
                    sample.setRenterDepartment("Civil Engineering (CE)");
                    sample.setRenterRollNumber("1901045");
                    sample.setRenterAddress("Rokeya Hall, Room 204");
                    sample.setRenterPhone("+8801700000006");
                    sample.setRenterWhatsApp("+8801700000006");
                    sample.setRenterTrustScore(4.9);
                    sample.setRenterVerificationStatus("Approved");
                    sample.setStartDateMillis(System.currentTimeMillis());
                    sample.setReturnDateMillis(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 3));
                    sample.setDurationDays(3);
                    sample.setTotalCost(60.0);
                    sample.setStatus("Pending");

                    Intent intent = new RentalRequestReviewActivity.Builder(MainActivity.this)
                            .setRentalRequest(sample)
                            .build();
                    startActivity(intent);
                } else {
                    String[] titles = new String[requests.size()];
                    for (int i = 0; i < requests.size(); i++) {
                        RentalRequest r = requests.get(i);
                        titles[i] = r.getProductName() + " from " + r.getRenterName() + " (" + r.getStatus() + ")";
                    }
                    new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle("Select Request to Review")
                            .setItems(titles, (dialog, which) -> {
                                Intent intent = new RentalRequestReviewActivity.Builder(MainActivity.this)
                                        .setRentalRequest(requests.get(which))
                                        .build();
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(MainActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMyBorrowRequests(List<RentalRequest> requests) {
        if (requests.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("No Active Requests")
                    .setMessage("You haven't submitted any borrow requests yet. Browse products on the home tab and tap 'Rent' to get started!")
                    .setPositiveButton("Browse Items", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (RentalRequest r : requests) {
            sb.append("• ").append(r.getProductName())
                    .append("\n  Status: ").append(r.getStatus())
                    .append("\n  Dates: ").append(r.getFormattedStartDate()).append(" - ").append(r.getFormattedReturnDate())
                    .append("\n  ").append(r.getCountdownText())
                    .append("\n\n");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Your Borrow Requests")
                .setMessage(sb.toString().trim())
                .setPositiveButton("OK", null)
                .show();
    }

    private void showGuestRedirectDialog(String actionDescription) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Account Required")
                .setMessage("Guests can browse and view details, but you must log in or register with your KUET student email to " + actionDescription + ".")
                .setPositiveButton("Log In / Register", (dialog, which) -> {
                    Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
