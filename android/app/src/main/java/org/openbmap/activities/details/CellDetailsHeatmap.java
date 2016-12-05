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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.model.common.Observer;
import org.mapsforge.map.util.MapPositionUtil;
import org.openbmap.R;
import org.openbmap.activities.tabs.BaseMapFragment;
import org.openbmap.db.models.CellRecord;
import org.openbmap.heatmap.HeatLatLong;
import org.openbmap.heatmap.HeatmapBuilder;
import org.openbmap.heatmap.HeatmapBuilder.HeatmapBuilderListener;

import java.util.ArrayList;

import butterknife.ButterKnife;

/**
 * Fragment for displaying cell detail information
 */
public class CellDetailsHeatmap extends BaseMapFragment implements HeatmapBuilderListener {

	private static final String TAG = CellDetailsHeatmap.class.getSimpleName();

	/**
	 * Radius heat-map circles
	 */
	private static final float RADIUS = 50f;

	private CellRecord mCell;

	private ArrayList<HeatLatLong> mPoints = new ArrayList<>();

	private boolean	mLayoutInflated = false;

	private Observer mMapObserver;

	/**
	 * Current zoom level
	 */
	private byte mCurrentZoom;

	private LatLong	mTarget;

	private boolean	mUpdatePending;

	private Marker mHeatmapLayer;

	private byte mZoomOnStartUpdate;

    private ArrayList<CellRecord> mMeasurements;

    private AsyncTask<Object, Integer, Boolean> builder;

    @Override
    public final void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);


        mCell = ((CellDetailsActivity) getActivity()).getCell();
        mMeasurements = ((CellDetailsActivity) getActivity()).getMeasurements();
        mPoints = measurementsToHeatPoints(this.mMeasurements);

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

    @Override
	public final View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.celldetailsmap, container, false);
		this.mUnbinder = ButterKnife.bind(this, view);

		initBaseMap();
        mHeatmapLayer = new Marker(null, null, 0, 0);

        // Register for zoom changes
        this.mMapObserver = new Observer() {
            @Override
            public void onChange() {

                final byte newZoom = mMapView.getModel().mapViewPosition.getZoomLevel();
                if (newZoom != mCurrentZoom) {
                    // update overlays on zoom level changed
                    Log.i(TAG, "New zoom level " + newZoom + ", reloading map objects");
                    Log.i(TAG, "Update" + mUpdatePending);
                    // cancel pending heat-maps
                    //if (builder != null) {
                    //	builder.cancel(true);
                    //}

                    // if another update is pending, wait for complete
                    if (!mUpdatePending) {
                        clearLayer();
                        proceedAfterHeatmapCompleted();
                    }
                    mCurrentZoom = newZoom;
                }
            }
        };
        this.mMapView.getModel().mapViewPosition.addObserver(mMapObserver);

		// zoom to moderate zoom level on startup
		if (mMapView.getModel().mapViewPosition.getZoomLevel() < (byte) 10 || mMapView.getModel().mapViewPosition.getZoomLevel() > (byte) 18) {
			Log.i(TAG, "Reseting zoom level");
			mMapView.getModel().mapViewPosition.setZoomLevel((byte) 16);
		}
		return view;
	}

	@Override
	protected String getPersistableId() {
		return CellDetailsHeatmap.class.getSimpleName();
	}

    private ArrayList<HeatLatLong> measurementsToHeatPoints(ArrayList<CellRecord> measurements) {
        if (measurements == null) {
            Log.i(TAG, "No heatmap input available, skipping");
            return null;
        }

        ArrayList<HeatLatLong> points = new ArrayList<>();
        for (CellRecord c : measurements) {
            points.add(new HeatLatLong(
                    c.getBeginPosition().getLatitude(),
                    c.getBeginPosition().getLongitude(),
                    c.getStrengthdBm()));
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

            mMapView.getLayerManager().getLayers().add(mHeatmapLayer);

            builder = new HeatmapBuilder(
                    CellDetailsHeatmap.this,
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

	/**
	 * 
	 */
	private void proceedAfterHeatmapCompleted() {
		if (mLayoutInflated && !mUpdatePending) {
			mUpdatePending = true;

			clearLayer();

			final BoundingBox bbox = MapPositionUtil.getBoundingBox(
					mMapView.getModel().mapViewPosition.getMapPosition(),
					mMapView.getDimension(), mMapView.getModel().displayModel.getTileSize());

			mTarget = mMapView.getModel().mapViewPosition.getCenter();
			mZoomOnStartUpdate = mMapView.getModel().mapViewPosition.getZoomLevel();

			mHeatmapLayer = new Marker(mTarget, null, 0, 0);
			mMapView.getLayerManager().getLayers().add(mHeatmapLayer);

			new HeatmapBuilder(
					CellDetailsHeatmap.this, mMapView.getWidth(), mMapView.getHeight(), bbox,
					mMapView.getModel().mapViewPosition.getZoomLevel(), mMapView.getModel().displayModel.getScaleFactor(),
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
            proceedAfterHeatmapCompleted();
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
