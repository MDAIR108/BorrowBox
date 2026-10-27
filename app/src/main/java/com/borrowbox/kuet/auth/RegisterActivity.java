package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.utils.AppConfig;

/**
 * Redirects to unified AuthActivity (Register tab).
 */
public class RegisterActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, AuthActivity.class);
        intent.putExtra(AppConfig.EXTRA_AUTH_TAB, "register");
        startActivity(intent);
        finish();
    }
}
