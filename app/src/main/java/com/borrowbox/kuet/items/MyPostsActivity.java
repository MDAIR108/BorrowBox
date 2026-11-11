package com.borrowbox.kuet.items;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.borrowbox.kuet.base.BaseActivity;
import com.borrowbox.kuet.databinding.ActivityMyPostsBinding;
import com.borrowbox.kuet.models.Product;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MyPostsActivity extends BaseActivity implements MyPostsAdapter.OnPostActionListener {

    private ActivityMyPostsBinding binding;
    private ApiClient apiClient;
    private SessionManager sessionManager;
    private MyPostsAdapter adapter;

    private final ActivityResultLauncher<Intent> editProductLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    fetchMyPosts();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        binding = ActivityMyPostsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiClient = ApiClient.getInstance(this);
        sessionManager = new SessionManager(this);

        setupWindowInsets();
        setupRecyclerView();
        setupListeners();

        fetchMyPosts();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorRoot, (v, windowInsets) -> {
            Insets statusBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());

            binding.headerLayout.setPadding(
                    binding.headerLayout.getPaddingLeft(),
                    statusBarInsets.top + (int) (12 * getResources().getDisplayMetrics().density),
                    binding.headerLayout.getPaddingRight(),
                    (int) (12 * getResources().getDisplayMetrics().density)
            );

            binding.recyclerViewPosts.setPadding(
                    0,
                    (int) (8 * getResources().getDisplayMetrics().density),
                    0,
                    navBarInsets.bottom + (int) (24 * getResources().getDisplayMetrics().density)
            );

            return windowInsets;
        });
    }

    private void setupRecyclerView() {
        adapter = new MyPostsAdapter(this, this);
        binding.recyclerViewPosts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewPosts.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        View.OnClickListener addClickListener = v -> {
            Intent intent = new Intent(MyPostsActivity.this, AddProductActivity.class);
            editProductLauncher.launch(intent);
        };

        binding.btnAddNewPost.setOnClickListener(addClickListener);
        binding.btnEmptyAddProduct.setOnClickListener(addClickListener);

        binding.swipeRefreshLayout.setOnRefreshListener(this::fetchMyPosts);
        binding.btnRetry.setOnClickListener(v -> fetchMyPosts());
    }

    private void fetchMyPosts() {
        String token = sessionManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            showError("Please log in to view your posts.");
            return;
        }

        if (!binding.swipeRefreshLayout.isRefreshing()) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }
        binding.layoutErrorState.setVisibility(View.GONE);

        apiClient.getMyProducts(token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefreshLayout.setRefreshing(false);
                parsePosts(response);
            }

            @Override
            public void onError(String errorMessage) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefreshLayout.setRefreshing(false);
                if (adapter.getItemCount() == 0) {
                    showError(errorMessage);
                } else {
                    Toast.makeText(MyPostsActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void parsePosts(JSONObject response) {
        try {
            JSONArray arr = response.optJSONArray("products");
            if (arr == null) arr = new JSONArray();

            List<Product> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.optString("id", "");
                String name = obj.optString("name", obj.optString("title", "Product"));
                double rent = obj.optDouble("rentPerDay", obj.optDouble("pricePerDay", 0.0));
                int maxDays = obj.optInt("maxRentDays", 7);
                String category = obj.optString("category", "Others");
                String condition = obj.optString("condition", "Good");
                String model = obj.optString("model", "");
                String desc = obj.optString("description", "");
                String whatsapp = obj.optString("whatsappNumber", "");
                String img = obj.optString("imageUrl", obj.optString("photoUrl", ""));
                boolean isAvail = obj.optBoolean("available", obj.optBoolean("isAvailable", true));

                Product p = new Product(
                        id,
                        name,
                        rent,
                        rent == 0,
                        isAvail,
                        category,
                        obj.optString("department", "KUET"),
                        obj.optString("hallLocation", "Campus"),
                        obj.optString("ownerName", sessionManager.getUserName()),
                        sessionManager.getUserId(),
                        desc,
                        whatsapp,
                        obj.optString("emoji", "📦"),
                        img
                );
                p.setMaxRentDays(maxDays);
                p.setModel(model);
                p.setCondition(condition);

                JSONArray feats = obj.optJSONArray("features");
                if (feats != null) {
                    List<String> featList = new ArrayList<>();
                    for (int f = 0; f < feats.length(); f++) {
                        featList.add(feats.optString(f));
                    }
                    p.setFeatures(featList);
                }

                list.add(p);
            }

            adapter.setPosts(list);

            if (list.isEmpty()) {
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
                binding.recyclerViewPosts.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyState.setVisibility(View.GONE);
                binding.recyclerViewPosts.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            showError("Error parsing posts: " + e.getMessage());
        }
    }

    private void showError(String message) {
        binding.layoutErrorState.setVisibility(View.VISIBLE);
        binding.tvErrorMessage.setText(message);
        binding.layoutEmptyState.setVisibility(View.GONE);
        binding.recyclerViewPosts.setVisibility(View.GONE);
    }

    @Override
    public void onItemClick(Product product) {
        Intent intent = new Intent(this, ProductDetailsActivity.class);
        intent.putExtra("product", product);
        startActivity(intent);
    }

    @Override
    public void onEditClick(Product product) {
        Intent intent = new Intent(this, AddProductActivity.class);
        intent.putExtra(AddProductActivity.EXTRA_EDIT_PRODUCT, product);
        intent.putExtra(AddProductActivity.EXTRA_IS_EDIT_MODE, true);
        editProductLauncher.launch(intent);
    }

    @Override
    public void onDeleteClick(Product product) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this post?")
                .setMessage("Are you sure you want to delete \"" + product.getName() + "\"?\n\nIf this product has any pending rental requests, they will be automatically cancelled and the renters notified.")
                .setPositiveButton("Delete", (dialog, which) -> executeDelete(product))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeDelete(Product product) {
        binding.progressBar.setVisibility(View.VISIBLE);
        apiClient.deleteProduct(product.getId(), sessionManager.getToken(), new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(MyPostsActivity.this, "Post deleted successfully", Toast.LENGTH_SHORT).show();
                adapter.removePost(product);
                if (adapter.getItemCount() == 0) {
                    binding.layoutEmptyState.setVisibility(View.VISIBLE);
                    binding.recyclerViewPosts.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String errorMessage) {
                binding.progressBar.setVisibility(View.GONE);
                new MaterialAlertDialogBuilder(MyPostsActivity.this)
                        .setTitle("Delete Failed")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }
}
