package com.borrowbox.kuet.search;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.gridlayout.widget.GridLayout;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.borrowbox.kuet.adapters.ProductAdapter;
import com.borrowbox.kuet.bookings.RentActivity;
import com.borrowbox.kuet.auth.WelcomeActivity;
import com.borrowbox.kuet.databinding.ActivitySearchBinding;
import com.borrowbox.kuet.localdatabase.RecentSearchDao;
import com.borrowbox.kuet.models.Product;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Samsung-style Search Screen.
 * - Back button returns to previous screen
 * - Editable search field: on type → live results (product cards)
 * - Recent Searches: stored in SQLite via RecentSearchDao (ExecutorService background thread)
 * - Each recent chip is deletable
 * - Categories section with tap → filter
 *
 * Course coverage: SQLite (SQLiteOpenHelper), ExecutorService, Handler(mainLooper).
 */
public class SearchActivity extends AppCompatActivity {

    // Categories with emojis matching our product data
    private static final String[][] CATEGORIES = {
        {"🧮", "Calculators"},
        {"📐", "Drafters"},
        {"📚", "Textbooks"},
        {"🥼", "Lab Coats"},
        {"⚡", "Electronics"},
        {"🚴", "Bicycles"},
        {"🔬", "Lab Gear"},
        {"💻", "Laptops"},
        {"📏", "Stationery"},
        {"🎒", "Bags"},
        {"🔧", "Tools"},
        {"📷", "Cameras"},
        {"🎸", "Instruments"},
    };

    private ActivitySearchBinding binding;
    private RecentSearchDao recentSearchDao;
    private ProductAdapter resultsAdapter;
    private final List<Product> sampleProducts = new ArrayList<>();
    private final List<Product> filteredResults = new ArrayList<>();
    private boolean isGuest = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        isGuest = getIntent().getBooleanExtra(WelcomeActivity.EXTRA_IS_GUEST, false);

