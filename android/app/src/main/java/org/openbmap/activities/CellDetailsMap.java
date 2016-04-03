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

package org.openbmap.activities;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Toast;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidResourceBitmap;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.download.TileDownloadLayer;
import org.mapsforge.map.layer.download.tilesource.OnlineTileSource;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.model.common.Observer;
import org.mapsforge.map.util.MapPositionUtil;
import org.openbmap.R;
import org.openbmap.db.DataHelper;
import org.openbmap.db.RadioBeaconContentProvider;
import org.openbmap.db.Schema;
import org.openbmap.db.models.CellRecord;
import org.openbmap.heatmap.HeatLatLong;
import org.openbmap.heatmap.HeatmapBuilder;
import org.openbmap.heatmap.HeatmapBuilder.HeatmapBuilderListener;
import org.openbmap.utils.MapUtils;

import java.util.ArrayList;

/**
 * Fragment for displaying cell detail information
 */
public class CellDetailsMap extends Fragment implements HeatmapBuilderListener, LoaderManager.LoaderCallbacks<Cursor>  {

	private static final String TAG = CellDetailsMap.class.getSimpleName();

	/**
	 * Radius heat-map circles
	 */
	private static final float RADIUS = 50f;

	private CellRecord mCell;

	// [start] UI controls
	/**
	 * MapView
	 */
	private MapView mMapView;

	//[end]

	// [start] Map styles
	/**
	 * Baselayer cache
	 */
	private TileCache mTileCache;

	/**
	 * Online tile layer, used when no offline map available
	 */
	private static TileDownloadLayer mapDownloadLayer = null;
	
	//[end]

	// [start] Dynamic map variables

	private final ArrayList<HeatLatLong> points = new ArrayList<HeatLatLong>();

	private boolean	mPointsLoaded  = false;

	private boolean	mLayoutInflated = false;

	private Observer mMapObserver;

	/**
	 * Current zoom level
	 */
	private byte mCurrentZoom;

	private LatLong	mTarget;

	private boolean	mUpdatePending;

	private Marker mHeatmapLayer;

	private byte mZoomAtTrigger;

	// [end]


