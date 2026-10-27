package com.borrowbox.kuet.utils;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.networking.ApiClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

public class ServerConfigDialog {

    public interface OnUrlConfiguredListener {
        void onConfigured(String newUrl);
    }

    public static void show(Context context, OnUrlConfiguredListener listener) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        TextView tvHint = new TextView(context);
        tvHint.setText("Enter your PC's IP address and port 5050. Phone and PC must be connected to the same Wi-Fi.");
        tvHint.setTextColor(ContextCompat.getColor(context, R.color.light_text_secondary));
        tvHint.setTextSize(13);
        layout.addView(tvHint);

        TextInputLayout til = new TextInputLayout(context);
        til.setHint("Server API URL");
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        LinearLayout.LayoutParams lpTil = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lpTil.topMargin = (int) (12 * context.getResources().getDisplayMetrics().density);
        til.setLayoutParams(lpTil);

        TextInputEditText etUrl = new TextInputEditText(til.getContext());
        etUrl.setText(AppConfig.API_BASE_URL);
        etUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        til.addView(etUrl);
        layout.addView(til);

        TextView tvStatus = new TextView(context);
        tvStatus.setTextSize(12);
        tvStatus.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpStatus = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lpStatus.topMargin = (int) (8 * context.getResources().getDisplayMetrics().density);
        tvStatus.setLayoutParams(lpStatus);
        layout.addView(tvStatus);

        TextView tvPresets = new TextView(context);
        tvPresets.setText("Quick presets:");
        tvPresets.setTextSize(11);
        tvPresets.setTextColor(ContextCompat.getColor(context, R.color.light_text_secondary));
        LinearLayout.LayoutParams lpPresets = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lpPresets.topMargin = (int) (10 * context.getResources().getDisplayMetrics().density);
        tvPresets.setLayoutParams(lpPresets);
        layout.addView(tvPresets);

        LinearLayout presetsRow = new LinearLayout(context);
        presetsRow.setOrientation(LinearLayout.HORIZONTAL);
        presetsRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Preset 1: Detected PC Wi-Fi LAN
        MaterialButton btnLan = new MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle);
        btnLan.setText("LAN Wi-Fi");
        btnLan.setTextSize(11);
        btnLan.setOnClickListener(v -> etUrl.setText("http://10.40.35.181:5050"));
        presetsRow.addView(btnLan);

        // Preset 2: USB adb reverse
        MaterialButton btnUsb = new MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle);
        btnUsb.setText("USB (adb)");
        btnUsb.setTextSize(11);
        btnUsb.setOnClickListener(v -> etUrl.setText("http://127.0.0.1:5050"));
        presetsRow.addView(btnUsb);

        // Preset 3: Android Emulator
        MaterialButton btnEmu = new MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle);
        btnEmu.setText("Emulator");
        btnEmu.setTextSize(11);
        btnEmu.setOnClickListener(v -> etUrl.setText("http://10.0.2.2:5050"));
        presetsRow.addView(btnEmu);

        layout.addView(presetsRow);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("⚙️ Server Configuration")
                .setView(layout)
                .setPositiveButton("Save", null)
                .setNeutralButton("Test", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            MaterialButton btnPositive = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            MaterialButton btnNeutral = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

            btnPositive.setOnClickListener(v -> {
                String inputUrl = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
                if (inputUrl.isEmpty()) {
                    til.setError("Server URL cannot be empty");
                    return;
                }
                AppConfig.setServerUrl(inputUrl, context);
                Toast.makeText(context, "Server URL saved: " + AppConfig.API_BASE_URL, Toast.LENGTH_SHORT).show();
                if (listener != null) {
                    listener.onConfigured(AppConfig.API_BASE_URL);
                }
                dialog.dismiss();
            });

            btnNeutral.setOnClickListener(v -> {
                String testUrl = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
                if (testUrl.isEmpty()) {
                    til.setError("Enter a URL to test");
                    return;
                }
                til.setError(null);
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setTextColor(ContextCompat.getColor(context, R.color.light_text_secondary));
                tvStatus.setText("Testing " + testUrl + "/health ...");

                String previousUrl = AppConfig.API_BASE_URL;
                AppConfig.setServerUrl(testUrl, null);
                ApiClient.getInstance(context).checkHealth(new ApiClient.ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject result) {
                        tvStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_green));
                        boolean db = result.optBoolean("db", false);
                        boolean smtp = result.optBoolean("smtp", false);
                        tvStatus.setText("✅ Connected! DB: " + (db ? "OK" : "Offline") + ", SMTP: " + (smtp ? "OK" : "Console mode"));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        tvStatus.setTextColor(ContextCompat.getColor(context, R.color.pink_supporting));
                        tvStatus.setText("❌ " + errorMessage);
                        AppConfig.API_BASE_URL = previousUrl;
                    }
                });
            });
        });

        dialog.show();
    }
}
