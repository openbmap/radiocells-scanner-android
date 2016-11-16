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

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.poi.android.storage.AndroidPoiPersistenceManagerFactory;
import org.mapsforge.poi.storage.ExactMatchPoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategory;
import org.mapsforge.poi.storage.PoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategoryManager;
import org.mapsforge.poi.storage.PoiFileInfo;
import org.mapsforge.poi.storage.PoiPersistenceManager;
import org.mapsforge.poi.storage.PointOfInterest;
import org.mapsforge.poi.storage.UnknownPoiCategoryException;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.events.onPoiAddOwnWifis;
import org.openbmap.events.onPoiUpdateAvailable;
import org.openbmap.events.onPoiUpdateRequested;
import org.openbmap.utils.PoiFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

public class PoiService extends AbstractService {

    private static final String TAG = PoiService.class.getSimpleName();

    private PoiPersistenceManager persistenceManager = null;
    private PoiCategoryManager categoryManager = null;
    String POI_FILE = null;

    public static final int MAX_OBJECTS = 5000;

    @Override
    public final void onCreate() {
        super.onCreate();
        Log.d(TAG, "PoiService created");
    }

    @Override
    public final void onStartService() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        POI_FILE = Environment.getExternalStorageDirectory() + "/de.poi";
        persistenceManager = AndroidPoiPersistenceManagerFactory.getPoiPersistenceManager(POI_FILE, false);
        if (!persistenceManager.isValidDataBase()) {
            Log.e(TAG, "Invalid POI database " + POI_FILE);
        } else {
            PoiFileInfo info = persistenceManager.getPoiFileInfo();
            Log.i(TAG, "POI metadata for " + POI_FILE);
            Log.i(TAG, String.format("Bounds %s", info.bounds));
            Log.i(TAG, String.format("Date %s", info.date));
            Log.i(TAG, String.format("Comment: %s", info.comment));
            Log.i(TAG, String.format("Version: %s", info.version));
            Log.i(TAG, String.format("Ways: %s", info.ways));
            Log.i(TAG, String.format("Writer: %s", info.writer));
            Log.i(TAG, String.format("Language: %s", info.language));
        }
        categoryManager = persistenceManager.getCategoryManager();
    }

    @Override
    public void onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        categoryManager = null;
        if (persistenceManager != null) {
            Log.i(TAG, "Closing POI database " + POI_FILE);
            persistenceManager.close();
        }
        super.onDestroy();
    }

    @Override
    public final void onStopService() {
        Log.d(TAG, "OnStopService called");
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }

        categoryManager = null;
        if (persistenceManager != null) {
            Log.i(TAG, "Closing POI database " + POI_FILE);
            persistenceManager.close();
        }

    }

    @Subscribe
    public void onEvent(onPoiAddOwnWifis event){
        AppendTask task = new AppendTask();
        task.execute(event.wifis);
    }

    public void addCommunityWifis(String filename) {
        RebuildTask task = new RebuildTask();
        task.execute("de_wifis.csv");
    }

    @Subscribe
    public void onEvent(onPoiUpdateRequested event) {
        LoadPoiTask task = new LoadPoiTask(event.filter);
        task.execute(event.bbox);
    }

    /**
     * Adds a list of own wifis
     */
    private class AppendTask extends AsyncTask<ArrayList<WifiRecord>, Void, Void> {
        @Override
        protected Void doInBackground(ArrayList<WifiRecord>... params) {
            Log.i(TAG, "Adding wifi records to POI database");
            try {
                String filter = "Own";

                PoiCategory category = null;
                try {
                    category = categoryManager.getPoiCategoryByTitle(filter);
                } catch (UnknownPoiCategoryException e) {
                    Log.e(TAG, "Invalid filter: " + filter);
                    return null;
                }

                ArrayList<PointOfInterest> batch = new ArrayList<>();
                BoundingBox global = new BoundingBox(MercatorProjection.LATITUDE_MIN, -180, MercatorProjection.LATITUDE_MAX, +180);
                PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
                categoryFilter.addCategory(category);

                for (WifiRecord w : params[0]) {
                    long time = System.currentTimeMillis();
                    boolean exists = persistenceManager.findInRect(global, categoryFilter, "%" + w.getBssid() + "%", 1).size() > 0;
                    Log.d(TAG, String.format("Search took %s", System.currentTimeMillis() - time));
                    if (!exists) {
                        batch.add(
                                new PointOfInterest(
                                        Math.abs(new Random().nextLong()),
                                        w.getBeginPosition().getLatitude(),
                                        w.getBeginPosition().getLongitude(),
                                        String.format("bssid=%s", w.getBssid().replace(":","")),
                                        category)
                        );
                    } else {
                        Log.i(TAG, "Not adding to POI database, " + w.getBssid() + " already there");
                    }
                }

                if (batch.size() > 0) {
                    Log.d(TAG, "Adding " + batch.size() + " wifis to catalog");
                    persistenceManager.insertPointsOfInterest(batch);
                }
            } catch (Throwable t) {
                Log.e(TAG, t.getMessage(), t);
            }

            return null;
        }
    }

    /**
     * Adds community wifis
     */
    private class RebuildTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            try {
                File dir = Environment.getExternalStorageDirectory();
                File yourFile = new File(dir, params[0]);

                String filter = "Radiocells.org";
                PoiCategory category = categoryManager.getPoiCategoryByTitle(filter);


            } catch (Throwable t) {
                Log.e(TAG, t.getMessage(), t);
            }

            return null;
        }
    }

    private class LoadPoiTask extends AsyncTask<BoundingBox, Void, Collection<PointOfInterest>> {

        private final PoiFilter filter;

        public LoadPoiTask(PoiFilter filter) {
            this.filter = filter;
        }

        @Override
        protected Collection<PointOfInterest> doInBackground(BoundingBox... params) {
            try {
                String filter = "";
                if (this.filter == PoiFilter.WifisCommunity) {
                    filter = "Radiocells.org";
                } else if (this.filter == PoiFilter.WifisOwn) {
                    filter = "Own";
                } else if (this.filter == PoiFilter.WifisAll) {
                    filter = "Wifis";
                } else if (this.filter == PoiFilter.Towers) {
                    filter = "Towers";
                } else {
                    // load everything
                    filter = "Cells and Wifis";
                }

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

                PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();

                try {
                    categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle(filter));
                } catch (UnknownPoiCategoryException e) {
                    Log.e(TAG, "Invalid filter: " + filter);
                }
                    return persistenceManager.findInRect(overdraw, categoryFilter, null, MAX_OBJECTS);
            } catch (Throwable t) {
                Log.e(TAG, t.getMessage(), t);
            } finally {

            }
            return null;
        }

        @Override
        protected void onPostExecute(Collection<PointOfInterest> pois) {
            EventBus.getDefault().post(new onPoiUpdateAvailable(pois, filter));
        }

    }
}
