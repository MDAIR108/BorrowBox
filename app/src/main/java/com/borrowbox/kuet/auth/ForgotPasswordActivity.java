package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.databinding.ActivityForgotPasswordBinding;
import com.borrowbox.kuet.repositories.AuthRepository;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;
    private AuthRepository authRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authRepo = new AuthRepository();

        binding.btnSendReset.setOnClickListener(v -> {
            String email = binding.etForgotEmail.getText() != null
                    ? binding.etForgotEmail.getText().toString().trim() : "";
            if (email.isEmpty()) {
                showError("Please enter your email.");
                return;
            }
            binding.btnSendReset.setEnabled(false);
            authRepo.sendPasswordResetEmail(email, new AuthRepository.VoidCallback() {
                @Override
                public void onSuccess() {
                    binding.btnSendReset.setEnabled(true);
                    hideError();
                    Toast.makeText(ForgotPasswordActivity.this,
                            "Reset link sent! Check your inbox.", Toast.LENGTH_LONG).show();
                    finish();
                }

                @Override
                public void onError(String message) {
                    binding.btnSendReset.setEnabled(true);
                    showError(message);
                }
            });
        });

        binding.btnForgotBack.setOnClickListener(v -> finish());
    }

    private void showError(String msg) {
        binding.tvForgotError.setText(msg);
        binding.tvForgotError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        binding.tvForgotError.setVisibility(View.GONE);
    }
}
