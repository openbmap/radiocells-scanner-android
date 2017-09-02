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

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.mapsforge.core.model.BoundingBox;
import org.openbmap.Preferences;
import org.openbmap.events.onCatalogQuery;
import org.openbmap.events.onCatalogResults;
import org.openbmap.events.onCatalogStart;
import org.openbmap.events.onCatalogStop;
import org.openbmap.utils.CatalogObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import jsqlite.Database;
import jsqlite.Exception;
import jsqlite.Stmt;

public class PoiCatalogService extends Service {

    private static final String TAG = PoiCatalogService.class.getSimpleName();
    private static final int MAX_OBJECTS = 10000;

    private String catalogLocation;

    // MIN_LON, MIN_LAT, MAX_LON, MAX_LAT, MIN_LON, MIN_LAT, MAX_LON, MAX_LAT, LIMIT
    public static final String FIND_CELL_IN_BOX_STATEMENT =
            "SELECT OGC_FID as id, Y(Geometry) as lat, X(Geometry) as lon FROM cells " +
                    "WHERE ST_Intersects(GEOMETRY, BuildMbr(?, ?, ?, ?)) = 1 AND " +
                    "cells.ROWID IN ( " +
                    "SELECT ROWID FROM SpatialIndex WHERE " +
                    "f_table_name = 'cells' AND " +
                    "search_frame = BuildMbr(?, ?, ?, ?)) LIMIT ?;";

    // MIN_LON, MIN_LAT, MAX_LON, MAX_LAT, MIN_LON, MIN_LAT, MAX_LON, MAX_LAT, LIMIT
    public static final String FIND_WIFI_IN_BOX_STATEMENT =
            "SELECT OGC_FID as id, Y(Geometry) as lat, X(Geometry) as lon FROM wifis " +
                    "WHERE ST_Intersects(GEOMETRY, BuildMbr(?, ?, ?, ?)) = 1 AND " +
                    "wifis.ROWID IN ( " +
                    "SELECT ROWID FROM SpatialIndex WHERE " +
                    "f_table_name = 'wifis' AND " +
                    "search_frame = BuildMbr(?, ?, ?, ?)) LIMIT ?;";

    private boolean inSync = false;
    private Database dbSpatialite;

    @Override
    public final void onCreate() {
        super.onCreate();
        Log.d(TAG, "PoiCatalogService created");

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (!prefs.getString(Preferences.KEY_CATALOG_FILE, Preferences.VAL_CATALOG_NONE).equals(Preferences.VAL_CATALOG_NONE)) {
            // Open catalog database
            String folder = prefs.getString(Preferences.KEY_WIFI_CATALOG_FOLDER,
                    this.getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.CATALOG_SUBDIR);

            Log.i(TAG, "Using catalog folder:" + folder);
            catalogLocation = folder + File.separator + prefs.getString(Preferences.KEY_CATALOG_FILE, Preferences.DEFAULT_CATALOG_FILE);
            Log.i(TAG, "Selected catalog file: " + catalogLocation);
        }

        openDatabase();

        /*
        SyncPOITask task = new SyncPOITask();
        task.execute();
        */
        inSync = true;
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger upstreamMessenger = new Messenger(new ManagerService.UpstreamHandler());

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return upstreamMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }

        closeDatabase();

        super.onDestroy();
    }

    private void openDatabase() {
        if (catalogLocation == null) {
            Log.w(TAG, "Catalog file not set! Aborting..");
            return;
        }
        try {
            if (dbSpatialite == null) {
                dbSpatialite = new jsqlite.Database();
            } else {
                dbSpatialite.close();
            }
            dbSpatialite.open(catalogLocation, jsqlite.Constants.SQLITE_OPEN_READWRITE | jsqlite.Constants.SQLITE_OPEN_CREATE);
        } catch (Exception e) {
            Log.e(TAG, "Error opening catalog file " + catalogLocation + ":" + e.getMessage());
        }
    }

    private Collection<CatalogObject> queryDatabase(BoundingBox bbox, int maxObjects) {
        Log.i(TAG, "Searching within " + bbox.toString());
        ArrayList<CatalogObject> pois = new ArrayList<>();

        if (dbSpatialite == null) {
            Log.e(TAG, "Database not available, skipping catalog query");
        }

        try {
            Stmt stmt = dbSpatialite.prepare(FIND_WIFI_IN_BOX_STATEMENT);

            stmt.reset();
            stmt.clear_bindings();

            // MIN_LON, MIN_LAT, MAX_LON, MAX_LAT, MIN_LON, MIN_LAT, MAX_LON, MAX_LAT, LIMIT
            stmt.bind(1, bbox.minLongitude);
            stmt.bind(2, bbox.minLatitude);
            stmt.bind(3, bbox.maxLongitude);
            stmt.bind(4, bbox.maxLatitude);
            stmt.bind(5, bbox.minLongitude);
            stmt.bind(6, bbox.minLatitude);
            stmt.bind(7, bbox.maxLongitude);
            stmt.bind(8, bbox.maxLatitude);
            stmt.bind(9, maxObjects);

            while (stmt.step()) {
                long id = stmt.column_long(0);
                double lat = stmt.column_double(1);
                double lon = stmt.column_double(2);
                CatalogObject poi = new CatalogObject(id, lat, lon, null, "Radiocells.org");

                pois.add(poi);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error executing catalog query: " + e.getMessage());
        }

        return pois;
    }

    @Subscribe
    public void onEvent(onCatalogStart event) {
        Log.d(TAG, "ACK onCatalogStart");
    }

    @Subscribe
    public void onEvent(onCatalogStop event) {
        Log.d(TAG, "ACK onCatalogStop");
        closeDatabase();
    }


    @Subscribe
    public void onEvent(onCatalogQuery event) {
        if (inSync) {
            if (dbSpatialite == null) {
                openDatabase();
            }
            LoadPoiTask task = new LoadPoiTask();
            task.execute(event.bbox);
        } else {
            Log.w(TAG, "POI database not yet ready");
        }
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
                final BoundingBox range = new BoundingBox(minLatitude, minLongitude, maxLatitude, maxLongitude);

                return queryDatabase(range, MAX_OBJECTS);
            } catch (Throwable t) {
                Log.e(TAG, t.getMessage(), t);
            } finally {

            }
            return null;
        }


        @Override
        protected void onPostExecute(Collection<CatalogObject> pois) {
            EventBus.getDefault().post(new onCatalogResults(pois));
        }
    }
}