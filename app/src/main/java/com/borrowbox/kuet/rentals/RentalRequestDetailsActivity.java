package com.borrowbox.kuet.rentals;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ActivityRentalRequestDetailsBinding;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class RentalRequestDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_RENTAL_ID = "rental_id";

    private ActivityRentalRequestDetailsBinding binding;
    private ApiClient apiClient;
    private SessionManager sessionManager;
    private String rentalId;

    private String requesterPhone = "";
    private String requesterName = "";
    private String productName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        binding = ActivityRentalRequestDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
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

            binding.scrollViewContent.setPadding(
                    0,
                    0,
                    0,
                    navBarInsets.bottom + (int) (48 * getResources().getDisplayMetrics().density)
            );

            return windowInsets;
        });

        apiClient = ApiClient.getInstance(this);
        sessionManager = new SessionManager(this);

        rentalId = getIntent().getStringExtra(EXTRA_RENTAL_ID);
        if (rentalId == null || rentalId.trim().isEmpty()) {
            rentalId = getIntent().getStringExtra("rentalId");
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRetry.setOnClickListener(v -> fetchRentalDetails());

        binding.btnAcceptRequest.setOnClickListener(v -> confirmAcceptRequest());
        binding.btnRejectRequest.setOnClickListener(v -> confirmRejectRequest());
        binding.btnWhatsAppChat.setOnClickListener(v -> openWhatsApp());

        if (rentalId == null || rentalId.trim().isEmpty()) {
            showError("Rental ID is missing.");
            return;
        }

        fetchRentalDetails();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String newRentalId = intent.getStringExtra(EXTRA_RENTAL_ID);
        if (newRentalId == null || newRentalId.trim().isEmpty()) {
            newRentalId = intent.getStringExtra("rentalId");
        }
        if (newRentalId == null || newRentalId.trim().isEmpty()) {
            newRentalId = intent.getStringExtra("rental_id");
        }
        if (newRentalId != null && !newRentalId.trim().isEmpty()) {
            this.rentalId = newRentalId;
            fetchRentalDetails();
        }
    }

    private void fetchRentalDetails() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            showError("Please sign in to view rental request details.");
            return;
        }

        binding.pbLoading.setVisibility(View.VISIBLE);
        binding.layoutError.setVisibility(View.GONE);
        binding.scrollViewContent.setVisibility(View.GONE);

        apiClient.getRentalDetails(rentalId, token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                binding.pbLoading.setVisibility(View.GONE);
                bindRentalData(response);
            }

            @Override
            public void onError(String errorMessage) {
                binding.pbLoading.setVisibility(View.GONE);
                showError("Failed to load details: " + errorMessage);
            }
        });
    }

    private void bindRentalData(JSONObject obj) {
        try {
            binding.scrollViewContent.setVisibility(View.VISIBLE);

            // 1. Product details
            productName = obj.optString("productName", obj.optString("name", "Product"));
            binding.tvProductName.setText(productName);

            String category = obj.optString("category", "General");
            String condition = obj.optString("condition", "Good");
            binding.tvProductMeta.setText(category + " • " + condition + " condition");

            double rentPerDay = obj.optDouble("rentPerDay", 0.0);
            binding.tvRentPerDay.setText("৳" + (int) Math.round(rentPerDay) + " / day");

            int days = obj.optInt("days", 1);
            binding.tvDuration.setText(days + (days == 1 ? " day" : " days"));

            double totalPrice = obj.optDouble("totalPrice", rentPerDay * days);
            binding.tvTotalPrice.setText("৳" + (int) Math.round(totalPrice));

            String createdAtStr = obj.optString("createdAt", "");
            binding.tvRequestDate.setText(formatDate(createdAtStr));

            // Product image
            String productImage = obj.optString("productImage",
                    obj.optString("imageUrl", obj.optString("image_url", obj.optString("photoUrl", ""))));
            RentalImageUtils.loadProductImage(binding.ivProductPhoto, binding.tvProductEmoji, productImage, "📦");

            // Status
            String status = obj.optString("status", "pending").trim();
            applyStatusBadge(status);

            // Action buttons: only visible while pending
            if ("pending".equalsIgnoreCase(status)) {
                binding.layoutActionButtons.setVisibility(View.VISIBLE);
            } else {
                binding.layoutActionButtons.setVisibility(View.GONE);
            }

            // 2. Requester details
            JSONObject requester = obj.optJSONObject("requester");
            if (requester == null) {
                requester = new JSONObject();
            }

            requesterName = requester.optString("fullName", obj.optString("renterName", "BorrowBox Member"));
            binding.tvRequesterName.setText(requesterName);

            String roll = requester.optString("rollNumber", requester.optString("roll_no", ""));
            binding.tvRequesterRoll.setText(roll.isEmpty() ? "Roll: N/A" : "Roll: " + roll);

            String dept = requester.optString("department", "");
            binding.tvRequesterDept.setText(dept.isEmpty() ? "KUET" : dept);

            String hall = requester.optString("hall", requester.optString("presentAddress", ""));
            String hallRoomNo = requester.optString("hallRoomNo", requester.optString("hall_room_no", ""));
            if (!hallRoomNo.isEmpty()) {
                binding.tvRequesterHall.setText((hall.isEmpty() ? "KUET Campus" : hall) + " • Room " + hallRoomNo);
            } else {
                binding.tvRequesterHall.setText(hall.isEmpty() ? "KUET Campus" : hall);
            }

            requesterPhone = requester.optString("phone", requester.optString("phoneNumber", ""));
            binding.tvRequesterPhone.setText(requesterPhone.isEmpty() ? "Not specified" : requesterPhone);

            String email = requester.optString("email", "");
            binding.tvRequesterEmail.setText(email.isEmpty() ? "Not specified" : email);

            // Requester profile photo
            String profilePhoto = requester.optString("profilePhoto",
                    requester.optString("photoUrl", requester.optString("imageUrl", "")));
            RentalImageUtils.loadCircularImage(binding.ivRequesterAvatar, binding.tvRequesterInitial, profilePhoto, requesterName);

        } catch (Exception e) {
            showError("Error parsing rental details: " + e.getMessage());
        }
    }

    private void applyStatusBadge(String status) {
        if ("accepted".equalsIgnoreCase(status)) {
            binding.tvStatusBadge.setText("Accepted");
            binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_accepted);
        } else if ("rejected".equalsIgnoreCase(status)) {
            binding.tvStatusBadge.setText("Rejected");
            binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_rejected);
        } else if ("cancelled".equalsIgnoreCase(status)) {
            binding.tvStatusBadge.setText("Cancelled");
            binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_cancelled);
        } else {
            binding.tvStatusBadge.setText("Pending");
            binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_pending);
        }
    }

    private void confirmAcceptRequest() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Accept Rental Request")
                .setMessage("Accept rental request for \"" + productName + "\" from " + requesterName + "?")
                .setPositiveButton("Accept", (dialog, which) -> executeAcceptRequest())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeAcceptRequest() {
        String token = sessionManager.getToken();
        binding.pbLoading.setVisibility(View.VISIBLE);
        binding.layoutActionButtons.setEnabled(false);

        apiClient.acceptRental(rentalId, token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                binding.pbLoading.setVisibility(View.GONE);
                Toast.makeText(RentalRequestDetailsActivity.this, "Rental request accepted! ✅", Toast.LENGTH_SHORT).show();
                fetchRentalDetails();
            }

            @Override
            public void onError(String errorMessage) {
                binding.pbLoading.setVisibility(View.GONE);
                binding.layoutActionButtons.setEnabled(true);
                Toast.makeText(RentalRequestDetailsActivity.this, "Failed to accept: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmRejectRequest() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Decline Rental Request")
                .setMessage("Are you sure you want to decline this rental request?")
                .setPositiveButton("Decline", (dialog, which) -> executeRejectRequest())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeRejectRequest() {
        String token = sessionManager.getToken();
        binding.pbLoading.setVisibility(View.VISIBLE);
        binding.layoutActionButtons.setEnabled(false);

        apiClient.rejectRental(rentalId, token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                binding.pbLoading.setVisibility(View.GONE);
                Toast.makeText(RentalRequestDetailsActivity.this, "Rental request declined. ❌", Toast.LENGTH_SHORT).show();
                fetchRentalDetails();
            }

            @Override
            public void onError(String errorMessage) {
                binding.pbLoading.setVisibility(View.GONE);
                binding.layoutActionButtons.setEnabled(true);
                Toast.makeText(RentalRequestDetailsActivity.this, "Failed to decline: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openWhatsApp() {
        if (requesterPhone == null || requesterPhone.trim().isEmpty()) {
            Toast.makeText(this, "Requester phone number is not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        String cleanedPhone = requesterPhone.replaceAll("[^0-9]", "");
        if (cleanedPhone.startsWith("01")) {
            cleanedPhone = "88" + cleanedPhone;
        }

        String prefilled = "Hi " + (requesterName.isEmpty() ? "there" : requesterName)
                + ", regarding your rental request for \"" + (productName.isEmpty() ? "the item" : productName) + "\" on BorrowBox:";
        String url = "https://wa.me/" + cleanedPhone + "?text=" + Uri.encode(prefilled);

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            // Fall back to opening in browser
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browserIntent.addCategory(Intent.CATEGORY_BROWSABLE);
                startActivity(browserIntent);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not open WhatsApp: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showError(String message) {
        binding.layoutError.setVisibility(View.VISIBLE);
        binding.tvErrorMessage.setText(message);
        binding.scrollViewContent.setVisibility(View.GONE);
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) return "Recently";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            String clean = isoDate.length() >= 19 ? isoDate.substring(0, 19) : isoDate;
            Date date = inputFormat.parse(clean);
            if (date != null) {
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM d, yyyy", Locale.US);
                return outputFormat.format(date);
            }
        } catch (Exception ignored) {}
        return isoDate.length() >= 10 ? isoDate.substring(0, 10) : isoDate;
    }
}