	@Override
	public final View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.celldetailsmap, container, false);	
		this.mMapView = (MapView) view.findViewById(R.id.map);

		return view;
	}

	@Override
	public final void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);	
		initMap();
		
		mCell = ((CellDetailsActivity) getActivity()).getCell();

		if (savedInstanceState != null) {
			// reset loader after screen rotation
			// also see http://stackoverflow.com/questions/12009895/loader-restarts-on-orientation-change
			getActivity().getSupportLoaderManager().restartLoader(0, null, this);
		} else {
			getActivity().getSupportLoaderManager().initLoader(0, null, this);
		}
	}
	
	@Override
	public final void onResume() {
		super.onResume();
		if (mapDownloadLayer != null) {
			mapDownloadLayer.onResume();
		}
	}
	
	@Override
	public final void onPause() {
		if (mapDownloadLayer != null) {
			mapDownloadLayer.onResume();
		}
		super.onPause();
	}
	
	@Override
	public void onDestroy() {
	    super.onDestroy();
	    this.mTileCache.destroy();
	    this.mMapView.getModel().mapViewPosition.destroy();
	    this.mMapView.destroy();
	    AndroidResourceBitmap.clearResourceBitmaps();
	}

	@Override
	public final Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
		// set query params: id and session id
		final ArrayList<String> args = new ArrayList<String>();
		String selectSql = "";

		if (mCell != null && mCell.getLogicalCellId() != -1  && !mCell.isCdma()) {
			// typical gsm/hdspa cells
			selectSql = Schema.COL_LOGICAL_CELLID + " = ? AND " + Schema.COL_PSC + " = ?" ;
			args.add(String.valueOf(mCell.getLogicalCellId()));
			args.add(String.valueOf(mCell.getPsc()));
		} else if (mCell != null && mCell.getLogicalCellId() == -1 &&  !mCell.isCdma()) {
			// umts cells
			selectSql = Schema.COL_PSC + " = ?";
			args.add(String.valueOf(mCell.getPsc()));
		} else if (mCell != null && mCell.isCdma()
				&& !mCell.getBaseId().equals("-1") && !mCell.getNetworkId().equals("-1") && !mCell.getSystemId().equals("-1")) {
			// cdma cells
			selectSql = Schema.COL_CDMA_BASEID + " = ? AND " + Schema.COL_CDMA_NETWORKID + " = ? AND " + Schema.COL_CDMA_SYSTEMID + " = ? AND " + Schema.COL_PSC + " = ?";

			args.add(mCell.getBaseId());
			args.add(mCell.getNetworkId());
			args.add(mCell.getSystemId());
			args.add(String.valueOf(mCell.getPsc()));
		}

		final DataHelper dbHelper = new DataHelper(this.getActivity());
		args.add(String.valueOf(dbHelper.getActiveSessionId()));
		if (selectSql.length() > 0) {
			selectSql += " AND ";
		}
		selectSql += Schema.COL_SESSION_ID + " = ?";

		final String[] projection = {Schema.COL_ID, Schema.COL_STRENGTHDBM, Schema.COL_TIMESTAMP,  "begin_" + Schema.COL_LATITUDE, "begin_" + Schema.COL_LONGITUDE};
		// query data from content provider
		final CursorLoader cursorLoader =
				new CursorLoader(getActivity().getBaseContext(),
						RadioBeaconContentProvider.CONTENT_URI_CELL_EXTENDED, projection, selectSql, args.toArray(new String[args.size()]), Schema.COL_STRENGTHDBM + " DESC");
		return cursorLoader;
	}

	@Override
	public final void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
		if (cursor != null && cursor.getCount() > 0) {

			final int colLat = cursor.getColumnIndex("begin_" + Schema.COL_LATITUDE);
			final int colLon = cursor.getColumnIndex("begin_" + Schema.COL_LONGITUDE);
			final int colLevel = cursor.getColumnIndex(Schema.COL_STRENGTHDBM);

			while (cursor.moveToNext()) {
				//int intensity = (int) (HEAT_AMPLIFIER * (Math.min(cursor.getInt(colLevel) + MIN_HEAT, 0)) / -10f);
				final int intensity = cursor.getInt(colLevel) / -1;
				points.add(new HeatLatLong(cursor.getDouble(colLat), cursor.getDouble(colLon), intensity));
			}

			if (points.size() > 0) {
				mMapView.getModel().mapViewPosition.setCenter(points.get(points.size()-1));
			}
			mPointsLoaded  = true;
			proceedAfterHeatmapCompleted();

			// update host activity
			((CellDetailsActivity) getActivity()).setNoMeasurements(cursor.getCount());
		}
	}

	/**
	 * 
	 */
	private void proceedAfterHeatmapCompleted() {
		if (mPointsLoaded  && mLayoutInflated && !mUpdatePending) {
			mUpdatePending = true;

			clearLayer();

			final BoundingBox bbox = MapPositionUtil.getBoundingBox(
					mMapView.getModel().mapViewPosition.getMapPosition(),
					mMapView.getDimension(), mMapView.getModel().displayModel.getTileSize());

			mTarget = mMapView.getModel().mapViewPosition.getCenter();
			mZoomAtTrigger = mMapView.getModel().mapViewPosition.getZoomLevel();

			mHeatmapLayer = new Marker(mTarget, null, 0, 0);
			mMapView.getLayerManager().getLayers().add(mHeatmapLayer);

			new HeatmapBuilder(
					CellDetailsMap.this, mMapView.getWidth(), mMapView.getHeight(), bbox,
					mMapView.getModel().mapViewPosition.getZoomLevel(), mMapView.getModel().displayModel.getScaleFactor(),
					mMapView.getModel().displayModel.getTileSize(), RADIUS).execute(points);
		} else {
			Log.i(TAG, "Another heat-map is currently generated. Skipped");
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.HeatmapBuilder.HeatmapBuilderListener#onHeatmapCompleted(android.graphics.Bitmap)
	 */
	@Override
	public final void onHeatmapCompleted(final Bitmap backbuffer) {
		// zoom level has changed in the mean time - regenerate
		if (mMapView.getModel().mapViewPosition.getZoomLevel() != mZoomAtTrigger) {
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

	/* (non-Javadoc)
	 * @see android.support.v4.app.LoaderManager.LoaderCallbacks#onLoaderReset(android.support.v4.content.Loader)
	 */
	@Override
	public void onLoaderReset(final Loader<Cursor> arg0) {

	}

	/**
	 * Removes heatmap layer (if any)
	 */
	private void clearLayer() {
		if (mHeatmapLayer == null) {
			return;
		}

		if (mMapView.getLayerManager().getLayers().indexOf(mHeatmapLayer) != -1) {
			mMapView.getLayerManager().getLayers().remove(mHeatmapLayer);
			mHeatmapLayer = null;
		}
	}

	/**
	 * Initializes map components
	 */
	@SuppressLint("NewApi")
	private void initMap() {
		this.mTileCache = createTileCache();

		if (MapUtils.isMapSelected(this.getActivity())) {
			final Layer offlineLayer = MapUtils.createTileRendererLayer(
					this.mTileCache,
					this.mMapView.getModel().mapViewPosition,
					MapUtils.getMapFile(this.getActivity()),
                    null,
                    MapUtils.getRenderTheme(this.getActivity()));
			if (offlineLayer != null) this.mMapView.getLayerManager().getLayers().add(offlineLayer);
		} else {
			//this.mMapView.getModel().displayModel.setBackgroundColor(0xffffffff);
			Toast.makeText(this.getActivity(), R.string.info_using_online_map, Toast.LENGTH_LONG).show();

			final OnlineTileSource onlineTileSource = new OnlineTileSource(new String[]{
					"otile1.mqcdn.com", "otile2.mqcdn.com", "otile3.mqcdn.com", "otile4.mqcdn.com"}, 80);
			onlineTileSource.setName("MapQuest")
			.setAlpha(false)
			.setBaseUrl("/tiles/1.0.0/map/")
			.setExtension("png")
			.setParallelRequestsLimit(8)
			.setProtocol("http")
			.setTileSize(256)
			.setZoomLevelMax((byte) 18)
			.setZoomLevelMin((byte) 0);

			mapDownloadLayer = new TileDownloadLayer(mTileCache,
					mMapView.getModel().mapViewPosition, onlineTileSource,
					AndroidGraphicFactory.INSTANCE);
			mMapView.getLayerManager().getLayers().add(mapDownloadLayer);
			mapDownloadLayer.onResume();
		}


		// register for layout finalization - we need this to get width and height
		final ViewTreeObserver vto = mMapView.getViewTreeObserver();
		vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

			@SuppressLint("NewApi")
			@Override
			public void onGlobalLayout() {

				mLayoutInflated = true;
				proceedAfterHeatmapCompleted();

				final ViewTreeObserver obs = mMapView.getViewTreeObserver();

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					obs.removeOnGlobalLayoutListener(this);
				} else {
					obs.removeGlobalOnLayoutListener(this);
				}
			}
		});

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

		this.mMapView.setClickable(true);
		this.mMapView.getMapScaleBar().setVisible(true);

		this.mMapView.getModel().mapViewPosition.setZoomLevel((byte) 16);
	}

	/** 
	 * Creates a separate map tile cache
	 * @return
	 */
	protected final TileCache createTileCache() {
		return AndroidUtil.createTileCache(this.getActivity(), "mapcache", mMapView.getModel().displayModel.getTileSize(), 1f, this.mMapView.getModel().frameBufferModel.getOverdrawFactor());
	}

	/**
	 * Saves heatmap to SD card
	 * @param bitmap
	 */
	/*
	@SuppressLint("NewApi")
	private void saveHeatmapToFile(final Bitmap backbuffer) {
		try {
			FileOutputStream out = new FileOutputStream("/sdcard/result.png");
			backbuffer.compress(Bitmap.CompressFormat.PNG, 90, out);
			out.close();

			// rescan SD card on honeycomb devices
			// Otherwise files may not be visible when connected to desktop pc (MTP cache problem)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				Log.i(TAG, "Re-indexing SD card temp folder");
				new MediaScanner(getActivity(), new File("/sdcard/"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	 */

}
