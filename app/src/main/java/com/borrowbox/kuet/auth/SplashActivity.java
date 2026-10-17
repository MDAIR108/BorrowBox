package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.databinding.ActivitySplashBinding;
import com.borrowbox.kuet.repositories.AuthRepository;
import com.google.firebase.auth.FirebaseUser;

/**
 * Launcher activity. Replaces WelcomeActivity as the MAIN entry point.
 *
 * Logic:
 *  - Show splash for 1.2 seconds
 *  - If user is logged in AND email is verified → go to MainActivity
 *  - If user is logged in but email NOT verified → go to EmailVerificationActivity
 *  - If no user → go to WelcomeActivity
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 1200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        new Handler(Looper.getMainLooper()).postDelayed(this::routeUser, SPLASH_DELAY_MS);
    }

    private void routeUser() {
        try {
            AuthRepository auth = new AuthRepository();
            FirebaseUser user = auth.getCurrentUser();

            if (user == null) {
                // Not logged in → Welcome screen
                startActivity(new Intent(this, WelcomeActivity.class));
            } else if (!user.isEmailVerified()) {
                // Logged in but not verified → Email verification gate
                startActivity(new Intent(this, EmailVerificationActivity.class));
            } else {
                // Fully authenticated → Main marketplace
                startActivity(new Intent(this, MainActivity.class));
            }
        } catch (Throwable t) {
            // Safe fallback if Firebase or storage has any issue on launch
            startActivity(new Intent(this, WelcomeActivity.class));
        }
        finish();
    }
}
