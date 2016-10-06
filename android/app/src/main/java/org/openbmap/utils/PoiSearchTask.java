package org.openbmap.utils;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.overlay.Circle;
import org.mapsforge.map.layer.overlay.FixedPixelCircle;
import org.mapsforge.poi.android.storage.AndroidPoiPersistenceManagerFactory;
import org.mapsforge.poi.storage.ExactMatchPoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategoryManager;
import org.mapsforge.poi.storage.PoiPersistenceManager;
import org.mapsforge.poi.storage.PointOfInterest;
import org.openbmap.activities.MapViewActivity;

import java.lang.ref.WeakReference;
import java.util.Collection;

public class PoiSearchTask extends AsyncTask<BoundingBox, Void, Collection<PointOfInterest>> {
    private static final String TAG = PoiSearchTask.class.getSimpleName();

    private static final int ALPHA_WIFI_CATALOG_FILL = 90;

    private final WeakReference<MapViewActivity> weakActivity;
    private final String category;

    public PoiSearchTask(MapViewActivity activity, String category) {
        Log.d(TAG, "Add category" + category);
        this.weakActivity = new WeakReference<>(activity);
        this.category = category;
    }

    @Override
    protected Collection<PointOfInterest> doInBackground(BoundingBox... params) {
        Log.d(TAG, "Add stuff from germany.poi in " + ((BoundingBox)params[0]).minLatitude + "/" + ((BoundingBox)params[0]).maxLatitude);
        Log.d(TAG, "Add lon" + ((BoundingBox)params[0]).minLongitude + "/" + ((BoundingBox)params[0]).maxLongitude);
        PoiPersistenceManager persistenceManager = null;
        try {
            String POI_FILE = Environment.getExternalStorageDirectory() + "/germany.poi";
            persistenceManager = AndroidPoiPersistenceManagerFactory.getPoiPersistenceManager(POI_FILE);
            PoiCategoryManager categoryManager = persistenceManager.getCategoryManager();
            Log.d(TAG, "Category mgr " + categoryManager.toString());
            PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
            categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle(this.category));
            return persistenceManager.findInRect(params[0], categoryFilter, null, Integer.MAX_VALUE);
        } catch (Throwable t) {
            Log.e(TAG, t.getMessage(), t);
        } finally {
            if (persistenceManager != null) {
                persistenceManager.close();
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Collection<PointOfInterest> pointOfInterests) {
        Log.d(TAG, "Add complete");
        final MapViewActivity activity = weakActivity.get();
        if (activity == null) {
            return;
        }

        if (pointOfInterests == null) {
            Log.d(TAG, "Add - no results");
            return;
        }
        Log.d(TAG, "Add more than null");
        for (final PointOfInterest pointOfInterest : pointOfInterests) {
            Log.d(TAG, "Add " + pointOfInterest.getData());
            final Circle circle = new FixedPixelCircle(pointOfInterest.getLatLong(), 16, MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_WIFI_CATALOG_FILL, 120, 150, 120), 2, Style.FILL), null) {
                @Override
                public boolean onTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
                    // GroupLayer does not have a position, layerXY is null
                    Point circleXY = activity.mMapView.getMapViewProjection().toPixels(getPosition());
                    if (this.contains(circleXY, tapXY)) {
                        Toast.makeText(activity.getActivity(), pointOfInterest.getName(), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    return false;
                }
            };
            activity.mMapView.getLayerManager().getLayers().add(circle);
        }

        //activity.redrawLayers();
    }
}