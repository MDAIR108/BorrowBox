package com.borrowbox.kuet.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.auth.WelcomeActivity;
import com.borrowbox.kuet.databinding.ActivityProfileBinding;
import com.borrowbox.kuet.models.UserProfile;
import com.borrowbox.kuet.repositories.UserRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private UserRepository userRepository;
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
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        isGuest = getIntent().getBooleanExtra(WelcomeActivity.EXTRA_IS_GUEST, false);
        userRepository = new UserRepository(this);

        setupListeners();
        loadProfileData();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        View.OnClickListener editClickListener = v -> {
            if (isGuest) {
                showGuestPrompt();
            } else {
                Intent intent = new EditProfileActivity.Builder(this)
                        .setUserProfile(currentProfile)
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
                String status = currentProfile != null ? currentProfile.getVerificationStatus() : "";
                if ("Pending".equalsIgnoreCase(status) || "Approved".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
                    startActivity(new Intent(ProfileActivity.this, com.borrowbox.kuet.verification.VerificationStatusActivity.class));
                } else {
                    startActivity(new Intent(ProfileActivity.this, com.borrowbox.kuet.verification.VerificationActivity.class));
                }
            }
        };

        binding.btnStudentVerification.setOnClickListener(verifClickListener);
        binding.tvVerificationBadge.setOnClickListener(verifClickListener);

        binding.btnAdminDashboard.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, com.borrowbox.kuet.admin.AdminDashboardActivity.class));
        });

        binding.btnMyRentals.setOnClickListener(v -> {
            Toast.makeText(this, "Active and past rentals are listed in your Rentals section.", Toast.LENGTH_SHORT).show();
        });

        binding.btnLogOut.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Log Out")
                    .setMessage("Are you sure you want to log out of BorrowBox?")
                    .setPositiveButton("Log Out", (dialog, which) -> {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
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
        binding.progressBar.setVisibility(View.VISIBLE);

        if (isGuest) {
            binding.progressBar.setVisibility(View.GONE);
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

        userRepository.getUserProfile(userRepository.getCurrentUserId(), new UserRepository.DataCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                binding.progressBar.setVisibility(View.GONE);
                currentProfile = profile;
                populateUI(profile);
            }

            @Override
            public void onError(String errorMessage) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(ProfileActivity.this, "Failed to load profile: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populateUI(UserProfile profile) {
        binding.tvProfileName.setText(profile.getFullName());
        binding.tvProfileDept.setText(profile.getDepartment());
        binding.tvEmail.setText(profile.getEmail());
        binding.tvPhone.setText(profile.getPhoneNumber());
        binding.tvWhatsApp.setText(profile.getWhatsappNumber());
        binding.tvAddress.setText(profile.getPresentAddress());
        binding.tvRoll.setText(profile.getRollNumber());
        binding.tvCR.setText(profile.getCrName());
        binding.tvTrustScore.setText(String.format(java.util.Locale.US, "★ %.1f Trust Score", profile.getTrustScore()));

        // Status badge
        String status = profile.getVerificationStatus();
        if ("Approved".equalsIgnoreCase(status)) {
            binding.tvVerificationBadge.setText("KUET Verified Student");
            binding.tvVerificationBadge.setBackgroundResource(R.drawable.bg_status_available);
        } else if ("Pending".equalsIgnoreCase(status)) {
            binding.tvVerificationBadge.setText("Verification Pending Review");
            binding.tvVerificationBadge.setBackgroundResource(R.drawable.bg_status_rented);
        } else if ("Rejected".equalsIgnoreCase(status)) {
            binding.tvVerificationBadge.setText("Verification Rejected");
            binding.tvVerificationBadge.setBackgroundResource(R.drawable.bg_status_rented);
        } else {
            binding.tvVerificationBadge.setText("Verification Not Submitted");
            binding.tvVerificationBadge.setBackgroundResource(R.drawable.bg_status_rented);
        }

        // Check for admin role
        com.google.firebase.auth.FirebaseUser fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection(com.borrowbox.kuet.utils.AppConfig.COL_USERS)
                    .document(fbUser.getUid())
                    .get()
                    .addOnSuccessListener(snap -> {
                        if ("admin".equals(snap.getString("role"))) {
                            binding.btnAdminDashboard.setVisibility(View.VISIBLE);
                        } else {
                            binding.btnAdminDashboard.setVisibility(View.GONE);
                        }
                    });
        }

        // Profile picture
        if (profile.getProfilePictureUrl() != null && !profile.getProfilePictureUrl().isEmpty()) {
            try {
                binding.ivProfilePicture.setImageURI(Uri.parse(profile.getProfilePictureUrl()));
                binding.ivProfilePicture.setImageTintList(null);
            } catch (Exception ignored) {}
        }
    }

    private void showGuestPrompt() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Sign In Required")
                .setMessage("Please sign in or register with your KUET account to create and manage your profile.")
                .setPositiveButton("Sign In", (dialog, which) -> {
                    Intent intent = new Intent(ProfileActivity.this, WelcomeActivity.class);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
