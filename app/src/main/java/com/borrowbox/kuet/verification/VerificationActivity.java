package com.borrowbox.kuet.verification;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.borrowbox.kuet.databinding.ActivityVerificationBinding;
import com.borrowbox.kuet.utils.AppConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * KUET Student ID Verification submission screen.
 *
 * Flow:
 * 1. User picks/takes a photo of their student ID
 * 2. Uploads to Firebase Storage: verifications/{uid}/id.jpg
 * 3. Writes studentVerifications/{uid} document:
 *    {userId, submittedAt, idImageUrl, status: "Pending"}
 * 4. Routes to VerificationStatusActivity
 *
 * Security:
 * - Only the submitting user can upload (enforced in storage.rules)
 * - Only admins can read the image or update status (enforced in both rules files)
 */
public class VerificationActivity extends AppCompatActivity {

    private ActivityVerificationBinding binding;
    private Uri selectedImageUri;
    private Uri cameraImageUri;

    // Gallery picker
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    showPreview(uri);
                }
            });

    // Camera launcher
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraImageUri != null) {
                    selectedImageUri = cameraImageUri;
                    showPreview(cameraImageUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVerificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            binding.getRoot().setPadding(0, top, 0, 0);
            return insets;
        });

        binding.btnVerifBack.setOnClickListener(v -> finish());

        binding.btnPickGallery.setOnClickListener(v ->
                galleryLauncher.launch("image/*"));

        binding.btnTakePhoto.setOnClickListener(v -> launchCamera());

        binding.cardIdPreview.setOnClickListener(v ->
                galleryLauncher.launch("image/*"));

        binding.btnSubmitVerification.setOnClickListener(v -> submitVerification());
    }

    private void launchCamera() {
        try {
            File photoFile = File.createTempFile("id_photo_", ".jpg", getCacheDir());
            cameraImageUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photoFile);
            cameraLauncher.launch(cameraImageUri);
        } catch (IOException e) {
            Toast.makeText(this, "Cannot open camera.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPreview(Uri uri) {
        binding.ivIdPreview.setImageURI(uri);
        binding.ivIdPreview.setVisibility(View.VISIBLE);
        binding.layoutIdPlaceholder.setVisibility(View.GONE);
    }

    private void submitVerification() {
        if (selectedImageUri == null) {
            showError("Please select or take a photo of your Student ID first.");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showError("You must be logged in.");
            return;
        }

        setLoading(true);

        String uid = user.getUid();
        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference()
                .child("verifications/" + uid + "/id.jpg");

        storageRef.putFile(selectedImageUri)
                .addOnSuccessListener(snap -> snap.getStorage().getDownloadUrl()
                        .addOnSuccessListener(downloadUrl -> {
                            // Write Firestore document
                            Map<String, Object> doc = new HashMap<>();
                            doc.put("userId", uid);
                            doc.put("submittedAt", System.currentTimeMillis());
                            doc.put("idImageUrl", downloadUrl.toString());
                            doc.put("status", AppConfig.VERIF_PENDING);

                            FirebaseFirestore.getInstance()
                                    .collection(AppConfig.COL_STUDENT_VERIF)
                                    .document(uid)
                                    .set(doc)
                                    .addOnSuccessListener(v -> {
                                        setLoading(false);
                                        // Also update users/{uid}.verificationStatus = "Pending"
                                        FirebaseFirestore.getInstance()
                                                .collection(AppConfig.COL_USERS)
                                                .document(uid)
                                                .update("verificationStatus", AppConfig.VERIF_PENDING);
                                        startActivity(new Intent(this, VerificationStatusActivity.class));
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        setLoading(false);
                                        showError("Failed to save record: " + e.getMessage());
                                    });
                        }))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Upload failed: " + e.getMessage());
                });
    }

    private void showError(String msg) {
        binding.tvVerifSubmitError.setText(msg);
        binding.tvVerifSubmitError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        binding.progressVerifSubmit.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSubmitVerification.setEnabled(!loading);
        binding.btnPickGallery.setEnabled(!loading);
        binding.btnTakePhoto.setEnabled(!loading);
    }
}
