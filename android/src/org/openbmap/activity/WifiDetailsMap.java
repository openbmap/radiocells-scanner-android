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

package org.openbmap.activity;

import java.io.File;

import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.AndroidPreferences;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.LayerManager;
import org.mapsforge.map.layer.Layers;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Circle;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.db.DataHelper;
import org.openbmap.db.RadioBeaconContentProvider;
import org.openbmap.db.Schema;
import org.openbmap.db.model.WifiRecord;
import org.openbmap.utils.MapUtils;

import android.content.SharedPreferences;
import android.database.Cursor;
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
import android.widget.ImageButton;
import android.widget.ToggleButton;

/**
 * Fragment for displaying cell detail information
 */
public class WifiDetailsMap extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>  {
	private static final String TAG = WifiDetailsMap.class.getSimpleName();

	private WifiRecord mWifiSelected;

	private static final int ALPHA_SESSION_FILL	= 20;

	/**
	 * Keeps the SharedPreferences.
	 */
	private SharedPreferences prefs = null;

	/**
	 * Database helper for retrieving session wifi scan results.
	 */
	private DataHelper dbHelper;

	/**
	 * 	Minimum time (in millis) between automatic overlay refresh
	 */
	protected static final float SESSION_REFRESH_INTERVAL = 2000;

	private static final int CIRCLE_SESSION_WIDTH = 30;	

	// [start] UI controls
	/**
	 * MapView
	 */
	private MapView mapView;

	/**
	 * When checked map view will automatically focus current location
	 */
	private ToggleButton btnSnapToLocation;

	private ImageButton	btnZoom;

	private ImageButton	btnUnzoom;
	//[end]

	// [start] Map styles
	/**
	 * Baselayer cache
	 */
	private TileCache tileCache;


	private Paint paintSessionFill;
	//[end]

	// [start] Dynamic map variables
	/**
	 * Used for persisting zoom and position settings onPause / onDestroy
	 */
	private AndroidPreferences	preferencesFacade;

	// [end]


