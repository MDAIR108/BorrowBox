package com.borrowbox.kuet.items;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ItemMyPostCardBinding;
import com.borrowbox.kuet.models.Product;
import com.borrowbox.kuet.rentals.RentalImageUtils;

import java.util.ArrayList;
import java.util.List;

public class MyPostsAdapter extends RecyclerView.Adapter<MyPostsAdapter.PostViewHolder> {

    public interface OnPostActionListener {
        void onItemClick(Product product);
        void onEditClick(Product product);
        void onDeleteClick(Product product);
    }

    private final Context context;
    private final List<Product> posts = new ArrayList<>();
    private final OnPostActionListener listener;

    public MyPostsAdapter(Context context, OnPostActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setPosts(List<Product> newPosts) {
        posts.clear();
        if (newPosts != null) {
            posts.addAll(newPosts);
        }
        notifyDataSetChanged();
    }

    public void removePost(Product product) {
        int index = -1;
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).getId().equals(product.getId())) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            posts.remove(index);
            notifyItemRemoved(index);
        }
    }

    public int getPostCount() {
        return posts.size();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMyPostCardBinding binding = ItemMyPostCardBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new PostViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        holder.bind(posts.get(position));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    class PostViewHolder extends RecyclerView.ViewHolder {
        private final ItemMyPostCardBinding binding;

        public PostViewHolder(@NonNull ItemMyPostCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Product product) {
            binding.tvProductName.setText(product.getName());
            binding.tvCategory.setText(product.getCategory());
            binding.tvMaxDays.setText("Max " + product.getMaxRentDays() + " days");
            binding.tvRentPrice.setText(product.getFormattedPrice());

            if (product.isAvailable()) {
                binding.tvStatusBadge.setText("Available");
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_available);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_available_text));
            } else {
                binding.tvStatusBadge.setText("Unavailable");
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_cancelled);
                binding.tvStatusBadge.setTextColor(Color.WHITE);
            }

            RentalImageUtils.loadImage(binding.ivProductThumbnail, product.getImageUrl());

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(product);
            });

            binding.btnEditPost.setOnClickListener(v -> {
                if (listener != null) listener.onEditClick(product);
            });

            binding.btnDeletePost.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClick(product);
            });
        }
    }
}
