package com.borrowbox.kuet.items;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ActivityFullScreenPhotoBinding;
import com.borrowbox.kuet.rentals.RentalImageUtils;

public class FullScreenPhotoActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "extra_image_url";
    public static final String EXTRA_TITLE = "extra_title";

    private ActivityFullScreenPhotoBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        binding = ActivityFullScreenPhotoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootFullScreenPhoto, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            binding.btnClosePhoto.setTranslationY(insets.top);
            return windowInsets;
        });

        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        if (title != null && !title.trim().isEmpty()) {
            binding.tvPhotoTitle.setText(title);
            binding.tvPhotoTitle.setVisibility(View.VISIBLE);
        } else {
            binding.tvPhotoTitle.setVisibility(View.GONE);
        }

        binding.btnClosePhoto.setOnClickListener(v -> finish());

        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            RentalImageUtils.loadBitmap(imageUrl, bitmap -> {
                if (bitmap != null) {
                    binding.zoomImageView.setImageBitmap(bitmap);
                    binding.zoomImageView.fitImageToCenter();
                } else {
                    binding.zoomImageView.setImageResource(R.drawable.ic_box);
                    binding.zoomImageView.fitImageToCenter();
                }
            });
        } else {
            binding.zoomImageView.setImageResource(R.drawable.ic_box);
            binding.zoomImageView.fitImageToCenter();
        }
    }
}
