package com.borrowbox.kuet.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ActivityAuthBinding;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.borrowbox.kuet.utils.AppConfig;
import com.borrowbox.kuet.verification.VerificationActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class AuthActivity extends AppCompatActivity {

    private ActivityAuthBinding binding;
    private SessionManager sessionManager;
    private ApiClient apiClient;
    private boolean isLoginTab = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        apiClient = ApiClient.getInstance(this);

        setupWindowInsets();
        setupTabs();
        setupRollWatcher();
        setupButtons();
        setupServerBanner();
        setupDeveloperShortcuts();

        // Check if intent specified a default tab
        String tab = getIntent().getStringExtra(AppConfig.EXTRA_AUTH_TAB);
        if ("register".equalsIgnoreCase(tab)) {
            selectTab(false);
        } else {
            selectTab(true);
        }

        checkServerConnection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkServerConnection();
    }

    private void setupServerBanner() {
        binding.btnBannerRetry.setOnClickListener(v -> checkServerConnection());
        binding.btnBannerChangeUrl.setOnClickListener(v -> showServerConfigDialog());
    }

    private void setupDeveloperShortcuts() {
        View.OnLongClickListener devListener = v -> {
            showServerConfigDialog();
            return true;
        };
        binding.ivAuthLogo.setOnLongClickListener(devListener);
        binding.tvAuthBrand.setOnLongClickListener(devListener);
    }

    /**
     * Checks server reachability via GET /health before user attempts login/register.
     */
    private void checkServerConnection() {
        apiClient.checkHealth(new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                binding.cardServerBanner.setVisibility(View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                binding.cardServerBanner.setVisibility(View.VISIBLE);
                binding.tvServerBannerMessage.setText("Cannot reach server at " + AppConfig.API_BASE_URL);
                binding.tvServerBannerHint.setText("Tap 'Change Server URL' or ensure your PC backend is running.");
            }
        });
    }

    /**
     * Hidden developer dialog allowing the user to view/modify the API Base URL persistently.
     */
    private void showServerConfigDialog() {
        com.borrowbox.kuet.utils.ServerConfigDialog.show(this, url -> checkServerConnection());
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.authRoot, (v, windowInsets) -> {
            Insets statusBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());

            binding.authContentContainer.setPadding(
                    binding.authContentContainer.getPaddingLeft(),
                    statusBarInsets.top + (int) (16 * getResources().getDisplayMetrics().density),
                    binding.authContentContainer.getPaddingRight(),
                    navBarInsets.bottom + (int) (24 * getResources().getDisplayMetrics().density)
            );
            return windowInsets;
        });
    }

    private void setupTabs() {
        binding.btnTabLogin.setOnClickListener(v -> selectTab(true));
        binding.btnTabRegister.setOnClickListener(v -> selectTab(false));
    }

    private void selectTab(boolean login) {
        isLoginTab = login;
        if (login) {
            // Login Active
            binding.btnTabLogin.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.peach_accent));
            binding.btnTabLogin.setTextColor(
                    ContextCompat.getColor(this, R.color.dark_text));

            // Register Inactive
            binding.btnTabRegister.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, android.R.color.transparent));
            binding.btnTabRegister.setTextColor(
                    ContextCompat.getColor(this, R.color.light_text_secondary));

            binding.loginFormContainer.setVisibility(View.VISIBLE);
            binding.registerFormContainer.setVisibility(View.GONE);
        } else {
            // Register Active
            binding.btnTabRegister.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.peach_accent));
            binding.btnTabRegister.setTextColor(
                    ContextCompat.getColor(this, R.color.dark_text));

            // Login Inactive
            binding.btnTabLogin.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, android.R.color.transparent));
            binding.btnTabLogin.setTextColor(
                    ContextCompat.getColor(this, R.color.light_text_secondary));

            binding.loginFormContainer.setVisibility(View.GONE);
            binding.registerFormContainer.setVisibility(View.VISIBLE);
        }
    }

    /** Helper: Auto-detects department from 7-digit KUET roll and suggests student email */
    private void setupRollWatcher() {
        binding.etRegRoll.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String roll = s.toString().trim();
                if (roll.length() == 7) {
                    AppConfig.RollValidationResult res = AppConfig.validateKuetRoll(roll);
                    if (res.isValid) {
                        binding.tilRegRoll.setError(null);
                        binding.tilRegRoll.setHelperText("Valid KUET roll: " + res.departmentName);
                        // Auto-fill dept if empty
                        if (binding.etRegDept.getText().toString().trim().isEmpty()) {
                            binding.etRegDept.setText(res.departmentName);
                        }
                        // Auto-fill student email if empty
                        if (binding.etRegEmail.getText().toString().trim().isEmpty()) {
                            binding.etRegEmail.setText(roll + "@stud.kuet.ac.bd");
                        }
                    } else {
                        binding.tilRegRoll.setError(res.errorMessage);
                    }
                } else if (roll.length() > 0 && roll.length() < 7) {
                    binding.tilRegRoll.setHelperText((7 - roll.length()) + " more digit(s) needed");
                } else {
                    binding.tilRegRoll.setError(null);
                    binding.tilRegRoll.setHelperText("7 digits: batch(2) + dept(2) + serial(3)");
                }
            }
        });
    }

    private void setupButtons() {
        binding.btnSubmitLogin.setOnClickListener(v -> handleLogin());
        binding.btnSubmitRegister.setOnClickListener(v -> handleRegister());
        binding.btnAuthGuest.setOnClickListener(v -> {
            sessionManager.setGuest(true);
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(WelcomeActivity.EXTRA_IS_GUEST, true);
            startActivity(intent);
            finish();
        });
    }

    private void handleLogin() {
        String identifier = binding.etLoginIdentifier.getText().toString().trim();
        String password = binding.etLoginPassword.getText().toString().trim();

        if (identifier.isEmpty()) {
            binding.tilLoginIdentifier.setError("KUET email or roll number required");
            return;
        } else {
            binding.tilLoginIdentifier.setError(null);
        }

        if (password.isEmpty()) {
            binding.tilLoginPassword.setError("Password required");
            return;
        } else {
            binding.tilLoginPassword.setError(null);
        }

        setLoading(true);

        apiClient.login(identifier, password, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                setLoading(false);
                try {
                    String token = response.optString("token", "");
                    JSONObject userObj = response.optJSONObject("user");
                    if (userObj != null) {
                        String id = userObj.optString("id", "");
                        String name = userObj.optString("full_name", "");
                        String roll = userObj.optString("roll_no", "");
                        String email = userObj.optString("email", "");
                        String phone = userObj.optString("phone", "");
                        String dept = userObj.optString("department", "");
                        String hall = userObj.optString("hall", "");
                        boolean emailVerified = userObj.optBoolean("is_email_verified", false);
                        boolean rollVerified = userObj.optBoolean("is_roll_verified", false);

                        sessionManager.saveSession(token, id, name, roll, email, phone, dept, hall, emailVerified, rollVerified);
                    }

                    Toast.makeText(AuthActivity.this, "Welcome back, " + sessionManager.getUserName() + "!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(AuthActivity.this, MainActivity.class);
                    intent.putExtra(WelcomeActivity.EXTRA_IS_GUEST, false);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } catch (Exception e) {
                    Toast.makeText(AuthActivity.this, "Login parsing error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                Toast.makeText(AuthActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleRegister() {
        String fullName = binding.etRegFullName.getText().toString().trim();
        String roll = binding.etRegRoll.getText().toString().trim();
        String email = binding.etRegEmail.getText().toString().trim();
        String phone = binding.etRegPhone.getText().toString().trim();
        String dept = binding.etRegDept.getText().toString().trim();
        String hall = binding.etRegHall.getText().toString().trim();
        String password = binding.etRegPassword.getText().toString().trim();
        String confirmPassword = binding.etRegConfirmPassword.getText().toString().trim();

        if (fullName.isEmpty()) {
            binding.tilRegFullName.setError("Full name is required");
            return;
        } else {
            binding.tilRegFullName.setError(null);
        }

        // KUET Roll Validation (TASK 6)
        AppConfig.RollValidationResult rollCheck = AppConfig.validateKuetRoll(roll);
        if (!rollCheck.isValid) {
            binding.tilRegRoll.setError(rollCheck.errorMessage);
            return;
        } else {
            binding.tilRegRoll.setError(null);
        }

        if (email.isEmpty()) {
            binding.tilRegEmail.setError("Email address is required");
            return;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilRegEmail.setError("Please enter a valid email address");
            return;
        } else {
            String lowerEmail = email.toLowerCase();
            if (lowerEmail.endsWith("@stud.kuet.ac.bd")) {
                String localPart = lowerEmail.substring(0, lowerEmail.indexOf('@'));
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(localPart);
                if (m.find()) {
                    String emailDigits = m.group();
                    if (!emailDigits.equals(roll)) {
                        binding.tilRegEmail.setError("Roll in student email (" + emailDigits + ") must match entered roll (" + roll + ")");
                        return;
                    }
                }
            }
            binding.tilRegEmail.setError(null);
        }

        if (phone.isEmpty()) {
            binding.tilRegPhone.setError("Phone number required");
            return;
        } else {
            binding.tilRegPhone.setError(null);
        }

        if (dept.isEmpty()) {
            binding.tilRegDept.setError("Department required");
            return;
        } else {
            binding.tilRegDept.setError(null);
        }

        if (hall.isEmpty()) {
            binding.tilRegHall.setError("Hall required");
            return;
        } else {
            binding.tilRegHall.setError(null);
        }

        if (password.length() < 6) {
            binding.tilRegPassword.setError("Password must be at least 6 characters");
            return;
        } else {
            binding.tilRegPassword.setError(null);
        }

        if (!password.equals(confirmPassword)) {
            binding.tilRegConfirmPassword.setError("Passwords do not match");
            return;
        } else {
            binding.tilRegConfirmPassword.setError(null);
        }

        setLoading(true);

        try {
            JSONObject payload = new JSONObject();
            payload.put("full_name", fullName);
            payload.put("roll_no", roll);
            payload.put("email", email);
            payload.put("phone", phone);
            payload.put("department", dept);
            payload.put("hall", hall);
            payload.put("password", password);
            payload.put("confirm_password", confirmPassword);

            apiClient.register(payload, new ApiClient.ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject response) {
                    setLoading(false);
                    try {
                        String token = response.optString("token", "");
                        JSONObject userObj = response.optJSONObject("user");
                        if (userObj != null) {
                            String id = userObj.optString("id", "");
                            String name = userObj.optString("full_name", fullName);
                            String r = userObj.optString("roll_no", roll);
                            String em = userObj.optString("email", email);
                            String ph = userObj.optString("phone", phone);
                            String d = userObj.optString("department", dept);
                            String h = userObj.optString("hall", hall);
                            boolean emailVerified = userObj.optBoolean("is_email_verified", false);
                            boolean rollVerified = userObj.optBoolean("is_roll_verified", true);

                            // User is logged in immediately (TASK 4)
                            sessionManager.saveSession(token, id, name, r, em, ph, d, h, emailVerified, rollVerified);
                        }

                        Toast.makeText(AuthActivity.this, "Account created! Verification code sent.", Toast.LENGTH_LONG).show();

                        // Sent straight to the verification step without second login (TASK 4)
                        Intent intent = new Intent(AuthActivity.this, VerificationActivity.class);
                        intent.putExtra("email", email);
                        intent.putExtra("auto_send", true);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } catch (Exception e) {
                        Toast.makeText(AuthActivity.this, "Registration parsing error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    setLoading(false);
                    Toast.makeText(AuthActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        } catch (JSONException e) {
            setLoading(false);
            Toast.makeText(this, "JSON error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setLoading(boolean loading) {
        binding.authProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSubmitLogin.setEnabled(!loading);
        binding.btnSubmitRegister.setEnabled(!loading);
        binding.btnTabLogin.setEnabled(!loading);
        binding.btnTabRegister.setEnabled(!loading);
    }
}
