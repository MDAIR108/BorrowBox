package com.borrowbox.kuet.bookings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ActivityRentalRequestReviewBinding;
import com.borrowbox.kuet.models.Rental;
import com.borrowbox.kuet.models.RentalRequest;
import com.borrowbox.kuet.repositories.RentalRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class RentalRequestReviewActivity extends AppCompatActivity {

    public static final String EXTRA_REQUEST = "extra_rental_request";

    public static class Builder {
        private final Context context;
        private RentalRequest request;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setRentalRequest(RentalRequest request) {
            this.request = request;
            return this;
        }

        public Intent build() {
            Intent intent = new Intent(context, RentalRequestReviewActivity.class);
            intent.putExtra(EXTRA_REQUEST, request);
            return intent;
        }
    }

    private ActivityRentalRequestReviewBinding binding;
    private RentalRequest rentalRequest;
    private RentalRepository rentalRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRentalRequestReviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        rentalRequest = (RentalRequest) getIntent().getSerializableExtra(EXTRA_REQUEST);
        if (rentalRequest == null) {
            Toast.makeText(this, "Rental request data missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        rentalRepository = new RentalRepository(this);

        setupToolbar();
        populateRequestData();
        setupActions();
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void populateRequestData() {
        // Borrower info
        binding.tvBorrowerName.setText(rentalRequest.getRenterName());
        binding.tvBorrowerRollDept.setText("Roll: " + (rentalRequest.getRenterRollNumber().isEmpty() ? "N/A" : rentalRequest.getRenterRollNumber())
                + " • " + (rentalRequest.getRenterDepartment().isEmpty() ? "KUET" : rentalRequest.getRenterDepartment()));
        binding.tvBorrowerHall.setText(rentalRequest.getRenterAddress().isEmpty() ? "KUET Campus" : rentalRequest.getRenterAddress());
        binding.tvBorrowerTrustScore.setText(String.format(Locale.US, "★ %.1f Trust", rentalRequest.getRenterTrustScore()));

        if (rentalRequest.isRenterVerified()) {
            binding.tvBorrowerBadge.setText("KUET Verified");
            binding.tvBorrowerBadge.setBackgroundResource(R.drawable.bg_status_available);
        } else {
            binding.tvBorrowerBadge.setText("Pending");
            binding.tvBorrowerBadge.setBackgroundResource(R.drawable.bg_status_rented);
        }

        // Product info
        binding.tvProductEmoji.setText(rentalRequest.getProductEmoji());
        binding.tvProductName.setText(rentalRequest.getProductName());
        binding.tvProductRate.setText("Rate: ৳" + (int)rentalRequest.getProductPricePerDay() + " / day");

        // Dates & total
        binding.tvStartDate.setText(rentalRequest.getFormattedStartDate());
        binding.tvReturnDate.setText(rentalRequest.getFormattedReturnDate());
        binding.tvDuration.setText("Duration: " + rentalRequest.getDurationDays() + (rentalRequest.getDurationDays() == 1 ? " day" : " days"));
        binding.tvTotalPrice.setText("Total: ৳" + (int)rentalRequest.getTotalCost());
        binding.tvCountdownText.setText("Status: " + rentalRequest.getStatus() + " • " + rentalRequest.getCountdownText());

        updateActionButtonsVisibility();
    }

    private void updateActionButtonsVisibility() {
        String status = rentalRequest.getStatus();
        if ("Accepted".equalsIgnoreCase(status) || "Approved".equalsIgnoreCase(status) || "Active".equalsIgnoreCase(status)) {
            binding.layoutPendingActions.setVisibility(View.GONE);
            binding.layoutApprovedActions.setVisibility(View.VISIBLE);
        } else if ("Rejected".equalsIgnoreCase(status)) {
            binding.layoutPendingActions.setVisibility(View.GONE);
            binding.layoutApprovedActions.setVisibility(View.GONE);
            binding.tvCountdownText.setText("This request was declined.");
        } else {
            binding.layoutPendingActions.setVisibility(View.VISIBLE);
            binding.layoutApprovedActions.setVisibility(View.GONE);
        }
    }

    private void setupActions() {
        binding.btnAcceptRequest.setOnClickListener(v -> acceptRequest());

        binding.btnRejectRequest.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Decline Request")
                    .setMessage("Are you sure you want to decline this rental request?")
                    .setPositiveButton("Decline", (dialog, which) -> rejectRequest())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        binding.btnWhatsAppChat.setOnClickListener(v -> openWhatsApp(rentalRequest.getRenterWhatsApp()));

        binding.btnViewBorrowerProfile.setOnClickListener(v -> {
            Intent intent = new RenterProfileDetailsActivity.Builder(this)
                    .setRentalRequest(rentalRequest)
                    .build();
            startActivity(intent);
        });
    }

    private void acceptRequest() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnAcceptRequest.setEnabled(false);
        binding.btnRejectRequest.setEnabled(false);

        rentalRepository.acceptRentalRequest(rentalRequest, new RentalRepository.DataCallback<Rental>() {
            @Override
            public void onSuccess(Rental rental) {
                binding.progressBar.setVisibility(View.GONE);
                rentalRequest.setStatus("Accepted");
                updateActionButtonsVisibility();

                new MaterialAlertDialogBuilder(RentalRequestReviewActivity.this)
                        .setTitle("Request Accepted!")
                        .setMessage("You have accepted the rental request for \"" + rentalRequest.getProductName() + "\".\n\nBorrower WhatsApp contact has been unlocked. Reach out to coordinate pickup.")
                        .setPositiveButton("Open WhatsApp", (dialog, which) -> openWhatsApp(rentalRequest.getRenterWhatsApp()))
                        .setNegativeButton("Done", null)
                        .show();
            }

            @Override
            public void onError(String errorMessage) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnAcceptRequest.setEnabled(true);
                binding.btnRejectRequest.setEnabled(true);
                Toast.makeText(RentalRequestReviewActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void rejectRequest() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnAcceptRequest.setEnabled(false);
        binding.btnRejectRequest.setEnabled(false);

        rentalRepository.rejectRentalRequest(rentalRequest, new RentalRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                binding.progressBar.setVisibility(View.GONE);
                rentalRequest.setStatus("Rejected");
                updateActionButtonsVisibility();
                Toast.makeText(RentalRequestReviewActivity.this, "Rental request declined.", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnAcceptRequest.setEnabled(true);
                binding.btnRejectRequest.setEnabled(true);
                Toast.makeText(RentalRequestReviewActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openWhatsApp(String rawPhone) {
        if (rawPhone == null || rawPhone.trim().isEmpty()) {
            Toast.makeText(this, "Borrower WhatsApp number not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        String cleanedPhone = rawPhone.replaceAll("[^0-9]", "");
        if (cleanedPhone.startsWith("01")) {
            cleanedPhone = "88" + cleanedPhone;
        }

        String url = "https://wa.me/" + cleanedPhone;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp could not be opened: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
