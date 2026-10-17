package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.databinding.ActivityLoginBinding;
import com.borrowbox.kuet.repositories.AuthRepository;
import com.google.firebase.auth.FirebaseUser;

/**
 * Login screen — KUET student email only.
 * After successful login:
 *   - Email not verified → EmailVerificationActivity
 *   - Email verified     → MainActivity
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AuthRepository authRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authRepo = new AuthRepository();

        // Status bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            binding.getRoot().setPadding(0, top, 0, 0);
            return insets;
        });

        binding.btnLogin.setOnClickListener(v -> attemptLogin());

        binding.etLoginPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { attemptLogin(); return true; }
            return false;
        });

        binding.tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));

        binding.tvGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });

        binding.btnGuestMode.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(WelcomeActivity.EXTRA_IS_GUEST, true);
            startActivity(intent);
            finish();
        });
    }

    private void attemptLogin() {
        String email    = binding.etLoginEmail.getText() != null    ? binding.etLoginEmail.getText().toString().trim()    : "";
        String password = binding.etLoginPassword.getText() != null ? binding.etLoginPassword.getText().toString() : "";

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }

        setLoading(true);

        authRepo.login(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                setLoading(false);
                if (!user.isEmailVerified()) {
                    startActivity(new Intent(LoginActivity.this, EmailVerificationActivity.class));
                } else {
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                }
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(message);
            }
        });
    }

    private void showError(String msg) {
        binding.tvLoginError.setText(msg);
        binding.tvLoginError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        binding.progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!loading);
    }
}
