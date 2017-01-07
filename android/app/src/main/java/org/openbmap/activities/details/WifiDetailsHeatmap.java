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

package org.openbmap.activities.details;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EFragment;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.model.common.Observer;
import org.mapsforge.map.util.MapPositionUtil;
import org.openbmap.R;
import org.openbmap.activities.tabs.BaseMapFragment;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.heatmap.HeatLatLong;
import org.openbmap.heatmap.HeatmapBuilder;
import org.openbmap.heatmap.HeatmapBuilder.HeatmapBuilderListener;

import java.util.ArrayList;


/**
 * Fragment for displaying cell detail information
 */
@EFragment(R.layout.wifidetailsmap)
public class WifiDetailsHeatmap extends BaseMapFragment implements HeatmapBuilderListener {

	private static final String TAG = WifiDetailsHeatmap.class.getSimpleName();

	/**
	 * Radius heat-map circles
	 */
	private static final float RADIUS = 50f;

	private ArrayList<HeatLatLong> mPoints = new ArrayList<>();

	/**
	 * Current zoom level
	 */
	private byte mCurrentZoom;

	private LatLong mTarget;

	private boolean mUpdatePending;

	private boolean mLayoutInflated;

	private Marker mHeatmapLayer;

	private byte mZoomOnStartUpdate;

	private AsyncTask<Object, Integer, Boolean>	builder;
    private Observer mMapObserver;
    private ArrayList<WifiRecord> mMeasurements;

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mMeasurements = ((WifiDetailsActivity) getActivity()).getMeasurements();
        mPoints = measurementsToHeatPoints(mMeasurements);

        if (mPoints.size() > 0) {
            mMapView.getModel().mapViewPosition.setCenter(mPoints.get(mPoints.size()-1));
        };
    }

    public void onViewCreated(final View view, Bundle saved) {
        super.onViewCreated(view, saved);
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
               mLayoutInflated = true;
                requestHeatmapUpdate();
            }
        });
    }

    @AfterViews
	public final void init() {
        initBaseMap();
        mHeatmapLayer = new Marker(null, null, 0, 0);
        mMapView.getLayerManager().getLayers().add(mHeatmapLayer);

        // zoom to moderate zoom level on startup
        if (mMapView.getModel().mapViewPosition.getZoomLevel() < (byte) 10 || mMapView.getModel().mapViewPosition.getZoomLevel() > (byte) 18) {
            Log.i(TAG, "Reseting zoom level");
            mMapView.getModel().mapViewPosition.setZoomLevel((byte) 16);
        }

        // register for zoom changes
        this.mMapObserver = new Observer() {
            @Override
            public void onChange() {

                final byte zoom = mMapView.getModel().mapViewPosition.getZoomLevel();
                if (zoom != mCurrentZoom) {
                    // update overlays on zoom level changed
                    Log.i(TAG, "New zoom level " + zoom + ", reloading map objects");
					/*
					// cancel pending heat-maps
					if (builder != null) {
						builder.cancel(true);
					}
					 */
                    clearLayer();
                    requestHeatmapUpdate();
                    mCurrentZoom = zoom;
                }
            }
        };
        this.mMapView.getModel().mapViewPosition.addObserver(mMapObserver);
	}

    @Override
    protected String getPersistableId() {
        return WifiDetailsHeatmap.class.getSimpleName();
    }

    private ArrayList<HeatLatLong> measurementsToHeatPoints(ArrayList<WifiRecord> measurements) {
        if (measurements == null) {
            Log.i(TAG, "No heatmap input available, skipping");
            return null;
        }

        ArrayList<HeatLatLong> points = new ArrayList<>();
        for (WifiRecord w : measurements) {
            points.add(new HeatLatLong(
                    w.getBeginPosition().getLatitude(),
                    w.getBeginPosition().getLongitude(),
                    w.getLevel()));
        }
        return points;
    }

	/**
	 * Triggers an heatmap update. As heatmap builder requires screen size, this will only
     * work after layout has been full inflated. If layout hasn't been inflated yet, call
     * to function is ignored. Also only one update can take place at a time. This is also enforced.
	 */
	private void requestHeatmapUpdate() {
		if (mLayoutInflated && !mUpdatePending) {
			mUpdatePending = true;
            mZoomOnStartUpdate = mMapView.getModel().mapViewPosition.getZoomLevel();

			clearLayer();

			final BoundingBox bbox = MapPositionUtil.getBoundingBox(
					mMapView.getModel().mapViewPosition.getMapPosition(),
					mMapView.getDimension(), mMapView.getModel().displayModel.getTileSize());

			mTarget = mMapView.getModel().mapViewPosition.getCenter();
            mHeatmapLayer.setLatLong(mTarget);
            mHeatmapLayer.setVisible(true);

			builder = new HeatmapBuilder(
					WifiDetailsHeatmap.this,
                    mMapView.getMeasuredWidth(),
                    mMapView.getMeasuredHeight(),
                    bbox,
					mMapView.getModel().mapViewPosition.getZoomLevel(),
                    mMapView.getModel().displayModel.getScaleFactor(),
                    mMapView.getModel().displayModel.getTileSize(), RADIUS).execute(mPoints);
		} else {
			Log.i(TAG, "Another heat-map is currently generated. Skipped");
		}
	}

	@Override
	public final void onHeatmapCompleted(final Bitmap backbuffer) {
		if (mMapView.getModel().mapViewPosition.getZoomLevel() != mZoomOnStartUpdate) {
            // zoom level has changed in the mean time - regenerate
			mUpdatePending = false;
            Log.i(TAG, "Zoom level has changed - have to re-generate heat-map");
			clearLayer();
			requestHeatmapUpdate();
			return;
		}

		final BitmapDrawable drawable = new BitmapDrawable(backbuffer);
		final org.mapsforge.core.graphics.Bitmap mfBitmap = AndroidGraphicFactory.convertToBitmap(drawable);
		if (mHeatmapLayer != null && mfBitmap != null) {
			mHeatmapLayer.setBitmap(mfBitmap);
		} else {
			Log.w(TAG, "Skipped heatmap draw: either layer or bitmap is null");
		}
		mUpdatePending = false;
		//saveHeatmapToFile(backbuffer);
	}

	/**
	 * Callback function when heatmap generation has failed
	 */
	@Override
	public final void onHeatmapFailed() {
		mUpdatePending = false;
	}


	/**
	 * Removes heatmap layer (if any)
	 */
	private void clearLayer() {
		if (mHeatmapLayer == null) {
			return;
		}
        mHeatmapLayer.setBitmap(null);
        mHeatmapLayer.setVisible(false);
	}

    @Override
    public void onLongPress(LatLong tapLatLong, Point thisXY, Point tapXY) {

    }
}
