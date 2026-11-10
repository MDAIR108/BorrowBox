package com.borrowbox.kuet.items;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ActivityAddProductBinding;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class AddProductActivity extends AppCompatActivity {

    private ActivityAddProductBinding binding;
    private ApiClient apiClient;
    private SessionManager sessionManager;
    private String selectedPhotoBase64 = null;

    private static final String[] CATEGORIES = new String[]{
            "Electronics", "Books", "Tools", "Sports", "Others"
    };

    private static final String[] CONDITIONS = new String[]{
            "New", "Like New", "Good", "Fair"
    };

    private final ActivityResultLauncher<String> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    handleSelectedPhoto(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddProductBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiClient = ApiClient.getInstance(this);
        sessionManager = new SessionManager(this);

        setupDropdowns();
        setupListeners();
        prefillWhatsapp();
    }

    private void prefillWhatsapp() {
        String phone = sessionManager.getUserPhone();
        if (phone != null && !phone.isEmpty()) {
            binding.etWhatsappNumber.setText(phone);
        }
    }

    private void setupDropdowns() {
        // Category dropdown
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                CATEGORIES
        );
        binding.actvCategory.setAdapter(categoryAdapter);
        binding.actvCategory.setText(CATEGORIES[0], false);

        // Condition dropdown
        ArrayAdapter<String> conditionAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                CONDITIONS
        );
        binding.actvCondition.setAdapter(conditionAdapter);
        binding.actvCondition.setText("Good", false);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.cardPhotoPicker.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));

        binding.btnRemovePhoto.setOnClickListener(v -> removePhoto());

        binding.btnSubmit.setOnClickListener(v -> handleSubmit());
    }

    private void handleSelectedPhoto(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap original = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            if (original != null) {
                // Scale down to max 800px to keep payload lightweight and within limits
                int maxDim = 800;
                int width = original.getWidth();
                int height = original.getHeight();
                float ratio = Math.min((float) maxDim / width, (float) maxDim / height);

                Bitmap scaled;
                if (ratio < 1.0f) {
                    int newW = Math.round(width * ratio);
                    int newH = Math.round(height * ratio);
                    scaled = Bitmap.createScaledBitmap(original, newW, newH, true);
                } else {
                    scaled = original;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] bytes = baos.toByteArray();
                if (bytes.length > 500 * 1024) {
                    Toast.makeText(this, "Image exceeds 500 KB after compression. Please choose a smaller photo.", Toast.LENGTH_LONG).show();
                    removePhoto();
                    return;
                }
                selectedPhotoBase64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);

                binding.ivPhotoPreview.setImageBitmap(scaled);
                binding.ivPhotoPreview.setVisibility(View.VISIBLE);
                binding.btnRemovePhoto.setVisibility(View.VISIBLE);
                binding.layoutPhotoPlaceholder.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not load image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            removePhoto();
        }
    }

    private void removePhoto() {
        selectedPhotoBase64 = null;
        binding.ivPhotoPreview.setImageDrawable(null);
        binding.ivPhotoPreview.setVisibility(View.GONE);
        binding.btnRemovePhoto.setVisibility(View.GONE);
        binding.layoutPhotoPlaceholder.setVisibility(View.VISIBLE);
    }

    private void handleSubmit() {
        // Clear previous errors
        binding.tilProductName.setError(null);
        binding.tilRentPerDay.setError(null);
        binding.tilMaxRentDays.setError(null);
        binding.tilCategory.setError(null);
        binding.tilCondition.setError(null);
        binding.tilWhatsappNumber.setError(null);

        boolean isValid = true;

        // 1. Product Name (Required)
        String name = binding.etProductName.getText() != null
                ? binding.etProductName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            binding.tilProductName.setError("Product name is required");
            binding.tilProductName.requestFocus();
            isValid = false;
        }

        // 2. Rent per Day (Required, number > 0)
        String rentStr = binding.etRentPerDay.getText() != null
                ? binding.etRentPerDay.getText().toString().trim() : "";
        double rentPerDay = 0.0;
        if (rentStr.isEmpty()) {
            binding.tilRentPerDay.setError("Rent per day is required");
            if (isValid) binding.tilRentPerDay.requestFocus();
            isValid = false;
        } else {
            try {
                rentPerDay = Double.parseDouble(rentStr);
                if (rentPerDay <= 0) {
                    binding.tilRentPerDay.setError("Rent per day must be greater than 0");
                    if (isValid) binding.tilRentPerDay.requestFocus();
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                binding.tilRentPerDay.setError("Please enter a valid amount");
                if (isValid) binding.tilRentPerDay.requestFocus();
                isValid = false;
            }
        }

        // 3. Maximum Rent Days (Required, whole number >= 1)
        String maxDaysStr = binding.etMaxRentDays.getText() != null
                ? binding.etMaxRentDays.getText().toString().trim() : "";
        int maxRentDays = 0;
        if (maxDaysStr.isEmpty()) {
            binding.tilMaxRentDays.setError("Maximum rent days is required");
            if (isValid) binding.tilMaxRentDays.requestFocus();
            isValid = false;
        } else {
            try {
                maxRentDays = Integer.parseInt(maxDaysStr);
                if (maxRentDays < 1) {
                    binding.tilMaxRentDays.setError("Maximum rent days must be at least 1");
                    if (isValid) binding.tilMaxRentDays.requestFocus();
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                binding.tilMaxRentDays.setError("Please enter a whole number");
                if (isValid) binding.tilMaxRentDays.requestFocus();
                isValid = false;
            }
        }

        // 4. Category
        String category = binding.actvCategory.getText() != null
                ? binding.actvCategory.getText().toString().trim() : "";
        if (category.isEmpty()) {
            binding.tilCategory.setError("Please select a category");
            if (isValid) binding.tilCategory.requestFocus();
            isValid = false;
        }

        // 5. Condition
        String condition = binding.actvCondition.getText() != null
                ? binding.actvCondition.getText().toString().trim() : "";
        if (condition.isEmpty()) {
            binding.tilCondition.setError("Please select condition");
            if (isValid) binding.tilCondition.requestFocus();
            isValid = false;
        }

        // 6. WhatsApp Number (Required, Bangladesh format: 11 digits starting with 01)
        String whatsapp = binding.etWhatsappNumber.getText() != null
                ? binding.etWhatsappNumber.getText().toString().trim() : "";
        if (whatsapp.isEmpty()) {
            binding.tilWhatsappNumber.setError("WhatsApp number is required");
            if (isValid) binding.tilWhatsappNumber.requestFocus();
            isValid = false;
        } else if (!whatsapp.matches("^01\\d{9}$")) {
            binding.tilWhatsappNumber.setError("Enter a valid 11-digit WhatsApp number (e.g. 01XXXXXXXXX)");
            if (isValid) binding.tilWhatsappNumber.requestFocus();
            isValid = false;
        }

        if (!isValid) {
            return;
        }

        // Optional fields
        String model = binding.etModel.getText() != null
                ? binding.etModel.getText().toString().trim() : "";
        String description = binding.etDescription.getText() != null
                ? binding.etDescription.getText().toString().trim() : "";
        String featuresStr = binding.etFeatures.getText() != null
                ? binding.etFeatures.getText().toString().trim() : "";

        JSONArray featuresArray = new JSONArray();
        if (!featuresStr.isEmpty()) {
            String[] lines = featuresStr.split("\n");
            for (String line : lines) {
                String clean = line.trim();
                if (!clean.isEmpty()) {
                    featuresArray.put(clean);
                }
            }
        }

        // Set Loading state
        setLoading(true);

        try {
            JSONObject payload = new JSONObject();
            payload.put("name", name);
            payload.put("title", name);
            payload.put("rentPerDay", rentPerDay);
            payload.put("pricePerDay", rentPerDay);
            payload.put("price_per_day", rentPerDay);
            payload.put("maxRentDays", maxRentDays);
            payload.put("model", model);
            payload.put("description", description);
            payload.put("features", featuresArray);
            payload.put("category", category);
            payload.put("condition", condition);
            payload.put("whatsappNumber", whatsapp);

            String ownerId = sessionManager.getUserId();
            if (ownerId == null || ownerId.trim().isEmpty()) {
                ownerId = "user_" + (sessionManager.getUserRoll().isEmpty() ? "1" : sessionManager.getUserRoll());
            }
            payload.put("ownerId", ownerId);
            payload.put("ownerName", sessionManager.getUserName());
            payload.put("department", sessionManager.getUserDept());
            payload.put("hallLocation", sessionManager.getUserHall());

            if (selectedPhotoBase64 != null) {
                payload.put("photoUrl", selectedPhotoBase64);
                payload.put("imageUrl", selectedPhotoBase64);
            }

            String token = sessionManager.getToken();
            apiClient.addProduct(payload, token, new ApiClient.ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject result) {
                    setLoading(false);
                    Toast.makeText(AddProductActivity.this, "Product listed successfully!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                }

                @Override
                public void onError(String errorMessage) {
                    setLoading(false);
                    String errorText = errorMessage != null && !errorMessage.isEmpty()
                            ? errorMessage : "Failed to list product. Please try again.";
                    Toast.makeText(AddProductActivity.this, errorText, Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            setLoading(false);
            Toast.makeText(this, "Error building request: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSubmit.setEnabled(!isLoading);
        binding.btnSubmit.setText(isLoading ? "Listing Product..." : "List Product for Rent");
    }
}
