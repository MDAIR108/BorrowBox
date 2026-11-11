package com.borrowbox.kuet.rentals;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ActivityRentalDetailBinding;
import com.borrowbox.kuet.models.RentalDetail;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;

import org.json.JSONObject;

import java.util.List;

public class RentalDetailActivity extends AppCompatActivity {

    private ActivityRentalDetailBinding binding;
    private RentalDetail rental;
    private ApiClient apiClient;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        binding = ActivityRentalDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiClient = ApiClient.getInstance(this);
        sessionManager = new SessionManager(this);

        setupWindowInsets();
        setupToolbar();

        rental = (RentalDetail) getIntent().getSerializableExtra("rental");
        String rentalId = getIntent().getStringExtra("rental_id");
        if (rental != null && (rentalId == null || rentalId.isEmpty())) {
            rentalId = rental.getId();
        }

        if (rental != null) {
            populateDetails(rental);
        }

        // Fetch fresh details from GET /rentals/:id if rentalId is present
        if (rentalId != null && !rentalId.isEmpty()) {
            fetchFreshDetails(rentalId);
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String rentalId = intent.getStringExtra("rental_id");
        if (rentalId == null || rentalId.isEmpty()) {
            rentalId = intent.getStringExtra("rentalId");
        }
        if (rentalId != null && !rentalId.isEmpty()) {
            fetchFreshDetails(rentalId);
        }
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailRoot, (v, windowInsets) -> {
            Insets statusBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());

            binding.toolbarLayout.setPadding(
                    binding.toolbarLayout.getPaddingLeft(),
                    statusBarInsets.top + (int) (12 * getResources().getDisplayMetrics().density),
                    binding.toolbarLayout.getPaddingRight(),
                    (int) (12 * getResources().getDisplayMetrics().density)
            );

            binding.contentContainer.setPadding(
                    binding.contentContainer.getPaddingLeft(),
                    (int) (8 * getResources().getDisplayMetrics().density),
                    binding.contentContainer.getPaddingRight(),
                    navBarInsets.bottom + (int) (24 * getResources().getDisplayMetrics().density)
            );

            return windowInsets;
        });
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void populateDetails(RentalDetail item) {
        if (item == null) return;
        this.rental = item;

        // 1. Product Image (with safe fallback)
        RentalImageUtils.loadImage(binding.ivProductImage, item.getProductImage());

        // 2. Status Badge (Case-insensitive check)
        String canonicalStatus = item.getCanonicalStatus();
        binding.tvStatusBadge.setText(canonicalStatus);
        switch (canonicalStatus) {
            case "Accepted":
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_accepted);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_accepted_text));
                break;
            case "Rejected":
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_rejected);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_rejected_text));
                break;
            case "Cancelled":
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_cancelled);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_cancelled_text));
                break;
            default: // Pending
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_pending);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_pending_text));
                break;
        }

        // 3. Product Info
        binding.tvProductName.setText(item.getProductName());

        String category = item.getCategory();
        binding.tvCategory.setText("Category: " + (category.isEmpty() ? "General" : category));

        String condition = item.getCondition();
        binding.tvCondition.setText("Condition: " + (condition.isEmpty() ? "Good" : condition));

        String model = item.getModel();
        if (model != null && !model.trim().isEmpty()) {
            binding.tvModel.setVisibility(View.VISIBLE);
            binding.tvModel.setText("Model: " + model.trim());
        } else {
            binding.tvModel.setVisibility(View.GONE);
        }

        String description = item.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            binding.tvDescription.setText(description.trim());
        } else {
            binding.tvDescription.setText("No description provided.");
        }

        List<String> features = item.getFeatures();
        if (features != null && !features.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < features.size(); i++) {
                sb.append("• ").append(features.get(i));
                if (i < features.size() - 1) {
                    sb.append("\n");
                }
            }
            binding.tvFeaturesHeader.setVisibility(View.VISIBLE);
            binding.tvFeatures.setVisibility(View.VISIBLE);
            binding.tvFeatures.setText(sb.toString());
        } else {
            binding.tvFeaturesHeader.setVisibility(View.GONE);
            binding.tvFeatures.setVisibility(View.GONE);
        }

        // 4. Rental Pricing & Duration
        binding.tvRentPerDay.setText(item.getFormattedRentPerDay());
        binding.tvDays.setText(item.getFormattedDays());
        binding.tvRequestDate.setText(item.getFormattedRequestDate());
        binding.tvTotalPrice.setText(item.getFormattedTotalPrice());

        // 5. Parties involved
        binding.tvOwnerName.setText("Owner: " + item.getOwnerName());
        binding.tvRenterName.setText("Renter: " + item.getRenterName());
    }

    private void fetchFreshDetails(String rentalId) {
        String token = sessionManager.getToken();
        apiClient.getRentalDetails(rentalId, token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                if (response != null) {
                    JSONObject rentalObj = response.optJSONObject("rental");
                    if (rentalObj == null) {
                        rentalObj = response;
                    }
                    RentalDetail fresh = RentalDetail.fromJson(rentalObj);
                    populateDetails(fresh);
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Keep displaying the passed rental data if live refresh fails
            }
        });
    }
}
