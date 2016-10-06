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

package org.openbmap.utils;

import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.overlay.Circle;
import org.mapsforge.map.layer.overlay.FixedPixelCircle;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.poi.android.storage.AndroidPoiPersistenceManagerFactory;
import org.mapsforge.poi.storage.ExactMatchPoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategoryManager;
import org.mapsforge.poi.storage.PoiPersistenceManager;
import org.mapsforge.poi.storage.PointOfInterest;
import org.openbmap.R;
import org.openbmap.activities.MapViewActivity;

import java.lang.ref.WeakReference;
import java.util.Collection;


public class PoiLoaderTask extends AsyncTask<BoundingBox, Void, Collection<PointOfInterest>> {
    private static final String TAG = PoiLoaderTask.class.getSimpleName();

    private static final int ALPHA_WIFI_CATALOG_FILL = 90;

    private static final Paint PAINT = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_WIFI_CATALOG_FILL, 120, 150, 120), 2, Style.FILL);
    public static final int MAX_OBJECTS = 5000;

    private final WeakReference<MapViewActivity> weakActivity;
    private final PoiType category;

    public PoiLoaderTask(MapViewActivity activity, PoiType category) {
        this.weakActivity = new WeakReference<>(activity);
        this.category = category;
    }

    @Override
    protected Collection<PointOfInterest> doInBackground(BoundingBox... params) {
        PoiPersistenceManager persistenceManager = null;
        try {
            String POI_FILE = Environment.getExternalStorageDirectory() + "/germany.poi";
            String filter = "";
            if (category == PoiType.Wifi) {
                filter = "Wifis";
            } else if (category == PoiType.CellTower) {
                filter = "Towers";
            } else {
                filter = null;
            }
            persistenceManager = AndroidPoiPersistenceManagerFactory.getPoiPersistenceManager(POI_FILE);
            PoiCategoryManager categoryManager = persistenceManager.getCategoryManager();
            PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
            categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle(filter));
            return persistenceManager.findInRect(params[0], categoryFilter, null, MAX_OBJECTS);
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
        final MapViewActivity activity = weakActivity.get();
        if (activity == null) {
            return;
        }

        if (pointOfInterests == null) {
            Log.d(TAG, "No POI founds");
            return;
        }

        final Drawable drawable = activity.getActivity().getDrawable(R.drawable.icon);
        Bitmap bitmap = AndroidGraphicFactory.convertToBitmap(drawable);
        bitmap.incrementRefCount();

        Log.d(TAG, pointOfInterests.size() + " POI found");
        LegacyGroupLayer groupLayer = new LegacyGroupLayer();
        for (final PointOfInterest pointOfInterest : pointOfInterests) {
            if (category == PoiType.Wifi) {
                final Circle circle = new FixedPixelCircle(pointOfInterest.getLatLong(), 16, PAINT, null) {
                    @Override
                    public boolean onTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
                        // GroupLayer does not have a position, layerXY is null
                        Point circleXY = activity.mapView.getMapViewProjection().toPixels(getPosition());
                        if (this.contains(circleXY, tapXY)) {
                            Toast.makeText(activity.getActivity(), pointOfInterest.getName(), Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        return false;
                    }
                };
                groupLayer.layers.add(circle);
            } else if (category == PoiType.CellTower) {
                final Marker marker = new Marker(pointOfInterest.getLatLong(), bitmap, 0, -bitmap.getHeight() / 2);
                groupLayer.layers.add(marker);
            }
        }
        activity.mapView.getLayerManager().getLayers().add(groupLayer);
        activity.redrawLayers(groupLayer, category);
    }
}