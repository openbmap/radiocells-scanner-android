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
import java.util.ArrayList;
import java.util.List;

import org.mapsforge.android.maps.MapActivity;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.overlay.Circle;
import org.mapsforge.android.maps.overlay.ListOverlay;
import org.mapsforge.android.maps.overlay.OverlayItem;
import org.mapsforge.android.maps.overlay.PolygonalChain;
import org.mapsforge.android.maps.overlay.Polyline;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.GeoPoint;
import org.mapsforge.core.model.MapPosition;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.model.WifiRecord;
import org.openbmap.utils.GpxMapObjectsLoader;
import org.openbmap.utils.GpxMapObjectsLoader.OnGpxLoadedListener;
import org.openbmap.utils.SessionMapObjectsLoader;
import org.openbmap.utils.SessionMapObjectsLoader.OnSessionLoadedListener;
import org.openbmap.utils.WifiCatalogMapObjectsLoader;
import org.openbmap.utils.WifiCatalogMapObjectsLoader.OnCatalogLoadedListener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * Activity for displaying session's GPX track and wifis
 */
public class MapViewActivity extends MapActivity implements
OnCatalogLoadedListener,
OnSessionLoadedListener,
OnGpxLoadedListener {

	private static final String TAG = MapActivity.class.getSimpleName();

	/**
	 * Settings for overlay circles
	 */
	private static final int COLOR_WIFI_CATALOG	= Color.GRAY;
	private static final int COLOR_SESSION	= Color.BLUE;

	private static final int ALPHA_WIFI_CATALOG_FILL	= 90;
	private static final int ALPHA_WIFI_CATALOG_STROKE	= 100;

	private static final int ALPHA_SESSION_FILL	= 50;
	private static final int ALPHA_SESSION_STROKE	= 100;

	private static final int CIRCLE_WIFI_CATALOG_WIDTH = 20;	
	private static final int CIRCLE_SESSION_WIDTH = 30;	

	/**
	 * Keeps the SharedPreferences.
	 */
	private SharedPreferences prefs = null;

	/**
	 * Database helper for retrieving session wifi scan results.
	 */
	private DataHelper dbHelper;

	/**
	 * 	Minimum distance in meters between automatic overlay refresh
	 */
	protected static final float OVERLAY_REFRESH_DISTANCE = 100;

	/**
	 * 	Minimum distance in meters between gpx refresh
	 */
	protected static final float GPX_REFRESH_DISTANCE = 0.2f;

	/**
	 * Another wifi catalog overlay refresh is taking place
	 */
	private boolean	mRefreshCatalogPending = false;

	/**
	 * Another session overlay refresh is taking place
	 */
	private boolean	mRefreshSessionPending = false;

	/**
	 * Direction marker is currently updated
	 */
	private boolean	mRefreshDirectionPending;
	
	/**
	 * Another gpx overlay refresh is taking place
	 */
	private boolean	mRefreshGpxPending;

	/**
	 * When isChecked map view will automatically focus current location
	 */
	private ToggleButton mSnapToLocation;

	/**
	 * Location where last overlay refresh took place
	 */
	private Location mOverlayDrawnAt;

	/**
	 * Location where last gpx refresh took place
	 */
	private Location mGpxDrawnAt;

	/**
	 * MapView
	 */
	private MapView mMapView;

	/**
	 * Overlay for wifis from OpenBmap
	 */
	private ListOverlay mWifiCatalogOverlay;

	/**
	 * Overlay for session wifis
	 */
	private ListOverlay mSessionOverlay;

	/**
	 * Overlay for gpx track
	 */
	private ListOverlay mGpxOverlay;

	/**
	 * Receives GPS location updates.
	 */
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		private Location location;

		@Override
		public void onReceive(final Context context, final Intent intent) {
			// handling GPS broadcasts
			if (RadioBeacon.INTENT_BROADCAST_POSITION.equals(intent.getAction())) {
				location = intent.getExtras().getParcelable("android.location.Location");

				// create a GeoPoint with the latitude and longitude coordinates
				GeoPoint currentPos = new GeoPoint(location.getLatitude(), location.getLongitude());

				// if SnapToLocation set, set new map center
				if (mSnapToLocation.isChecked() && mMapView != null) {
					mMapView.getMapViewPosition().setCenter(currentPos);
				}

				if (isValidLocation(location)) {
					/*
					 * Update overlays
					 * Overlay refresh won't be triggered, if in free-move mode, i.e. snap to location deactivated
					 */
					if (mSnapToLocation.isChecked() && isOverlayDrawDistance(location, mOverlayDrawnAt)) {
						drawOverlays(location);
					}
					if (mSnapToLocation.isChecked() && isGpxDrawDistance(location, mGpxDrawnAt)) {	
						drawGpxTrace(location);
					}

					// indicate bearing
					drawDirectionIndicator(location);

				} else {
					Log.e(TAG, "Invalid positon! Cycle skipped");
				}

				location = null;
			} 
		}

		private boolean isGpxDrawDistance(final Location current, final Location old) {
			return current.distanceTo(old) > GPX_REFRESH_DISTANCE;
		}

		/**
		 * @return
		 */
		private boolean isOverlayDrawDistance(final Location current, final Location old) {
			return current.distanceTo(old) > OVERLAY_REFRESH_DISTANCE;
		}

		private boolean isValidLocation(final Location location) {
			if (location == null) {
				return false;
			}
			if (location.getLatitude() == 0 && location.getLongitude() == 0) {
				return false;
			}
			return true;
		}
	};

	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// get shared preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		setContentView(R.layout.mapview);

		// force automatic overlay refresh by setting mOverlayDrawnAt to default values (lat 0 long 0)
		mOverlayDrawnAt = new Location("DUMMY");
		mGpxDrawnAt = new Location("DUMMY");

		// Register our gps broadcast mReceiver
		registerReceiver();
		initUi();

	}

	@Override
	protected final void onResume() {
		super.onResume();

		initDatabases();
		initMap();
		registerReceiver();

		if (this.getIntent().hasExtra(Schema.COL_ID)) {
			int zoomOntoWifi = this.getIntent().getExtras().getInt(Schema.COL_ID);
			Log.d(TAG, "Zooming onto " + zoomOntoWifi);
			if (zoomOntoWifi != 0) {
				loadSingleObject(zoomOntoWifi);
			}
		}
	}

	@Override
	protected final void onPause() {
		releaseMap();
		unregisterReceiver();
		super.onPause();
	}

	/**
	 * Configures database connections for well-known wifis database as well
	 * as database helper to retrieve current wifi scan results.
	 */
	private void initDatabases() {
		dbHelper = new DataHelper(this);
	}

	/**
	 * Initializes UI componensts
	 */
	private void initUi() {
		mSnapToLocation = (ToggleButton) findViewById(R.id.mapview_tb_snap_to_location);
		mMapView = (MapView) findViewById(R.id.mvMap);
	}

	/**
	 * Initializes map view
	 * TODO: proper error handling, if file doesn't exist
	 */
	private void initMap() {
		mMapView = (MapView) findViewById(R.id.mvMap);
		File mapFile = new File(
				Environment.getExternalStorageDirectory().getPath()
				+ prefs.getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR)
				+ Preferences.MAPS_SUBDIR, 
				prefs.getString(Preferences.KEY_MAP_FILE, Preferences.VAL_MAP_FILE));

		if (mapFile.exists()) {
			mMapView.setClickable(true);
			mMapView.setBuiltInZoomControls(true);
			mMapView.setMapFile(mapFile);
		} else {
			Log.i(TAG, "No map file found!");
			Toast.makeText(this.getBaseContext(), R.string.please_select_map, Toast.LENGTH_LONG).show();
			mMapView.setClickable(false);
			mMapView.setBuiltInZoomControls(false);
			mMapView.setVisibility(View.GONE);
		}

		mMapView.requestLayout();
		mWifiCatalogOverlay = new ListOverlay();
		mSessionOverlay = new ListOverlay();
		mGpxOverlay = new ListOverlay();

		mMapView.getOverlays().add(mWifiCatalogOverlay);
		mMapView.getOverlays().add(mSessionOverlay);
		mMapView.getOverlays().add(mGpxOverlay);
	}

	/**
	 * Sets map-related object to null to enable garbage collection.
	 */
	private void releaseMap() {
		Log.d(TAG, "Releasing map components");
		mWifiCatalogOverlay = null;
		mSessionOverlay = null;
		mGpxOverlay = null;
		mMapView = null;
	}


	private void registerReceiver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(RadioBeacon.INTENT_BROADCAST_POSITION);
		registerReceiver(mReceiver, filter);		
	}

	/**
	 * Unregisters receivers for GPS and wifi scan results.
	 */
	private void unregisterReceiver() {
		try {
			unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-mReceiver-is-registered-in-android}
			return;
		}
	}

	/**
	 * Refreshes reference and session overlays.
	 * If another refresh is in progress, update is skipped.
	 * @param location
	 */
	protected final void drawOverlays(final Location location) {
		if (!mRefreshCatalogPending) {
			Log.d(TAG, "Updating wifi catalog overlay. Distance to last refresh " + location.distanceTo(mOverlayDrawnAt));
			mRefreshCatalogPending = true;
			loadCatalog();
			mOverlayDrawnAt = location;
		} else {
			Log.v(TAG, "Reference overlay refresh in progress. Skipping refresh..");
		}

		if (!mRefreshSessionPending) {
			Log.d(TAG, "Updating session overlay. Distance to last refresh " + location.distanceTo(mOverlayDrawnAt));
			mRefreshSessionPending = true;
			loadSessionObjects(null);
			mOverlayDrawnAt = location;
		} else {
			Log.v(TAG, "Session overlay refresh in progress. Skipping refresh..");
		}
	}

	/**
	 * @param location
	 */
	private void drawGpxTrace(final Location location) {
		if (!mRefreshGpxPending) {
			Log.d(TAG, "Updating gpx overlay. Distance to last refresh " + location.distanceTo(mOverlayDrawnAt));
			mRefreshGpxPending = true;
			loadGpxObjects();
			mGpxDrawnAt = location;
		} else {
			Log.v(TAG, "Gpx overlay refresh in progress. Skipping refresh..");
		}
	}

	/**
	 * Loads reference wifis around location from openbmap wifi catalog.
	 * Callback function, upon completion onCatalogLoaded is called for drawing
	 * @param center
	 * 			For performance reasons only wifis around specified location are displayed.
	 */
	private void loadCatalog() {
		BoundingBox bbox = mMapView.getMapViewPosition().getBoundingBox();

		// Load known wifis asynchronous
		WifiCatalogMapObjectsLoader task = new WifiCatalogMapObjectsLoader(this);
		task.execute(bbox.minLatitude, bbox.maxLatitude, bbox.minLongitude, bbox.maxLongitude);

	}

	/* (non-Javadoc)
	 * @see org.openbmap.utils.WifiCatalogMapObjectsLoader.OnCatalogLoadedListener#onComplete(java.util.ArrayList)
	 */
	@Override
	public final void onCatalogLoaded(final ArrayList<GeoPoint> points) {
		if (mWifiCatalogOverlay == null) {
			Log.w(TAG, "Catalog overlay is null. Not yet initialized?");
			return;
		}

		List<OverlayItem> overlayItems = mWifiCatalogOverlay.getOverlayItems();
		overlayItems.clear();
		for (int i = 0; i < points.size(); i++) {
			overlayItems.add(createCatalogSymbol((GeoPoint) points.get(i)));
		}

		MapPosition pos = mMapView.getMapViewPosition().getMapPosition();
		mMapView.getMapViewPosition().setMapPosition(pos);
		Log.d(TAG, "Loading ref wifis completed");

		// enable next refresh
		mRefreshCatalogPending = false;
	}

	/**
	 * Loads session wifis in visible range.
	 * Will call onSessionLoaded callback upon completion
	 * @param highlight
	 * 			If highlight is specified only this wifi is displayed
	 */
	private void loadSessionObjects(final WifiRecord highlight) {

		BoundingBox bbox = mMapView.getMapViewPosition().getBoundingBox();
		if (highlight == null) {
			// load all session wifis
			SessionMapObjectsLoader task = new SessionMapObjectsLoader(this);
			task.execute(bbox.minLatitude, bbox.maxLatitude, bbox.minLongitude, bbox.maxLatitude, null);
		} else {
			// draw specific wifi
			SessionMapObjectsLoader task = new SessionMapObjectsLoader(this);
			task.execute(bbox.minLatitude, bbox.maxLatitude, bbox.minLongitude, bbox.maxLatitude, highlight);
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.utils.SessionMapObjectsLoader.OnSessionLoadedListener#onSessionLoaded(java.util.ArrayList)
	 */
	@Override
	public final void onSessionLoaded(final ArrayList<GeoPoint> points) {
		if (mSessionOverlay == null) {
			Log.w(TAG, "Session overlay is null. Not yet initialized?");
			return;
		}

		List<OverlayItem> overlayItems = mSessionOverlay.getOverlayItems();

		overlayItems.clear();
		for (int i = 0; i < points.size(); i++) {
			overlayItems.add(createSessionSymbol((GeoPoint) points.get(i)));
		}

		// if we have just loaded on point, set map center
		if (points.size() == 1) {
			mMapView.getMapViewPosition().setCenter((GeoPoint) points.get(0));
		}

		Log.d(TAG, "Loading session wifis completed");
		// enable next refresh
		mRefreshSessionPending = false;
	}

	/*
	 * Loads gpx points in visible range.
	 */
	private void loadGpxObjects() {
		BoundingBox bbox = mMapView.getMapViewPosition().getBoundingBox();
		GpxMapObjectsLoader task = new GpxMapObjectsLoader(this);
		task.execute(bbox.minLatitude, bbox.maxLatitude, bbox.minLongitude, bbox.maxLatitude);
	}

	/**
	 * Callback function for loadGpxObjects()
	 */
	@Override
	public final void onGpxLoaded(final ArrayList<GeoPoint> points) {
		if (mGpxOverlay == null) {
			Log.w(TAG, "Gpx overlay is null. Not yet initialized?");
			return;
		}

		List<OverlayItem> overlayItems = mGpxOverlay.getOverlayItems();

		Paint paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
		paintStroke.setStyle(Paint.Style.STROKE);
		paintStroke.setColor(Color.BLACK);
		paintStroke.setAlpha(255);
		paintStroke.setStrokeWidth(3);
		paintStroke.setStrokeCap(Cap.ROUND);
		paintStroke.setStrokeJoin(Paint.Join.ROUND);

		PolygonalChain chain = new PolygonalChain(points);
		Polyline line = new Polyline(chain, paintStroke);
		overlayItems.add(line);

		mRefreshGpxPending = false;
	}

	/**
	 * Highlights single wifi on map.
	 * @param id wifi id to highlight
	 */
	public final void loadSingleObject(final int id) {
		Log.d(TAG, "Adding selected wifi to overlay: " + id);

		WifiRecord wifi = dbHelper.loadWifiById(id);

		if (wifi != null) {
			loadSessionObjects(wifi);
		}
	}

	/**
	 * Draws arrow in direction of travel. If bearing is unavailable a generic position symbol is used.
	 * If another refresh is taking place, update is skipped
	 */
	private void drawDirectionIndicator(final Location location) {
		// ensure that previous refresh has been finished
		if (mRefreshDirectionPending) {
			return;
		}
		mRefreshDirectionPending = true;

		ImageView iv = (ImageView) findViewById(R.id.position_marker);

		if (location.hasBearing()) {
			// determine which drawable we currently use
			Integer id = (Integer) iv.getTag();
			id = id == null ? 0 : id;
			// refresh only if needed
			if (id != R.drawable.arrow) {
				iv.setImageResource(R.drawable.arrow);
			}

			// rotate arrow
			Matrix matrix = new Matrix();
			iv.setScaleType(ScaleType.MATRIX);   //required
			matrix.postRotate((float) location.getBearing(), iv.getWidth() / 2f, iv.getHeight() / 2f);
			iv.setImageMatrix(matrix);
		} else {
			// determine which drawable we currently use
			Integer id = (Integer) iv.getTag();
			id = id == null ? 0 : id;
			// refresh only if needed
			if (id != R.drawable.cross) {
				iv.setImageResource(R.drawable.cross);
			}

			Log.i(TAG, "Can't draw direction marker: no bearing provided");
		}
		mRefreshDirectionPending = false;
	}

	/**
	 * Creates reference overlay circle
	 * @param geoPoint
	 * @return overlay circle
	 */
	private static Circle createCatalogSymbol(final GeoPoint geoPoint) {
		Paint paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
		paintFill.setStyle(Paint.Style.FILL);
		paintFill.setColor(COLOR_WIFI_CATALOG);
		paintFill.setAlpha(ALPHA_WIFI_CATALOG_FILL);
		paintFill.setAntiAlias(true);

		Paint paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
		paintStroke.setStyle(Paint.Style.STROKE);
		paintStroke.setColor(COLOR_WIFI_CATALOG);
		paintStroke.setAlpha(ALPHA_WIFI_CATALOG_STROKE);
		paintStroke.setStrokeWidth(1);
		paintStroke.setStrokeCap(Cap.ROUND);
		paintStroke.setStrokeJoin(Paint.Join.ROUND);

		return new Circle(geoPoint, CIRCLE_WIFI_CATALOG_WIDTH, paintFill, paintStroke);
	}

	/**
	 * Creates session overlay circle
	 * @param geoPoint
	 * @return overlay circle
	 */
	private static Circle createSessionSymbol(final GeoPoint geoPoint) {
		Paint paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
		paintFill.setStyle(Paint.Style.FILL);
		paintFill.setColor(COLOR_SESSION);
		paintFill.setAlpha(ALPHA_SESSION_FILL);
		paintFill.setAntiAlias(true);

		Paint paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
		paintStroke.setStyle(Paint.Style.STROKE);
		paintStroke.setColor(COLOR_SESSION);
		paintStroke.setAlpha(ALPHA_SESSION_STROKE);
		paintStroke.setStrokeWidth(1);
		paintStroke.setStrokeCap(Cap.ROUND);
		paintStroke.setStrokeJoin(Paint.Join.ROUND);

		return new Circle(geoPoint, CIRCLE_SESSION_WIDTH, paintFill, paintStroke);
	}

}

