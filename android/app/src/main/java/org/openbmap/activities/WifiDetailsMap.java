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
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
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
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.download.TileDownloadLayer;
import org.mapsforge.map.layer.download.tilesource.OnlineTileSource;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.model.common.Observer;
import org.mapsforge.map.util.MapPositionUtil;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.ContentProvider;
import org.openbmap.db.DataHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.heatmap.HeatLatLong;
import org.openbmap.heatmap.HeatmapBuilder;
import org.openbmap.heatmap.HeatmapBuilder.HeatmapBuilderListener;
import org.openbmap.utils.MapUtils;

import java.util.ArrayList;

/**
 * Fragment for displaying cell detail information
 */
public class WifiDetailsMap extends Fragment implements HeatmapBuilderListener, LoaderManager.LoaderCallbacks<Cursor>  {

	private static final String TAG = WifiDetailsMap.class.getSimpleName();

	/**
	 * Radius heat-map circles
	 */
	private static final float RADIUS = 50f;

	private WifiRecord mWifi;

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

	private byte zoomAtTrigger;

	private AsyncTask<Object, Integer, Boolean>	builder;

	// [end]


	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public final View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.wifidetailsmap, container, false);
		this.mMapView = (MapView) view.findViewById(R.id.map);
		return view;
	}

	@Override
	public final void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		initMap();

		mWifi = ((WifiDetailsActivity) getActivity()).getWifi();

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

		if (mapDownloadLayer != null) {
			mapDownloadLayer.onResume();
		}

		// register for zoom changes
		this.mapObserver = new Observer() {
			@Override
			public void onChange() {

				final byte zoom = mMapView.getModel().mapViewPosition.getZoomLevel();
				if (zoom != currentZoom) {
					// update overlays on zoom level changed
					Log.i(TAG, "New zoom level " + zoom + ", reloading map objects");
					/*
					// cancel pending heat-maps
					if (builder != null) {
						builder.cancel(true);
					}
					 */
					clearLayer();
					proceedAfterHeatmapCompleted();
					currentZoom = zoom;
				}
			}
		};
		this.mMapView.getModel().mapViewPosition.addObserver(mapObserver);
	}


	@Override
	public void onDestroy() {
		super.onDestroy();

		this.mMapView.destroyAll();
        MapUtils.clearRessources();
	}

	@Override
	public void onPause(){
		mMapView.getModel().mapViewPosition.removeObserver(mapObserver);
		if ((mapDownloadLayer != null)) {
			mapDownloadLayer.onResume();
		}
		super.onPause();
	}

	@Override
	public final Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
		// set query params: bssid and session id
		final String[] args = {"-1", String.valueOf(RadioBeacon.SESSION_NOT_TRACKING)};
		if (mWifi != null) {
			args[0] = mWifi.getBssid();
		}
		final DataHelper dbHelper = new DataHelper(this.getActivity());
		args[1] = String.valueOf(dbHelper.getActiveSessionId());

		final String[] projection = { Schema.COL_ID, Schema.COL_SSID, Schema.COL_LEVEL,  "begin_" + Schema.COL_LATITUDE, "begin_" + Schema.COL_LONGITUDE};
		final CursorLoader cursorLoader =
				new CursorLoader(getActivity().getBaseContext(),  ContentProvider.CONTENT_URI_WIFI_EXTENDED,
						projection, Schema.COL_BSSID + " = ? AND " + Schema.COL_SESSION_ID + " = ?", args, Schema.COL_LEVEL + " ASC");
		return cursorLoader;
	}

	@Override
	public final void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
		if (cursor != null && cursor.getCount() > 0) {

			final int colLat = cursor.getColumnIndex("begin_" + Schema.COL_LATITUDE);
			final int colLon = cursor.getColumnIndex("begin_" + Schema.COL_LONGITUDE);
			final int colLevel = cursor.getColumnIndex(Schema.COL_LEVEL);

			while (cursor.moveToNext()) {
				//int intensity = (int) (cursor.getInt(colLevel) / -10);
				final int intensity = cursor.getInt(colLevel) / -50;
				points.add(new HeatLatLong(cursor.getDouble(colLat), cursor.getDouble(colLon), intensity));
			}

			if (points.size() > 0) {
				mMapView.getModel().mapViewPosition.setCenter(points.get(points.size()-1));
			}
			pointsLoaded  = true;
			proceedAfterHeatmapCompleted();
		}
	}

	/**
	 *
	 */
	private void proceedAfterHeatmapCompleted() {
		if (pointsLoaded  && layoutInflated && !updatePending) {
			updatePending = true;

			clearLayer();

			final BoundingBox bbox = MapPositionUtil.getBoundingBox(
					mMapView.getModel().mapViewPosition.getMapPosition(),
					mMapView.getDimension(), mMapView.getModel().displayModel.getTileSize());

			target = mMapView.getModel().mapViewPosition.getCenter();
			zoomAtTrigger = mMapView.getModel().mapViewPosition.getZoomLevel();

			heatmapLayer = new Marker(target, null, 0, 0);
			mMapView.getLayerManager().getLayers().add(heatmapLayer);

			builder = new HeatmapBuilder(
					WifiDetailsMap.this, mMapView.getWidth(), mMapView.getHeight(), bbox,
					mMapView.getModel().mapViewPosition.getZoomLevel(), mMapView.getModel().displayModel.getScaleFactor(), mMapView.getModel().displayModel.getTileSize(), RADIUS).execute(points);
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
		if (mMapView.getModel().mapViewPosition.getZoomLevel() != zoomAtTrigger) {
			updatePending = false;
			Log.i(TAG, "Zoom level has changed - have to re-generate heat-map");
			clearLayer();
			proceedAfterHeatmapCompleted();
			return;
		}

		final BitmapDrawable drawable = new BitmapDrawable(backbuffer);
		final org.mapsforge.core.graphics.Bitmap mfBitmap = AndroidGraphicFactory.convertToBitmap(drawable);
		if (heatmapLayer != null && mfBitmap != null) {
			heatmapLayer.setBitmap(mfBitmap);
		} else {
			Log.w(TAG, "Skipped heatmap draw: either layer or bitmap is null");
		}
		updatePending = false;
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

		if (mMapView.getLayerManager().getLayers().indexOf(heatmapLayer) != -1) {
			mMapView.getLayerManager().getLayers().remove(heatmapLayer);
			heatmapLayer = null;
		}
	}


	/**
	 * Initializes map components
	 */
	@SuppressLint("NewApi")
	private void initMap() {
		this.mTileCache = createTileCache();

		if (MapUtils.hasOfflineMap(this.getActivity())) {
			addOfflineLayer();
		} else if (MapUtils.useOnlineMaps(this.getActivity())) {
			Toast.makeText(this.getActivity(), R.string.info_using_online_map, Toast.LENGTH_LONG).show();
			addOnlineLayer();
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					PreferenceManager.getDefaultSharedPreferences(getActivity()).edit().putString(Preferences.KEY_MAP_FILE, Preferences.VAL_MAP_ONLINE).commit();
					addOnlineLayer();
				}
			});
			builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					// User cancelled the dialog
				}
			});
			AlertDialog dialog = builder.create();
		}

		// register for layout finalization - we need this to get width and height
		final ViewTreeObserver vto = mMapView.getViewTreeObserver();
		vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

			@SuppressLint("NewApi")
			@Override
			public void onGlobalLayout() {

				layoutInflated = true;
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
		this.mapObserver = new Observer() {
			@Override
			public void onChange() {

				final byte newZoom = mMapView.getModel().mapViewPosition.getZoomLevel();
				if (newZoom != currentZoom) {
					// update overlays on zoom level changed
					Log.i(TAG, "New zoom level " + newZoom + ", reloading map objects");
					Log.i(TAG, "Update" + updatePending);
					// cancel pending heat-maps
					//if (builder != null) {
					//	builder.cancel(true);
					//}

					// if another update is pending, wait for complete
					if (!updatePending) {
						clearLayer();
						proceedAfterHeatmapCompleted();
					}
					currentZoom = newZoom;
				}
			}
		};
		this.mMapView.getModel().mapViewPosition.addObserver(mapObserver);

		this.mMapView.setClickable(true);
		this.mMapView.getMapScaleBar().setVisible(true);

		/*
			Layers layers = layerManager.getLayers();
			// remove all layers including base layer
			layers.clear();
		 */

		this.mMapView.getModel().mapViewPosition.setZoomLevel((byte) 16);

	}

	/**
	 * Creates a separate tile cache
	 * @return
	 */
	protected final TileCache createTileCache() {
		return AndroidUtil.createTileCache(this.getActivity(), "mapcache", mMapView.getModel().displayModel.getTileSize(), 1f, this.mMapView.getModel().frameBufferModel.getOverdrawFactor());
	}

	private void addOfflineLayer() {
		final Layer offlineLayer = MapUtils.createTileRendererLayer(
				this.mTileCache,
				this.mMapView.getModel().mapViewPosition,
				MapUtils.getMapFile(this.getActivity()),
				MapUtils.getRenderTheme(this.getActivity()));

		if (offlineLayer != null) this.mMapView.getLayerManager().getLayers().add(offlineLayer);
	}

	private void addOnlineLayer() {
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
}
