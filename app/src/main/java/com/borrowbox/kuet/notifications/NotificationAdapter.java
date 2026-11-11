package com.borrowbox.kuet.notifications;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ItemNotificationBinding;

import org.json.JSONObject;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(JSONObject notification);
    }

    private final List<JSONObject> notifications;
    private final OnNotificationClickListener listener;

    public NotificationAdapter(List<JSONObject> notifications, OnNotificationClickListener listener) {
        this.notifications = notifications;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNotificationBinding binding = ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject item = notifications.get(position);

        String type = item.optString("type", "general");
        String title = item.optString("title", "Notification");
        String message = item.optString("message", "");
        boolean read = item.optBoolean("read", false);

        holder.binding.tvNotifTitle.setText(title);
        holder.binding.tvNotifMessage.setText(message);
        holder.binding.viewUnreadDot.setVisibility(read ? View.GONE : View.VISIBLE);

        if ("rent_request".equals(type)) {
            holder.binding.tvNotifIcon.setText("📦");
            holder.binding.layoutActionHint.setVisibility(View.VISIBLE);
            holder.binding.tvActionHint.setText("Tap to Accept / Reject →");
        } else if ("rental_accepted".equals(type)) {
            holder.binding.tvNotifIcon.setText("✅");
            holder.binding.layoutActionHint.setVisibility(View.GONE);
        } else if ("rental_rejected".equals(type)) {
            holder.binding.tvNotifIcon.setText("❌");
            holder.binding.layoutActionHint.setVisibility(View.GONE);
        } else {
            holder.binding.tvNotifIcon.setText("🔔");
            holder.binding.layoutActionHint.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemNotificationBinding binding;

        ViewHolder(@NonNull ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
