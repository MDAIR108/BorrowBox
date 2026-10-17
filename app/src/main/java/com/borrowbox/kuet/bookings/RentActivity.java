package com.borrowbox.kuet.bookings;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ActivityRentBinding;
import com.borrowbox.kuet.models.Product;
import com.borrowbox.kuet.models.RentalRequest;
import com.borrowbox.kuet.models.UserProfile;
import com.borrowbox.kuet.repositories.RentalRepository;
import com.borrowbox.kuet.repositories.UserRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class RentActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT = "extra_product";

    public static class Builder {
        private final Context context;
        private Product product;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setProduct(Product product) {
            this.product = product;
            return this;
        }

        public Intent build() {
            Intent intent = new Intent(context, RentActivity.class);
            intent.putExtra(EXTRA_PRODUCT, product);
            return intent;
        }
    }

    private ActivityRentBinding binding;
    private Product product;
    private UserProfile renterProfile;
    private UserRepository userRepository;
    private RentalRepository rentalRepository;

    private final Calendar startCalendar = Calendar.getInstance();
    private final Calendar returnCalendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        product = (Product) getIntent().getSerializableExtra(EXTRA_PRODUCT);
        if (product == null) {
            Toast.makeText(this, "Product information missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userRepository = new UserRepository(this);
        rentalRepository = new RentalRepository(this);

        // Default: Start today, return tomorrow
        returnCalendar.add(Calendar.DAY_OF_MONTH, 1);

        setupToolbar();
        populateProductDetails();
        loadRenterProfile();
        setupDatePickers();
        updateCalculation();

        binding.btnSubmitRequest.setOnClickListener(v -> submitRentalRequest());
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void populateProductDetails() {
        binding.tvProductEmoji.setText(product.getEmoji() != null ? product.getEmoji() : "📦");
        binding.tvProductName.setText(product.getName());
        binding.tvProductPrice.setText(product.getFormattedPrice());
        binding.tvProductOwner.setText("Owner: " + product.getOwnerName() + " (" + product.getDepartment() + ")");
        binding.tvProductLocation.setText("Pickup Location: " + product.getHallLocation());
    }

    private void loadRenterProfile() {
        String currentUid = userRepository.getCurrentUserId();

        // Check self-rent prevention
        if (product.getOwnerId() != null && product.getOwnerId().equals(currentUid)) {
            showSelfRentDialog();
            binding.btnSubmitRequest.setEnabled(false);
        }

        userRepository.getUserProfile(currentUid, new UserRepository.DataCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                renterProfile = profile;
                binding.tvRenterName.setText(profile.getFullName());
                binding.tvRenterRollDept.setText("Roll: " + (profile.getRollNumber().isEmpty() ? "N/A" : profile.getRollNumber())
                        + " • " + (profile.getDepartment().isEmpty() ? "KUET" : profile.getDepartment()));
                binding.tvRenterWhatsApp.setText("WhatsApp: " + (profile.getWhatsappNumber().isEmpty() ? "Not set" : profile.getWhatsappNumber()));
                binding.tvRenterAddress.setText("Campus Address: " + (profile.getPresentAddress().isEmpty() ? "Campus" : profile.getPresentAddress()));

                if (profile.isVerified()) {
                    binding.tvRenterBadge.setText("KUET Verified");
                    binding.tvRenterBadge.setBackgroundResource(R.drawable.bg_status_available);
                } else {
                    binding.tvRenterBadge.setText("Pending");
                    binding.tvRenterBadge.setBackgroundResource(R.drawable.bg_status_rented);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(RentActivity.this, "Could not load profile: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSelfRentDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cannot Rent Own Item")
                .setMessage("You are the registered owner of \"" + product.getName() + "\". You cannot submit a rental request for your own listing.")
                .setPositiveButton("Go Back", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void setupDatePickers() {
        binding.tvStartDate.setText(dateFormat.format(startCalendar.getTime()));
        binding.tvReturnDate.setText(dateFormat.format(returnCalendar.getTime()));

        binding.btnSelectStartDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        startCalendar.set(year, month, dayOfMonth, 0, 0, 0);
                        binding.tvStartDate.setText(dateFormat.format(startCalendar.getTime()));

                        // If return date is now <= start date, push return date forward by 1 day
                        if (returnCalendar.getTimeInMillis() <= startCalendar.getTimeInMillis()) {
                            returnCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
                            returnCalendar.add(Calendar.DAY_OF_MONTH, 1);
                            binding.tvReturnDate.setText(dateFormat.format(returnCalendar.getTime()));
                        }
                        updateCalculation();
                    },
                    startCalendar.get(Calendar.YEAR),
                    startCalendar.get(Calendar.MONTH),
                    startCalendar.get(Calendar.DAY_OF_MONTH)
            );
            dpd.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dpd.show();
        });

        binding.btnSelectReturnDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        returnCalendar.set(year, month, dayOfMonth, 23, 59, 59);
                        binding.tvReturnDate.setText(dateFormat.format(returnCalendar.getTime()));
                        updateCalculation();
                    },
                    returnCalendar.get(Calendar.YEAR),
                    returnCalendar.get(Calendar.MONTH),
                    returnCalendar.get(Calendar.DAY_OF_MONTH)
            );
            // Min return date must be at least tomorrow or after start calendar
            dpd.getDatePicker().setMinDate(startCalendar.getTimeInMillis() + (1000 * 60 * 60 * 24));
            dpd.show();
        });
    }

    private void updateCalculation() {
        long startMillis = startCalendar.getTimeInMillis();
        long returnMillis = returnCalendar.getTimeInMillis();

        long diff = returnMillis - startMillis;
        int days = (int) Math.ceil((double) diff / (1000 * 60 * 60 * 24));
        if (days <= 0) days = 1;

        binding.tvDuration.setText("Duration: " + days + (days == 1 ? " day" : " days"));

        if (product.isFree() || product.getPricePerDay() == 0) {
            binding.tvCostBreakdown.setText("FREE Item");
            binding.tvTotalCost.setText("FREE");
        } else {
            double total = product.getPricePerDay() * days;
            binding.tvCostBreakdown.setText("৳" + (int)product.getPricePerDay() + " × " + days + (days == 1 ? " day" : " days"));
            binding.tvTotalCost.setText("৳" + (int)total);
        }
    }

    private void submitRentalRequest() {
        if (renterProfile == null) {
            Toast.makeText(this, "Profile information loading, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate self-rent
        if (product.getOwnerId() != null && product.getOwnerId().equals(renterProfile.getUid())) {
            showSelfRentDialog();
            return;
        }

        long startMillis = startCalendar.getTimeInMillis();
        long returnMillis = returnCalendar.getTimeInMillis();

        // Validate dates
        Calendar todayMidnight = Calendar.getInstance();
        todayMidnight.set(Calendar.HOUR_OF_DAY, 0);
        todayMidnight.set(Calendar.MINUTE, 0);
        todayMidnight.set(Calendar.SECOND, 0);

        if (startMillis < todayMidnight.getTimeInMillis() - (1000 * 60 * 60 * 12)) {
            Toast.makeText(this, "Rental start date cannot be in the past.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (returnMillis <= startMillis) {
            Toast.makeText(this, "Return date must be after the start date.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Recompute duration
        long diff = returnMillis - startMillis;
        int days = (int) Math.ceil((double) diff / (1000 * 60 * 60 * 24));
        if (days <= 0) days = 1;

        double totalCost = product.isFree() ? 0.0 : product.getPricePerDay() * days;

        // Build RentalRequest object
        RentalRequest request = new RentalRequest();
        request.setProductId(product.getId());
        request.setProductName(product.getName());
        request.setProductPricePerDay(product.getPricePerDay());
        request.setProductEmoji(product.getEmoji());
        request.setPickupLocation(product.getHallLocation());

        request.setOwnerId(product.getOwnerId());
        request.setOwnerName(product.getOwnerName());

        request.setRenterId(renterProfile.getUid());
        request.setRenterName(renterProfile.getFullName());
        request.setRenterDepartment(renterProfile.getDepartment());
        request.setRenterRollNumber(renterProfile.getRollNumber());
        request.setRenterAddress(renterProfile.getPresentAddress());
        request.setRenterPhone(renterProfile.getPhoneNumber());
        request.setRenterWhatsApp(renterProfile.getWhatsappNumber());
        request.setRenterPhotoUrl(renterProfile.getProfilePictureUrl());
        request.setRenterTrustScore(renterProfile.getTrustScore());
        request.setRenterVerificationStatus(renterProfile.getVerificationStatus());

        request.setStartDateMillis(startMillis);
        request.setReturnDateMillis(returnMillis);
        request.setDurationDays(days);
        request.setTotalCost(totalCost);
        request.setStatus("Pending");

        // Submit to repository
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSubmitRequest.setEnabled(false);

        rentalRepository.submitRentalRequest(request, new RentalRepository.DataCallback<String>() {
            @Override
            public void onSuccess(String requestId) {
                binding.progressBar.setVisibility(View.GONE);
                showSuccessDialog(product.getOwnerName());
            }

            @Override
            public void onError(String errorMessage) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSubmitRequest.setEnabled(true);
                Toast.makeText(RentActivity.this, "Failed to submit request: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showSuccessDialog(String ownerName) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Rental Request Sent!")
                .setMessage("Your request to rent \"" + product.getName() + "\" has been sent to " + ownerName + ".\n\nYou will receive an in-app notification once the owner reviews your request.")
                .setPositiveButton("View My Requests", (dialog, which) -> {
                    setResult(RESULT_OK);
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}
