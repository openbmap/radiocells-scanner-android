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

import java.io.File;
import java.util.ArrayList;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.model.common.Observer;
import org.mapsforge.map.util.MapPositionUtil;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.db.DataHelper;
import org.openbmap.db.RadioBeaconContentProvider;
import org.openbmap.db.Schema;
import org.openbmap.db.models.CellRecord;
import org.openbmap.heatmap.HeatLatLong;
import org.openbmap.heatmap.HeatmapBuilder;
import org.openbmap.heatmap.HeatmapBuilder.HeatmapBuilderListener;
import org.openbmap.utils.MapUtils;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
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

/**
 * Fragment for displaying cell detail information
 */
public class CellDetailsMap extends Fragment implements HeatmapBuilderListener, LoaderManager.LoaderCallbacks<Cursor>  {

	private static final String TAG = CellDetailsMap.class.getSimpleName();

	/**
	 * Radius heat-map circles
	 */
	private static final float RADIUS	= 50f;

	private CellRecord mCell;

	// [start] UI controls
	/**
	 * MapView
	 */
	private MapView mapView;

	//[end]

	// [start] Map styles
	/**
	 * Baselayer cache
	 */
	private TileCache tileCache;

	//[end]

	// [start] Dynamic map variables

	private ArrayList<HeatLatLong> points = new ArrayList<HeatLatLong>();

	private boolean	pointsLoaded  = false;

	private boolean	layoutInflated = false;

	private Observer mapObserver;

	/**
	 * Current zoom level
	 */
	private byte currentZoom;

	private LatLong	target;

	private boolean	updatePending;

	private Marker heatmapLayer;

	private byte initialZoom;

	private AsyncTask<Object, Integer, Boolean>	builder;

