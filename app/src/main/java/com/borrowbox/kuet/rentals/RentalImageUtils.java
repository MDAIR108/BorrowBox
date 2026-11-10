package com.borrowbox.kuet.rentals;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.borrowbox.kuet.R;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RentalImageUtils {

    private static final ExecutorService networkExecutor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // In-memory LRU cache sized to 1/8th of available runtime memory
    private static final LruCache<String, Bitmap> memoryCache;

    static {
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        memoryCache = new LruCache<String, Bitmap>(cacheSize > 0 ? cacheSize : 1024 * 10) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public static Bitmap getFromCache(String key) {
        if (key == null) return null;
        return memoryCache.get(key);
    }

    public static void putInCache(String key, Bitmap bitmap) {
        if (key != null && bitmap != null) {
            memoryCache.put(key, bitmap);
        }
    }

    /**
     * Decode base64 or fetch network image safely into Bitmap with in-memory caching.
     */
    public static void loadBitmap(final String imageSource, final BitmapCallback callback) {
        if (imageSource == null || imageSource.trim().isEmpty() || "null".equalsIgnoreCase(imageSource.trim())) {
            if (callback != null) callback.onLoaded(null);
            return;
        }

        final String src = imageSource.trim();
        final String cacheKey = src.length() > 100 ? src.substring(0, 100) + src.length() : src;

        Bitmap cached = getFromCache(cacheKey);
        if (cached != null) {
            if (callback != null) callback.onLoaded(cached);
            return;
        }

        networkExecutor.execute(() -> {
            Bitmap decoded = null;
            try {
                if (src.startsWith("data:image/") || (!src.startsWith("http://") && !src.startsWith("https://") && src.length() > 60)) {
                    String clean = src;
                    int comma = src.indexOf(",");
                    if (comma != -1) {
                        clean = src.substring(comma + 1);
                    }
                    byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
                    decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                } else if (src.startsWith("http://") || src.startsWith("https://")) {
                    URL url = new URL(src);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    conn.setDoInput(true);
                    conn.connect();
                    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        try (InputStream is = conn.getInputStream()) {
                            decoded = BitmapFactory.decodeStream(is);
                        }
                    }
                    conn.disconnect();
                }
            } catch (Throwable ignored) {}

            final Bitmap finalBitmap = decoded;
            if (finalBitmap != null) {
                putInCache(cacheKey, finalBitmap);
            }

            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onLoaded(finalBitmap);
                }
            });
        });
    }

    /**
     * Standard loader for general ImageViews with placeholder fallback.
     */
    public static void loadImage(final ImageView imageView, final String imageSource) {
        if (imageView == null) return;
        imageView.setImageResource(R.drawable.ic_box);

        if (imageSource == null || imageSource.trim().isEmpty() || "null".equalsIgnoreCase(imageSource.trim())) {
            return;
        }

        final String src = imageSource.trim();
        imageView.setTag(src);

        loadBitmap(src, bitmap -> {
            if (bitmap != null && src.equals(imageView.getTag())) {
                imageView.setImageBitmap(bitmap);
            }
        });
    }

    /**
     * Product Card Image loader:
     * If image exists, renders into ImageView and hides emoji.
     * If no image, hides ImageView and shows category emoji TextView.
     */
    public static void loadProductImage(final ImageView imageView, final TextView tvEmoji,
                                       final String imageSource, final String emoji) {
        if (imageView == null) return;

        if (imageSource == null || imageSource.trim().isEmpty() || "null".equalsIgnoreCase(imageSource.trim())) {
            imageView.setVisibility(View.GONE);
            if (tvEmoji != null) {
                tvEmoji.setVisibility(View.VISIBLE);
                tvEmoji.setText(emoji != null && !emoji.isEmpty() ? emoji : "📦");
            }
            return;
        }

        final String src = imageSource.trim();
        imageView.setTag(src);

        loadBitmap(src, bitmap -> {
            if (src.equals(imageView.getTag())) {
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setVisibility(View.VISIBLE);
                    if (tvEmoji != null) tvEmoji.setVisibility(View.GONE);
                } else {
                    imageView.setVisibility(View.GONE);
                    if (tvEmoji != null) {
                        tvEmoji.setVisibility(View.VISIBLE);
                        tvEmoji.setText(emoji != null && !emoji.isEmpty() ? emoji : "📦");
                    }
                }
            }
        });
    }

    /**
     * Circular User Avatar loader:
     * If image exists, renders circular cropped bitmap into ImageView and hides initial TextView.
     * If no image, shows initial TextView (e.g. "T") and hides ImageView.
     */
    public static void loadCircularImage(final ImageView imageView, final TextView tvInitial,
                                        final String imageSource, final String displayName) {
        String initial = "U";
        if (displayName != null && !displayName.trim().isEmpty()) {
            initial = displayName.trim().substring(0, 1).toUpperCase();
        }

        if (imageSource == null || imageSource.trim().isEmpty() || "null".equalsIgnoreCase(imageSource.trim())) {
            if (imageView != null) imageView.setVisibility(View.GONE);
            if (tvInitial != null) {
                tvInitial.setVisibility(View.VISIBLE);
                tvInitial.setText(initial);
            }
            return;
        }

        final String src = imageSource.trim();
        final String finalInitial = initial;
        if (imageView != null) imageView.setTag(src);

        loadBitmap(src, bitmap -> {
            if (imageView != null && src.equals(imageView.getTag())) {
                if (bitmap != null) {
                    Bitmap circle = createCircularBitmap(bitmap);
                    imageView.setImageBitmap(circle);
                    imageView.setVisibility(View.VISIBLE);
                    if (tvInitial != null) tvInitial.setVisibility(View.GONE);
                } else {
                    imageView.setVisibility(View.GONE);
                    if (tvInitial != null) {
                        tvInitial.setVisibility(View.VISIBLE);
                        tvInitial.setText(finalInitial);
                    }
                }
            }
        });
    }

    /**
     * Crops and masks a Bitmap into a perfect circle.
     */
    public static Bitmap createCircularBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int size = Math.min(width, height);

            Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);

            final Paint paint = new Paint();
            final Rect srcRect = new Rect((width - size) / 2, (height - size) / 2, (width + size) / 2, (height + size) / 2);
            final Rect dstRect = new Rect(0, 0, size, size);

            paint.setAntiAlias(true);
            paint.setFilterBitmap(true);
            paint.setDither(true);
            canvas.drawARGB(0, 0, 0, 0);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint);

            return output;
        } catch (Throwable t) {
            return bitmap;
        }
    }

    public interface BitmapCallback {
        void onLoaded(Bitmap bitmap);
    }
}
