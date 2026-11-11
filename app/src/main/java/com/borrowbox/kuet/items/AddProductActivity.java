package com.borrowbox.kuet.items;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Base64;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.base.BaseActivity;
import com.borrowbox.kuet.databinding.ActivityAddProductBinding;
import com.borrowbox.kuet.models.Product;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.borrowbox.kuet.services.PhotoUploadForegroundService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

public class AddProductActivity extends BaseActivity {

    public static final String EXTRA_EDIT_PRODUCT = "extra_edit_product";
    public static final String EXTRA_IS_EDIT_MODE = "extra_is_edit_mode";

    private ActivityAddProductBinding binding;
    private ApiClient apiClient;
    private SessionManager sessionManager;
    private String selectedPhotoBase64 = null;
    private Uri tempCameraUri = null;
    private boolean isEditMode = false;
    private Product editProduct = null;

    private static final String[] CATEGORIES = new String[]{
            "Electronics", "Books", "Tools", "Sports", "Others"
    };

    private static final String[] CONDITIONS = new String[]{
            "New", "Like New", "Good", "Fair"
    };

    // Gallery Picker Launcher
    private final ActivityResultLauncher<String> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    handleSelectedPhoto(uri);
                }
            });

    // Camera Capture Launcher
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && tempCameraUri != null) {
                    handleSelectedPhoto(tempCameraUri);
                }
            });

    // Contact Picker Launcher (ContentProvider)
    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();
                    if (contactUri != null) {
                        extractPhoneNumberFromContact(contactUri);
                    }
                }
            });

    // Contacts Permission Request Launcher
    private final ActivityResultLauncher<String> contactsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchContactPicker();
                } else {
                    Toast.makeText(this, "Contacts permission denied. You can still type the number manually.", Toast.LENGTH_SHORT).show();
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

        isEditMode = getIntent().getBooleanExtra(EXTRA_IS_EDIT_MODE, false);
        editProduct = (Product) getIntent().getSerializableExtra(EXTRA_EDIT_PRODUCT);
        if (isEditMode && editProduct != null) {
            setupEditMode(editProduct);
        }

        if (savedInstanceState != null) {
            restoreFormState(savedInstanceState);
        }
    }

    private void setupEditMode(Product p) {
        if (binding.tvHeaderTitle != null) {
            binding.tvHeaderTitle.setText("Edit Product");
        }
        if (binding.tvHeaderSubtitle != null) {
            binding.tvHeaderSubtitle.setText("Update your listing details");
        }
        binding.btnSubmit.setText("Save Changes");

        binding.etProductName.setText(p.getName());
        binding.etRentPerDay.setText(String.valueOf((int) p.getPricePerDay()));
        binding.etMaxRentDays.setText(String.valueOf(p.getMaxRentDays()));
        binding.actvCategory.setText(p.getCategory(), false);
        binding.actvCondition.setText(p.getCondition(), false);
        binding.etModel.setText(p.getModel());
        binding.etDescription.setText(p.getDescription());
        binding.etWhatsappNumber.setText(p.getWhatsappNumber());

        if (p.getFeatures() != null && !p.getFeatures().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String f : p.getFeatures()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(f);
            }
            binding.etFeatures.setText(sb.toString());
        }

        if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) {
            selectedPhotoBase64 = p.getImageUrl();
            com.borrowbox.kuet.rentals.RentalImageUtils.loadImage(binding.ivPhotoPreview, p.getImageUrl());
            binding.ivPhotoPreview.setVisibility(View.VISIBLE);
            binding.btnRemovePhoto.setVisibility(View.VISIBLE);
            binding.layoutPhotoPlaceholder.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("photoBase64", selectedPhotoBase64);
        outState.putString("name", binding.etProductName.getText() != null ? binding.etProductName.getText().toString() : "");
        outState.putString("rent", binding.etRentPerDay.getText() != null ? binding.etRentPerDay.getText().toString() : "");
        outState.putString("maxDays", binding.etMaxRentDays.getText() != null ? binding.etMaxRentDays.getText().toString() : "");
        outState.putString("category", binding.actvCategory.getText() != null ? binding.actvCategory.getText().toString() : "");
        outState.putString("condition", binding.actvCondition.getText() != null ? binding.actvCondition.getText().toString() : "");
        outState.putString("model", binding.etModel.getText() != null ? binding.etModel.getText().toString() : "");
        outState.putString("features", binding.etFeatures.getText() != null ? binding.etFeatures.getText().toString() : "");
        outState.putString("description", binding.etDescription.getText() != null ? binding.etDescription.getText().toString() : "");
        outState.putString("whatsapp", binding.etWhatsappNumber.getText() != null ? binding.etWhatsappNumber.getText().toString() : "");
    }

    private void restoreFormState(Bundle savedInstanceState) {
        selectedPhotoBase64 = savedInstanceState.getString("photoBase64");
        binding.etProductName.setText(savedInstanceState.getString("name", ""));
        binding.etRentPerDay.setText(savedInstanceState.getString("rent", ""));
        binding.etMaxRentDays.setText(savedInstanceState.getString("maxDays", ""));
        binding.actvCategory.setText(savedInstanceState.getString("category", CATEGORIES[0]), false);
        binding.actvCondition.setText(savedInstanceState.getString("condition", "Good"), false);
        binding.etModel.setText(savedInstanceState.getString("model", ""));
        binding.etFeatures.setText(savedInstanceState.getString("features", ""));
        binding.etDescription.setText(savedInstanceState.getString("description", ""));
        binding.etWhatsappNumber.setText(savedInstanceState.getString("whatsapp", ""));

        if (selectedPhotoBase64 != null && !selectedPhotoBase64.isEmpty()) {
            try {
                String clean = selectedPhotoBase64;
                int comma = clean.indexOf(",");
                if (comma != -1) clean = clean.substring(comma + 1);
                byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    binding.ivPhotoPreview.setImageBitmap(bitmap);
                    binding.ivPhotoPreview.setVisibility(View.VISIBLE);
                    binding.btnRemovePhoto.setVisibility(View.VISIBLE);
                    binding.layoutPhotoPlaceholder.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {}
        }
    }

    private void prefillWhatsapp() {
        String phone = sessionManager.getUserPhone();
        if (phone != null && !phone.isEmpty()) {
            binding.etWhatsappNumber.setText(phone);
        }
    }

    private void setupDropdowns() {
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                CATEGORIES
        );
        binding.actvCategory.setAdapter(categoryAdapter);
        binding.actvCategory.setText(CATEGORIES[0], false);

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
        binding.cardPhotoPicker.setOnClickListener(v -> showPhotoSourceDialog());
        binding.btnRemovePhoto.setOnClickListener(v -> removePhoto());
        binding.btnSubmit.setOnClickListener(v -> handleSubmit());
        binding.btnPickContact.setOnClickListener(v -> handlePickContactClick());
    }

    private void showPhotoSourceDialog() {
        String[] options = new String[]{"Choose from Gallery (MediaStore)", "Take Photo with Camera (FileProvider)"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Product Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        photoPickerLauncher.launch("image/*");
                    } else {
                        launchCamera();
                    }
                })
                .show();
    }

    private void launchCamera() {
        try {
            File photoFile = new File(getCacheDir(), "product_temp_" + System.currentTimeMillis() + ".jpg");
            tempCameraUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    photoFile
            );
            cameraLauncher.launch(tempCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "Could not launch camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handlePickContactClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            launchContactPicker();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Contacts Permission Needed")
                        .setMessage("BorrowBox uses your device contacts to let you quickly pick your WhatsApp contact number without typing. Would you like to grant permission?")
                        .setPositiveButton("Grant Permission", (dialog, which) -> contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS))
                        .setNegativeButton("Enter Manually", (dialog, which) -> dialog.dismiss())
                        .show();
            } else {
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
            }
        }
    }

    private void launchContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void extractPhoneNumberFromContact(Uri contactUri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    contactUri,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);

                String rawNumber = cursor.getString(numberIdx);
                String contactName = cursor.getString(nameIdx);

                if (rawNumber != null && !rawNumber.trim().isEmpty()) {
                    String normalized = normalizeBangladeshPhoneNumber(rawNumber);
                    binding.etWhatsappNumber.setText(normalized);
                    Toast.makeText(this, "Selected: " + (contactName != null ? contactName : normalized), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "No phone number found in selected contact.", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to read contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * Normalizes phone numbers into Bangladesh format 01XXXXXXXXX (11 digits)
     */
    private String normalizeBangladeshPhoneNumber(String raw) {
        if (raw == null) return "";
        // Strip out non-digits except leading +
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+880")) {
            digits = digits.substring(4);
        } else if (digits.startsWith("880")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("+88")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("+0")) {
            digits = digits.substring(1);
        }

        if (!digits.startsWith("0")) {
            digits = "0" + digits;
        }

        return digits;
    }

    private void handleSelectedPhoto(Uri uri) {
        // Start Foreground Service to update user with live progress notification
        PhotoUploadForegroundService.startUpload(this, "Optimizing product photo...");

        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap original = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            if (original != null) {
                PhotoUploadForegroundService.updateProgress(this, 50, "Compressing photo to 800px...");

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
                    PhotoUploadForegroundService.failUpload(this, "Image exceeds 500 KB limit");
                    Toast.makeText(this, "Image exceeds 500 KB after compression. Please choose a smaller photo.", Toast.LENGTH_LONG).show();
                    removePhoto();
                    return;
                }
                selectedPhotoBase64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);

                binding.ivPhotoPreview.setImageBitmap(scaled);
                binding.ivPhotoPreview.setVisibility(View.VISIBLE);
                binding.btnRemovePhoto.setVisibility(View.VISIBLE);
                binding.layoutPhotoPlaceholder.setVisibility(View.GONE);

                PhotoUploadForegroundService.completeUpload(this, "Product photo ready!");
            } else {
                PhotoUploadForegroundService.failUpload(this, "Could not decode photo");
            }
        } catch (Exception e) {
            PhotoUploadForegroundService.failUpload(this, "Failed to load photo: " + e.getMessage());
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
        binding.tilProductName.setError(null);
        binding.tilRentPerDay.setError(null);
        binding.tilMaxRentDays.setError(null);
        binding.tilCategory.setError(null);
        binding.tilCondition.setError(null);
        binding.tilWhatsappNumber.setError(null);

        boolean isValid = true;

        String name = binding.etProductName.getText() != null
                ? binding.etProductName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            binding.tilProductName.setError("Product name is required");
            binding.tilProductName.requestFocus();
            isValid = false;
        }

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

        String category = binding.actvCategory.getText() != null
                ? binding.actvCategory.getText().toString().trim() : "";
        if (category.isEmpty()) {
            binding.tilCategory.setError("Please select a category");
            if (isValid) binding.tilCategory.requestFocus();
            isValid = false;
        }

        String condition = binding.actvCondition.getText() != null
                ? binding.actvCondition.getText().toString().trim() : "";
        if (condition.isEmpty()) {
            binding.tilCondition.setError("Please select condition");
            if (isValid) binding.tilCondition.requestFocus();
            isValid = false;
        }

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
            if (isEditMode && editProduct != null) {
                apiClient.updateProduct(editProduct.getId(), payload, token, new ApiClient.ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject result) {
                        setLoading(false);
                        Toast.makeText(AddProductActivity.this, "Product updated successfully!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false);
                        String errorText = errorMessage != null && !errorMessage.isEmpty()
                                ? errorMessage : "Failed to update product. Please try again.";
                        Toast.makeText(AddProductActivity.this, errorText, Toast.LENGTH_LONG).show();
                    }
                });
            } else {
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
            }

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
