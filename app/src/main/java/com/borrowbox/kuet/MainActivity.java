package com.borrowbox.kuet;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.borrowbox.kuet.adapters.ProductAdapter;
import com.borrowbox.kuet.auth.WelcomeActivity;
import com.borrowbox.kuet.databinding.ActivityMainBinding;
import com.borrowbox.kuet.models.Product;
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
        if (isGuest) {
            binding.tvUserStatus.setText("Guest Mode");
        }

        initSampleData();
        setupRecyclerView();
        setupFloatingSearchBar();
        setupBottomNav();
    }

    private void initSampleData() {
        allProducts.clear();
        allProducts.add(new Product("1", "Casio fx-991EX ClassWiz", 20.0, false, true,
                "Tools", "CSE Dept", "Amar Ekushey Hall", "Tanvir Ahmed",
                "Original scientific calculator with natural textbook display. Essential for calculus and exam hall.",
                "+8801700000001", "🧮"));

        allProducts.add(new Product("2", "Rotary Mini Drafter + Sheet Clamps", 15.0, false, true,
                "Tools", "ME Dept", "Fazlul Haque Hall", "Shadman Sakib",
                "Full precision engineering drawing drafter set with scale and clamp holder. Ideal for ME drawing sessional.",
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
                "Smooth city ride bicycle with front basket and lock. Great for fast transit between halls and academic buildings.",
                "+8801700000006", "🚴"));

        displayedProducts.clear();
        displayedProducts.addAll(allProducts);
    }

    private void setupRecyclerView() {
        adapter = new ProductAdapter(displayedProducts, this::showProductDetailsDialog);
        binding.recyclerViewProducts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewProducts.setAdapter(adapter);
    }

    private void setupFloatingSearchBar() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProducts(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
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
                    Toast.makeText(this,
                            "Rental request for \"" + product.getName() + "\" will connect in Phase 5.",
                            Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Add Item flow will be connected in Phase 4 (Product listings).", Toast.LENGTH_SHORT).show();
            }
        });

        // Bottom Navigation Tabs
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                binding.recyclerViewProducts.smoothScrollToPosition(0);
                filterProducts("");
                binding.etSearch.setText("");
                return true;
            } else if (id == R.id.nav_search) {
                binding.etSearch.requestFocus();
                return true;
            } else if (id == R.id.nav_rentals) {
                if (isGuest) {
                    showGuestRedirectDialog("view your rentals");
                } else {
                    Toast.makeText(this, "My Rentals: Active and past rentals will display here (Phase 5).", Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if (id == R.id.nav_profile) {
                showProfileDialog();
                return true;
            }
            return false;
        });
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

    private void showProfileDialog() {
        String profileInfo = isGuest
                ? "You are currently browsing as a Guest.\n\nSign in with your @kuet.ac.bd account to unlock student verification, item posting, and direct rentals."
                : "Student: Verified KUETian\nAffiliation: Khulna University of Engineering & Technology\nHalls: Connected across campus\nTrust Score: 5.0 ★";

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("KUET Student Profile")
                .setMessage(profileInfo)
                .setNegativeButton("Close", null);

        if (isGuest) {
            builder.setPositiveButton("Log In", (dialog, which) -> {
                Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
                startActivity(intent);
                finish();
            });
        }

        builder.show();
    }
}
