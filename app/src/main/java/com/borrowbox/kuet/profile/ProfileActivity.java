package com.borrowbox.kuet.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
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
    private CountDownTimer profileResendTimer;

    private final ActivityResultLauncher<Intent> editProfileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadProfileData();
                }
            }
    );

    private final ActivityResultLauncher<Intent> verificationLauncher = registerForActivityResult(
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
                intent.putExtra("auto_send", true);
                verificationLauncher.launch(intent);
            }
        };

        binding.btnStudentVerification.setOnClickListener(verifClickListener);
        binding.tvVerificationBadge.setOnClickListener(verifClickListener);

        binding.btnSendOtp.setOnClickListener(v -> sendProfileOtp());
        binding.btnVerifyProfileOtp.setOnClickListener(v -> submitProfileOtp());

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

            binding.cardOtpSection.setVisibility(View.GONE);
            binding.tvOtpSectionHeader.setVisibility(View.GONE);
            return;
        }

        binding.cardOtpSection.setVisibility(View.VISIBLE);
        binding.tvOtpSectionHeader.setVisibility(View.VISIBLE);

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

        // Verification status badge & OTP Section state on Profile page
        if (sessionManager.isEmailVerified()) {
            binding.tvVerificationBadge.setText("✓ Verified KUETian");
            binding.tvVerificationBadge.setBackgroundResource(R.drawable.bg_status_available);
            binding.tvVerificationBadge.setTextColor(getResources().getColor(R.color.dark_text, getTheme()));

            // Update OTP section in profile to reflect verified status
            binding.layoutSendOtp.setVisibility(View.GONE);
            binding.layoutOtpInput.setVisibility(View.GONE);
            binding.layoutOtpVerified.setVisibility(View.VISIBLE);
            binding.tvProfileOtpStatus.setVisibility(View.GONE);
            binding.tvOtpSectionSubtitle.setText("Your institutional email is verified and student privileges are active.");
            binding.btnStudentVerification.setText("✓ Student Verification Complete");
            binding.btnStudentVerification.setEnabled(false);
        } else {
            binding.tvVerificationBadge.setText("⚠️ Unverified (Tap to Verify)");
            binding.tvVerificationBadge.setBackgroundResource(R.drawable.bg_status_rented);
            binding.tvVerificationBadge.setTextColor(getResources().getColor(R.color.dark_text, getTheme()));

            binding.layoutSendOtp.setVisibility(View.VISIBLE);
            binding.layoutOtpInput.setVisibility(View.VISIBLE);
            binding.layoutOtpVerified.setVisibility(View.GONE);
            binding.tvOtpSectionSubtitle.setText("Submit the 6-digit OTP sent to your email to activate your account.");
            binding.btnStudentVerification.setText("🎓  Student ID Verification");
            binding.btnStudentVerification.setEnabled(true);
        }
    }

    private void sendProfileOtp() {
        if (isGuest) {
            showGuestPrompt();
            return;
        }

        String email = sessionManager.getUserEmail();
        if (email == null || email.trim().isEmpty()) {
            binding.tvProfileOtpStatus.setText("User email not found. Please log in again.");
            binding.tvProfileOtpStatus.setTextColor(getResources().getColor(R.color.pink_supporting, getTheme()));
            binding.tvProfileOtpStatus.setVisibility(View.VISIBLE);
            return;
        }

        binding.btnSendOtp.setEnabled(false);
        binding.tvProfileOtpStatus.setText("Sending verification OTP to " + email + "...");
        binding.tvProfileOtpStatus.setTextColor(getResources().getColor(R.color.peach_accent, getTheme()));
        binding.tvProfileOtpStatus.setVisibility(View.VISIBLE);

        String token = sessionManager.getToken();
        apiClient.sendCode(token, email, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                Toast.makeText(ProfileActivity.this, "Verification OTP sent to " + email, Toast.LENGTH_SHORT).show();
                binding.tvProfileOtpStatus.setText("Verification code sent to " + email + ". Check your inbox.");
                binding.tvProfileOtpStatus.setTextColor(getResources().getColor(R.color.peach_accent, getTheme()));
                binding.tvProfileOtpStatus.setVisibility(View.VISIBLE);
                binding.etProfileOtp.requestFocus();

                int cooldown = response.optInt("cooldownSeconds", 60);
                startProfileResendCountdown(cooldown);
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnSendOtp.setEnabled(true);
                binding.tvProfileOtpStatus.setText(errorMessage);
                binding.tvProfileOtpStatus.setTextColor(getResources().getColor(R.color.pink_supporting, getTheme()));
                binding.tvProfileOtpStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    private void startProfileResendCountdown(int seconds) {
        if (profileResendTimer != null) {
            profileResendTimer.cancel();
        }
        binding.btnSendOtp.setEnabled(false);
        profileResendTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000;
                binding.btnSendOtp.setText(sec + "s");
            }

            @Override
            public void onFinish() {
                binding.btnSendOtp.setEnabled(true);
                binding.btnSendOtp.setText("Resend OTP");
            }
        };
        profileResendTimer.start();
    }

    private void submitProfileOtp() {
        if (isGuest) {
            showGuestPrompt();
            return;
        }

        String code = binding.etProfileOtp.getText() != null ? binding.etProfileOtp.getText().toString().trim() : "";
        if (code.length() != 6) {
            binding.tvProfileOtpStatus.setText("Please enter the 6-digit verification code.");
            binding.tvProfileOtpStatus.setTextColor(getResources().getColor(R.color.pink_supporting, getTheme()));
            binding.tvProfileOtpStatus.setVisibility(View.VISIBLE);
            return;
        }

        binding.btnVerifyProfileOtp.setEnabled(false);
        binding.tvProfileOtpStatus.setText("Verifying code...");
        binding.tvProfileOtpStatus.setTextColor(getResources().getColor(R.color.peach_accent, getTheme()));
        binding.tvProfileOtpStatus.setVisibility(View.VISIBLE);

        String token = sessionManager.getToken();
        String email = sessionManager.getUserEmail();

        apiClient.verifyCode(token, code, email, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                binding.btnVerifyProfileOtp.setEnabled(true);
                sessionManager.setEmailVerified(true);

                JSONObject userObj = response.optJSONObject("user");
                if (userObj != null) {
                    String id = userObj.optString("id", sessionManager.getUserId());
                    String name = userObj.optString("full_name", sessionManager.getUserName());
                    String roll = userObj.optString("roll_no", sessionManager.getUserRoll());
                    String em = userObj.optString("email", sessionManager.getUserEmail());
                    String ph = userObj.optString("phone", sessionManager.getUserPhone());
                    String dept = userObj.optString("department", sessionManager.getUserDept());
                    String hall = userObj.optString("hall", sessionManager.getUserHall());
                    boolean emailVerified = userObj.optBoolean("is_email_verified", true);
                    boolean rollVerified = userObj.optBoolean("is_roll_verified", sessionManager.isRollVerified());

                    sessionManager.saveSession(token, id, name, roll, em, ph, dept, hall, emailVerified, rollVerified);
                }

                currentProfile = sessionManager.getUserProfile();
                populateUI(currentProfile);

                Toast.makeText(ProfileActivity.this, "Email verified successfully! 🎉", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnVerifyProfileOtp.setEnabled(true);
                binding.tvProfileOtpStatus.setText(errorMessage);
                binding.tvProfileOtpStatus.setTextColor(getResources().getColor(R.color.pink_supporting, getTheme()));
                binding.tvProfileOtpStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (profileResendTimer != null) {
            profileResendTimer.cancel();
        }
        super.onDestroy();
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
