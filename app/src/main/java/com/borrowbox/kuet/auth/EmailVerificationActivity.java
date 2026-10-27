package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.verification.VerificationActivity;

/**
 * Delegates to modern VerificationActivity (TASK 6).
 */
public class EmailVerificationActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, VerificationActivity.class);
        startActivity(intent);
        finish();
    }
}
