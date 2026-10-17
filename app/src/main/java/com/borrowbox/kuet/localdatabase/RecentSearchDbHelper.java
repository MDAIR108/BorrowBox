package com.borrowbox.kuet.localdatabase;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLiteOpenHelper for local recent-searches storage.
 * Table: recent_searches (id INTEGER PK, query TEXT UNIQUE, created_at INTEGER)
 * Using UNIQUE on query so inserting a duplicate bumps it to the top via
 * INSERT OR REPLACE (which deletes old row and inserts a new one with fresh timestamp).
 */
public class RecentSearchDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "borrowbox_local.db";
    private static final int    DB_VERSION = 1;

    public static final String TABLE_RECENT = "recent_searches";
    public static final String COL_ID       = "id";
    public static final String COL_QUERY    = "search_query";
    public static final String COL_TIME     = "created_at";

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_RECENT + " ("
            + COL_ID    + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_QUERY + " TEXT NOT NULL UNIQUE, "
            + COL_TIME  + " INTEGER NOT NULL"
            + ");";

    public RecentSearchDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECENT);
        onCreate(db);
    }
}
