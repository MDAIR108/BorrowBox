package com.borrowbox.kuet.items;

import android.app.DatePickerDialog;
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

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.R;
import com.borrowbox.kuet.auth.AuthActivity;
import com.borrowbox.kuet.auth.WelcomeActivity;
import com.borrowbox.kuet.databinding.ActivityProductDetailsBinding;
import com.borrowbox.kuet.models.Product;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.borrowbox.kuet.utils.AppConfig;
import com.borrowbox.kuet.verification.VerificationActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ProductDetailsActivity extends AppCompatActivity {

    private ActivityProductDetailsBinding binding;
    private SessionManager sessionManager;
    private ApiClient apiClient;
    private Product product;

    private final Calendar startCalendar = Calendar.getInstance();
    private final Calendar returnCalendar = Calendar.getInstance();
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        binding = ActivityProductDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        apiClient = ApiClient.getInstance(this);

        product = (Product) getIntent().getSerializableExtra(AppConfig.EXTRA_PRODUCT);
        if (product == null) {
            Toast.makeText(this, "Product details missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Return date defaults to tomorrow
        returnCalendar.add(Calendar.DAY_OF_MONTH, 1);

        setupWindowInsets();
        populateProductDetails();
        setupOwnerSection();
        setupDatePickers();
        updateCalculation();
        checkRentPermissions();
        fetchFreshBackendDetails();

        binding.btnDetailsBack.setOnClickListener(v -> finish());
        binding.btnSubmitRent.setOnClickListener(v -> submitRentalRequest());
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailsRoot, (v, windowInsets) -> {
            Insets statusBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());

            binding.detailsContentContainer.setPadding(
                    binding.detailsContentContainer.getPaddingLeft(),
                    statusBarInsets.top + (int) (14 * getResources().getDisplayMetrics().density),
                    binding.detailsContentContainer.getPaddingRight(),
                    navBarInsets.bottom + (int) (24 * getResources().getDisplayMetrics().density)
            );
            return windowInsets;
        });
    }

    private void populateProductDetails() {
        binding.tvLargeProductEmoji.setText(product.getEmoji() != null ? product.getEmoji() : "📦");
        binding.tvProductTitle.setText(product.getName());
        binding.tvProductPriceBadge.setText(product.getFormattedPrice());
        binding.tvProductStatusBadge.setText(product.getAvailabilityText());
        binding.tvProductCategoryBadge.setText(product.getCategory() != null ? product.getCategory() : "Item");
        binding.tvProductDescription.setText(product.getDescription());
        binding.tvLocationDetails.setText(product.getDepartment() + " • " + product.getHallLocation());

        if (product.isAvailable()) {
            binding.tvProductStatusBadge.setBackgroundResource(R.drawable.bg_status_available);
        } else {
            binding.tvProductStatusBadge.setBackgroundResource(R.drawable.bg_status_rented);
        }
    }

    private void setupOwnerSection() {
        boolean isGuest = sessionManager.isGuest();

        binding.tvOwnerName.setText(product.getOwnerName() != null ? product.getOwnerName() : "KUET Student");
        binding.tvOwnerRollDept.setText("Dept: " + product.getDepartment());
        binding.tvOwnerHall.setText("Pickup: " + product.getHallLocation());

        if (isGuest) {
            // Mask owner info for guests (TASK 4 & TASK 5)
            binding.ownerContactContainer.setVisibility(View.GONE);
            binding.guestMaskPrompt.setVisibility(View.VISIBLE);
            binding.btnGuestLoginPrompt.setOnClickListener(v -> {
                Intent intent = new Intent(this, AuthActivity.class);
                intent.putExtra(AppConfig.EXTRA_AUTH_TAB, "login");
                startActivity(intent);
            });
        } else {
            binding.ownerContactContainer.setVisibility(View.VISIBLE);
            binding.guestMaskPrompt.setVisibility(View.GONE);

            String phone = product.getWhatsappNumber() != null ? product.getWhatsappNumber() : "+8801700000001";
            binding.tvOwnerPhone.setText("📞 Phone: " + phone);
            binding.tvOwnerEmail.setText("✉️ KUET Email: Verified student");

            binding.btnCallOwner.setOnClickListener(v -> {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                dialIntent.setData(Uri.parse("tel:" + phone));
                try {
                    startActivity(dialIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "Cannot open dialer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

            binding.btnMessageOwner.setOnClickListener(v -> {
                Intent smsIntent = new Intent(Intent.ACTION_VIEW);
                smsIntent.setData(Uri.parse("sms:" + phone));
                smsIntent.putExtra("sms_body", "Hi " + product.getOwnerName() + ", I saw your listing for " + product.getName() + " on BorrowBox.");
                try {
                    startActivity(smsIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "Cannot open messaging app: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setupDatePickers() {
        binding.tvDetailStartDate.setText(displayDateFormat.format(startCalendar.getTime()));
        binding.tvDetailReturnDate.setText(displayDateFormat.format(returnCalendar.getTime()));

        binding.btnPickStartDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        startCalendar.set(year, month, dayOfMonth, 0, 0, 0);
                        binding.tvDetailStartDate.setText(displayDateFormat.format(startCalendar.getTime()));

                        if (returnCalendar.getTimeInMillis() <= startCalendar.getTimeInMillis()) {
                            returnCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
                            returnCalendar.add(Calendar.DAY_OF_MONTH, 1);
                            binding.tvDetailReturnDate.setText(displayDateFormat.format(returnCalendar.getTime()));
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

        binding.btnPickReturnDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        returnCalendar.set(year, month, dayOfMonth, 23, 59, 59);
                        binding.tvDetailReturnDate.setText(displayDateFormat.format(returnCalendar.getTime()));
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

        binding.tvDetailDuration.setText("Duration: " + days + (days == 1 ? " day" : " days"));

        if (product.isFree() || product.getPricePerDay() == 0) {
            binding.tvDetailTotalCost.setText("Total: FREE");
        } else {
            double total = product.getPricePerDay() * days;
            binding.tvDetailTotalCost.setText("Total: ৳" + (int) total);
        }
    }

    /**
     * Checks user verification state and blocks/enables renting with clear messages (TASK 5 & TASK 6).
     */
    private void checkRentPermissions() {
        boolean isGuest = sessionManager.isGuest();
        boolean isVerified = sessionManager.isEmailVerified();

        if (isGuest) {
            // Blocked for guests
            binding.cardRentBlockedNotice.setVisibility(View.VISIBLE);
            binding.tvRentBlockedReason.setText("🔒 Guest Mode: You must log in or register with your KUET student email to rent items.");
            binding.btnGoToVerificationFromDetails.setText("Log In / Register →");
            binding.btnGoToVerificationFromDetails.setOnClickListener(v -> {
                Intent intent = new Intent(this, AuthActivity.class);
                intent.putExtra(AppConfig.EXTRA_AUTH_TAB, "login");
                startActivity(intent);
            });
            binding.btnSubmitRent.setEnabled(false);
            binding.btnSubmitRent.setAlpha(0.5f);
        } else if (!isVerified) {
            // Blocked for unverified users
            binding.cardRentBlockedNotice.setVisibility(View.VISIBLE);
            binding.tvRentBlockedReason.setText("⚠️ Student verification required: Complete student email verification to unlock renting.");
            binding.btnGoToVerificationFromDetails.setText("Verify KUET Student Email →");
            binding.btnGoToVerificationFromDetails.setOnClickListener(v -> {
                Intent intent = new Intent(this, VerificationActivity.class);
                intent.putExtra("email", sessionManager.getUserEmail());
                startActivity(intent);
            });
            binding.btnSubmitRent.setEnabled(false);
            binding.btnSubmitRent.setAlpha(0.5f);
        } else {
            // Verified student
            binding.cardRentBlockedNotice.setVisibility(View.GONE);
            binding.btnSubmitRent.setEnabled(true);
            binding.btnSubmitRent.setAlpha(1.0f);
        }
    }

    private void fetchFreshBackendDetails() {
        String token = sessionManager.getToken();
        apiClient.getProductDetails(product.getId(), token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONObject pObj = response.optJSONObject("product");
                if (pObj != null) {
                    JSONObject ownerObj = pObj.optJSONObject("owner");
                    if (ownerObj != null) {
                        String name = ownerObj.optString("name", product.getOwnerName());
                        String roll = ownerObj.optString("roll", "");
                        String dept = ownerObj.optString("department", product.getDepartment());
                        String hall = ownerObj.optString("hall", product.getHallLocation());
                        String phone = ownerObj.optString("phone", "");
                        String email = ownerObj.optString("email", "");

                        binding.tvOwnerName.setText(name);
                        binding.tvOwnerRollDept.setText("Roll: " + (roll.isEmpty() ? "KUET" : roll) + " • " + dept);
                        binding.tvOwnerHall.setText("Hall: " + hall);

                        if (!sessionManager.isGuest() && !phone.isEmpty()) {
                            binding.tvOwnerPhone.setText("📞 Phone: " + phone);
                            binding.tvOwnerEmail.setText("✉️ Email: " + email);
                        }
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Keep local sample data if backend is offline
            }
        });
    }

    private void submitRentalRequest() {
        if (!sessionManager.isLoggedIn() || !sessionManager.isEmailVerified()) {
            checkRentPermissions();
            return;
        }

        String startDate = apiDateFormat.format(startCalendar.getTime());
        String endDate = apiDateFormat.format(returnCalendar.getTime());
        String token = sessionManager.getToken();

        binding.detailsProgressBar.setVisibility(View.VISIBLE);
        binding.btnSubmitRent.setEnabled(false);

        apiClient.createRental(product.getId(), startDate, endDate, token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                binding.detailsProgressBar.setVisibility(View.GONE);
                binding.btnSubmitRent.setEnabled(true);

                new MaterialAlertDialogBuilder(ProductDetailsActivity.this)
                        .setTitle("Rental Request Submitted! 📦")
                        .setMessage("Your request to rent \"" + product.getName() + "\" from "
                                + displayDateFormat.format(startCalendar.getTime()) + " to "
                                + displayDateFormat.format(returnCalendar.getTime()) + " has been sent to "
                                + product.getOwnerName() + ".\n\nYou can track this in 'My Rentals'.")
                        .setPositiveButton("Done", (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
            }

            @Override
            public void onError(String errorMessage) {
                binding.detailsProgressBar.setVisibility(View.GONE);
                binding.btnSubmitRent.setEnabled(true);

                // If backend rejected with 403 unverified
                if (errorMessage.toLowerCase().contains("verification required") || errorMessage.toLowerCase().contains("verify")) {
                    sessionManager.setEmailVerified(false);
                    checkRentPermissions();
                }

                Toast.makeText(ProductDetailsActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkRentPermissions();
    }
}
