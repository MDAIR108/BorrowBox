package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.databinding.ActivityEmailVerificationBinding;
import com.borrowbox.kuet.repositories.AuthRepository;
import com.google.firebase.auth.FirebaseUser;

/**
 * Shown after registration (or if user logs in with unverified email).
 * User must click the email link, then tap "I've Verified — Continue".
 * We reload the Firebase user to check isEmailVerified().
 */
public class EmailVerificationActivity extends AppCompatActivity {

    private ActivityEmailVerificationBinding binding;
    private AuthRepository authRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEmailVerificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authRepo = new AuthRepository();

        FirebaseUser user = authRepo.getCurrentUser();
        if (user != null) {
            binding.tvVerifEmail.setText(user.getEmail());
        }

        // "I've Verified" — reload user and check
        binding.btnVerifContinue.setOnClickListener(v -> {
            FirebaseUser current = authRepo.getCurrentUser();
            if (current == null) {
                navigateToWelcome();
                return;
            }
            // Reload to get fresh token from Firebase
            current.reload().addOnCompleteListener(task -> {
                if (current.isEmailVerified()) {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                } else {
                    Toast.makeText(this,
                            "Email not verified yet. Check your inbox (and spam folder).",
                            Toast.LENGTH_LONG).show();
                }
            });
        });

        // Resend email
        binding.btnVerifResend.setOnClickListener(v ->
                authRepo.resendVerificationEmail(new AuthRepository.VoidCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(EmailVerificationActivity.this,
                                "Verification email sent!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(EmailVerificationActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }));

        // Sign out
        binding.btnVerifLogout.setOnClickListener(v -> {
            authRepo.signOut();
            navigateToWelcome();
        });
    }

    private void navigateToWelcome() {
        startActivity(new Intent(this, WelcomeActivity.class));
        finish();
    }
}
