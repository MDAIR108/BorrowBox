package com.borrowbox.kuet.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.borrowbox.kuet.databinding.ItemBorrowCardBinding;
import com.borrowbox.kuet.models.BorrowItem;
import java.util.List;

public class BorrowItemAdapter extends RecyclerView.Adapter<BorrowItemAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(BorrowItem item);
    }

    private final List<BorrowItem> items;
    private final OnItemClickListener listener;

    public BorrowItemAdapter(List<BorrowItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBorrowCardBinding binding = ItemBorrowCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemBorrowCardBinding binding;

        ViewHolder(ItemBorrowCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final BorrowItem item) {
            binding.tvItemEmoji.setText(item.getEmoji());
            binding.tvItemTitle.setText(item.getTitle());
            binding.tvPrice.setText(item.getPriceFormatted());
            binding.tvLocation.setText(item.getHallLocation() + " • " + item.getDepartment());
            binding.tvOwner.setText(item.getOwnerName() + " • " + item.getRating() + " ★");

            if (item.isFree()) {
                binding.btnBorrow.setText("Borrow");
            } else {
                binding.btnBorrow.setText("Rent");
            }

            binding.btnBorrow.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }
}
