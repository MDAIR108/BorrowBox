package com.borrowbox.kuet.verification;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.auth.WelcomeActivity;
import com.borrowbox.kuet.databinding.ActivityVerificationBinding;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

public class VerificationActivity extends AppCompatActivity {

    private ActivityVerificationBinding binding;
    private SessionManager sessionManager;
    private ApiClient apiClient;
    private CountDownTimer resendTimer;
    private EditText[] otpBoxes;
    private String email;
    private boolean isPasting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVerificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        apiClient = ApiClient.getInstance(this);

        email = getIntent().getStringExtra("email");
        if (email == null || email.isEmpty()) {
            email = sessionManager.getUserEmail();
        }
        if (email == null || email.isEmpty()) {
            email = "student@stud.kuet.ac.bd";
        }
        binding.tvTargetEmail.setText(email);

        setupWindowInsets();
        setupOtpInputs();
        setupButtons();

        boolean autoSend = getIntent().getBooleanExtra("auto_send", false);
        if (autoSend) {
            resendOtpCode();
        } else {
            startResendCountdown(60);
        }
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.verificationRoot, (v, windowInsets) -> {
            Insets statusBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());

            binding.verificationContentContainer.setPadding(
                    binding.verificationContentContainer.getPaddingLeft(),
                    statusBarInsets.top + (int) (16 * getResources().getDisplayMetrics().density),
                    binding.verificationContentContainer.getPaddingRight(),
                    navBarInsets.bottom + (int) (24 * getResources().getDisplayMetrics().density)
            );
            return windowInsets;
        });
    }

    private void setupOtpInputs() {
        otpBoxes = new EditText[]{
                binding.etOtp1,
                binding.etOtp2,
                binding.etOtp3,
                binding.etOtp4,
                binding.etOtp5,
                binding.etOtp6
        };

        for (int i = 0; i < otpBoxes.length; i++) {
            final int index = i;

            otpBoxes[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    binding.tvOtpError.setVisibility(View.GONE);

                    if (isPasting) return;

                    // Support pasting multi-digit code
                    if (s.length() > 1) {
                        isPasting = true;
                        String pasted = s.toString().replaceAll("[^0-9]", "");
                        for (int j = 0; j < otpBoxes.length; j++) {
                            if (j < pasted.length()) {
                                otpBoxes[j].setText(String.valueOf(pasted.charAt(j)));
                            } else {
                                otpBoxes[j].setText("");
                            }
                        }
                        if (pasted.length() >= otpBoxes.length) {
                            otpBoxes[otpBoxes.length - 1].requestFocus();
                            otpBoxes[otpBoxes.length - 1].setSelection(otpBoxes[otpBoxes.length - 1].getText().length());
                        }
                        isPasting = false;
                        return;
                    }

                    if (s.length() == 1 && index < otpBoxes.length - 1) {
                        otpBoxes[index + 1].requestFocus();
                    }
                }
            });

            otpBoxes[i].setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                    if (otpBoxes[index].getText().toString().isEmpty() && index > 0) {
                        otpBoxes[index - 1].requestFocus();
                        otpBoxes[index - 1].setText("");
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private void setupButtons() {
        binding.btnVerifBack.setOnClickListener(v -> finish());

        binding.btnVerifyCode.setOnClickListener(v -> submitVerification());

        binding.btnResendCode.setOnClickListener(v -> resendOtpCode());
    }

    private void submitVerification() {
        StringBuilder sb = new StringBuilder();
        for (EditText box : otpBoxes) {
            sb.append(box.getText().toString().trim());
        }
        String code = sb.toString();

        if (code.length() != 6) {
            showError("Please enter all 6 digits of the verification code.");
            return;
        }

        setLoading(true);

        String token = sessionManager.getToken();
        apiClient.verifyCode(token, code, email, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                setLoading(false);
                sessionManager.setEmailVerified(true);

                // Update SessionManager user details if returned
                JSONObject userObj = response.optJSONObject("user");
                if (userObj != null) {
                    String id = userObj.optString("id", sessionManager.getUserId());
                    String name = userObj.optString("full_name", sessionManager.getUserName());
                    String roll = userObj.optString("roll_no", sessionManager.getUserRoll());
                    String em = userObj.optString("email", sessionManager.getUserEmail());
                    String ph = userObj.optString("phone", sessionManager.getUserPhone());
                    String dept = userObj.optString("department", sessionManager.getUserDept());
                    String hall = userObj.optString("hall", sessionManager.getUserHall());
                    boolean emailVerified = userObj.optBoolean("is_email_verified", true);
                    boolean rollVerified = userObj.optBoolean("is_roll_verified", true);

                    sessionManager.saveSession(token, id, name, roll, em, ph, dept, hall, emailVerified, rollVerified);
                }

                new MaterialAlertDialogBuilder(VerificationActivity.this)
                        .setTitle("Verification Complete! 🎉")
                        .setMessage("Your KUET student identity and email have been verified.\n\nYou now have full access to rent campus items and list your own equipment!")
                        .setPositiveButton("Go to Marketplace", (dialog, which) -> {
                            Intent intent = new Intent(VerificationActivity.this, MainActivity.class);
                            intent.putExtra(WelcomeActivity.EXTRA_IS_GUEST, false);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        })
                        .setCancelable(false)
                        .show();
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                showError(errorMessage);
                // Clear boxes and focus first
                for (EditText box : otpBoxes) {
                    box.setText("");
                }
                otpBoxes[0].requestFocus();
            }
        });
    }

    private void resendOtpCode() {
        setLoading(true);
        String token = sessionManager.getToken();

        apiClient.sendCode(token, email, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                setLoading(false);
                Toast.makeText(VerificationActivity.this, "New verification code sent to " + email, Toast.LENGTH_SHORT).show();
                int cooldown = response.optInt("cooldownSeconds", 60);
                startResendCountdown(cooldown);
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                showError(errorMessage);
            }
        });
    }

    private void startResendCountdown(int seconds) {
        if (resendTimer != null) {
            resendTimer.cancel();
        }

        binding.btnResendCode.setEnabled(false);

        resendTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000;
                binding.btnResendCode.setText("Resend code in " + sec + "s");
            }

            @Override
            public void onFinish() {
                binding.btnResendCode.setEnabled(true);
                binding.btnResendCode.setText("Resend verification code");
            }
        };
        resendTimer.start();
    }

    private void showError(String message) {
        binding.tvOtpError.setText(message);
        binding.tvOtpError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        binding.otpProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnVerifyCode.setEnabled(!loading);
    }

    @Override
    protected void onDestroy() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        super.onDestroy();
    }
}
