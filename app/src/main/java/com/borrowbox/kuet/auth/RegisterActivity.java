package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.borrowbox.kuet.databinding.ActivityRegisterBinding;
import com.borrowbox.kuet.repositories.AuthRepository;
import com.google.firebase.auth.FirebaseUser;

/**
 * Registration screen.
 * Validates:
 *  - All fields non-empty
 *  - Email ends with @stud.kuet.ac.bd  (also enforced in AuthRepository)
 *  - Password ≥ 6 characters
 * On success → EmailVerificationActivity
 */
public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private AuthRepository authRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authRepo = new AuthRepository();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            binding.getRoot().setPadding(0, top, 0, 0);
            return insets;
        });

        binding.btnRegister.setOnClickListener(v -> attemptRegister());

        binding.tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void attemptRegister() {
        String fullName  = getText(binding.etFullName);
        String roll      = getText(binding.etRollNumber);
        String dept      = getText(binding.etDepartment);
        String email     = getText(binding.etRegEmail);
        String password  = getText(binding.etRegPassword);

        if (fullName.isEmpty() || roll.isEmpty() || dept.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters.");
            return;
        }

        setLoading(true);
        authRepo.register(email, password, fullName, roll, dept, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                setLoading(false);
                startActivity(new Intent(RegisterActivity.this, EmailVerificationActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(message);
            }
        });
    }

    private String getText(android.widget.EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void showError(String msg) {
        binding.tvRegError.setText(msg);
        binding.tvRegError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        binding.progressRegister.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!loading);
    }
}
