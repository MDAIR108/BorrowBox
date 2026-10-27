package com.borrowbox.kuet.bookings;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.auth.AuthActivity;
import com.borrowbox.kuet.databinding.ActivityRentBinding;
import com.borrowbox.kuet.models.Product;
import com.borrowbox.kuet.models.UserProfile;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.borrowbox.kuet.utils.AppConfig;
import com.borrowbox.kuet.verification.VerificationActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

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
    private SessionManager sessionManager;
    private ApiClient apiClient;

    private final Calendar startCalendar = Calendar.getInstance();
    private final Calendar returnCalendar = Calendar.getInstance();
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        apiClient = ApiClient.getInstance(this);

        product = (Product) getIntent().getSerializableExtra(EXTRA_PRODUCT);
        if (product == null) {
            Toast.makeText(this, "Product information missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Default: Start today, return tomorrow
        returnCalendar.add(Calendar.DAY_OF_MONTH, 1);

        setupWindowInsets();
        setupToolbar();
        populateProductDetails();
        loadRenterProfile();
        setupDatePickers();
        updateCalculation();

        binding.btnSubmitRequest.setOnClickListener(v -> submitRentalRequest());
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets statusBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());

            binding.getRoot().findViewById(R.id.btnBack).getRootView().setPadding(
                    0,
                    statusBarInsets.top,
                    0,
                    navBarInsets.bottom
            );
            return windowInsets;
        });
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
        boolean isGuest = sessionManager.isGuest();
        boolean isVerified = sessionManager.isEmailVerified();

        if (isGuest) {
            binding.tvRenterName.setText("Guest Mode");
            binding.tvRenterRollDept.setText("Login required to rent");
            binding.tvRenterWhatsApp.setText("Not available");
            binding.tvRenterAddress.setText("Campus");
            binding.tvRenterBadge.setText("Guest");
            binding.tvRenterBadge.setBackgroundResource(R.drawable.bg_status_rented);
            binding.btnSubmitRequest.setEnabled(false);
            showGuestWarning();
            return;
        }

        binding.tvRenterName.setText(sessionManager.getUserName());
        binding.tvRenterRollDept.setText("Roll: " + sessionManager.getUserRoll() + " • " + sessionManager.getUserDept());
        binding.tvRenterWhatsApp.setText("Phone: " + sessionManager.getUserPhone());
        binding.tvRenterAddress.setText("Hall: " + sessionManager.getUserHall());

        if (isVerified) {
            binding.tvRenterBadge.setText("KUET Verified");
            binding.tvRenterBadge.setBackgroundResource(R.drawable.bg_status_available);
            binding.btnSubmitRequest.setEnabled(true);
        } else {
            binding.tvRenterBadge.setText("Unverified");
            binding.tvRenterBadge.setBackgroundResource(R.drawable.bg_status_rented);
            binding.btnSubmitRequest.setEnabled(false);
            showUnverifiedWarning();
        }
    }

    private void showGuestWarning() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Account Required")
                .setMessage("Guests cannot submit rental requests. Please log in or register with your KUET student email.")
                .setPositiveButton("Log In / Register", (d, w) -> {
                    Intent intent = new Intent(this, AuthActivity.class);
                    intent.putExtra(AppConfig.EXTRA_AUTH_TAB, "login");
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", (d, w) -> finish())
                .show();
    }

    private void showUnverifiedWarning() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Verification Required")
                .setMessage("You must verify your KUET student email before renting items.")
                .setPositiveButton("Verify Now", (d, w) -> {
                    Intent intent = new Intent(this, VerificationActivity.class);
                    intent.putExtra("email", sessionManager.getUserEmail());
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", (d, w) -> finish())
                .show();
    }

    private void setupDatePickers() {
        binding.tvStartDate.setText(displayDateFormat.format(startCalendar.getTime()));
        binding.tvReturnDate.setText(displayDateFormat.format(returnCalendar.getTime()));

        binding.btnSelectStartDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        startCalendar.set(year, month, dayOfMonth, 0, 0, 0);
                        binding.tvStartDate.setText(displayDateFormat.format(startCalendar.getTime()));

                        if (returnCalendar.getTimeInMillis() <= startCalendar.getTimeInMillis()) {
                            returnCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
                            returnCalendar.add(Calendar.DAY_OF_MONTH, 1);
                            binding.tvReturnDate.setText(displayDateFormat.format(returnCalendar.getTime()));
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
                        binding.tvReturnDate.setText(displayDateFormat.format(returnCalendar.getTime()));
                        updateCalculation();
                    },
                    returnCalendar.get(Calendar.YEAR),
                    returnCalendar.get(Calendar.MONTH),
                    returnCalendar.get(Calendar.DAY_OF_MONTH)
            );
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
        if (sessionManager.isGuest()) {
            showGuestWarning();
            return;
        }

        if (!sessionManager.isEmailVerified()) {
            showUnverifiedWarning();
            return;
        }

        String startDate = apiDateFormat.format(startCalendar.getTime());
        String endDate = apiDateFormat.format(returnCalendar.getTime());
        String token = sessionManager.getToken();

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSubmitRequest.setEnabled(false);

        apiClient.createRental(product.getId(), startDate, endDate, token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                binding.progressBar.setVisibility(View.GONE);
                showSuccessDialog(product.getOwnerName());
            }

            @Override
            public void onError(String errorMessage) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSubmitRequest.setEnabled(true);
                Toast.makeText(RentActivity.this, "Request failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showSuccessDialog(String ownerName) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Rental Request Sent!")
                .setMessage("Your request to rent \"" + product.getName() + "\" has been sent to " + ownerName + ".\n\nYou will receive updates in 'My Rentals'.")
                .setPositiveButton("OK", (dialog, which) -> {
                    setResult(RESULT_OK);
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}