        // Status bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            binding.searchTopBar.setPadding(
                    binding.searchTopBar.getPaddingLeft(),
                    statusBarHeight + 8,
                    binding.searchTopBar.getPaddingRight(),
                    binding.searchTopBar.getPaddingBottom());
            return insets;
        });

        recentSearchDao = new RecentSearchDao(this);

        buildSampleProducts();
        setupResultsRecyclerView();
        setupSearchInput();
        setupCategoryGrid();
        setupBackButton();

        // Focus search field and show keyboard
        binding.etSearchInput.post(() -> {
            binding.etSearchInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(binding.etSearchInput, InputMethodManager.SHOW_IMPLICIT);
        });

        // Load recent searches from SQLite (background thread via DAO)
        loadRecentSearches();
    }

    private void buildSampleProducts() {
        sampleProducts.add(new Product("1", "Casio fx-991EX ClassWiz", 20.0, false, true,
                "Calculators", "CSE Dept", "Amar Ekushey Hall", "Tanvir Ahmed",
                "Original scientific calculator.", "+8801700000001", "🧮"));
        sampleProducts.add(new Product("2", "Rotary Mini Drafter + Sheet Clamps", 15.0, false, true,
                "Drafters", "ME Dept", "Fazlul Haque Hall", "Shadman Sakib",
                "Full precision engineering drawing drafter set.", "+8801700000002", "📐"));
        sampleProducts.add(new Product("3", "Cotton White Lab Coat (Size L)", 0.0, true, true,
                "Lab Coats", "ChE Dept", "Lalan Shah Hall", "Mahir Faysal",
                "Pure cotton protective lab coat.", "+8801700000003", "🥼"));
        sampleProducts.add(new Product("4", "Arduino Mega 2560 & Sensor Kit", 35.0, false, true,
                "Electronics", "EEE Dept", "Bangabandhu Hall", "Rifat Hasan",
                "Microcontroller board with sensors.", "+8801700000004", "⚡"));
        sampleProducts.add(new Product("5", "Higher Engineering Mathematics - B.S. Grewal", 10.0, false, false,
                "Textbooks", "Math Dept", "Khan Jahan Ali Hall", "Nafis Imtiaz",
                "44th edition, clean textbook.", "+8801700000005", "📚"));
        sampleProducts.add(new Product("6", "Phoenix Hero 26\" Campus Bicycle", 50.0, false, true,
                "Bicycles", "CE Dept", "Rokeya Hall", "Sadia Noor",
                "Smooth city ride bicycle.", "+8801700000006", "🚴"));
    }

    private void setupResultsRecyclerView() {
        resultsAdapter = new ProductAdapter(filteredResults, product -> {
            Intent intent = new Intent(this, com.borrowbox.kuet.items.ProductDetailsActivity.class);
            intent.putExtra(com.borrowbox.kuet.utils.AppConfig.EXTRA_PRODUCT, product);
            startActivity(intent);
        });
        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSearchResults.setAdapter(resultsAdapter);
        binding.rvSearchResults.setNestedScrollingEnabled(false);
    }

    private void setupSearchInput() {
        binding.etSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                binding.ivClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                if (query.isEmpty()) {
                    binding.cardSearchResults.setVisibility(View.GONE);
                } else {
                    filterAndShowResults(query);
                }
            }
        });

        // Clear X button
        binding.ivClearSearch.setOnClickListener(v -> {
            binding.etSearchInput.setText("");
            binding.cardSearchResults.setVisibility(View.GONE);
        });

        // IME action: Save to recent searches when user hits Search
        binding.etSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = binding.etSearchInput.getText().toString().trim();
                if (!query.isEmpty()) {
                    recentSearchDao.saveSearch(query);
                    loadRecentSearches();
                }
                return true;
            }
            return false;
        });
    }

    private void filterAndShowResults(String query) {
        filteredResults.clear();
        String lower = query.toLowerCase();
        for (Product p : sampleProducts) {
            if (p.getName().toLowerCase().contains(lower)
                    || p.getCategory().toLowerCase().contains(lower)
                    || p.getDepartment().toLowerCase().contains(lower)
                    || p.getHallLocation().toLowerCase().contains(lower)) {
                filteredResults.add(p);
            }
        }
        binding.tvSearchResultsHeader.setText(filteredResults.size() + " result" + (filteredResults.size() == 1 ? "" : "s") + " for \"" + query + "\"");
        resultsAdapter.notifyDataSetChanged();
        binding.cardSearchResults.setVisibility(filteredResults.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Loads recent searches from SQLite (background thread in DAO) then populates chip group on main thread. */
    private void loadRecentSearches() {
        recentSearchDao.getRecentSearches(recentList -> {
            // We are already on main thread (DAO delivers on mainHandler)
            ChipGroup chipGroup = binding.chipGroupRecent;
            chipGroup.removeAllViews();

            if (recentList.isEmpty()) {
                binding.cardRecentSearches.setVisibility(View.GONE);
                return;
            }

            binding.cardRecentSearches.setVisibility(View.VISIBLE);

            for (String queryText : recentList) {
                Chip chip = buildRecentChip(queryText);
                chipGroup.addView(chip);
            }

            // "Clear all" link
            binding.tvClearAllRecent.setOnClickListener(v -> {
                recentSearchDao.clearAllSearches(() -> {
                    chipGroup.removeAllViews();
                    binding.cardRecentSearches.setVisibility(View.GONE);
                });
            });
        });
    }

    private Chip buildRecentChip(String queryText) {
        Chip chip = new Chip(this);
        chip.setText(queryText);
        chip.setCloseIconVisible(true);
        chip.setCheckable(false);
        // Set chip background to input_bg_dark (#3D375C)
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                getResources().getColor(com.borrowbox.kuet.R.color.input_bg_dark, getTheme())));
        chip.setTextColor(getResources().getColor(com.borrowbox.kuet.R.color.light_text_primary, getTheme()));
        chip.setCloseIconTint(android.content.res.ColorStateList.valueOf(
                getResources().getColor(com.borrowbox.kuet.R.color.light_text_secondary, getTheme())));
        // chip.setChipStrokeColor / setChipStrokeWidth – these ARE the correct Material Chip methods
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(
                getResources().getColor(com.borrowbox.kuet.R.color.card_border, getTheme())));
        chip.setChipStrokeWidth(1f);

        // Tap chip → fill search input
        chip.setOnClickListener(v -> {
            binding.etSearchInput.setText(queryText);
            binding.etSearchInput.setSelection(queryText.length());
            filterAndShowResults(queryText);
        });

        // Close chip → delete from DB
        chip.setOnCloseIconClickListener(v -> {
            recentSearchDao.deleteSearch(queryText);
            loadRecentSearches();
        });

        return chip;
    }

    private void setupCategoryGrid() {
        GridLayout grid = binding.gridCategories;
        grid.removeAllViews();

        int colCount = 2;
        for (int i = 0; i < CATEGORIES.length; i++) {
            final String emoji = CATEGORIES[i][0];
            final String label = CATEGORIES[i][1];

            // Each category is a small MaterialCardView with emoji + label
            com.google.android.material.card.MaterialCardView catCard =
                    new com.google.android.material.card.MaterialCardView(this);
            catCard.setRadius(16 * getResources().getDisplayMetrics().density);
            catCard.setCardElevation(0);
            catCard.setStrokeWidth((int)(1 * getResources().getDisplayMetrics().density));
            catCard.setStrokeColor(getResources().getColor(com.borrowbox.kuet.R.color.card_border, getTheme()));
            catCard.setCardBackgroundColor(getResources().getColor(com.borrowbox.kuet.R.color.input_bg_dark, getTheme()));
            catCard.setClickable(true);
            android.util.TypedValue outValue = new android.util.TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
                catCard.setForeground(androidx.core.content.ContextCompat.getDrawable(this, outValue.resourceId));
            }

            // Layout params for the grid item
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width  = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(i % colCount, 1, GridLayout.FILL, 1f);
            lp.rowSpec = GridLayout.spec(i / colCount, 1, GridLayout.FILL, 1f);
            int margin = (int)(6 * getResources().getDisplayMetrics().density);
            lp.setMargins(margin, margin, margin, margin);
            catCard.setLayoutParams(lp);

            // Inner row: emoji + label
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int pad = (int)(12 * getResources().getDisplayMetrics().density);
            row.setPadding(pad, pad, pad, pad);

            TextView tvEmoji = new TextView(this);
            tvEmoji.setText(emoji);
            tvEmoji.setTextSize(22f);

            TextView tvLabel = new TextView(this);
            tvLabel.setText(label);
            tvLabel.setTextColor(getResources().getColor(com.borrowbox.kuet.R.color.light_text_primary, getTheme()));
            tvLabel.setTextSize(13f);
            tvLabel.setPadding((int)(8 * getResources().getDisplayMetrics().density), 0, 0, 0);
            tvLabel.setMaxLines(1);
            tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);

            row.addView(tvEmoji);
            row.addView(tvLabel);
            catCard.addView(row);

            catCard.setOnClickListener(v -> {
                binding.etSearchInput.setText(label);
                binding.etSearchInput.setSelection(label.length());
                filterAndShowResults(label);
                recentSearchDao.saveSearch(label);
            });

            grid.addView(catCard);
        }
    }

    private void setupBackButton() {
        binding.btnSearchBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, android.R.anim.fade_out);
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recentSearchDao.shutdown();
    }
}
