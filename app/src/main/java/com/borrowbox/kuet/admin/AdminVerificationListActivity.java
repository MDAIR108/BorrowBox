package com.borrowbox.kuet.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ActivityAdminVerificationListBinding;
import com.borrowbox.kuet.utils.AppConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin screen: list all pending student verifications.
 * Admin-only — role is re-checked at onCreate.
 */
public class AdminVerificationListActivity extends AppCompatActivity {

    private ActivityAdminVerificationListBinding binding;
    private final List<Map<String, Object>> pendingList = new ArrayList<>();
    private PendingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminVerificationListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Re-check admin role
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        FirebaseFirestore.getInstance()
                .collection(AppConfig.COL_USERS)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(snap -> {
                    if (!"admin".equals(snap.getString("role"))) {
                        Toast.makeText(this, "Admin only.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    setupList();
                    loadPendingVerifications();
                })
                .addOnFailureListener(e -> { finish(); });

        binding.btnAdminListBack.setOnClickListener(v -> finish());
    }

    private void setupList() {
        adapter = new PendingAdapter(pendingList, item -> {
            // Pass the doc ID (uid of the student) to detail activity
            String studentUid = (String) item.get("userId");
            Intent intent = new Intent(this, AdminVerificationDetailActivity.class);
            intent.putExtra("studentUid", studentUid);
            startActivity(intent);
        });
        binding.rvPendingVerifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPendingVerifications.setAdapter(adapter);
    }

    private void loadPendingVerifications() {
        binding.progressAdminList.setVisibility(View.VISIBLE);
        FirebaseFirestore.getInstance()
                .collection(AppConfig.COL_STUDENT_VERIF)
                .whereEqualTo("status", AppConfig.VERIF_PENDING)
                .get()
                .addOnSuccessListener(querySnap -> {
                    binding.progressAdminList.setVisibility(View.GONE);
                    pendingList.clear();
                    for (QueryDocumentSnapshot doc : querySnap) {
                        pendingList.add(doc.getData());
                    }
                    if (pendingList.isEmpty()) {
                        binding.tvNoPending.setVisibility(View.VISIBLE);
                        binding.rvPendingVerifications.setVisibility(View.GONE);
                    } else {
                        binding.tvNoPending.setVisibility(View.GONE);
                        binding.rvPendingVerifications.setVisibility(View.VISIBLE);
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressAdminList.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /** Simple inline RecyclerView adapter for the pending list */
    static class PendingAdapter extends RecyclerView.Adapter<PendingAdapter.VH> {
        interface OnItemClick { void onClick(Map<String, Object> item); }

        private final List<Map<String, Object>> data;
        private final OnItemClick listener;

        PendingAdapter(List<Map<String, Object>> data, OnItemClick listener) {
            this.data = data;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Simple text row
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(48, 36, 48, 36);
            tv.setTextColor(parent.getContext().getResources().getColor(R.color.light_text_primary, null));
            tv.setTextSize(15f);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> item = data.get(position);
            String uid = (String) item.get("userId");
            holder.tv.setText("Student UID: " + (uid != null ? uid.substring(0, Math.min(uid.length(), 12)) : "?") + "... — Pending");
            holder.tv.setOnClickListener(v -> listener.onClick(item));
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(TextView tv) { super(tv); this.tv = tv; }
        }
    }
}
