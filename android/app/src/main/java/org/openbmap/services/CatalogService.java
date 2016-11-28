/*
	Radiobeacon - Openbmap wifi and cell logger
    Copyright (C) 2013  wish7

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.openbmap.services;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.poi.android.storage.AndroidPoiPersistenceManagerFactory;
import org.mapsforge.poi.storage.PoiCategory;
import org.mapsforge.poi.storage.PoiFileInfo;
import org.mapsforge.poi.storage.PoiPersistenceManager;
import org.mapsforge.poi.storage.PointOfInterest;
import org.mapsforge.poi.storage.UnknownPoiCategoryException;
import org.openbmap.db.ContentProvider;
import org.openbmap.db.DatabaseHelper;
import org.openbmap.db.Schema;
import org.openbmap.events.onPoiUpdateAvailable;
import org.openbmap.events.onPoiUpdateRequested;
import org.openbmap.utils.CatalogObject;
import org.openbmap.utils.LayerHelpers.LayerFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import jsqlite.Database;
import jsqlite.Exception;
import jsqlite.Stmt;

import static org.openbmap.utils.LayerHelpers.filterToCriteria;

public class CatalogService extends AbstractService {

    private static final String TAG = CatalogService.class.getSimpleName();
    private static final int MAX_OBJECTS = 10000;

    private String POI_FILE = Environment.getExternalStorageDirectory() + "/de_current.sqlite";

    public static final String FIND_CELL_IN_BOX_STATEMENT =
            "SELECT cell_poi_index.id, cell_poi_index.minLat, cell_poi_index.minLon "
                    // + ", poi_data.data, poi_data.category "
                    + "FROM cell_poi_index "
                    //+ "JOIN poi_data ON poi_index.id = poi_data.id "
                    + "WHERE "
                    + "minLat <= ? AND "
                    + "minLon <= ? AND "
                    + "minLat >= ? AND "
                    + "minLon >= ? "
                    + "LIMIT ?";

    public static final String FIND_WIFI_IN_BOX_STATEMENT =
            "SELECT wifi_poi_index.id, wifi_poi_index.minLat, wifi_poi_index.minLon "
                    // + ", poi_data.data, poi_data.category "
                    + "FROM wifi_poi_index "
                    //+ "JOIN poi_data ON poi_index.id = poi_data.id "
                    + "WHERE "
                    + "minLat <= ? AND "
                    + "minLon <= ? AND "
                    + "minLat >= ? AND "
                    + "minLon >= ? "
                    + "LIMIT ?";

    private boolean inSync = false;
    private Database dbSpatialite;

    @Override
    public final void onCreate() {
        super.onCreate();
        Log.d(TAG, "CatalogService created");
    }

    /*
    static <T> List<List<T>> chopped(List<T> list, final int L) {
        List<List<T>> parts = new ArrayList<List<T>>();
        final int N = list.size();
        for (int i = 0; i < N; i += L) {
            parts.add(new ArrayList<T>(
                    list.subList(i, Math.min(N, i + L)))
            );
        }
        return parts;
    }*/

    private class SyncPOITask extends AsyncTask<Void, Void, Void> {
        private PoiPersistenceManager writableManager = null;
        ArrayList<ContentValues> toAdd;

                @Override
        protected void onPreExecute () {
            toAdd = getNotInCatalog();
        }

        @Override
        protected Void doInBackground(Void... params) {
            long start = System.currentTimeMillis();

            addToPoiDatabase(toAdd);
            markSynced(toAdd);

            Log.d(TAG, "Deserializing new wifis took " + (System.currentTimeMillis() - start));
            return null;
        }

        private boolean addToPoiDatabase(ArrayList<ContentValues> newWifis) {
            Log.i(TAG, "Starting POI update for " + String.valueOf(newWifis.size()) + " bssid");
            try {
                Log.i(TAG, "Target " + POI_FILE);
                writableManager = AndroidPoiPersistenceManagerFactory.getPoiPersistenceManager(POI_FILE, false);
                if (!writableManager.isValidDataBase()) {
                    Log.e(TAG, "Invalid POI database " + POI_FILE);
                } else {
                    PoiFileInfo info = writableManager.getPoiFileInfo();
                    Log.i(TAG, "POI metadata for " + POI_FILE);
                    Log.i(TAG, String.format("Bounds %s", info.bounds));
                    Log.i(TAG, String.format("Date %s", info.date));
                    Log.i(TAG, String.format("Comment: %s", info.comment));
                    Log.i(TAG, String.format("Version: %s", info.version));
                    Log.i(TAG, String.format("Ways: %s", info.ways));
                    Log.i(TAG, String.format("Writer: %s", info.writer));
                    Log.i(TAG, String.format("Language: %s", info.language));
                }

                String filter = filterToCriteria(LayerFilter.WifisOwn);

                PoiCategory category = null;
                try {
                    category = writableManager.getCategoryManager().getPoiCategoryByTitle(filter);
                } catch (UnknownPoiCategoryException e) {
                    Log.e(TAG, "Invalid filter: " + filter);
                    return true;
                }

                for (ContentValues wifi : newWifis) {
                    writableManager.insertPointOfInterest(new PointOfInterest(
                            Math.abs(new Random().nextLong()),
                            wifi.getAsDouble("latitude"),
                            wifi.getAsDouble("longitude"),
                            "bssid="+wifi.getAsString("bssid-stripped"),
                            category));
                }

                /*
                int CHUNK_SIZE = 500;
                List<List<PointOfInterest>> chunks = chopped(all, CHUNK_SIZE);
                int i = 0;
                for (List<PointOfInterest> chunk: chunks) {
                    Log.d(TAG, String.valueOf(i) + "/" + String.valueOf(chunks.size()));
                    writableManager.insertPointsOfInterest(chunk);
                    i+=CHUNK_SIZE;
                }
                */

                if (writableManager != null) {
                    Log.i(TAG, "Closing POI database " + POI_FILE);
                    writableManager.close();
                    writableManager = null;
                }
            } catch (Throwable t) {
                Log.e(TAG, "POI update error: " + t.getMessage(), t);
            }
            inSync = true;

            Log.i(TAG, "POI database synced");
            return false;
        }

        private ArrayList<ContentValues> getNotInCatalog() {
            boolean REBUILD = false;
            final ArrayList<ContentValues> data = new ArrayList<>();
            final DatabaseHelper dbHelper = new DatabaseHelper(CatalogService.this);
            final SQLiteDatabase db = dbHelper.getReadableDatabase();
            final Cursor cursor = db.rawQuery(
                    "SELECT w." + Schema.COL_BSSID +
                            ", avg(p." + Schema.COL_LATITUDE + ") as latitude" +
                            ", avg(p." + Schema.COL_LONGITUDE + ") as longitude " +
                            " FROM " + Schema.TBL_WIFIS + " w" +
                            " JOIN " + Schema.TBL_POSITIONS +
                            " p ON (w." + Schema.COL_BEGIN_POSITION_ID +
                            " = p." + Schema.COL_ID + ") " +
                            (!REBUILD ? " WHERE " + Schema.COL_KNOWN_WIFI + " = 0 " : "") +
                            " GROUP BY w." + Schema.COL_BSSID,
                    null);

            while (cursor.moveToNext()) {
                ContentValues newWifi = new ContentValues();
                newWifi.put("bssid-stripped", cursor.getString(0).replace(":", "").toUpperCase());
                newWifi.put("bssid", cursor.getString(0).toUpperCase());
                newWifi.put("latitude", cursor.getDouble(1));
                newWifi.put("longitude", cursor.getDouble(2));
                newWifi.put("source", 99);
                data.add(newWifi);
            }

            Log.i(TAG, cursor.getCount() + " not synced to catalog");
            cursor.close();
            db.close();

            return data;
        }
    }

    private void markSynced(ArrayList<ContentValues> addedToCatalog) {
        Log.i(TAG, "Updating known wifi tag for all sessions");
        ContentResolver contentResolver = this.getContentResolver();

        ContentValues updateExisting = new ContentValues();
        updateExisting.put(Schema.COL_KNOWN_WIFI, 2);

        for (ContentValues record : addedToCatalog) {
            int status = contentResolver.update(
                    ContentProvider.CONTENT_URI_WIFI,
                    updateExisting,
                    Schema.COL_BSSID + " = ?",
                    new String[]{record.getAsString("bssid").toUpperCase()});
        }
    }

    @Override
    public final void onStartService() {

        openDatabase();

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        /*
        SyncPOITask task = new SyncPOITask();
        task.execute();
        */
        inSync = true;
    }


    @Override
    public void onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }

        closeDatabase();

        super.onDestroy();
    }

    @Override
    public final void onStopService() {
        Log.d(TAG, "OnStopService called");
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }

        closeDatabase();
    }

    private void openDatabase() {
        try {
            if (dbSpatialite == null) {
                dbSpatialite = new jsqlite.Database();
            } else {
                dbSpatialite.close();
            }
            dbSpatialite.open(POI_FILE, jsqlite.Constants.SQLITE_OPEN_READWRITE | jsqlite.Constants.SQLITE_OPEN_CREATE);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private Collection<CatalogObject> queryDatabase(BoundingBox bbox, int maxObjects) {
        Log.i(TAG, "Searching within " + bbox.toString());
        ArrayList<CatalogObject> pois = new ArrayList<>();

        try {
            Stmt stmt = dbSpatialite.prepare(FIND_WIFI_IN_BOX_STATEMENT);

            stmt.reset();
            stmt.clear_bindings();

            stmt.bind(1, bbox.maxLatitude);
            stmt.bind(2, bbox.maxLongitude);
            stmt.bind(3, bbox.minLatitude);
            stmt.bind(4, bbox.minLongitude);
            stmt.bind(5, maxObjects);

            while (stmt.step()) {
                long id = stmt.column_long(0);
                double lat = stmt.column_double(1);
                double lon = stmt.column_double(2);
                CatalogObject poi = new CatalogObject(id, lat, lon, null, "Radiocells.org");

                pois.add(poi);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        /*
        try {
            Stmt stmt = dbSpatialite.prepare(FIND_CELL_IN_BOX_STATEMENT);

            stmt.reset();
            stmt.clear_bindings();

            stmt.bind(1, bbox.maxLatitude);
            stmt.bind(2, bbox.maxLongitude);
            stmt.bind(3, bbox.minLatitude);
            stmt.bind(4, bbox.minLongitude);
            stmt.bind(5, maxObjects);

            while (stmt.step()) {
                long id = stmt.column_long(0);
                double lat = stmt.column_double(1);
                double lon = stmt.column_double(2);
                CatalogObject poi = new CatalogObject(id, lat, lon, null, "Towers");

                pois.add(poi);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        */
        return pois;
    }

    private void closeDatabase() {
        if (dbSpatialite != null) {
            try {
                dbSpatialite.close();
                dbSpatialite = null;
            } catch (Exception e) {
                Log.e(TAG, "Error closing database: " + e.getMessage());
            }
        }
    }

    @Subscribe
    public void onEvent(onPoiUpdateRequested event) {
        if (inSync) {
            Log.i(TAG, "Loading POI data");
            if (dbSpatialite == null) {
                openDatabase();
            }
            LoadPoiTask task = new LoadPoiTask();
            task.execute(event.bbox);
        } else {
            Log.w(TAG, "POI database not yet ready");
        }
    }

    private class LoadPoiTask extends AsyncTask<BoundingBox, Void, Collection<CatalogObject>> {

        public LoadPoiTask() {
        }

        @Override
        protected Collection<CatalogObject> doInBackground(BoundingBox... params) {
            try {
                /*
                Log.i(TAG, "Loading " + filter);
                String filterString = filterToCriteria(filter);
                */
                // Set over-draw: query more than visible range for smoother data scrolling / less database queries
                double minLatitude = params[0].minLatitude;
                double maxLatitude = params[0].maxLatitude;
                double minLongitude = params[0].minLongitude;
                double maxLongitude = params[0].maxLongitude;

                final double latSpan = maxLatitude - minLatitude;
                final double lonSpan = maxLongitude - minLongitude;
                minLatitude -= latSpan * 1.0;
                maxLatitude += latSpan * 1.0;
                minLongitude -= lonSpan * 1.0;
                maxLongitude += lonSpan * 1.0;
                final BoundingBox overdraw = new BoundingBox(minLatitude, minLongitude, maxLatitude, maxLongitude);

                return queryDatabase(overdraw, MAX_OBJECTS);
            } catch (Throwable t) {
                Log.e(TAG, t.getMessage(), t);
            } finally {

            }
            return null;
        }

        @Override
        protected void onPostExecute(Collection<CatalogObject> pois) {
            EventBus.getDefault().post(new onPoiUpdateAvailable(pois));
        }

    }
}