	// [end]


	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public final View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.celldetailsmap, container, false);	
		this.mapView = (MapView) view.findViewById(R.id.map);

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
	public void onResume() {
		super.onResume();
		// register for zoom changes
		this.mapObserver = new Observer() {
			@Override
			public void onChange() {

				byte zoom = mapView.getModel().mapViewPosition.getZoomLevel();
				if (zoom != currentZoom) {
					// update overlays on zoom level changed
					Log.i(TAG, "New zoom level " + zoom + ", reloading map objects");
					// cancel pending heat-maps
					if (builder != null) {
						builder.cancel(true);
					}

					clearLayer();
					proceedAfterHeatmapCompleted();
					currentZoom = zoom;
				}
			}
		};
		this.mapView.getModel().mapViewPosition.addObserver(mapObserver);
	}

	@Override
	public void onPause(){
		mapView.getModel().mapViewPosition.removeObserver(mapObserver);
		super.onPause();
	}

	@Override
	public final Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
		// set query params: id and session id
		ArrayList<String> args = new ArrayList<String>();
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

		DataHelper dbHelper = new DataHelper(this.getActivity());
		args.add(String.valueOf(dbHelper.getActiveSessionId()));
		if (selectSql.length() > 0) {
			selectSql += " AND ";
		}
		selectSql += Schema.COL_SESSION_ID + " = ?";

		String[] projection = {Schema.COL_ID, Schema.COL_STRENGTHDBM, Schema.COL_TIMESTAMP,  "begin_" + Schema.COL_LATITUDE, "begin_" + Schema.COL_LONGITUDE};
		// query data from content provider
		CursorLoader cursorLoader =
				new CursorLoader(getActivity().getBaseContext(),
						RadioBeaconContentProvider.CONTENT_URI_CELL_EXTENDED, projection, selectSql, args.toArray(new String[args.size()]), Schema.COL_STRENGTHDBM + " DESC");
		return cursorLoader;
	}

	@Override
	public final void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
		if (cursor != null && cursor.getCount() > 0) {

			int colLat = cursor.getColumnIndex("begin_" + Schema.COL_LATITUDE);
			int colLon = cursor.getColumnIndex("begin_" + Schema.COL_LONGITUDE);
			int colLevel = cursor.getColumnIndex(Schema.COL_STRENGTHDBM);

			while (cursor.moveToNext()) {
				//int intensity = (int) (HEAT_AMPLIFIER * (Math.min(cursor.getInt(colLevel) + MIN_HEAT, 0)) / -10f);
				int intensity = cursor.getInt(colLevel) / -1;
				points.add(new HeatLatLong(cursor.getDouble(colLat), cursor.getDouble(colLon), intensity));
			}

			if (points.size() > 0) {
				mapView.getModel().mapViewPosition.setCenter(points.get(points.size()-1));
			}
			pointsLoaded  = true;
			proceedAfterHeatmapCompleted();

			// update host activity
			((CellDetailsActivity) getActivity()).setNoMeasurements(cursor.getCount());
		}
	}

	/**
	 * 
	 */
	private void proceedAfterHeatmapCompleted() {
		if (pointsLoaded  && layoutInflated && !updatePending) {
			updatePending = true;

			clearLayer();

			BoundingBox bbox = MapPositionUtil.getBoundingBox(
					mapView.getModel().mapViewPosition.getMapPosition(),
					mapView.getDimension(), mapView.getModel().displayModel.getTileSize());

			target = mapView.getModel().mapViewPosition.getCenter();
			initialZoom = mapView.getModel().mapViewPosition.getZoomLevel();

			heatmapLayer = new Marker(target, null, 0, 0);
			mapView.getLayerManager().getLayers().add(heatmapLayer);

			builder = new HeatmapBuilder(
					CellDetailsMap.this, mapView.getWidth(), mapView.getHeight(), bbox,
					mapView.getModel().mapViewPosition.getZoomLevel(), mapView.getModel().displayModel.getTileSize(), RADIUS).execute(points);
		} else {
			Log.i(TAG, "Another heat-map is currently generated. Skipped");
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.HeatmapBuilder.HeatmapBuilderListener#onHeatmapCompleted(android.graphics.Bitmap)
	 */
	@Override
	public final void onHeatmapCompleted(final Bitmap backbuffer) {
		updatePending = false;

		// zoom level has changed in the mean time - regenerate
		if (mapView.getModel().mapViewPosition.getZoomLevel() != initialZoom) {
			Log.i(TAG, "Zoom level has changed - have to re-generate heat-map");
			clearLayer();
			proceedAfterHeatmapCompleted();
			return;
		}

		BitmapDrawable drawable = new BitmapDrawable(backbuffer);
		org.mapsforge.core.graphics.Bitmap mfBitmap = AndroidGraphicFactory.convertToBitmap(drawable);
		heatmapLayer.setBitmap(mfBitmap);

		//saveHeatmapToFile(backbuffer);
	}
	/** 
	 * Callback function when heatmap generation has failed
	 */
	@Override
	public final void onHeatmapFailed() {
		updatePending = false;
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
		if (heatmapLayer == null) {
			return;
		}

		if (mapView.getLayerManager().getLayers().indexOf(heatmapLayer) != -1) {
			mapView.getLayerManager().getLayers().remove(heatmapLayer);
			heatmapLayer = null;
		}
	}

	/**
	 * Opens selected map file
	 * @return a map file
	 */
	protected final File getMapFile() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		File mapFile = new File(
				Environment.getExternalStorageDirectory().getPath()
				+ prefs.getString(Preferences.KEY_MAP_FOLDER, Preferences.VAL_MAP_FOLDER), 
				prefs.getString(Preferences.KEY_MAP_FILE, Preferences.VAL_MAP_FILE));

		return mapFile;
	}

	/**
	 * Initializes map components
	 */
	@SuppressLint("NewApi")
	private void initMap() {
		this.tileCache = createTileCache();

		mapView.getLayerManager().getLayers().add(MapUtils.createTileRendererLayer(
				this.tileCache,
				this.mapView.getModel().mapViewPosition,
				getMapFile()));
		
		// register for layout finalization - we need this to get width and height
		ViewTreeObserver vto = mapView.getViewTreeObserver();
		vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

			@SuppressLint("NewApi")
			@Override
			public void onGlobalLayout() {

				layoutInflated = true;
				proceedAfterHeatmapCompleted();

				ViewTreeObserver obs = mapView.getViewTreeObserver();

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					obs.removeOnGlobalLayoutListener(this);
				} else {
					obs.removeGlobalOnLayoutListener(this);
				}
			}
		});

		// Register for zoom changes
		this.mapObserver = new Observer() {
			@Override
			public void onChange() {

				byte newZoom = mapView.getModel().mapViewPosition.getZoomLevel();
				if (newZoom != currentZoom) {
					// update overlays on zoom level changed
					Log.i(TAG, "New zoom level " + newZoom + ", reloading map objects");
					// cancel pending heat-maps
					if (builder != null) {
						builder.cancel(true);
					}

					clearLayer();
					proceedAfterHeatmapCompleted();
					currentZoom = newZoom;
				}
			}
		};
		this.mapView.getModel().mapViewPosition.addObserver(mapObserver);

		this.mapView.setClickable(true);
		this.mapView.getMapScaleBar().setVisible(true);

		/*
			Layers layers = layerManager.getLayers();
			// remove all layers including base layer
			layers.clear();
		 */
		
		this.mapView.getModel().mapViewPosition.setZoomLevel((byte) 16);

	}
	/** 
	 * Creates a separate tile cache
	 * @return
	 */
	protected final TileCache createTileCache() {
		return MapUtils.createExternalStorageTileCache(getActivity(), getClass().getSimpleName());
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
