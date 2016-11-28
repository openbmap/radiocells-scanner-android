package org.openbmap.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;
import android.util.Log;

import org.mapsforge.core.model.LatLong;
import org.openbmap.Preferences;

import java.io.File;
import java.util.ArrayList;

/**
 * The type Catalog database helper.
 */
public class CatalogDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = CatalogDatabaseHelper.class.getSimpleName();

    /**
     * The constant VERBOSE_QUERY.
     */
    public static final String VERBOSE_QUERY = "SELECT _id, latitude as grouped_lat, longitude as grouped_lon FROM wifi_zone WHERE "
            + "(latitude > ? AND latitude < ? AND longitude > ? AND longitude < ?)";

    /**
     * The constant HIGHSPEED_QUERY.
     */
    public static final String HIGHSPEED_QUERY = "SELECT round(latitude,4) as grouped_lat, round(longitude,4) as grouped_lon FROM wifi_zone WHERE "
            + "(latitude > ? AND latitude < ? AND longitude > ? AND longitude < ?) GROUP BY grouped_lat, grouped_lon";

    /**
     * Maximum overlay items diplayed
     * Prevents out of memory/performance issues
     */
    private static final int MAX_REFS = 5000;

    private static CatalogDatabaseHelper sInstance;

    private static String mFileLocation;

    /**
     * Gets instance.
     *
     * @param context the context
     * @return the instance
     */
    public static synchronized CatalogDatabaseHelper getInstance(final Context context) {
        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (sInstance == null) {
            // Open catalog database
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            mFileLocation = prefs.getString(Preferences.KEY_WIFI_CATALOG_FOLDER,
                    context.getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.CATALOG_SUBDIR)
                    + File.separator + prefs.getString(Preferences.KEY_CATALOG_FILE, Preferences.VAL_CATALOG_FILE);
            sInstance = new CatalogDatabaseHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Gets filename.
     *
     * @return the filename
     */
    public String getFilename() {
        return mFileLocation;
    }

    /**
     * Constructor should be private to prevent direct instantiation.
     * Make a call to the static method "getInstance()" instead.
     */
    private CatalogDatabaseHelper(final Context context) {
        super(context, mFileLocation, null, 1);
    }

    // Called when the database is created for the FIRST time.
    // If a database already exists on disk with the same DATABASE_NAME, this method will NOT be called.
    @Override
    public void onCreate(SQLiteDatabase db) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    /**
     * Returns a readable database with disabled journaling for better performance
     * @return SQLiteDatabase
     */
    private SQLiteDatabase getOptimizedDatabase() {
        SQLiteDatabase db = getReadableDatabase();

        // disable journaling
        Log.v(TAG, "Disable journaling on catalog database");
        Cursor pragma = db.rawQuery("PRAGMA journal_mode=OFF", null);
        pragma.close();
        return db;
    }

    /**
     * Returns all points in the database
     *
     * @param min_lat the min lat
     * @param max_lat the max lat
     * @param min_lon the min lon
     * @param max_lon the max lon
     * @return the points
     */
    public ArrayList<LatLong> getPoints(Double min_lat, Double max_lat, Double min_lon, Double max_lon) {
        ArrayList<LatLong> points = new ArrayList<>();

        String[] args = new String[] {
                        String.valueOf(min_lat),
                        String.valueOf(max_lat),
                        String.valueOf(min_lon),
                        String.valueOf(max_lon)};

        try {
            // TODO: do we really need to open database on each query???
            SQLiteDatabase db = getOptimizedDatabase();

            Cursor cursor = db.rawQuery(VERBOSE_QUERY, args);

            int i = 0;
            final int latCol = cursor.getColumnIndex("grouped_lat");
            final int lonCol = cursor.getColumnIndex("grouped_lon");

            if (cursor.moveToFirst()) {
                do {
                    points.add(new LatLong(cursor.getDouble(latCol), cursor.getDouble(lonCol)));
                    i++;
                } while(cursor.moveToNext() && i < MAX_REFS);
            }
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            db.close();
        } catch (SQLiteException e) {
            Log.e(TAG, "Error reading from catalog: " + e.toString());
        }

        return points;
    }

    /**
     * Gets points lazy.
     *
     * @param min_lat the min lat
     * @param max_lat the max lat
     * @param min_lon the min lon
     * @param max_lon the max lon
     * @return the points lazy
     */
    public ArrayList<LatLong> getPointsLazy(Double min_lat, Double max_lat, Double min_lon, Double max_lon) {
        ArrayList<LatLong> points = new ArrayList<>();

        String[] args = new String[] {
                String.valueOf(min_lat),
                String.valueOf(max_lat),
                String.valueOf(min_lon),
                String.valueOf(max_lon)};

        // "getReadableDatabase()" and "getWriteableDatabase()" return the same object (except under low disk space scenarios)

        try {
            SQLiteDatabase db = getOptimizedDatabase();
            Cursor cursor = db.rawQuery(HIGHSPEED_QUERY, args);
            int i = 0;
            final int latCol = cursor.getColumnIndex("grouped_lat");
            final int lonCol = cursor.getColumnIndex("grouped_lon");

            if (cursor.moveToFirst()) {
                do {
                    points.add(new LatLong(cursor.getDouble(latCol), cursor.getDouble(lonCol)));
                    i++;
                } while(cursor.moveToNext() && i < MAX_REFS);
            }
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Error reading from catalog: " + e.toString());
        }
        return points;
    }
}