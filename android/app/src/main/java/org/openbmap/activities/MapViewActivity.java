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

import android.app.ActionBar;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.mapsforge.core.graphics.Color;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidResourceBitmap;
import org.mapsforge.map.android.util.AndroidPreferences;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.Layers;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.download.TileDownloadLayer;
import org.mapsforge.map.layer.download.tilesource.OnlineTileSource;
import org.mapsforge.map.layer.overlay.Circle;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.model.common.Observer;
import org.mapsforge.map.util.MapPositionUtil;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.PositionRecord;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.events.onLocationUpdate;
import org.openbmap.utils.CatalogObjectsLoader;
import org.openbmap.utils.CatalogObjectsLoader.OnCatalogLoadedListener;
import org.openbmap.utils.GeometryUtils;
import org.openbmap.utils.GpxMapObjectsLoader;
import org.openbmap.utils.GpxMapObjectsLoader.OnGpxLoadedListener;
import org.openbmap.utils.MapUtils;
import org.openbmap.utils.MapUtils.onLongPressHandler;
import org.openbmap.utils.SessionLatLong;
import org.openbmap.utils.SessionObjectsLoader;
import org.openbmap.utils.SessionObjectsLoader.OnSessionLoadedListener;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Fragment for displaying map with session's GPX track and wifis
 * Caution: due to ViewPager default implementation, this fragment is loaded even before it becomes
 * visible
 */
