package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.databinding.ActivityWelcomeBinding;

public class WelcomeActivity extends AppCompatActivity {

    public static final String EXTRA_IS_GUEST = "extra_is_guest";

    private ActivityWelcomeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupListeners();
    }

    private void setupListeners() {
        binding.btnLogIn.setOnClickListener(v -> {
            Toast.makeText(this, "Log In will be available in Phase 2 (Authentication)", Toast.LENGTH_SHORT).show();
            // In Phase 1, navigate to MainActivity for demonstration
            navigateToMain(false);
        });

        binding.btnRegister.setOnClickListener(v -> {
            Toast.makeText(this, "Registration will be available in Phase 2 (Authentication)", Toast.LENGTH_SHORT).show();
            // In Phase 1, navigate to MainActivity for demonstration
            navigateToMain(false);
        });

        binding.btnContinueAsGuest.setOnClickListener(v -> {
            Toast.makeText(this, "Browsing BorrowBox in Guest Mode", Toast.LENGTH_SHORT).show();
            navigateToMain(true);
        });
    }

    private void navigateToMain(boolean isGuest) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(EXTRA_IS_GUEST, isGuest);
        startActivity(intent);
        finish();
    }
}
