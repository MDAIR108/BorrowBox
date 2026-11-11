package com.borrowbox.kuet.base;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

public abstract class BaseActivity extends AppCompatActivity {

    private BroadcastReceiver systemEventReceiver;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Snackbar currentSnackbar;
    private boolean isReceiverRegistered = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerSystemReceivers();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterSystemReceivers();
    }

    private void registerSystemReceivers() {
        if (isReceiverRegistered) return;

        systemEventReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;

                String action = intent.getAction();

                if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                    boolean isAirplaneModeOn = intent.getBooleanExtra("state", false) ||
                            Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
                    if (isAirplaneModeOn) {
                        showSystemNotice("✈️ Airplane mode is on. Network is offline.", false);
                    } else {
                        dismissSystemNotice();
                    }
                } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                    checkAndNotifyConnectivity();
                } else if (Intent.ACTION_BATTERY_LOW.equals(action)) {
                    showSystemNotice("🪫 Battery is low. Connect charger to continue renting.", false);
                } else if (Intent.ACTION_BATTERY_OKAY.equals(action)) {
                    dismissSystemNotice();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(systemEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(systemEventReceiver, filter);
            }
            isReceiverRegistered = true;
        } catch (Exception ignored) {}

        // Modern NetworkCallback monitoring for API 24+
        if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onLost(@NonNull Network network) {
                        runOnUiThread(() -> showSystemNotice("📡 You are offline. Check internet connection.", true));
                    }

                    @Override
                    public void onAvailable(@NonNull Network network) {
                        runOnUiThread(() -> dismissSystemNotice());
                    }
                };
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                connectivityManager.registerNetworkCallback(request, networkCallback);
            } catch (Exception ignored) {}
        }
    }

    private void unregisterSystemReceivers() {
        if (isReceiverRegistered && systemEventReceiver != null) {
            try {
                unregisterReceiver(systemEventReceiver);
            } catch (IllegalArgumentException ignored) {}
            isReceiverRegistered = false;
        }

        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
            networkCallback = null;
        }

        dismissSystemNotice();
    }

    private void checkAndNotifyConnectivity() {
        if (connectivityManager == null) return;
        boolean isConnected = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
                    isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                }
            } else {
                android.net.NetworkInfo info = connectivityManager.getActiveNetworkInfo();
                isConnected = info != null && info.isConnected();
            }
        } catch (Exception ignored) {}

        if (!isConnected) {
            showSystemNotice("📡 You are offline. Check internet connection.", true);
        } else {
            dismissSystemNotice();
        }
    }

    public void showSystemNotice(String message, boolean persistent) {
        View rootView = findViewById(android.R.id.content);
        if (rootView == null) return;

        if (currentSnackbar != null && currentSnackbar.isShown()) {
            currentSnackbar.setText(message);
            return;
        }

        currentSnackbar = Snackbar.make(
                rootView,
                message,
                persistent ? Snackbar.LENGTH_INDEFINITE : Snackbar.LENGTH_LONG
        );
        currentSnackbar.setBackgroundTint(Color.parseColor("#48426D"));
        currentSnackbar.setTextColor(Color.parseColor("#F0C38E"));

        View snackbarView = currentSnackbar.getView();
        TextView tv = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setMaxLines(3);
            tv.setTextSize(13f);
        }

        if (persistent) {
            currentSnackbar.setAction("Dismiss", v -> currentSnackbar.dismiss());
            currentSnackbar.setActionTextColor(Color.parseColor("#FFFFFF"));
        }

        currentSnackbar.show();
    }

    public void dismissSystemNotice() {
        if (currentSnackbar != null && currentSnackbar.isShown()) {
            currentSnackbar.dismiss();
            currentSnackbar = null;
        }
    }
}
