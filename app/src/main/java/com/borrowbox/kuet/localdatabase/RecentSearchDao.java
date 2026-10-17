package com.borrowbox.kuet.localdatabase;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Data-Access Object for recent searches.
 * ALL database operations run on a single background thread (ExecutorService).
 * Results are delivered back on the main thread via Handler(mainLooper).
 *
 * Course note: demonstrates ExecutorService + Handler pattern required in PROJECT_SPEC.
 */
public class RecentSearchDao {

    private static final int MAX_RECENT = 10; // keep only the 10 most recent queries

    public interface Callback<T> {
        void onResult(T result);
    }

    private final RecentSearchDbHelper dbHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public RecentSearchDao(Context context) {
        this.dbHelper = new RecentSearchDbHelper(context.getApplicationContext());
    }

    /** Save (or bump) a search query. Runs on background thread. */
    public void saveSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;
        final String trimmed = query.trim();
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            // INSERT OR REPLACE: if query already exists, old row is deleted and new row
            // is inserted with a fresh timestamp — effectively bumping it to the top.
            ContentValues cv = new ContentValues();
            cv.put(RecentSearchDbHelper.COL_QUERY, trimmed);
            cv.put(RecentSearchDbHelper.COL_TIME, System.currentTimeMillis());
            db.insertWithOnConflict(
                    RecentSearchDbHelper.TABLE_RECENT, null, cv,
                    SQLiteDatabase.CONFLICT_REPLACE);
            pruneOldSearches(db);
        });
    }

    /** Delete all rows older than the MAX_RECENT newest. */
    private void pruneOldSearches(SQLiteDatabase db) {
        db.execSQL(
            "DELETE FROM " + RecentSearchDbHelper.TABLE_RECENT +
            " WHERE " + RecentSearchDbHelper.COL_ID + " NOT IN (" +
            "  SELECT " + RecentSearchDbHelper.COL_ID +
            "  FROM "   + RecentSearchDbHelper.TABLE_RECENT +
            "  ORDER BY " + RecentSearchDbHelper.COL_TIME + " DESC" +
            "  LIMIT " + MAX_RECENT +
            ")");
    }

    /** Load recent searches (newest first). Calls back on the main thread. */
    public void getRecentSearches(Callback<List<String>> callback) {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            List<String> results = new ArrayList<>();
            Cursor cursor = db.query(
                    RecentSearchDbHelper.TABLE_RECENT,
                    new String[]{RecentSearchDbHelper.COL_QUERY},
                    null, null, null, null,
                    RecentSearchDbHelper.COL_TIME + " DESC",
                    String.valueOf(MAX_RECENT));
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    results.add(cursor.getString(0));
                }
                cursor.close();
            }
            mainHandler.post(() -> callback.onResult(results));
        });
    }

    /** Delete a specific search query. Runs on background thread. */
    public void deleteSearch(String query) {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.delete(RecentSearchDbHelper.TABLE_RECENT,
                    RecentSearchDbHelper.COL_QUERY + " = ?",
                    new String[]{query});
        });
    }

    /** Delete all recent searches. Runs on background thread. */
    public void clearAllSearches(Runnable onDone) {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.delete(RecentSearchDbHelper.TABLE_RECENT, null, null);
            mainHandler.post(onDone);
        });
    }

    /** Shutdown the executor when no longer needed (call from onDestroy). */
    public void shutdown() {
        executor.shutdown();
    }
}