	@Override
	public final void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);	

		// get shared preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

		mWifiSelected = ((WifiDetailsActivity) getActivity()).getWifi();

		initMap();

		getActivity().getSupportLoaderManager().initLoader(0, null, this); 
	}


	@Override
	public final View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.wifidetailsmap, container, false);
		return view;
	}

	@Override
	public final void onDestroy() {
		releaseMap();
		super.onDestroy();
	}


	/**
	 * Sets map-related object to null to enable garbage collection.
	 */
	private void releaseMap() {
		Log.i(TAG, "Releasing map components");
		// save map settings
		this.mapView.getModel().save(this.preferencesFacade);
		this.preferencesFacade.save();

		if (mapView != null) {
			mapView.destroy();
		}
	}

	@Override
	public final Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
		String[] bssid = {"-1"};
		if (mWifiSelected != null) {
			bssid[0] = mWifiSelected.getBssid();
		}

		String[] projection = { Schema.COL_ID, Schema.COL_SSID, Schema.COL_LEVEL,  "begin_" + Schema.COL_LATITUDE, "begin_" + Schema.COL_LONGITUDE};
		CursorLoader cursorLoader =
				new CursorLoader(getActivity().getBaseContext(),  RadioBeaconContentProvider.CONTENT_URI_WIFI_EXTENDED,
						projection, Schema.COL_BSSID + " = ?", bssid, Schema.COL_LEVEL + " ASC");
		return cursorLoader;
	}

	@Override
	public final void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
		if (cursor != null && cursor.getCount() > 0) {
			int colLat = cursor.getColumnIndex("begin_" + Schema.COL_LATITUDE);
			int colLon = cursor.getColumnIndex("begin_" + Schema.COL_LONGITUDE);
			int colLevel = cursor.getColumnIndex(Schema.COL_LEVEL);

			LatLong measurement = null;
			while (cursor.moveToNext()) {
				measurement = new LatLong(
						cursor.getDouble(colLat),
						cursor.getDouble(colLon));

				int level = cursor.getInt(colLevel);
				int customAlpha = ALPHA_SESSION_FILL;
				int customWidth = CIRCLE_SESSION_WIDTH;

				/**
				 * Heat map color set
				 *   0, -50:   0   0 255 --> blue
				 * -50, -80:   0 255   0 --> green
				 * -80, -99: 255   0   0 --> red
				 */

				int red = 0;
				int blue = 0;
				int green = 0;

				if (level < 0 && level >= -50) {
					Log.i(TAG, "[0..-50]");
					float factor = (float) (level / (-50.0));
					blue = (int) ((1 - factor) * 0 + factor * 255);
					green = (int) (factor * 0 + (1 - factor) * 255);
					red = 0;

					customAlpha = (int) (ALPHA_SESSION_FILL * 1.0 * cursor.getInt(colLevel) / (-50.0));
				} else if (level <= -50 && level > -80) {
					Log.i(TAG, "[-50..-80]");
					float factor = (float) (level / (-80.0));
					green = (int) ((1 - factor) * 0 + factor * 255);
					red = (int) (factor * 0 + (1 - factor) * 255);
					blue = 0;

					customAlpha = (int) (ALPHA_SESSION_FILL * 1.5 * cursor.getInt(colLevel) / (-80.0));
				} else if (level > -100){
					Log.i(TAG, "[-80..-100]");
					float factor = (float) (level / (-100.0));
					red = (int)((1 - factor) * 0 + factor * 255);
					green = 0;
					blue = 0;

					customAlpha = (int) (ALPHA_SESSION_FILL * 2.0 * cursor.getInt(colLevel) / (-100.0));
				}

				Log.i(TAG, "Level " + level + ":  R" + red + ", G" + green + ", B" + blue + ", ALPHA" + customAlpha);

				Paint paintWifi = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(customAlpha, red, green, blue), 2, Style.FILL);

				Paint paintStroke = null;
				if (cursor.isLast()) {
					// strongest signal with stroke
					paintStroke = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(255, red, green, blue), 6, Style.STROKE);
				}

				Circle circle = new Circle(measurement, customWidth, paintWifi, paintStroke);

				this.mapView.getLayerManager().getLayers().add(circle);
			}

			// focus last
			if (measurement != null) {
				this.mapView.getModel().mapViewPosition.setCenter(measurement);
			}

		}
	}

	/**
	 * Initializes map components
	 */
	private void initMap() {

		SharedPreferences sharedPreferences = getActivity().getSharedPreferences(getPersistableId(), android.content.Context.MODE_PRIVATE);
		preferencesFacade = new AndroidPreferences(sharedPreferences);

		this.mapView = (MapView) getView().findViewById(R.id.map);
		this.mapView.getModel().init(preferencesFacade);
		this.mapView.setClickable(true);
		this.mapView.getMapScaleBar().setVisible(true);
		this.tileCache = createTileCache();

		LayerManager layerManager = this.mapView.getLayerManager();
		Layers layers = layerManager.getLayers();

		// remove all layers including base layer
		layers.clear();

		this.mapView.getModel().mapViewPosition.setZoomLevel((byte) 16);

		layers.add(MapUtils.createTileRendererLayer(
				this.tileCache,
				this.mapView.getModel().mapViewPosition,
				getMapFile()));
	}

	/**
	 * Opens selected map file
	 * @return a map file
	 */
	protected final File getMapFile() {
		File mapFile = new File(
				Environment.getExternalStorageDirectory().getPath()
				+ prefs.getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR)
				+ Preferences.MAPS_SUBDIR, 
				prefs.getString(Preferences.KEY_MAP_FILE, Preferences.VAL_MAP_FILE));

		return mapFile;
	}


	/**
	 * @return the id that is used to save this mapview
	 */
	protected final String getPersistableId() {
		return this.getClass().getSimpleName();
	}

	protected final TileCache createTileCache() {
		return MapUtils.createExternalStorageTileCache(getActivity(), getPersistableId());
	}


	/* (non-Javadoc)
	 * @see android.support.v4.app.LoaderManager.LoaderCallbacks#onLoaderReset(android.support.v4.content.Loader)
	 */
	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		// TODO Auto-generated method stub

	}
}
