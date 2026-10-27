package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.databinding.ActivitySplashBinding;
import com.borrowbox.kuet.networking.SessionManager;

/**
 * Launcher activity.
 * Keeps user session after app restart (TASK 4).
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        new Handler(Looper.getMainLooper()).postDelayed(this::routeUser, SPLASH_DELAY_MS);
    }

    private void routeUser() {
        try {
            SessionManager session = new SessionManager(this);

            if (session.isLoggedIn()) {
                // User is authenticated, route to MainActivity
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra(WelcomeActivity.EXTRA_IS_GUEST, false);
                startActivity(intent);
            } else if (session.isGuest()) {
                // Guest mode
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra(WelcomeActivity.EXTRA_IS_GUEST, true);
                startActivity(intent);
            } else {
                // Not logged in → Welcome screen
                startActivity(new Intent(this, WelcomeActivity.class));
            }
        } catch (Throwable t) {
            startActivity(new Intent(this, WelcomeActivity.class));
        }
        finish();
    }
}
