package com.borrowbox.kuet.profile;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.borrowbox.kuet.databinding.ActivityEditProfileBinding;
import com.borrowbox.kuet.models.UserProfile;
import com.borrowbox.kuet.repositories.UserRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;

public class EditProfileActivity extends AppCompatActivity {

    public static final String EXTRA_PROFILE = "extra_user_profile";

    public static class Builder {
        private final Context context;
        private UserProfile profile;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setUserProfile(UserProfile profile) {
            this.profile = profile;
            return this;
        }

        public Intent build() {
            Intent intent = new Intent(context, EditProfileActivity.class);
            if (profile != null) {
                intent.putExtra(EXTRA_PROFILE, profile);
            }
            return intent;
        }
    }

    private ActivityEditProfileBinding binding;
    private UserRepository userRepository;
    private UserProfile currentProfile;
    private Uri selectedPhotoUri = null;
    private Uri tempCameraUri = null;

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedPhotoUri = uri;
                    binding.ivProfilePicture.setImageURI(uri);
                    binding.ivProfilePicture.setImageTintList(null);
                }
            }
    );

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && tempCameraUri != null) {
                    selectedPhotoUri = tempCameraUri;
                    binding.ivProfilePicture.setImageURI(tempCameraUri);
                    binding.ivProfilePicture.setImageTintList(null);
                }
            }
    );

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    launchCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required to capture a photo.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userRepository = new UserRepository(this);
        currentProfile = (UserProfile) getIntent().getSerializableExtra(EXTRA_PROFILE);
        if (currentProfile == null) {
            currentProfile = new UserProfile();
            currentProfile.setUid(userRepository.getCurrentUserId());
        }

        populateFields();
        setupListeners();
    }

    private void populateFields() {
        binding.etFullName.setText(currentProfile.getFullName());
        binding.etEmail.setText(currentProfile.getEmail().isEmpty() ? "student@stud.kuet.ac.bd" : currentProfile.getEmail());
        binding.etPhoneNumber.setText(currentProfile.getPhoneNumber());
        binding.etWhatsappNumber.setText(currentProfile.getWhatsappNumber());
        binding.etPresentAddress.setText(currentProfile.getPresentAddress());
        binding.etDepartment.setText(currentProfile.getDepartment());
        binding.etRollNumber.setText(currentProfile.getRollNumber());
        binding.etCrName.setText(currentProfile.getCrName());

        if (currentProfile.getProfilePictureUrl() != null && !currentProfile.getProfilePictureUrl().isEmpty()) {
            try {
                binding.ivProfilePicture.setImageURI(Uri.parse(currentProfile.getProfilePictureUrl()));
                binding.ivProfilePicture.setImageTintList(null);
            } catch (Exception ignored) {}
        }

        // Lock roll number & department if verified
        if (currentProfile.isVerified()) {
            binding.etRollNumber.setEnabled(false);
            binding.etDepartment.setEnabled(false);
            binding.tvRollLockNote.setVisibility(View.VISIBLE);
            binding.tvRollLockNote.setText("🔒 Verified identity: Roll Number & Department are locked.");
        } else {
            binding.tvRollLockNote.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCancel.setOnClickListener(v -> finish());

        binding.btnChangePhoto.setOnClickListener(v -> showPhotoSourceDialog());
        binding.avatarContainer.setOnClickListener(v -> showPhotoSourceDialog());

        binding.btnSaveProfile.setOnClickListener(v -> validateAndSave());
    }

    private void showPhotoSourceDialog() {
        String[] options = {"Choose from Gallery", "Take Photo with Camera"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Profile Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        galleryLauncher.launch("image/*");
                    } else {
                        checkCameraPermissionAndLaunch();
                    }
                })
                .show();
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File photoFile = File.createTempFile("profile_", ".jpg", getCacheDir());
            tempCameraUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    photoFile
            );
            cameraLauncher.launch(tempCameraUri);
        } catch (IOException e) {
            Toast.makeText(this, "Could not create temporary photo file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void validateAndSave() {
        String name = binding.etFullName.getText().toString().trim();
        String phone = binding.etPhoneNumber.getText().toString().trim();
        String whatsapp = binding.etWhatsappNumber.getText().toString().trim();
        String address = binding.etPresentAddress.getText().toString().trim();
        String dept = binding.etDepartment.getText().toString().trim();
        String roll = binding.etRollNumber.getText().toString().trim();
        String cr = binding.etCrName.getText().toString().trim();

        if (name.isEmpty()) {
            binding.etFullName.setError("Full name is required");
            binding.etFullName.requestFocus();
            return;
        }

        if (phone.isEmpty() || phone.length() < 10) {
            binding.etPhoneNumber.setError("Enter a valid phone number (at least 10 digits)");
            binding.etPhoneNumber.requestFocus();
            return;
        }

        if (whatsapp.isEmpty() || whatsapp.length() < 10) {
            binding.etWhatsappNumber.setError("Enter a valid WhatsApp number");
            binding.etWhatsappNumber.requestFocus();
            return;
        }

        if (address.isEmpty()) {
            binding.etPresentAddress.setError("Present address or hall is required");
            binding.etPresentAddress.requestFocus();
            return;
        }

        if (dept.isEmpty()) {
            binding.etDepartment.setError("Department is required");
            binding.etDepartment.requestFocus();
            return;
        }

        if (roll.isEmpty()) {
            binding.etRollNumber.setError("Student roll number is required");
            binding.etRollNumber.requestFocus();
            return;
        }

        if (cr.isEmpty()) {
            binding.etCrName.setError("Class representative name is required");
            binding.etCrName.requestFocus();
            return;
        }

        // Apply changes
        currentProfile.setFullName(name);
        currentProfile.setPhoneNumber(phone);
        currentProfile.setWhatsappNumber(whatsapp);
        currentProfile.setPresentAddress(address);
        if (!currentProfile.isVerified()) {
            currentProfile.setDepartment(dept);
            currentProfile.setRollNumber(roll);
        }
        currentProfile.setCrName(cr);

        // Show loading progress
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSaveProfile.setEnabled(false);

        userRepository.saveUserProfile(currentProfile, selectedPhotoUri, new UserRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(EditProfileActivity.this, "Profile saved successfully!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSaveProfile.setEnabled(true);
                Toast.makeText(EditProfileActivity.this, "Failed to save: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
