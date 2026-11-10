package com.borrowbox.kuet.utils;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.R;
import com.borrowbox.kuet.auth.AuthActivity;
import com.borrowbox.kuet.items.AddProductActivity;
import com.borrowbox.kuet.networking.SessionManager;
import com.borrowbox.kuet.profile.ProfileActivity;
import com.borrowbox.kuet.rentals.MyRentalsActivity;
import com.borrowbox.kuet.search.SearchActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class BottomNavHelper {

    public static void setupBottomNav(final Activity activity,
                                      final BottomNavigationView bottomNav,
                                      final View fabAdd,
                                      final int currentTabId) {
        if (bottomNav == null) return;

        // Explicitly set colors from nav_item_color selector
        bottomNav.setItemIconTintList(activity.getColorStateList(R.color.nav_item_color));
        bottomNav.setItemTextColor(activity.getColorStateList(R.color.nav_item_color));

        // Highlight the current tab without triggering navigation listener
        bottomNav.setOnItemSelectedListener(null);
        bottomNav.setSelectedItemId(currentTabId);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == currentTabId) {
                return true;
            }

            SessionManager session = new SessionManager(activity);
            boolean isGuest = session.isGuest();

            if (id == R.id.nav_home) {
                Intent intent = new Intent(activity, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
                if (!(activity instanceof MainActivity)) {
                    activity.finish();
                }
                return true;
            } else if (id == R.id.nav_search) {
                Intent intent = new Intent(activity, SearchActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
                if (!(activity instanceof MainActivity)) {
                    activity.finish();
                }
                return true;
            } else if (id == R.id.nav_rentals) {
                if (isGuest) {
                    showGuestPrompt(activity, "view your rentals");
                    bottomNav.setSelectedItemId(currentTabId);
                    return false;
                }
                Intent intent = new Intent(activity, MyRentalsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
                if (!(activity instanceof MainActivity)) {
                    activity.finish();
                }
                return true;
            } else if (id == R.id.nav_profile) {
                Intent intent = new Intent(activity, ProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
                if (!(activity instanceof MainActivity)) {
                    activity.finish();
                }
                return true;
            }
            return false;
        });

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                SessionManager session = new SessionManager(activity);
                if (session.isGuest()) {
                    showGuestPrompt(activity, "list an item for rent");
                    return;
                }
                Intent intent = new Intent(activity, AddProductActivity.class);
                activity.startActivity(intent);
            });
        }
    }

    private static void showGuestPrompt(Activity activity, String action) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Account Required")
                .setMessage("Guests can browse and view items, but you must log in or register with your KUET student email to " + action + ".")
                .setPositiveButton("Log In / Register", (dialog, which) -> {
                    Intent intent = new Intent(activity, AuthActivity.class);
                    intent.putExtra(AppConfig.EXTRA_AUTH_TAB, "login");
                    activity.startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
