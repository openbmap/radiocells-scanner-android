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
import java.util.Iterator;

import org.mapsforge.core.graphics.Color;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.AndroidPreferences;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.LayerManager;
import org.mapsforge.map.layer.Layers;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Circle;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.model.MapViewPosition;
import org.mapsforge.map.model.common.Observer;
import org.mapsforge.map.util.MapPositionUtil;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.utils.GpxMapObjectsLoader;
import org.openbmap.utils.GpxMapObjectsLoader.OnGpxLoadedListener;
import org.openbmap.utils.LatLongHelper;
import org.openbmap.utils.MapUtils;
import org.openbmap.utils.SessionLatLong;
import org.openbmap.utils.SessionMapObjectsLoader;
import org.openbmap.utils.SessionMapObjectsLoader.OnSessionLoadedListener;
import org.openbmap.utils.WifiCatalogMapObjectsLoader;
import org.openbmap.utils.WifiCatalogMapObjectsLoader.OnCatalogLoadedListener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ToggleButton;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragment;

/**
 * Activity for displaying session's GPX track and wifis
 */
public class MapViewActivity extends SherlockFragment implements
OnCatalogLoadedListener,
OnSessionLoadedListener,
OnGpxLoadedListener,
ActionBar.OnNavigationListener {

	private static final String TAG = MapViewActivity.class.getSimpleName();
	
	/**
	 * Layers (Session layer + catalog layer, session layer only)
	 */
	public enum LayersDisplayed { ALL, SESSION_ONLY};

	/**
	 * If zoom level < MIN_OBJECT_ZOOM session wifis and wifi catalog objects won't be displayed for performance reasons
	 */
	private static final int MIN_OBJECT_ZOOM = 12;

	private static final int ALPHA_WIFI_CATALOG_FILL = 90;
	private static final int ALPHA_WIFI_CATALOG_STROKE = 100;

	private static final int ALPHA_SESSION_FILL = 50;
	private static final int ALPHA_OTHER_SESSIONS_FILL = 35;

	/**
	 * Circle size current session objects
	 */
	private static final int CIRCLE_SESSION_WIDTH = 30; 
	private static final int CIRCLE_OTHER_SESSION_WIDTH = 15; 
	private static final int CIRCLE_WIFI_CATALOG_WIDTH = 15; 

	private static final int STROKE_GPX_WIDTH = 5;

	/**
	 * Keeps the SharedPreferences.
	 */
	private SharedPreferences prefs = null;

	/**
	 * Database helper for retrieving session wifi scan results.
	 */
	private DataHelper dbHelper;

	/**
	 *  Minimum time (in millis) between automatic layer refresh
	 */
	protected static final float SESSION_REFRESH_INTERVAL = 2000;

	/**
	 * Minimum distance (in meter) between automatic session layer refresh
	 */
	protected static final float SESSION_REFRESH_DISTANCE = 10;


	/**
	 * Minimum distance (in meter) between automatic catalog layer refresh
	 * Please note: catalog objects are static, thus updates aren't necessary that often
	 */
	protected static final float CATALOG_REFRESH_DISTANCE = 200;

	/**
	 *  Minimum time (in millis) between automatic catalog refresh
	 *  Please note: catalog objects are static, thus updates aren't necessary that often
	 */
	protected static final float CATALOG_REFRESH_INTERVAL = 5000;

	/**
	 *  Minimum time (in millis) between gpx position refresh
	 */
	protected static final float GPX_REFRESH_INTERVAL = 1000;

	/**
	 * Load more than currently visible objects?
	 */
	private static final boolean PREFETCH_MAP_OBJECTS = true;

	/**
	 * Session currently displayed
	 */
	private int mSessionId;

	/**
	 * System time of last gpx refresh (in millis)
	 */
	private long gpxRefreshTime;

	private byte lastZoom;

	// [start] UI controls
	/**
	 * MapView
	 */
	private MapView mapView;

	/**
	 * When checked map view will automatically focus current location
	 */
	private static ToggleButton btnSnapToLocation;

	/**
	 * Zoom button
	 */
	private ImageButton btnZoom;

	/**
	 * Un-zoom button
	 */
	private ImageButton btnUnzoom;

	//[end]

	// [start] Map styles
	/**
	 * Baselayer cache
	 */
	private TileCache tileCache;

	private Paint paintCatalogFill;

	private Paint paintCatalogStroke;

	/** 
	 * Paint style for active sessions objects
	 */
	private Paint paintActiveSessionFill;

	/** 
	 * Paint style for objects from other sessions
	 */
	private Paint paintOtherSessionFill;

	private ArrayList<Layer> catalogObjects;

	private ArrayList<Layer> sessionObjects;

	private Polyline gpxObjects;
	//[end]

	// [start] Dynamic map variables
	/**
	 * Used for persisting zoom and position settings onPause / onDestroy
	 */
	private AndroidPreferences preferencesFacade;

	/**
	 * Observes zoom and map movements (for triggering layer updates)
	 */
	private Observer mapObserver;

	/**
	 * Wifi catalog layer is currently refreshing
	 */
	private boolean mRefreshCatalogPending = false;

	/**
	 * Session layer is currently refreshing
	 */
	private boolean mRefreshSessionPending = false;

	/**
	 * Direction marker is currently updated
	 */
	private boolean mRefreshDirectionPending;

	/**
	 * Gpx layer is currently refreshing
	 */
	private boolean mRefreshGpxPending;

	/**
	 * System time of last session layer refresh (in millis)
	 */
	private long sessionObjectsRefreshTime;

	/**
	 * System time of last catalog layer refresh (in millis)
	 */
	private long catalogObjectsRefreshTime;

	/**
	 * Location of last session layer refresh
	 */
	private Location sessionObjectsRefreshedAt = new Location("DUMMY");

	/**
	 * Location of last session layer refresh
	 */
	private Location catalogObjectsRefreshedAt = new Location("DUMMY");
	// [end]

	/**
	 * Receives GPS location updates.
	 */
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			// handling GPS broadcasts
			if (RadioBeacon.INTENT_POSITION_UPDATE.equals(intent.getAction())) {
				Location location = intent.getExtras().getParcelable("android.location.Location");

				if (mapView == null) {
					Log.wtf(TAG, "Map view is null");
					return;
				}
				
				// if btnSnapToLocation is checked, move map
				if (btnSnapToLocation.isChecked()) {
					LatLong currentPos = new LatLong(location.getLatitude(), location.getLongitude());
					mapView.getModel().mapViewPosition.setCenter(currentPos);
				}

				// update layers
				if (LatLongHelper.isValidLocation(location)) {
					/*
					 * Update layers if necessary, but only if
					 * 1.) current zoom level >= 12 (otherwise single points not visible, huge performance impact)
					 * 2.) layer items haven't been refreshed for a while AND user has moved a bit
					 */
					if ((mapView.getModel().mapViewPosition.getZoomLevel() >= MIN_OBJECT_ZOOM) && (sessionLayerOutdated(location))) { 
						refreshSessionLayer(location);
					}

					if ((mapView.getModel().mapViewPosition.getZoomLevel() >= MIN_OBJECT_ZOOM) &&
							catalogLayerSelected() &&
							catalogLayerOutdated(location)) { 
						refreshCatalogLayer(location);
					}

					if (gpxLayerOutdated()) { 
						refreshGpxTrace(location);
					}

					// indicate bearing
					refreshCompass(location);

				} else {
					Log.e(TAG, "Invalid positon! Cycle skipped");
				}

				location = null;
			} 
		}
	};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.mapview, container, false);

		initUi(view);
		initMap(view);

		return view;
	}

	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// get shared preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity());

		// Register our gps broadcast mReceiver
		registerReceiver();

		catalogObjects = new ArrayList<Layer>();
		sessionObjects = new ArrayList<Layer>();
		gpxObjects = new Polyline(MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(Color.BLACK), STROKE_GPX_WIDTH,
				Style.STROKE), AndroidGraphicFactory.INSTANCE);
	}

	@Override
	public final void onResume() {
		super.onResume();

		initDb();

		registerReceiver();

		if (getSherlockActivity().getIntent().hasExtra(Schema.COL_ID)) {
			int focusWifi = getSherlockActivity().getIntent().getExtras().getInt(Schema.COL_ID);
			Log.d(TAG, "Zooming onto " + focusWifi);
			if (focusWifi != 0) {
				loadSingleObject(focusWifi);
			}
		}
	}

	@Override
	public final void onPause() {
		//releaseMap();
		clearCatalogLayer();
		clearSessionLayer();

		unregisterReceiver();

		super.onPause();
	}

	@Override
	public final void onDestroy() {
		releaseMap();
		super.onDestroy();
	}

	private void initDb() {
		dbHelper = new DataHelper(getSherlockActivity());
		mSessionId = dbHelper.getActiveSessionId();

		if (mSessionId != RadioBeacon.SESSION_NOT_TRACKING) {
			Log.i(TAG, "Displaying session " + mSessionId);
		} else {
			Log.w(TAG, "No active session?");
		}
	}

	/**
	 * Initializes map components
	 */
	private void initMap(View view) {

		SharedPreferences sharedPreferences = getSherlockActivity().getSharedPreferences(getPersistableId(), /*MODE_PRIVATE*/ 0);
		preferencesFacade = new AndroidPreferences(sharedPreferences);

		this.mapView = (MapView) view.findViewById(R.id.map);
		this.mapView.getModel().init(preferencesFacade);
		this.mapView.setClickable(true);
		this.mapView.getMapScaleBar().setVisible(true);
		this.tileCache = createTileCache();

		// on first start zoom is set to very low value, so users won't see anything
		// zoom to moderate zoomlevel..
		if (this.mapView.getModel().mapViewPosition.getZoomLevel() < (byte) 10) {
			this.mapView.getModel().mapViewPosition.setZoomLevel((byte) 15);
		}

		LayerManager layerManager = this.mapView.getLayerManager();
		Layers layers = layerManager.getLayers();

		// remove all layers including base layer
		layers.clear();

		layers.add(MapUtils.createTileRendererLayer(
				this.tileCache,
				this.mapView.getModel().mapViewPosition,
				getMapFile()));

		this.mapObserver = new Observer() {
			@Override
			public void onChange() {

				byte zoom = mapView.getModel().mapViewPosition.getZoomLevel();
				if (zoom != lastZoom && zoom >= MIN_OBJECT_ZOOM) {
					// Zoom level changed
					Log.i(TAG, "New zoom level " + zoom + ", reloading map objects");
					refreshAllLayers();

					lastZoom = zoom;
				}

				if (!btnSnapToLocation.isChecked()) {
					// Free-move mode
					LatLong tmp = mapView.getModel().mapViewPosition.getCenter();
					Location position = new Location("DUMMY");
					position.setLatitude(tmp.latitude);
					position.setLongitude(tmp.longitude);

					if (sessionLayerOutdated(position)) {
						refreshSessionLayer(position);
					}

					if (catalogLayerSelected() && catalogLayerOutdated(position)) {
						refreshCatalogLayer(position);
					} else {
						clearCatalogLayer();
					}
				}

			}

		};
		mapView.getModel().mapViewPosition.addObserver(mapObserver);
	}

	/**
	 * Initializes UI componensts
	 * @param view 
	 */
	private void initUi(View view) {
		/*
		Context context = getSherlockActivity().getSupportActionBar().getThemedContext();
		layerList = ArrayAdapter.createFromResource(getSherlockActivity(),
				R.array.layers, android.R.layout.simple_spinner_item);

		layerList.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);

		getSherlockActivity().getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		getSherlockActivity().getSupportActionBar().setListNavigationCallbacks(layerList, this);
		*/
		
		/*      
		btnLayerSelection = (Spinner) view.findViewById(R.id.mapview_layers_selection);
		btnLayerSelection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			public void onItemSelected(final AdapterView<?> parent, final View view, final int pos, final long id) {
				Location mapCenter = new Location("DUMMY");
				mapCenter.setLatitude(mapView.getModel().mapViewPosition.getCenter().latitude);
				mapCenter.setLongitude(mapView.getModel().mapViewPosition.getCenter().longitude);
				refreshSessionOverlays(mapCenter);

				if (catalogLayerSelected()) {
					refreshCatalogOverlay(mapCenter);
				} else {
					clearCatalogLayer();
				}
			}
			public void onNothingSelected(final AdapterView<?> parent) {
			}
		});

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getSherlockActivity(),
				R.array.layers, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		btnLayerSelection.setAdapter(adapter);
		 */
		btnSnapToLocation = (ToggleButton) view.findViewById(R.id.mapview_tb_snap_to_location);
		btnZoom = (ImageButton) view.findViewById(R.id.btnZoom);  
		btnZoom.setOnClickListener(new OnClickListener() { 
			@Override
			public void onClick(final View v) {
				byte zoom  = mapView.getModel().mapViewPosition.getZoomLevel();
				if (zoom < mapView.getModel().mapViewPosition.getZoomLevelMax()) {
					mapView.getModel().mapViewPosition.setZoomLevel((byte) (zoom + 1));
				}
			}
		});

		btnUnzoom = (ImageButton) view.findViewById(R.id.btnUnzoom);
		btnUnzoom.setOnClickListener(new OnClickListener() { 
			@Override
			public void onClick(final View v) {
				byte zoom  = mapView.getModel().mapViewPosition.getZoomLevel();
				if (zoom > mapView.getModel().mapViewPosition.getZoomLevelMin()) {
					mapView.getModel().mapViewPosition.setZoomLevel((byte) (zoom - 1));
				}
			}
		});
		paintCatalogFill = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_WIFI_CATALOG_FILL, 120, 150, 120), 2, Style.FILL);
		paintCatalogStroke = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_WIFI_CATALOG_STROKE, 120, 150, 120), 2, Style.STROKE);
		paintActiveSessionFill = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_SESSION_FILL, 0, 0, 255), 2, Style.FILL);
		paintOtherSessionFill = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_OTHER_SESSIONS_FILL, 255, 0, 255), 2, Style.FILL);
	}

	private void registerReceiver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(RadioBeacon.INTENT_POSITION_UPDATE);
		getSherlockActivity().registerReceiver(mReceiver, filter);  
	}

	/**
	 * Unregisters receivers for GPS and wifi scan results.
	 */
	private void unregisterReceiver() {
		try {
			getSherlockActivity().unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-mReceiver-is-registered-in-android}
			return;
		}
	}

	/**
	 * 
	 */
	private void refreshAllLayers() {
		Location mapCenter = new Location("DUMMY");
		mapCenter.setLatitude(mapView.getModel().mapViewPosition.getCenter().latitude);
		mapCenter.setLongitude(mapView.getModel().mapViewPosition.getCenter().longitude);
		refreshSessionLayer(mapCenter);

		if (catalogLayerSelected()) {
			refreshCatalogLayer(mapCenter);
		} else {
			clearCatalogLayer();
		}
	}

	/**
	 * Refreshes catalog layer, if not already refreshing
	 * @param location
	 */
	protected final void refreshCatalogLayer(final Location location) {
		if (!mRefreshCatalogPending) {
			Log.d(TAG, "Updating wifi catalog layer");
			mRefreshCatalogPending = true;
			proceedAfterCatalogLoaded();
			catalogObjectsRefreshedAt = location;
			catalogObjectsRefreshTime = System.currentTimeMillis();
		} else {
			Log.v(TAG, "Wifi catalog layer is refreshing. Skipping refresh..");
		}
	}

	/**
	 * 
	 */
	private void clearSessionLayer() {
		for (Iterator<Layer> iterator = sessionObjects.iterator(); iterator.hasNext();) {
			Layer layer = (Layer) iterator.next();
			this.mapView.getLayerManager().getLayers().remove(layer);
		}
		sessionObjects.clear();
		
	}

	/**
	 * Clears catalog layer objects
	 */
	private void clearCatalogLayer() {
		for (Iterator<Layer> iterator = catalogObjects.iterator(); iterator.hasNext();) {
			Layer layer = (Layer) iterator.next();
			this.mapView.getLayerManager().getLayers().remove(layer);
		}
		catalogObjects.clear();
	}

	/**
	 * Is new location far enough from last refresh location?
	 * @return true if catalog layer needs refresh
	 */
	private boolean catalogLayerOutdated(final Location current) {
		if (current == null) { 
			// fail safe: draw if something went wrong
			return true;
		}

		return (
				(catalogObjectsRefreshedAt.distanceTo(current) > CATALOG_REFRESH_DISTANCE)
				&& ((System.currentTimeMillis() - catalogObjectsRefreshTime) > CATALOG_REFRESH_INTERVAL)
				);
	}

	/**
	 * Loads reference wifis around location from openbmap wifi catalog.
	 * Callback function, upon completion onCatalogLoaded is called for drawing
	 * @param center
	 *    For performance reasons only wifis around specified location are displayed.
	 */
	private void proceedAfterCatalogLoaded() {

		BoundingBox bbox = MapPositionUtil.getBoundingBox(
				mapView.getModel().mapViewPosition.getMapPosition(),
				mapView.getDimension());

		double minLatitude = bbox.minLatitude;
		double maxLatitude = bbox.maxLatitude;
		double minLongitude = bbox.minLongitude;
		double maxLongitude = bbox.maxLongitude;

		// query more than visible objects for smoother data scrolling / less database queries?
		if (PREFETCH_MAP_OBJECTS) {
			double latSpan = maxLatitude - minLatitude;
			double lonSpan = maxLongitude - minLongitude;
			minLatitude -= latSpan * 0.5;
			maxLatitude += latSpan * 0.5;
			minLongitude -= lonSpan * 0.5;
			maxLongitude += lonSpan * 0.5;
		}
		WifiCatalogMapObjectsLoader task = new WifiCatalogMapObjectsLoader(getSherlockActivity(), this);
		task.execute(minLatitude, maxLatitude, minLongitude, maxLongitude);

	}

	/* (non-Javadoc)
	 * @see org.openbmap.utils.WifiCatalogMapObjectsLoader.OnCatalogLoadedListener#onComplete(java.util.ArrayList)
	 */
	@Override
	public final void onCatalogLoaded(final ArrayList<LatLong> points) {
		Log.d(TAG, "Loaded catalog objects");
		Layers layers = this.mapView.getLayerManager().getLayers();

		clearCatalogLayer(); 

		// redraw
		for (LatLong point : points) {
			 Circle circle = new Circle(point, CIRCLE_WIFI_CATALOG_WIDTH, paintCatalogFill, paintCatalogStroke);
				catalogObjects.add(circle);
		}

		/**
		 * Draw stack (z-order):
		 *   base map
		 *   catalog objects
		 *   session objects
		 */
		int insertAfter = -1;
		synchronized (catalogObjects) {
			if (layers.size() > 0) {
				// base map 
				insertAfter = 1;
			} else {
				// no map 
				insertAfter = 0;
			}

			for (int i = 0; i < catalogObjects.size(); i++) {
				layers.add(insertAfter + i, catalogObjects.get(i));
			}  
		}
		
		// enable next refresh
		mRefreshCatalogPending = false;
		Log.d(TAG, "Drawed catalog objects");

	}

	/**
	 * Refreshes reference and session layer.
	 * If another refresh is in progress, update is skipped.
	 * @param location
	 */
	protected final void refreshSessionLayer(final Location location) {
		if (!mRefreshSessionPending) {
			Log.d(TAG, "Updating session layer");
			mRefreshSessionPending = true;
			proceedAfterSessionObjectsLoaded(null);
			sessionObjectsRefreshTime = System.currentTimeMillis();
			sessionObjectsRefreshedAt = location;
		} else {
			Log.v(TAG, "Session layer is refreshing. Skipping refresh..");
		}
	}

	/**
	 * Is new location far enough from last refresh location and is last refresh to old?
	 * @return true if session layer needs refresh
	 */
	private boolean sessionLayerOutdated(final Location current) {
		if (current == null) { 
			// fail safe: draw if something went wrong
			return true;
		}

		return (
				(sessionObjectsRefreshedAt.distanceTo(current) > SESSION_REFRESH_DISTANCE)
				&& ((System.currentTimeMillis() - sessionObjectsRefreshTime) > SESSION_REFRESH_INTERVAL) 
				);
	}

	/**
	 * Loads session wifis in visible range.
	 * Will call onSessionLoaded callback upon completion
	 * @param highlight
	 *    If highlight is specified only this wifi is displayed
	 */
	private void proceedAfterSessionObjectsLoaded(final WifiRecord highlight) {
		BoundingBox bbox = MapPositionUtil.getBoundingBox(
				mapView.getModel().mapViewPosition.getMapPosition(),
				mapView.getDimension());

		if (highlight == null) {

			ArrayList<Integer> sessions = new ArrayList<Integer>();
			/*if (allLayerSelected()) {
				// load all session wifis
				sessions = new DataHelper(this).getSessionList();
			} else {*/
			sessions.add(mSessionId);
			//}

			double minLatitude = bbox.minLatitude;
			double maxLatitude = bbox.maxLatitude;
			double minLongitude = bbox.minLongitude;
			double maxLongitude = bbox.maxLongitude;

			// query more than visible objects for smoother data scrolling / less database queries
			if (PREFETCH_MAP_OBJECTS) {
				double latSpan = maxLatitude - minLatitude;
				double lonSpan = maxLongitude - minLongitude;
				minLatitude -= latSpan * 0.5;
				maxLatitude += latSpan * 0.5;
				minLongitude -= lonSpan * 0.5;
				maxLongitude += lonSpan * 0.5;
			}

			SessionMapObjectsLoader task = new SessionMapObjectsLoader(getSherlockActivity(), this, sessions);
			task.execute(minLatitude, maxLatitude, minLongitude, maxLatitude, null);
		} else {
			// draw specific wifi
			ArrayList<Integer> sessions = new ArrayList<Integer>();
			sessions.add(mSessionId);

			SessionMapObjectsLoader task = new SessionMapObjectsLoader(getSherlockActivity(), this, sessions);
			task.execute(bbox.minLatitude, bbox.maxLatitude, bbox.minLongitude, bbox.maxLatitude, highlight.getBssid());
		}
	}


	/* (non-Javadoc)
	 * @see org.openbmap.utils.SessionMapObjectsLoader.OnSessionLoadedListener#onSessionLoaded(java.util.ArrayList)
	 */
	@Override
	public final void onSessionLoaded(final ArrayList<SessionLatLong> points) {
		Log.d(TAG, "Loaded session objects");
		Layers layers = this.mapView.getLayerManager().getLayers();

		// clear layer
		for (Iterator<Layer> iterator = sessionObjects.iterator(); iterator.hasNext();) {
			Layer layer = (Layer) iterator.next();
			layers.remove(layer);
		}
		sessionObjects.clear();

		for (SessionLatLong point : points) {
			if (point.getSession() == mSessionId) {
				// current session objects are larger
				Circle circle = new Circle(point, CIRCLE_SESSION_WIDTH, paintActiveSessionFill, null);
				sessionObjects.add(circle);
			} else {
				// other session objects are smaller and in other color
				Circle circle = new Circle(point, CIRCLE_OTHER_SESSION_WIDTH, paintOtherSessionFill, null);
				sessionObjects.add(circle);
			}
		}
		

		/**
		 * Draw stack (z-order):
		 *   base map
		 *   catalog 
		 *   objects
		 *   session objects
		 */
		int insertAfter = -1;

		synchronized (catalogObjects) {
			if (layers.size() > 0 && catalogObjects.size() > 0) {
				// base map + catalog objects
				// this fails if we catalog objects which have not yet been drawn 
				// insertAfter = layers.indexOf((Layer) catalogObjects.get(catalogObjects.size() - 1));
				// fail safe insert: at the end..
				insertAfter = layers.size();
			} else if (layers.size() > 0 && catalogObjects.size() == 0) {
				// base map + no catalog objects
				insertAfter = 1;
			} else {
				// no map + no catalog objects
				insertAfter = 0;
			}
			
			if (insertAfter > layers.size() + 1) {
				Log.w(TAG, "Index out of bounds, resetting to list end");
				insertAfter = layers.size();
			}
			
			if (insertAfter == -1) {
				Log.w(TAG, "Index out of bounds, resetting to 0");
				insertAfter = 0;
			}
			
			for (int i = 0; i < sessionObjects.size(); i++) {
				layers.add(insertAfter + i, sessionObjects.get(i));	
			}
		}  

		/*
		 * 
		// if we have just loaded on point, set map center
		if (points.size() == 1) {
			mapView.getModel().mapViewPosition.setCenter((LatLong) points.get(0));
		}
		 */
		
		// enable next refresh
		mRefreshSessionPending = false;
		Log.d(TAG, "Drawed catalog objects");
	}

	/**
	 * @param location
	 */
	private void refreshGpxTrace(final Location location) {
		if (!mRefreshGpxPending) {
			Log.d(TAG, "Updating gpx layer");
			mRefreshGpxPending = true;
			proceedAfterGpxObjectsLoaded();
			gpxRefreshTime = System.currentTimeMillis();
		} else {
			Log.v(TAG, "Gpx layer refreshing. Skipping refresh..");
		}
	}

	/**
	 * Is last gpx layer update to old?
	 * @return true if layer needs refresh
	 */
	private boolean gpxLayerOutdated() {
		return ((System.currentTimeMillis() - gpxRefreshTime) > GPX_REFRESH_INTERVAL);
	}

	/*
	 * Loads gpx points in visible range.
	 */
	private void proceedAfterGpxObjectsLoaded() {
		BoundingBox bbox = MapPositionUtil.getBoundingBox(
				mapView.getModel().mapViewPosition.getMapPosition(),
				mapView.getDimension());
		GpxMapObjectsLoader task = new GpxMapObjectsLoader(getSherlockActivity(), this);
		// query with some extra space
		task.execute(mSessionId, bbox.minLatitude - 0.01, bbox.maxLatitude + 0.01, bbox.minLongitude - 0.15, bbox.maxLatitude + 0.15);
	}

	/**
	 * Callback function for loadGpxObjects()
	 */
	@Override
	public final void onGpxLoaded(final ArrayList<LatLong> points) {
		Log.d(TAG, "Loaded gpx objects");

		if (gpxObjects != null) {
			synchronized (this) {
				// clear layer
				mapView.getLayerManager().getLayers().remove(gpxObjects);	
			}
		}

		gpxObjects = new Polyline(MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(Color.BLACK), STROKE_GPX_WIDTH,
				Style.STROKE), AndroidGraphicFactory.INSTANCE);
		
		for (LatLong point : points) {
			gpxObjects.getLatLongs().add(point);	
		}

		synchronized (this) {
			mapView.getLayerManager().getLayers().add(gpxObjects);
		}

		mRefreshGpxPending = false;
	}

	/**
	 * Highlights single wifi on map.
	 * @param id wifi id to highlight
	 */
	public final void loadSingleObject(final int id) {
		Log.d(TAG, "Adding selected wifi to layer: " + id);

		WifiRecord wifi = dbHelper.loadWifiById(id);

		if (wifi != null) {
			proceedAfterSessionObjectsLoaded(wifi);
		}
	}

	/**
	 * Draws arrow in direction of travel. If bearing is unavailable a generic position symbol is used.
	 * If another refresh is taking place, update is skipped
	 */
	private void refreshCompass(final Location location) {
		if (location == null) {
			return;
		}
		
		// ensure that previous refresh has been finished
		if (mRefreshDirectionPending) {
			return;
		}
		mRefreshDirectionPending = true;

		// determine which drawable we currently 
		ImageView iv = (ImageView) getView().findViewById(R.id.position_marker);
		Integer id = (Integer) iv.getTag() == null ? 0 : (Integer) iv.getTag();

		if (location.hasBearing()) {
			// determine which drawable we currently use
			drawCompass(iv, id, location.getBearing());
		} else {
			// refresh only if needed
			if (id != R.drawable.cross) {
				iv.setImageResource(R.drawable.cross);
			}

			//Log.i(TAG, "Can't draw direction marker: no bearing provided");
		}
		mRefreshDirectionPending = false;
	}

	/**
	 * Draws compass
	 * @param iv image view used for compass
	 * @param ressourceId resource id compass needle
	 * @param bearing bearing (azimuth)
	 */
	private void drawCompass(final ImageView iv, final Integer ressourceId, final float bearing) {
		// refresh only if needed
		if (ressourceId != R.drawable.arrow) {
			iv.setImageResource(R.drawable.arrow);
		}

		// rotate arrow
		Matrix matrix = new Matrix();
		iv.setScaleType(ScaleType.MATRIX);   //required
		matrix.postRotate(bearing, iv.getWidth() / 2f, iv.getHeight() / 2f);
		iv.setImageMatrix(matrix);
	}

	/**
	 * Checks whether user wants to see catalog objects
	 * @return true if catalog objects need to be drawn
	 */
	private boolean catalogLayerSelected() {
		// TODO add some ui control
		return true; //(getSherlockActivity().getSupportActionBar().getSelectedNavigationIndex() == LayersDisplayed.ALL.ordinal());
	}



	/**
	 * Opens selected map file
	 * @return a map file
	 */
	protected final File getMapFile() {
		File mapFile = new File(
				Environment.getExternalStorageDirectory().getPath()
				+ prefs.getString(Preferences.KEY_MAP_FOLDER, Preferences.VAL_MAP_FOLDER),
				prefs.getString(Preferences.KEY_MAP_FILE, Preferences.VAL_MAP_FILE));

		return mapFile;
	}

	protected final void addLayers(final LayerManager layerManager, final TileCache tileCache, final MapViewPosition mapViewPosition) {
		layerManager.getLayers().add(MapUtils.createTileRendererLayer(tileCache, mapViewPosition, getMapFile()));
	}

	/**
	 * Creates a tile cache for the baselayer
	 * @return
	 */
	protected final TileCache createTileCache() {
		return MapUtils.createExternalStorageTileCache(getSherlockActivity(), getPersistableId());
	}

	/**
	 * @return the id that is used to save this mapview
	 */
	protected final String getPersistableId() {
		return this.getClass().getSimpleName();
	}

	/**
	 * Sets map-related object to null to enable garbage collection.
	 */
	private void releaseMap() {
		Log.i(TAG, "Releasing map components");
		

		// release zoom / move observer for gc
		this.mapObserver = null;

		if (mapView != null) {
			// save map settings
			this.mapView.getModel().save(this.preferencesFacade);
			this.preferencesFacade.save();
			this.mapView.destroy();
		}
	}

	static Paint createPaint(final int color, final int strokeWidth, final Style style) {
		Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
		paint.setColor(color);
		paint.setStrokeWidth(strokeWidth);
		paint.setStyle(style);

		return paint;
	}

	/* (non-Javadoc)
	 * @see com.actionbarsherlock.app.ActionBar.OnNavigationListener#onNavigationItemSelected(int, long)
	 */
	@Override
	public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		refreshAllLayers();
		return true;
	}

}

