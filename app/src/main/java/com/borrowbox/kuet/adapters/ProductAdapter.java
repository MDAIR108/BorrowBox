package com.borrowbox.kuet.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ItemProductCardBinding;
import com.borrowbox.kuet.models.Product;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    private final List<Product> products;
    private final OnProductClickListener listener;

    public ProductAdapter(List<Product> products, OnProductClickListener listener) {
        this.products = products;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductCardBinding binding = ItemProductCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(products.get(position));
    }

    @Override
    public int getItemCount() {
        return products != null ? products.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemProductCardBinding binding;

        ViewHolder(ItemProductCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final Product product) {
            binding.tvProductEmoji.setText(product.getEmoji());
            binding.tvProductName.setText(product.getName());
            binding.tvProductPrice.setText(product.getFormattedPrice());
            binding.tvProductLocation.setText(product.getDepartment() + " • " + product.getHallLocation());
            binding.tvOwnerSnippet.setText("Owner: " + product.getOwnerName());

            // Availability badge styling (Dark text on Peach/Pink background)
            if (product.isAvailable()) {
                binding.tvAvailabilityBadge.setText(product.getAvailabilityText());
                binding.tvAvailabilityBadge.setBackgroundResource(R.drawable.bg_status_available);
                binding.tvAvailabilityBadge.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.dark_text));
            } else {
                binding.tvAvailabilityBadge.setText(product.getAvailabilityText());
                binding.tvAvailabilityBadge.setBackgroundResource(R.drawable.bg_status_rented);
                binding.tvAvailabilityBadge.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.dark_text));
            }

            // Click actions
            binding.btnViewDetails.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProductClick(product);
                }
            });

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProductClick(product);
                }
            });
        }
    }
}
