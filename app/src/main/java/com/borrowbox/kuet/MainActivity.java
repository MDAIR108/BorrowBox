package com.borrowbox.kuet;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.borrowbox.kuet.adapters.BorrowItemAdapter;
import com.borrowbox.kuet.databinding.ActivityMainBinding;
import com.borrowbox.kuet.models.BorrowItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final List<BorrowItem> allItems = new ArrayList<>();
    private final List<BorrowItem> displayedItems = new ArrayList<>();
    private BorrowItemAdapter adapter;
    private String selectedCategory = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initData();
        setupRecyclerView();
        setupSearch();
        setupCategoryChips();
        setupActions();
        setupBottomNav();
    }

    private void initData() {
        allItems.clear();
        allItems.add(new BorrowItem("1", "Casio fx-991EX ClassWiz", "Tools", 20.0, false,
                "CSE Dept", "Amar Ekushey Hall", "Tanvir Ahmed", 4.9, "🧮"));
        allItems.add(new BorrowItem("2", "Rotary Mini Drafter + Scale Set", "Tools", 15.0, false,
                "ME Dept", "Fazlul Haque Hall", "Shadman Sakib", 4.8, "📐"));
        allItems.add(new BorrowItem("3", "Pure Cotton Lab Coat (Size L, Clean)", "Labs", 0.0, true,
                "ChE Dept", "Lalan Shah Hall", "Mahir Faysal", 5.0, "🥼"));
        allItems.add(new BorrowItem("4", "Arduino Mega 2560 Starter Kit", "Gadgets", 35.0, false,
                "EEE Dept", "Bangabandhu Hall", "Rifat Hasan", 4.7, "⚡"));
        allItems.add(new BorrowItem("5", "Higher Engineering Mathematics - B.S. Grewal", "Books", 10.0, false,
                "Math Dept", "Khan Jahan Ali Hall", "Nafis Imtiaz", 4.9, "📚"));
        allItems.add(new BorrowItem("6", "Phoenix Hero 26\" Campus Bicycle", "Cycles", 50.0, false,
                "CE Dept", "Rokeya Hall", "Sadia Noor", 5.0, "🚴"));

        displayedItems.clear();
        displayedItems.addAll(allItems);
    }

    private void setupRecyclerView() {
        adapter = new BorrowItemAdapter(displayedItems, this::showRentalDialog);
        binding.recyclerViewItems.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewItems.setAdapter(adapter);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterItems(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupCategoryChips() {
        binding.chipAll.setOnClickListener(v -> selectCategory("All", binding.chipAll));
        binding.chipTools.setOnClickListener(v -> selectCategory("Tools", binding.chipTools));
        binding.chipBooks.setOnClickListener(v -> selectCategory("Books", binding.chipBooks));
        binding.chipLabs.setOnClickListener(v -> selectCategory("Labs", binding.chipLabs));
        binding.chipGadgets.setOnClickListener(v -> selectCategory("Gadgets", binding.chipGadgets));
        binding.chipCycles.setOnClickListener(v -> selectCategory("Cycles", binding.chipCycles));
    }

    private void selectCategory(String category, View selectedChip) {
        this.selectedCategory = category;
        resetChipStyles();
        selectedChip.setBackgroundTintList(getColorStateList(R.color.primary));
        if (selectedChip instanceof android.widget.TextView) {
            ((android.widget.TextView) selectedChip).setTextColor(getColor(R.color.white));
        }
        filterItems(binding.etSearch.getText().toString());
    }

    private void resetChipStyles() {
        View[] chips = new View[]{
                binding.chipAll, binding.chipTools, binding.chipBooks,
                binding.chipLabs, binding.chipGadgets, binding.chipCycles
        };
        for (View chip : chips) {
            chip.setBackgroundTintList(null);
            if (chip instanceof android.widget.TextView) {
                ((android.widget.TextView) chip).setTextColor(getColor(R.color.text_primary));
            }
        }
    }

    private void filterItems(String query) {
        displayedItems.clear();
        String lowerQuery = query.toLowerCase().trim();

        for (BorrowItem item : allItems) {
            boolean matchesCategory = selectedCategory.equals("All") || item.getCategory().equalsIgnoreCase(selectedCategory);
            boolean matchesQuery = lowerQuery.isEmpty()
                    || item.getTitle().toLowerCase().contains(lowerQuery)
                    || item.getDepartment().toLowerCase().contains(lowerQuery)
                    || item.getHallLocation().toLowerCase().contains(lowerQuery);

            if (matchesCategory && matchesQuery) {
                displayedItems.add(item);
            }
        }

        binding.tvListingCount.setText(displayedItems.size() + " items near you");
        adapter.notifyDataSetChanged();
    }

    private void setupActions() {
        binding.btnNotification.setOnClickListener(v ->
                Toast.makeText(this, "No new notifications", Toast.LENGTH_SHORT).show());

        binding.btnPostItem.setOnClickListener(v -> showPostItemDialog());
    }

    private void showPostItemDialog() {
        View dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, null);
        final EditText inputTitle = new EditText(this);
        inputTitle.setHint("Item name (e.g., T-Square Drafter)");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Post Campus Item to BorrowBox")
                .setMessage("Share your academic equipment or textbooks with fellow KUETians:")
                .setView(inputTitle)
                .setPositiveButton("Post Item", (dialog, which) -> {
                    String title = inputTitle.getText().toString().trim();
                    if (!title.isEmpty()) {
                        BorrowItem newItem = new BorrowItem(
                                String.valueOf(System.currentTimeMillis()),
                                title,
                                "Tools",
                                20.0,
                                false,
                                "KUET Campus",
                                "My Hall",
                                "You (KUET Student)",
                                5.0,
                                "📦"
                        );
                        allItems.add(0, newItem);
                        filterItems(binding.etSearch.getText().toString());
                        Toast.makeText(MainActivity.this, "Item posted successfully to BorrowBox!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRentalDialog(BorrowItem item) {
        String actionType = item.isFree() ? "Borrow" : "Rent";
        String message = "Item: " + item.getTitle() + "\n" +
                "Rate: " + item.getPriceFormatted() + "\n" +
                "Owner: " + item.getOwnerName() + " (" + item.getDepartment() + ")\n" +
                "Location: " + item.getHallLocation() + "\n\n" +
                "Would you like to request this item from the owner?";

        new MaterialAlertDialogBuilder(this)
                .setTitle(actionType + " Confirmation")
                .setMessage(message)
                .setPositiveButton("Confirm " + actionType, (dialog, which) -> {
                    Toast.makeText(MainActivity.this,
                            "Request sent to " + item.getOwnerName() + "! Check your Bookings tab.",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Back", null)
                .show();
    }

    private void setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                filterItems("");
                return true;
            } else if (id == R.id.nav_search) {
                binding.etSearch.requestFocus();
                return true;
            } else if (id == R.id.nav_bookings) {
                Toast.makeText(this, "My Bookings: 1 active reservation (Casio fx-991EX)", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_chat) {
                Toast.makeText(this, "KUET Student Chat: No unread messages", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_profile) {
                showProfileDialog();
                return true;
            }
            return false;
        });
    }

    private void showProfileDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("KUET Student Profile")
                .setMessage("Student Name: Verified KUETian\n" +
                        "University: Khulna University of Engineering & Technology\n" +
                        "Status: Verified Student 🎓\n" +
                        "Halls: Connected across all 7 campus halls\n" +
                        "Trust Score: 5.0 ★")
                .setPositiveButton("OK", null)
                .show();
    }
}
