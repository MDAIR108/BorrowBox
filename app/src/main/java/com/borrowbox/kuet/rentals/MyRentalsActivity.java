package com.borrowbox.kuet.rentals;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ActivityMyRentalsBinding;
import com.borrowbox.kuet.models.RentalDetail;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MyRentalsActivity extends AppCompatActivity {

    private ActivityMyRentalsBinding binding;
    private final List<RentalDetail> rentalList = new ArrayList<>();
    private RentalAdapter adapter;
    private SessionManager sessionManager;
    private ApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        binding = ActivityMyRentalsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        apiClient = ApiClient.getInstance(this);

        setupWindowInsets();
        setupToolbar();
        setupRecyclerView();
        setupSwipeRefresh();
        setupRetryButton();
        com.borrowbox.kuet.utils.BottomNavHelper.setupBottomNav(this, binding.bottomNavigation, binding.fabAddItem, R.id.nav_rentals);

        fetchRentals();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding.bottomNavigation != null) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_rentals);
        }
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout, (v, windowInsets) -> {
            Insets statusBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());

            binding.headerLayout.setPadding(
                    binding.headerLayout.getPaddingLeft(),
                    statusBarInsets.top + (int) (12 * getResources().getDisplayMetrics().density),
                    binding.headerLayout.getPaddingRight(),
                    (int) (12 * getResources().getDisplayMetrics().density)
            );

            binding.recyclerViewRentals.setPadding(
                    0,
                    (int) (8 * getResources().getDisplayMetrics().density),
                    0,
                    navBarInsets.bottom + (int) (120 * getResources().getDisplayMetrics().density)
            );

            return windowInsets;
        });
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new RentalAdapter(rentalList, this::openRentalDetails);
        binding.recyclerViewRentals.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewRentals.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(this, R.color.peach_accent),
                ContextCompat.getColor(this, R.color.pink_supporting)
        );
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(this, R.color.card_bg)
        );
        binding.swipeRefreshLayout.setOnRefreshListener(this::fetchRentals);
    }

    private void setupRetryButton() {
        binding.btnRetry.setOnClickListener(v -> fetchRentals());
    }

    private void openRentalDetails(RentalDetail rental) {
        Intent intent = new Intent(this, RentalDetailActivity.class);
        intent.putExtra("rental", rental);
        intent.putExtra("rental_id", rental.getId());
        startActivity(intent);
    }

    private void fetchRentals() {
        String token = sessionManager.getToken();

        if (!binding.swipeRefreshLayout.isRefreshing()) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }
        binding.layoutError.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);

        apiClient.getMyRentals(token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefreshLayout.setRefreshing(false);

                rentalList.clear();
                if (response != null) {
                    JSONArray arr = response.optJSONArray("rentals");
                    if (arr == null) {
                        arr = response.optJSONArray("myRentals");
                    }

                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.optJSONObject(i);
                            if (obj != null) {
                                rentalList.add(RentalDetail.fromJson(obj));
                            }
                        }
                    }
                }

                adapter.notifyDataSetChanged();

                if (rentalList.isEmpty()) {
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                    binding.recyclerViewRentals.setVisibility(View.GONE);
                } else {
                    binding.layoutEmpty.setVisibility(View.GONE);
                    binding.recyclerViewRentals.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String errorMessage) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefreshLayout.setRefreshing(false);

                if (rentalList.isEmpty()) {
                    binding.layoutError.setVisibility(View.VISIBLE);
                    binding.tvErrorMessage.setText(errorMessage != null ? errorMessage : "Unable to retrieve rentals.");
                    binding.recyclerViewRentals.setVisibility(View.GONE);
                } else {
                    Toast.makeText(MyRentalsActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
