package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.databinding.ActivityWelcomeBinding;
import com.borrowbox.kuet.networking.SessionManager;
import com.borrowbox.kuet.utils.AppConfig;

/**
 * Welcome / landing screen.
 * Routes to unified AuthActivity (Login/Register) or guest mode.
 */
public class WelcomeActivity extends AppCompatActivity {

    public static final String EXTRA_IS_GUEST = "extra_is_guest";

    private ActivityWelcomeBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        binding.btnLogIn.setOnClickListener(v -> {
            Intent intent = new Intent(this, AuthActivity.class);
            intent.putExtra(AppConfig.EXTRA_AUTH_TAB, "login");
            startActivity(intent);
        });

        binding.btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, AuthActivity.class);
            intent.putExtra(AppConfig.EXTRA_AUTH_TAB, "register");
            startActivity(intent);
        });

        binding.btnContinueAsGuest.setOnClickListener(v -> {
            sessionManager.setGuest(true);
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(EXTRA_IS_GUEST, true);
            startActivity(intent);
            finish();
        });

        // Hidden developer shortcut to change API Server URL
        View.OnLongClickListener devListener = v -> {
            com.borrowbox.kuet.utils.ServerConfigDialog.show(this, null);
            return true;
        };
        binding.ivLogo.setOnLongClickListener(devListener);
        binding.tvAppName.setOnLongClickListener(devListener);
    }
}
