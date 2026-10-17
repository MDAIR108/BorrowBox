package com.borrowbox.kuet.bookings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ActivityRenterProfileDetailsBinding;
import com.borrowbox.kuet.models.RentalRequest;

import java.util.Locale;

public class RenterProfileDetailsActivity extends AppCompatActivity {

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
            Intent intent = new Intent(context, RenterProfileDetailsActivity.class);
            intent.putExtra(EXTRA_REQUEST, request);
            return intent;
        }
    }

    private ActivityRenterProfileDetailsBinding binding;
    private RentalRequest rentalRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRenterProfileDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        rentalRequest = (RentalRequest) getIntent().getSerializableExtra(EXTRA_REQUEST);
        if (rentalRequest == null) {
            Toast.makeText(this, "Borrower details unavailable.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        populateData();
        setupListeners();
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void populateData() {
        binding.tvBorrowerName.setText(rentalRequest.getRenterName());
        binding.tvBorrowerDept.setText(rentalRequest.getRenterDepartment().isEmpty() ? "KUET Student" : rentalRequest.getRenterDepartment());
        binding.tvRollNumber.setText(rentalRequest.getRenterRollNumber().isEmpty() ? "N/A" : rentalRequest.getRenterRollNumber());
        binding.tvCampusAddress.setText(rentalRequest.getRenterAddress().isEmpty() ? "Campus Resident" : rentalRequest.getRenterAddress());
        binding.tvWhatsAppNumber.setText(rentalRequest.getRenterWhatsApp().isEmpty() ? "Not specified" : rentalRequest.getRenterWhatsApp());
        binding.tvPhoneNumber.setText(rentalRequest.getRenterPhone().isEmpty() ? "Not specified" : rentalRequest.getRenterPhone());
        binding.tvTrustScore.setText(String.format(Locale.US, "★ %.1f Trust Score", rentalRequest.getRenterTrustScore()));

        if (rentalRequest.isRenterVerified()) {
            binding.tvVerificationBadge.setText("KUET Verified Student");
            binding.tvVerificationBadge.setBackgroundResource(R.drawable.bg_status_available);
        } else {
            binding.tvVerificationBadge.setText("Verification Pending");
            binding.tvVerificationBadge.setBackgroundResource(R.drawable.bg_status_rented);
        }

        // Profile photo (NEVER a student ID image)
        if (rentalRequest.getRenterPhotoUrl() != null && !rentalRequest.getRenterPhotoUrl().isEmpty()) {
            try {
                binding.ivBorrowerPhoto.setImageURI(Uri.parse(rentalRequest.getRenterPhotoUrl()));
                binding.ivBorrowerPhoto.setImageTintList(null);
            } catch (Exception ignored) {}
        }
    }

    private void setupListeners() {
        binding.btnOpenWhatsApp.setOnClickListener(v -> openWhatsApp(rentalRequest.getRenterWhatsApp()));
    }

    private void openWhatsApp(String rawPhone) {
        if (rawPhone == null || rawPhone.trim().isEmpty()) {
            Toast.makeText(this, "WhatsApp number is not provided.", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Unable to launch WhatsApp: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
