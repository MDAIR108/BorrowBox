package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.databinding.ActivityWelcomeBinding;

/**
 * Welcome / landing screen.
 * Routes to real LoginActivity / RegisterActivity or guest mode.
 */
public class WelcomeActivity extends AppCompatActivity {

    public static final String EXTRA_IS_GUEST = "extra_is_guest";

    private ActivityWelcomeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogIn.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        binding.btnRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        binding.btnContinueAsGuest.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(EXTRA_IS_GUEST, true);
            startActivity(intent);
            finish();
        });
    }
}
