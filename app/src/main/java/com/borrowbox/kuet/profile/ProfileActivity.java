package com.borrowbox.kuet.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Base64;
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
import com.borrowbox.kuet.base.BaseActivity;
import com.borrowbox.kuet.databinding.ActivityProfileBinding;
import com.borrowbox.kuet.models.UserProfile;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.borrowbox.kuet.services.PhotoUploadForegroundService;
import com.borrowbox.kuet.utils.AppConfig;
import com.borrowbox.kuet.verification.VerificationActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ProfileActivity extends BaseActivity {

    private ActivityProfileBinding binding;
    private SessionManager sessionManager;
    private ApiClient apiClient;
    private UserProfile currentProfile;
    private boolean isGuest = false;
    private CountDownTimer profileResendTimer;

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processImageFromUri(uri);
                }
            }
    );

    private final ActivityResultLauncher<Void> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null) {
                    processBitmap(bitmap);
                }
            }
    );

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
        com.borrowbox.kuet.utils.BottomNavHelper.setupBottomNav(this, binding.bottomNavigation, binding.fabAddItem, R.id.nav_profile);
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

        View.OnClickListener photoClickListener = v -> showPhotoOptionsDialog();
        binding.btnChangePhoto.setOnClickListener(photoClickListener);
        binding.avatarContainer.setOnClickListener(photoClickListener);

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

        binding.btnMyPosts.setOnClickListener(v -> {
            if (isGuest) {
                showGuestPrompt();
            } else {
                Intent intent = new Intent(ProfileActivity.this, com.borrowbox.kuet.items.MyPostsActivity.class);
                startActivity(intent);
            }
        });

        binding.btnTestPush.setOnClickListener(v -> sendTestPushNotification());

        binding.btnEditHallRoom.setOnClickListener(v -> showEditHallRoomDialog());

        binding.btnEnableNotifications.setOnClickListener(v -> {
            try {
                Intent intent = new Intent();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    intent.setAction(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                } else {
                    intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                }
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent fallback = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    fallback.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(fallback);
                } catch (Exception ignored) {}
            }
        });

        binding.btnDeleteAccount.setOnClickListener(v -> confirmDeleteAccount());

        // TASK 4: Add logout in Profile
        binding.btnLogOut.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Log Out")
                    .setMessage("Are you sure you want to log out of BorrowBox?")
                    .setPositiveButton("Log Out", (dialog, which) -> {
                        String authToken = sessionManager.getToken();
                        String fcmToken = sessionManager.getFcmToken();
                        if (fcmToken != null && !fcmToken.isEmpty() && authToken != null) {
                            try {
                                org.json.JSONObject body = new org.json.JSONObject();
                                body.put("token", fcmToken);
                                apiClient.postJson("/users/fcm-token/remove", body, authToken, new ApiClient.ApiCallback<org.json.JSONObject>() {
                                    @Override public void onSuccess(org.json.JSONObject result) {}
                                    @Override public void onError(String errorMessage) {}
                                });
                            } catch (Exception ignored) {}
                        }
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
            binding.tvFirebaseUid.setText("Firebase UID: Guest Mode");
            binding.btnEditProfileHeader.setText("Sign In to Create Profile");

            com.borrowbox.kuet.rentals.RentalImageUtils.loadCircularImage(binding.ivProfilePicture, binding.tvProfileInitial, "", "G");

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
                        String hallRoomNo = u.optString("hallRoomNo", u.optString("hall_room_no", sessionManager.getUserHallRoomNo()));
                        boolean emailVerified = u.optBoolean("is_email_verified", sessionManager.isEmailVerified());
                        boolean rollVerified = u.optBoolean("is_roll_verified", sessionManager.isRollVerified());

                        String profilePhoto = u.optString("profilePhoto", u.optString("photoUrl", ""));
                        if (!profilePhoto.isEmpty()) {
                            sessionManager.saveProfilePhoto(profilePhoto);
                        }

                        sessionManager.saveSession(token, id, name, roll, email, phone, dept, hall, hallRoomNo, emailVerified, rollVerified);
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
        String room = profile.getHallRoomNo();
        binding.tvHallRoomNo.setText(room.isEmpty() ? "Not set (e.g. 312)" : "Room " + room);
        binding.tvRoll.setText(profile.getRollNumber().isEmpty() ? "KUET Student" : profile.getRollNumber());
        binding.tvCR.setText("Campus Member");
        binding.tvTrustScore.setText("★ 5.0 Trust Score");

        try {
            com.google.firebase.auth.FirebaseUser fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (fbUser != null) {
                binding.tvFirebaseUid.setText("Firebase UID: " + fbUser.getUid());
            } else {
                binding.tvFirebaseUid.setText("Firebase UID: Local (" + sessionManager.getUserId() + ")");
            }
        } catch (Exception e) {
            binding.tvFirebaseUid.setText("Firebase UID: " + sessionManager.getUserId());
        }

        String currentPhoto = sessionManager.getProfilePhoto();
        com.borrowbox.kuet.rentals.RentalImageUtils.loadCircularImage(
                binding.ivProfilePicture,
                binding.tvProfileInitial,
                currentPhoto,
                profile.getFullName()
        );

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
        if (binding.bottomNavigation != null) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_profile);
        }
        loadProfileData();
        checkNotificationStatus();
    }

    private void checkNotificationStatus() {
        if (isGuest) {
            binding.cardNotificationWarning.setVisibility(View.GONE);
            return;
        }
        boolean enabled = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled();
        binding.cardNotificationWarning.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private void showPhotoOptionsDialog() {
        if (isGuest) {
            showGuestPrompt();
            return;
        }

        String[] options = {"Choose from Gallery", "Take Photo"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Change Profile Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        try {
                            galleryLauncher.launch("image/*");
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not open gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        try {
                            cameraLauncher.launch(null);
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not open camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void processImageFromUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap != null) {
                processBitmap(bitmap);
            } else {
                Toast.makeText(this, "Unable to read image.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processBitmap(Bitmap original) {
        if (original == null) return;
        try {
            // 1. Crop to square
            int width = original.getWidth();
            int height = original.getHeight();
            int size = Math.min(width, height);
            int x = (width - size) / 2;
            int y = (height - size) / 2;
            Bitmap square = Bitmap.createBitmap(original, x, y, size, size);

            // 2. Resize to ~400px
            int targetSize = Math.min(400, size);
            Bitmap scaled = Bitmap.createScaledBitmap(square, targetSize, targetSize, true);

            // 3. Compress to JPEG quality ~70 (under 200 KB)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] bytes = baos.toByteArray();

            if (bytes.length > 200 * 1024) {
                baos.reset();
                scaled.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                bytes = baos.toByteArray();
            }

            String base64Image = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
            uploadProfilePhoto(base64Image);
        } catch (Exception e) {
            Toast.makeText(this, "Error processing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadProfilePhoto(String base64Photo) {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please log in to upload photo.", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.pbPhotoUploading.setVisibility(View.VISIBLE);
        binding.btnChangePhoto.setEnabled(false);

        PhotoUploadForegroundService.startUpload(this, "Uploading profile photo...");

        apiClient.uploadProfilePhoto(base64Photo, token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                PhotoUploadForegroundService.completeUpload(ProfileActivity.this, "Profile photo updated!");
                binding.pbPhotoUploading.setVisibility(View.GONE);
                binding.btnChangePhoto.setEnabled(true);
                sessionManager.saveProfilePhoto(base64Photo);
                com.borrowbox.kuet.rentals.RentalImageUtils.loadCircularImage(
                        binding.ivProfilePicture,
                        binding.tvProfileInitial,
                        base64Photo,
                        sessionManager.getUserName()
                );
                Toast.makeText(ProfileActivity.this, "Profile photo updated successfully!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                PhotoUploadForegroundService.failUpload(ProfileActivity.this, "Upload failed: " + errorMessage);
                binding.pbPhotoUploading.setVisibility(View.GONE);
                binding.btnChangePhoto.setEnabled(true);
                Toast.makeText(ProfileActivity.this, "Failed to upload photo: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditHallRoomDialog() {
        if (isGuest) {
            showGuestPrompt();
            return;
        }

        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("e.g. 312");
        input.setText(sessionManager.getUserHallRoomNo());
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#A5A1B8"));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F0C38E")));

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, pad / 2);
        container.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit Hall Room Number")
                .setMessage("Enter your room number in " + sessionManager.getUserHall() + ":")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newRoom = input.getText().toString().trim();
                    binding.progressBar.setVisibility(View.VISIBLE);
                    try {
                        org.json.JSONObject payload = new org.json.JSONObject();
                        payload.put("hallRoomNo", newRoom);
                        payload.put("hall_room_no", newRoom);
                        apiClient.updateProfile(payload, sessionManager.getToken(), new ApiClient.ApiCallback<org.json.JSONObject>() {
                            @Override
                            public void onSuccess(org.json.JSONObject result) {
                                binding.progressBar.setVisibility(View.GONE);
                                sessionManager.saveHallRoomNo(newRoom);
                                if (currentProfile != null) currentProfile.setHallRoomNo(newRoom);
                                binding.tvHallRoomNo.setText(newRoom.isEmpty() ? "Not set (e.g. 312)" : "Room " + newRoom);
                                Toast.makeText(ProfileActivity.this, "Hall room number updated", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                binding.progressBar.setVisibility(View.GONE);
                                Toast.makeText(ProfileActivity.this, "Failed to update room: " + errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        binding.progressBar.setVisibility(View.GONE);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendTestPushNotification() {
        if (isGuest) {
            showGuestPrompt();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnTestPush.setEnabled(false);

        apiClient.testPushNotification(sessionManager.getToken(), new ApiClient.ApiCallback<org.json.JSONObject>() {
            @Override
            public void onSuccess(org.json.JSONObject result) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnTestPush.setEnabled(true);
                String msg = result.optString("message", "Sent to device");
                new MaterialAlertDialogBuilder(ProfileActivity.this)
                        .setTitle("Push Notification Test")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show();
            }

            @Override
            public void onError(String errorMessage) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnTestPush.setEnabled(true);
                new MaterialAlertDialogBuilder(ProfileActivity.this)
                        .setTitle("Push Notification Test Failed")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    private void confirmDeleteAccount() {
        if (isGuest) {
            showGuestPrompt();
            return;
        }

        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Type DELETE to confirm");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#A5A1B8"));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252")));

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, pad / 2);
        container.addView(input);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Account Permanently?")
                .setMessage("This action is permanent and cannot be undone. All your listings, rental history, notifications, and profile details will be permanently erased.\n\nType DELETE below to confirm:")
                .setView(container)
                .setPositiveButton("Delete Forever", (d, which) -> executeDeleteAccount())
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button confirmBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            confirmBtn.setEnabled(false);
            confirmBtn.setTextColor(Color.parseColor("#FF5252"));

            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    confirmBtn.setEnabled("DELETE".equals(s.toString().trim()));
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        });

        dialog.show();
    }

    private void executeDeleteAccount() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnDeleteAccount.setEnabled(false);

        apiClient.deleteAccount(sessionManager.getToken(), new ApiClient.ApiCallback<org.json.JSONObject>() {
            @Override
            public void onSuccess(org.json.JSONObject result) {
                binding.progressBar.setVisibility(View.GONE);
                sessionManager.logout();
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                } catch (Exception ignored) {}

                Toast.makeText(ProfileActivity.this, "Your account has been deleted", Toast.LENGTH_LONG).show();

                Intent intent = new Intent(ProfileActivity.this, WelcomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnDeleteAccount.setEnabled(true);
                new MaterialAlertDialogBuilder(ProfileActivity.this)
                        .setTitle("Account Deletion Failed")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }
}
