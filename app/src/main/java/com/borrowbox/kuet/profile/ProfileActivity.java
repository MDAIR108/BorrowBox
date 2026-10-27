package com.borrowbox.kuet.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.auth.AuthActivity;
import com.borrowbox.kuet.auth.WelcomeActivity;
import com.borrowbox.kuet.databinding.ActivityProfileBinding;
import com.borrowbox.kuet.models.UserProfile;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.borrowbox.kuet.utils.AppConfig;
import com.borrowbox.kuet.verification.VerificationActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private SessionManager sessionManager;
    private ApiClient apiClient;
    private UserProfile currentProfile;
    private boolean isGuest = false;

    private final ActivityResultLauncher<Intent> editProfileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadProfileData();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        apiClient = ApiClient.getInstance(this);

        isGuest = getIntent().getBooleanExtra(WelcomeActivity.EXTRA_IS_GUEST, false) || sessionManager.isGuest();

        setupSafeAreas();
        setupListeners();
        loadProfileData();
    }

    private void setupSafeAreas() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets statusBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());
            // Pad the scrollable content so top is below status bar and bottom is above nav bar
            binding.getRoot().setPadding(
                    binding.getRoot().getPaddingLeft(),
                    statusBarInsets.top,
                    binding.getRoot().getPaddingRight(),
                    navBarInsets.bottom
            );
            return windowInsets;
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        View.OnClickListener editClickListener = v -> {
            if (isGuest) {
                showGuestPrompt();
            } else {
                Intent intent = new EditProfileActivity.Builder(this)
                        .setUserProfile(currentProfile != null ? currentProfile : sessionManager.getUserProfile())
                        .build();
                editProfileLauncher.launch(intent);
            }
        };

        binding.btnEditToolbar.setOnClickListener(editClickListener);
        binding.btnEditProfileHeader.setOnClickListener(editClickListener);

        View.OnClickListener verifClickListener = v -> {
            if (isGuest) {
                showGuestPrompt();
            } else {
                Intent intent = new Intent(ProfileActivity.this, VerificationActivity.class);
                intent.putExtra("email", sessionManager.getUserEmail());
                startActivity(intent);
            }
        };

        binding.btnStudentVerification.setOnClickListener(verifClickListener);
        binding.tvVerificationBadge.setOnClickListener(verifClickListener);

        binding.btnMyRentals.setOnClickListener(v -> {
            Toast.makeText(this, "Active and past rentals are listed in your Rentals section.", Toast.LENGTH_SHORT).show();
        });

        // TASK 4: Add logout in Profile
        binding.btnLogOut.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Log Out")
                    .setMessage("Are you sure you want to log out of BorrowBox?")
                    .setPositiveButton("Log Out", (dialog, which) -> {
                        sessionManager.logout();
                        try {
                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                        } catch (Exception ignored) {}

                        Intent intent = new Intent(ProfileActivity.this, WelcomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void loadProfileData() {
        binding.progressBar.setVisibility(View.GONE);

        if (isGuest) {
            binding.tvProfileName.setText("Guest Student");
            binding.tvProfileDept.setText("KUET Campus Visitor");
            binding.tvVerificationBadge.setText("Guest Mode");
            binding.tvVerificationBadge.setBackgroundResource(R.drawable.bg_status_rented);
            binding.tvEmail.setText("Not registered");
            binding.tvPhone.setText("Not registered");
            binding.tvWhatsApp.setText("Not registered");
            binding.tvAddress.setText("Browse-only access");
            binding.tvRoll.setText("N/A");
            binding.tvCR.setText("N/A");
            binding.tvTrustScore.setText("★ 0.0 Trust Score");
            binding.btnEditProfileHeader.setText("Sign In to Create Profile");
            return;
        }

        // Load from SessionManager
        currentProfile = sessionManager.getUserProfile();
        populateUI(currentProfile);

        // Fetch fresh data from backend
        String token = sessionManager.getToken();
        if (token != null && !token.isEmpty()) {
            apiClient.getMe(token, new ApiClient.ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject response) {
                    JSONObject u = response.optJSONObject("user");
                    if (u != null) {
                        String id = u.optString("id", sessionManager.getUserId());
                        String name = u.optString("full_name", sessionManager.getUserName());
                        String roll = u.optString("roll_no", sessionManager.getUserRoll());
                        String email = u.optString("email", sessionManager.getUserEmail());
                        String phone = u.optString("phone", sessionManager.getUserPhone());
                        String dept = u.optString("department", sessionManager.getUserDept());
                        String hall = u.optString("hall", sessionManager.getUserHall());
                        boolean emailVerified = u.optBoolean("is_email_verified", sessionManager.isEmailVerified());
                        boolean rollVerified = u.optBoolean("is_roll_verified", sessionManager.isRollVerified());

                        sessionManager.saveSession(token, id, name, roll, email, phone, dept, hall, emailVerified, rollVerified);
                        currentProfile = sessionManager.getUserProfile();
                        populateUI(currentProfile);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    // Keep session values if backend unavailable
                }
            });
        }
    }

    private void populateUI(UserProfile profile) {
        binding.tvProfileName.setText(profile.getFullName());
        binding.tvProfileDept.setText(profile.getDepartment());
        binding.tvEmail.setText(profile.getEmail());
        binding.tvPhone.setText(profile.getPhoneNumber());
        binding.tvWhatsApp.setText(profile.getPhoneNumber());
        binding.tvAddress.setText(profile.getHallName());
        binding.tvRoll.setText(profile.getRollNumber().isEmpty() ? "KUET Student" : profile.getRollNumber());
        binding.tvCR.setText("Campus Member");
        binding.tvTrustScore.setText("★ 5.0 Trust Score");

        // TASK 6: Verification status badge on Profile page
        if (sessionManager.isEmailVerified()) {
            binding.tvVerificationBadge.setText("✓ Verified KUETian");
            binding.tvVerificationBadge.setBackgroundResource(R.drawable.bg_status_available);
            binding.tvVerificationBadge.setTextColor(getResources().getColor(R.color.dark_text, getTheme()));
        } else {
            binding.tvVerificationBadge.setText("⚠️ Unverified (Tap to Verify)");
            binding.tvVerificationBadge.setBackgroundResource(R.drawable.bg_status_rented);
            binding.tvVerificationBadge.setTextColor(getResources().getColor(R.color.dark_text, getTheme()));
        }
    }

    private void showGuestPrompt() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Sign In Required")
                .setMessage("Please sign in or register with your KUET account to create and manage your profile.")
                .setPositiveButton("Sign In", (dialog, which) -> {
                    Intent intent = new Intent(ProfileActivity.this, AuthActivity.class);
                    intent.putExtra(AppConfig.EXTRA_AUTH_TAB, "login");
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileData();
    }
}
