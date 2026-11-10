package com.borrowbox.kuet.rentals;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.databinding.ItemRentalCardBinding;
import com.borrowbox.kuet.models.RentalDetail;

import java.util.List;

public class RentalAdapter extends RecyclerView.Adapter<RentalAdapter.ViewHolder> {

    public interface OnRentalClickListener {
        void onRentalClick(RentalDetail rental);
    }

    private final List<RentalDetail> rentals;
    private final OnRentalClickListener listener;

    public RentalAdapter(List<RentalDetail> rentals, OnRentalClickListener listener) {
        this.rentals = rentals;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRentalCardBinding binding = ItemRentalCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(rentals.get(position));
    }

    @Override
    public int getItemCount() {
        return rentals != null ? rentals.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemRentalCardBinding binding;

        ViewHolder(ItemRentalCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final RentalDetail rental) {
            Context context = itemView.getContext();

            // Load product thumbnail safely
            RentalImageUtils.loadImage(binding.ivProductThumbnail, rental.getProductImage());

            // Product Name
            binding.tvProductName.setText(rental.getProductName());

            // Rent terms
            binding.tvRentPerDay.setText(rental.getFormattedRentPerDay());
            binding.tvDays.setText(rental.getFormattedDays());
            binding.tvTotalPrice.setText("Total: " + rental.getFormattedTotalPrice());

            // Request Date
            binding.tvRequestDate.setText("Requested: " + rental.getFormattedRequestDate());

            // Status Badge (Case-insensitive check)
            String canonicalStatus = rental.getCanonicalStatus();
            binding.tvStatusBadge.setText(canonicalStatus);

            switch (canonicalStatus) {
                case "Accepted":
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_accepted);
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_accepted_text));
                    break;
                case "Rejected":
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_rejected);
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_rejected_text));
                    break;
                case "Cancelled":
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_cancelled);
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_cancelled_text));
                    break;
                default: // Pending
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_pending);
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_pending_text));
                    break;
            }

            // Click listener
            binding.btnViewDetails.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRentalClick(rental);
                }
            });

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRentalClick(rental);
                }
            });
        }
    }
}