public class MapViewActivity extends Fragment implements
        OnCatalogLoadedListener,
        OnSessionLoadedListener,
        OnGpxLoadedListener,
        ActionBar.OnNavigationListener, onLongPressHandler {

    private static final String TAG = MapViewActivity.class.getSimpleName();

    /**
     * Layers (Session layer + catalog layer, session layer only)
     */
    public enum LayersDisplayed {
        ALL, SESSION_ONLY
    }

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
     * Minimum time (in millis) between automatic layer refresh
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
     * Minimum time (in millis) between automatic catalog refresh
     * Please note: catalog objects are static, thus updates aren't necessary that often
     */
    protected static final float CATALOG_REFRESH_INTERVAL = 5000;

    /**
     * Minimum time (in millis) between gpx position refresh
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
    private long mGpxRefreshTime;

    private byte mLastZoom;

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
    private static TileDownloadLayer mMapDownloadLayer = null;

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

    private boolean snapToLocation = true;
    /**
     * Used for persisting zoom and position settings onPause / onDestroy
     */
    private AndroidPreferences mPreferencesFacade;

    /**
     * Observes zoom and map movements (for triggering layer updates)
     */
    private Observer mMapObserver;

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

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.mapview, container, false);

        // Register our gps broadcast mReceiver
        registerReceiver();

        initUi(view);
        initMap(view);

        return view;
    }

    @Override
    public void onDestroyView() {
        unregisterReceiver();
        super.onDestroyView();
    }

    @Override
    public final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        // get shared preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        catalogObjects = new ArrayList<Layer>();
        sessionObjects = new ArrayList<Layer>();
        gpxObjects = new Polyline(MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(Color.BLACK), STROKE_GPX_WIDTH,
                Style.STROKE), AndroidGraphicFactory.INSTANCE);
    }

    @Override
    public final void onResume() {
        super.onResume();

        getSession();

        if (mMapDownloadLayer != null) {
            mMapDownloadLayer.onResume();
        }

        registerReceiver();
    }

    @Override
    public final void onPause() {

        if (mMapDownloadLayer != null) {
            mMapDownloadLayer.onPause();
        }

        clearCatalogLayer();
        clearSessionLayer();
        clearGpxLayer();

        unregisterReceiver();

        super.onPause();
    }

    /**
     * Clean up layers and disable GPX events before leaving
     * @param isVisibleToUser
     */
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            Log.d(TAG, "Map become visible, initializing");
            registerReceiver();
            // TODO: currently no possible due to https://github.com/mapsforge/mapsforge/issues/659
            //initMap();
        } else {
            Log.d(TAG, "Map not visible, releasing");
            clearCatalogLayer();
            clearSessionLayer();
            clearGpxLayer();
            // TODO: currently no possible due to https://github.com/mapsforge/mapsforge/issues/659
            //releaseMap();

            unregisterReceiver();
        }
    }

    @Override
    public final void onDestroy() {
        unregisterReceiver();
        releaseMap();
        super.onDestroy();
    }

    private void getSession() {
        final DataHelper dbHelper = new DataHelper(getActivity().getApplicationContext());
        mSessionId = dbHelper.getActiveSessionId();

        if (mSessionId != RadioBeacon.SESSION_NOT_TRACKING) {
            Log.i(TAG, "Displaying session " + mSessionId);
        } else {
            Log.w(TAG, "No active session?");
        }
    }

    /**
     * Initializes map components
     * @param view
     */
    private void initMap(View view) {

        if (view == null) {
            Log.w(TAG, "view is null, can't initialize map");
            return;
        } else {
            mMapView = (MapView) view.findViewById(R.id.map);
        }

        if (mMapView == null) {
            Log.e(TAG, "MapView is null");
            return;
        }

        final SharedPreferences sharedPreferences = getActivity().getApplicationContext().getSharedPreferences(getPersistableId(), /*MODE_PRIVATE*/ 0);
        mPreferencesFacade = new AndroidPreferences(sharedPreferences);

        mMapView.getModel().init(mPreferencesFacade);
        mMapView.setClickable(true);
        mMapView.getMapScaleBar().setVisible(true);

        mTileCache = createTileCache();

        // on first start zoom is set to very low value, so users won't see anything
        // zoom to moderate zoomlevel..
        if (mMapView.getModel().mapViewPosition.getZoomLevel() < (byte) 10) {
            mMapView.getModel().mapViewPosition.setZoomLevel((byte) 15);
        }

        if (MapUtils.isMapSelected(this.getActivity().getApplicationContext())) {
            // remove all layers including base layer
            mMapView.getLayerManager().getLayers().clear();
            final Layer offlineLayer = MapUtils.createTileRendererLayer(
                    mTileCache,
                    mMapView.getModel().mapViewPosition,
                    MapUtils.getMapFile(getActivity().getApplicationContext()),
                    this,
                    MapUtils.getRenderTheme(getActivity().getApplicationContext()));
            if (offlineLayer != null) {
                mMapView.getLayerManager().getLayers().add(offlineLayer);
            }
        } else {
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

            mMapDownloadLayer = new TileDownloadLayer(mTileCache,
                    mMapView.getModel().mapViewPosition, onlineTileSource,
                    AndroidGraphicFactory.INSTANCE);
            this.mMapView.getLayerManager().getLayers().add(mMapDownloadLayer);
            mMapDownloadLayer.onResume();
        }

        mMapObserver = new Observer() {
            @Override
            public void onChange() {
                final byte zoom = mMapView.getModel().mapViewPosition.getZoomLevel();
                if (zoom != mLastZoom && zoom >= MIN_OBJECT_ZOOM) {
                    // Zoom level changed
                    Log.i(TAG, "New zoom level " + zoom + ", reloading map objects");
                    refreshAllLayers();

                    mLastZoom = zoom;
                }

                if (!snapToLocation) {
                    // Free-move mode
                    final LatLong tmp = mMapView.getModel().mapViewPosition.getCenter();
                    final Location position = new Location("DUMMY");
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

        mMapView.getModel().mapViewPosition.addObserver(mMapObserver);
    }

    /**
     * Initializes UI componensts
     *
     * @param view
     */
    private void initUi(final View view) {
        paintCatalogFill = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_WIFI_CATALOG_FILL, 120, 150, 120), 2, Style.FILL);
        paintCatalogStroke = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_WIFI_CATALOG_STROKE, 120, 150, 120), 2, Style.STROKE);
        paintActiveSessionFill = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_SESSION_FILL, 0, 0, 255), 2, Style.FILL);
        paintOtherSessionFill = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_OTHER_SESSIONS_FILL, 255, 0, 255), 2, Style.FILL);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.map_menu, menu);
        menu.findItem(R.id.menu_snaptoLocation).setChecked(snapToLocation);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_snaptoLocation:
                item.setChecked(!item.isChecked());
                snapToLocation = item.isChecked();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void registerReceiver() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);} else {
            Log.i(TAG, "Event bus receiver already registered");
        }
    }

    /**
     * Unregisters receivers for GPS.
     */
    private void unregisterReceiver() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    /**
     * Receives GPS location updates.
     */
    @Subscribe
    public void onEvent(onLocationUpdate event) {
        // handling GPS broadcasts
        Location location = event.location;

        if (mMapView == null) {
            Log.wtf(TAG, "MapView is null");
            return;
        }

        // if btnSnapToLocation is checked, move map
        if (snapToLocation) {
            final LatLong currentPos = new LatLong(location.getLatitude(), location.getLongitude());
            mMapView.getModel().mapViewPosition.setCenter(currentPos);
        }

        // update layers
        if (GeometryUtils.isValidLocation(location)) {
                    /*
					 * Update layers if necessary, but only if
					 * 1.) current zoom level >= 12 (otherwise single points not visible, huge performance impact)
					 * 2.) layer items haven't been refreshed for a while AND user has moved a bit
					 */

            if ((mMapView.getModel().mapViewPosition.getZoomLevel() >= MIN_OBJECT_ZOOM) && (sessionLayerOutdated(location))) {
                refreshSessionLayer(location);
            }

            if ((mMapView.getModel().mapViewPosition.getZoomLevel() >= MIN_OBJECT_ZOOM) &&
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

    /**
     *
     */
    private void refreshAllLayers() {
        if (mMapView == null) {
            return;
        }

        final Location mapCenter = new Location("DUMMY");
        mapCenter.setLatitude(mMapView.getModel().mapViewPosition.getCenter().latitude);
        mapCenter.setLongitude(mMapView.getModel().mapViewPosition.getCenter().longitude);
        refreshSessionLayer(mapCenter);

        if (catalogLayerSelected()) {
            refreshCatalogLayer(mapCenter);
        } else {
            clearCatalogLayer();
        }
    }

    /**7
     * Refreshes catalog layer, if not already refreshing
     *
     * @param location
     */
    protected final void refreshCatalogLayer(final Location location) {
        if (!mRefreshCatalogPending && isVisible()) {
            Log.d(TAG, "Updating wifi catalog layer");
            mRefreshCatalogPending = true;
            triggerCatalogObjectsUpdate();
            catalogObjectsRefreshedAt = location;
            catalogObjectsRefreshTime = System.currentTimeMillis();
        } else if (!isVisible()){
            Log.v(TAG, "Not visible, skipping refresh");
        } else {
            Log.v(TAG, "Wifi catalog layer is refreshing. Skipping refresh..");
        }
    }

    /**
     *
     */
    private void clearSessionLayer() {
        if (sessionObjects == null) {
            return;
        }

        if (mMapView == null) {
            sessionObjects = null;
            return;
        }

        for (final Iterator<Layer> iterator = sessionObjects.iterator(); iterator.hasNext(); ) {
            final Layer layer = iterator.next();
            this.mMapView.getLayerManager().getLayers().remove(layer);
        }
        sessionObjects.clear();
    }

    /**
     * Clears catalog layer objects
     */
    private void clearCatalogLayer() {
        if (catalogObjects == null) {
            return;
        }

        if (mMapView == null) {
            catalogObjects = null;
            return;
        }

        synchronized (catalogObjects) {
            for (final Iterator<Layer> iterator = catalogObjects.iterator(); iterator.hasNext(); ) {
                final Layer layer = iterator.next();
                this.mMapView.getLayerManager().getLayers().remove(layer);
            }
            catalogObjects.clear();
        }
    }

    /**
     * Clears GPX layer objects
     */
    private void clearGpxLayer() {
        if (gpxObjects != null && mMapView != null) {
            synchronized (this) {
                // clear layer
                mMapView.getLayerManager().getLayers().remove(gpxObjects);
            }
        } else {
            gpxObjects = null;
        }
    }

    /**
     * Is new location far enough from last refresh location?
     *
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
     */
    private void triggerCatalogObjectsUpdate() {

        if (mMapView == null) {
            return;
        }

        final BoundingBox bbox = MapPositionUtil.getBoundingBox(
                mMapView.getModel().mapViewPosition.getMapPosition(),
                mMapView.getDimension(), mMapView.getModel().displayModel.getTileSize());

        double minLatitude = bbox.minLatitude;
        double maxLatitude = bbox.maxLatitude;
        double minLongitude = bbox.minLongitude;
        double maxLongitude = bbox.maxLongitude;

        // query more than visible objects for smoother data scrolling / less database queries?
        if (PREFETCH_MAP_OBJECTS) {
            final double latSpan = maxLatitude - minLatitude;
            final double lonSpan = maxLongitude - minLongitude;
            minLatitude -= latSpan * 0.5;
            maxLatitude += latSpan * 0.5;
            minLongitude -= lonSpan * 0.5;
            maxLongitude += lonSpan * 0.5;
        }
        final CatalogObjectsLoader task = new CatalogObjectsLoader(getActivity(), this);
        task.execute(minLatitude, maxLatitude, minLongitude, maxLongitude);

    }

    /* (non-Javadoc)
     * @see org.openbmap.utils.WifiCatalogMapObjectsLoader.OnCatalogLoadedListener#onComplete(java.util.ArrayList)
     */
    @Override
    public final void onCatalogLoaded(final ArrayList<LatLong> points) {
        Log.d(TAG, "Loaded catalog objects");
        if (mMapView == null) {
            return;
        }

        final Layers layers = mMapView.getLayerManager().getLayers();

        clearCatalogLayer();

        // redraw
        for (final LatLong point : points) {
            final Circle circle = new Circle(point, CIRCLE_WIFI_CATALOG_WIDTH, paintCatalogFill, paintCatalogStroke);
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
     *
     * @param location
     */
    protected final void refreshSessionLayer(final Location location) {
        if (!mRefreshSessionPending && isVisible()) {
            Log.d(TAG, "Updating session layer");
            mRefreshSessionPending = true;
            triggerSessionObjectsUpdate(null);
            sessionObjectsRefreshTime = System.currentTimeMillis();
            sessionObjectsRefreshedAt = location;
        } else if (!isVisible()) {
            Log.v(TAG, "Not visible, skipping refresh");
        } else {
            Log.v(TAG, "Session layer is refreshing. Skipping refresh..");
        }
    }

    /**
     * Is new location far enough from last refresh location and is last refresh to old?
     *
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
     *
     * @param highlight If highlight is specified only this wifi is displayed
     */
    private void triggerSessionObjectsUpdate(final WifiRecord highlight) {
        if (mMapView == null) {
            return;
        }

        final BoundingBox bbox = MapPositionUtil.getBoundingBox(
                mMapView.getModel().mapViewPosition.getMapPosition(),
                mMapView.getDimension(), mMapView.getModel().displayModel.getTileSize());

        if (highlight == null) {

            final ArrayList<Integer> sessions = new ArrayList<Integer>();
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
                final double latSpan = maxLatitude - minLatitude;
                final double lonSpan = maxLongitude - minLongitude;
                minLatitude -= latSpan * 0.5;
                maxLatitude += latSpan * 0.5;
                minLongitude -= lonSpan * 0.5;
                maxLongitude += lonSpan * 0.5;
            }

            final SessionObjectsLoader task = new SessionObjectsLoader(getActivity().getApplicationContext(), this, sessions);
            task.execute(minLatitude, maxLatitude, minLongitude, maxLongitude, null);
        } else {
            // draw specific wifi
            final ArrayList<Integer> sessions = new ArrayList<Integer>();
            sessions.add(mSessionId);

            final SessionObjectsLoader task = new SessionObjectsLoader(getActivity().getApplicationContext(), this, sessions);
            task.execute(bbox.minLatitude, bbox.maxLatitude, bbox.minLongitude, bbox.maxLatitude, highlight.getBssid());
        }
    }

    /* (non-Javadoc)
     * @see org.openbmap.utils.SessionMapObjectsLoader.OnSessionLoadedListener#onSessionLoaded(java.util.ArrayList)
     */
    @Override
    public final void onSessionLoaded(final ArrayList<SessionLatLong> points) {
        Log.d(TAG, "Loaded session objects");
        final Layers layers = this.mMapView.getLayerManager().getLayers();

        clearSessionLayer();

        if (mMapView == null) {
            return;
        }

        for (final SessionLatLong point : points) {
            if (point.getSession() == mSessionId) {
                // current session objects are larger
                final Circle circle = new Circle(point, CIRCLE_SESSION_WIDTH, paintActiveSessionFill, null);
                sessionObjects.add(circle);
            } else {
                // other session objects are smaller and in other color
                final Circle circle = new Circle(point, CIRCLE_OTHER_SESSION_WIDTH, paintOtherSessionFill, null);
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
        if (!mRefreshGpxPending && isVisible()) {
            Log.d(TAG, "Updating gpx layer");
            mRefreshGpxPending = true;
            triggerGpxObjectsUpdate();
            mGpxRefreshTime = System.currentTimeMillis();
        } else if (!isVisible()) {
            Log.v(TAG, "Not visible, skipping refresh");
        }  else {
            Log.v(TAG, "Gpx layer refreshing. Skipping refresh..");
        }
    }

    /**
     * Is last gpx layer update to old?
     *
     * @return true if layer needs refresh
     */
    private boolean gpxLayerOutdated() {
        return ((System.currentTimeMillis() - mGpxRefreshTime) > GPX_REFRESH_INTERVAL);
    }

    /*
     * Loads gpx points in visible range.
     */
    private void triggerGpxObjectsUpdate() {
        if (mMapView == null) {
            return;
        }

        final BoundingBox bbox = MapPositionUtil.getBoundingBox(
                mMapView.getModel().mapViewPosition.getMapPosition(),
                mMapView.getDimension(), mMapView.getModel().displayModel.getTileSize());
        final GpxMapObjectsLoader task = new GpxMapObjectsLoader(getActivity().getApplicationContext(), this);
        // query with some extra space
        task.execute(mSessionId, bbox.minLatitude - 0.01, bbox.maxLatitude + 0.01, bbox.minLongitude - 0.15, bbox.maxLatitude + 0.15);
    }

    /**
     * Callback function for loadGpxObjects()
     */
    @Override
    public final void onGpxLoaded(final ArrayList<LatLong> points) {
        Log.d(TAG, "Loading " + points.size() + " gpx objects");

        clearGpxLayer();

        if (mMapView == null) {
            return;
        }

        gpxObjects = new Polyline(MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(Color.GREEN), STROKE_GPX_WIDTH,
                Style.STROKE), AndroidGraphicFactory.INSTANCE);

        for (final LatLong point : points) {
            gpxObjects.getLatLongs().add(point);
        }

        synchronized (this) {
            mMapView.getLayerManager().getLayers().add(gpxObjects);
        }

        mRefreshGpxPending = false;
    }

    /**
     * Draws arrow in direction of travel. If bearing is unavailable a generic position symbol is used.
     * If another refresh is taking place, update is skipped
     */
    private void refreshCompass(final Location location) {
        if (location == null) {
            return;
        }

        if (!isVisible()) {
            return;
        }

        // ensure that previous refresh has been finished
        if (mRefreshDirectionPending) {
            return;
        }
        mRefreshDirectionPending = true;

        // determine which drawable we currently
        final ImageView iv = (ImageView) getView().findViewById(R.id.position_marker);
        final Integer id = iv.getTag() == null ? 0 : (Integer) iv.getTag();

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
     *
     * @param iv          image view used for compass
     * @param ressourceId resource id compass needle
     * @param bearing     bearing (azimuth)
     */
    private void drawCompass(final ImageView iv, final Integer ressourceId, final float bearing) {
        // refresh only if needed
        if (ressourceId != R.drawable.arrow) {
            iv.setImageResource(R.drawable.arrow);
        }

        // rotate arrow
        final Matrix matrix = new Matrix();
        iv.setScaleType(ScaleType.MATRIX);   //required
        matrix.postRotate(bearing, iv.getWidth() / 2f, iv.getHeight() / 2f);
        iv.setImageMatrix(matrix);
    }

    /**
     * Checks whether user wants to see catalog objects
     *
     * @return true if catalog objects need to be drawn
     */
    private boolean catalogLayerSelected() {
        // TODO add some ui control
        return true; //(getSherlockActivity().getSupportActionBar().getSelectedNavigationIndex() == LayersDisplayed.ALL.ordinal());
    }

    /**
     * Creates a tile cache for the baselayer
     *
     * @return
     */
    protected final TileCache createTileCache() {
        if (mMapView == null) {
            return null;
        }
        return AndroidUtil.createTileCache(
                getActivity().getApplicationContext(),
                "mapcache",
                mMapView.getModel().displayModel.getTileSize(),
                1f,
                mMapView.getModel().frameBufferModel.getOverdrawFactor());
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

        AndroidResourceBitmap.clearResourceBitmaps();

        if (mTileCache != null) {
            this.mTileCache.destroy();
        }

        // release zoom / move observer for gc
        this.mMapObserver = null;

        if (mMapView != null) {
            // save map settings
            mMapView.getModel().save(mPreferencesFacade);
            mPreferencesFacade.save();
            //mMapView.getModel().mapViewPosition.destroy();
            mMapView.destroyAll();
            mMapView = null;
        }
    }

    /* (non-Javadoc)
     * @see com.actionbarsherlock.app.ActionBar.OnNavigationListener#onNavigationItemSelected(int, long)
     */
    @Override
    public boolean onNavigationItemSelected(final int itemPosition, final long itemId) {
        refreshAllLayers();
        return true;
    }

    /* (non-Javadoc)
     * @see org.openbmap.utils.MapUtils.onLongPressHandler#onLongPress(org.mapsforge.core.model.LatLong, org.mapsforge.core.model.Point, org.mapsforge.core.model.Point)
     */
    @Override
    public void onLongPress(final LatLong tapLatLong, final Point thisXY, final Point tapXY) {
        Toast.makeText(this.getActivity(), this.getActivity().getString(R.string.saved_waypoint)  + "\n" + tapLatLong.toString(), Toast.LENGTH_LONG).show();

        final DataHelper dbHelper = new DataHelper(getActivity().getApplicationContext());

        final PositionRecord pos = new PositionRecord(GeometryUtils.toLocation(tapLatLong), mSessionId, RadioBeacon.PROVIDER_USER_DEFINED, true);
        dbHelper.storePosition(pos);

        // beep once point has been saved
        ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
    }
}

