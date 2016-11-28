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

package org.openbmap.activities.tabs;

import android.app.ActionBar;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
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
import org.mapsforge.core.graphics.Bitmap;
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
import org.mapsforge.map.layer.overlay.FixedPixelCircle;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.model.common.Observer;
import org.mapsforge.map.util.MapPositionUtil;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.PositionRecord;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.events.onLocationUpdate;
import org.openbmap.events.onPoiUpdateAvailable;
import org.openbmap.events.onPoiUpdateRequested;
import org.openbmap.utils.CatalogObject;
import org.openbmap.utils.GeometryUtils;
import org.openbmap.utils.GpxMapObjectsLoader;
import org.openbmap.utils.GpxMapObjectsLoader.OnGpxLoadedListener;
import org.openbmap.utils.LegacyGroupLayer;
import org.openbmap.utils.MapUtils;
import org.openbmap.utils.MapUtils.onLongPressHandler;
import org.openbmap.utils.SessionLatLong;
import org.openbmap.utils.SessionObjectsLoader;
import org.openbmap.utils.SessionObjectsLoader.OnSessionLoadedListener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Fragment for displaying map with session's GPX track and wifis
 * Caution: due to ViewPager default implementation, this fragment is loaded even before it becomes
 * visible
 */
public class MapFragment extends Fragment implements
        OnSessionLoadedListener,
        OnGpxLoadedListener,
        ActionBar.OnNavigationListener, onLongPressHandler {

    private static final String TAG = MapFragment.class.getSimpleName();

    private Unbinder unbinder;

    @BindView(R.id.direction) ImageView directionSymbol;

    private Marker positionMarker;

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

    private static final Paint PAINT = MapUtils.createPaint(
            AndroidGraphicFactory.INSTANCE.createColor(ALPHA_WIFI_CATALOG_FILL, 120, 150, 120), 2, Style.FILL);
    private static final Paint PAINT_RED = MapUtils.createPaint(
            AndroidGraphicFactory.INSTANCE.createColor(ALPHA_WIFI_CATALOG_FILL, 255, 150, 120), 2, Style.FILL);

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
    private int sessionId;

    /**
     * System time of last gpx refresh (in millis)
     */
    private long gpxRefreshedAt;

    private byte lastZoom;

    /**
     * MapView
     */
    @BindView(R.id.map)
    public MapView mapView;

    //[end]

    // [start] Map styles
    /**
     * Baselayer cache
     */
    private TileCache tileCache;

    /**
     * Online tile layer, used when no offline map available
     */
    private static TileDownloadLayer mapDownloadLayer = null;

    /**
     * Layer with radiocells wifis
     */
    private LegacyGroupLayer wifisLayer;

    /**
     * Openstreetmap towers layer
     */
    private LegacyGroupLayer towersLayer;

    /**
     * Paint style for active sessions objects
     */
    private Paint activeSessionFill = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_SESSION_FILL, 0, 0, 255), 2, Style.FILL);

    /**
     * Paint style for objects from other sessions
     */
    private Paint otherSessionFill = MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(ALPHA_OTHER_SESSIONS_FILL, 255, 0, 255), 2, Style.FILL);

    private List<Layer> sessionObjects;

    private Polyline gpxObjects;

    private boolean followLocation = true;

    /**
     * Used for persisting zoom and position settings onPause / onDestroy
     */
    private AndroidPreferences preferencesFacade;

    /**
     * Checks whether wifis layer is active
     */
    private Boolean isWifisLayerEnabled = true;

    /**
     * Checks whether tower layer is active
     */
    private Boolean isTowersLayerEnabled = true;


    /**
     * Observes zoom and map movements (for triggering layer updates)
     */
    private Observer mapObserver;

    /**
     * Session layer is currently refreshing
     */
    private boolean isUpdatingSession = false;

    /**
     * Direction marker is currently updated
     */
    private boolean isUpdatingCompass;

    /**
     * Gpx layer is currently refreshing
     */
    private boolean isUpdatingGpx;

    /**
     * System time of last session layer refresh (in millis)
     */
    private long sessionObjectsRefreshTime;

    /**
     * System time of last catalog layer refresh (in millis)
     */
    private long poiLayerRefreshTime;

    /**
     * Location of last session layer refresh
     */
    private Location sessionObjectsRefreshLocation = new Location("DUMMY");

    /**
     * Location of wifi layer refresh
     */
    private Location poiLayerRefreshLocation = new Location("DUMMY");


    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.mapview, container, false);

        // Register our gps broadcast mReceiver
        registerReceiver();

        unbinder = ButterKnife.bind(this, view);

        initMap();
        return view;
    }

    @Override
    public void onDestroyView() {
        unregisterReceiver();
        unbinder.unbind();

        releaseMap();
        super.onDestroyView();
    }

    @Override
    public final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        sessionObjects = new ArrayList<>();
        gpxObjects = new Polyline(MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(Color.BLACK), STROKE_GPX_WIDTH,
                Style.STROKE), AndroidGraphicFactory.INSTANCE);
    }

    @Override
    public final void onResume() {
        super.onResume();

        getSession();

        if (mapView == null) {
            initMap();
        }

        if (mapDownloadLayer != null) {
            mapDownloadLayer.onResume();
        }

        registerReceiver();
    }

    @Override
    public final void onPause() {

        if (mapDownloadLayer != null) {
            mapDownloadLayer.onPause();
        }

        clearSessionLayer();
        clearGpxLayer();

        unregisterReceiver();

        super.onPause();
    }

    /**
     * Clean up layers and disable GPX events
     * Should be callebefore leaving
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
        sessionId = dbHelper.getActiveSessionId();

        if (sessionId != RadioBeacon.SESSION_NOT_TRACKING) {
            Log.i(TAG, "Displaying session " + sessionId);
        } else {
            Log.w(TAG, "No active session?");
        }
    }

    /**
     * Initializes map components
     */
    private void initMap() {

        if (mapView == null) {
            Log.e(TAG, "MapView is null");
            return;
        }

        final Drawable icon = ResourcesCompat.getDrawable(getResources(), R.drawable.position, null);
        Bitmap bitmap = AndroidGraphicFactory.convertToBitmap(icon);
        positionMarker = new Marker(null, bitmap, 0, -bitmap.getHeight() / 2);
        mapView.getLayerManager().getLayers().add(positionMarker);

        final SharedPreferences sharedPreferences = getActivity().getApplicationContext().getSharedPreferences(getPersistableId(), /*MODE_PRIVATE*/ 0);
        preferencesFacade = new AndroidPreferences(sharedPreferences);

        mapView.getModel().init(preferencesFacade);
        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);

        tileCache = createTileCache();

       // zoom to moderate zoom level on startup
        if (mapView.getModel().mapViewPosition.getZoomLevel() < (byte) 10 || mapView.getModel().mapViewPosition.getZoomLevel() > (byte) 18) {
            Log.i(TAG, "Reseting zoom level");
            mapView.getModel().mapViewPosition.setZoomLevel((byte) 15);
        }

        if (MapUtils.hasOfflineMap(this.getActivity())) {
            Log.i(TAG, "Using offline map mode");
            mapView.getLayerManager().getLayers().clear();
            addOfflineLayer();
        } else if (MapUtils.useOnlineMaps(this.getActivity())) {
            Log.i(TAG, "Using online map mode");
            Toast.makeText(this.getActivity(), R.string.info_using_online_map, Toast.LENGTH_LONG).show();
            addOnlineLayer();
        } else {
            Log.w(TAG, "Neither online mode activated, nor offline map avaibable");
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

        mapObserver = new Observer() {
            @Override
            public void onChange() {
                final byte zoom = mapView.getModel().mapViewPosition.getZoomLevel();
                if (zoom != lastZoom && zoom >= MIN_OBJECT_ZOOM) {
                    // Zoom level changed
                    Log.i(TAG, "New zoom level " + zoom + ", reloading map objects");
                    updateAllLayers();

                    lastZoom = zoom;
                }

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideDirections(true);
                    }
                });

                if (!followLocation) {
                    // Free-move mode

                    final LatLong tmp = mapView.getModel().mapViewPosition.getCenter();
                    final Location position = new Location("DUMMY");
                    position.setLatitude(tmp.latitude);
                    position.setLongitude(tmp.longitude);

                    if (sessionLayerNeedsUpdate(position)) {
                        requestSessionUpdate(position);
                    }

                    if (poiLayerNeedsUpdate(position)) {
                        requestPoiUpdate();
                    }
                }
            }
        };

        mapView.getModel().mapViewPosition.addObserver(mapObserver);
    }

    private void hideDirections(boolean hide) {
        if (hide) {
            directionSymbol.setVisibility(View.INVISIBLE);
        } else {
            directionSymbol.setVisibility(View.VISIBLE);
        }
    }


    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.map_menu, menu);
        menu.findItem(R.id.menu_followLocation).setChecked(followLocation);
        menu.findItem(R.id.menu_enableTowers).setChecked(isTowersLayerEnabled);
        menu.findItem(R.id.menu_enableWifis).setChecked(isWifisLayerEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_followLocation:
                item.setChecked(!item.isChecked());
                followLocation = item.isChecked();

                if (followLocation) {
                    hideDirections(true);
                } else {
                    hideDirections(false);
                }
                return true;
            case R.id.menu_enableTowers:
                item.setChecked(!item.isChecked());
                isTowersLayerEnabled = item.isChecked();
                clearOverlays();
                updateAllLayers();
                return true;
            case R.id.menu_enableWifis:
                item.setChecked(!item.isChecked());
                isWifisLayerEnabled= item.isChecked();
                clearOverlays();
                updateAllLayers();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void registerReceiver() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);}
        else {
            Log.w(TAG, "Event bus receiver already registered");
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

        if (mapView == null) {
            Log.wtf(TAG, "MapView is null");
            return;
        }

        // update layers
        if (GeometryUtils.isValidLocation(location)) {
            final LatLong currentPos = new LatLong(location.getLatitude(), location.getLongitude());

            positionMarker.setLatLong(currentPos);

            /*
             * Update layers if necessary, but only if
             * 1.) current zoom level >= 12 (otherwise single points not visible, huge performance impact)
             * 2.) layer items haven't been refreshed for a while AND user has moved a bit
             */

            // if follow location mode is checked, move map
            if (followLocation) {
                mapView.getModel().mapViewPosition.setCenter(currentPos);

                // might have been disabled by user - reactivate automatically
                hideDirections(false);
            }

            if ((mapView.getModel().mapViewPosition.getZoomLevel() >= MIN_OBJECT_ZOOM) && sessionLayerNeedsUpdate(location)) {
                requestSessionUpdate(location);
            }

            if ((mapView.getModel().mapViewPosition.getZoomLevel() >= MIN_OBJECT_ZOOM) && poiLayerNeedsUpdate(location)) {
                requestPoiUpdate();
            }

            if (gpxNeedsUpdate()) {
                refreshGpxTrace(location);
            }

            // indicate direction
            refreshCompass(location);
        } else {
            Log.e(TAG, "Invalid positon! Cycle skipped");
        }
    }

    /**
     *
     */
    private void updateAllLayers() {
        if (mapView == null) {
            Log.w(TAG, "Map view is null, ignoring");
            return;
        }

        final Location mapCenter = new Location("DUMMY");
        mapCenter.setLatitude(mapView.getModel().mapViewPosition.getCenter().latitude);
        mapCenter.setLongitude(mapView.getModel().mapViewPosition.getCenter().longitude);

        requestSessionUpdate(mapCenter);

        requestPoiUpdate();
    }

    private void requestPoiUpdate() {
        EventBus.getDefault().post(new onPoiUpdateRequested(this.mapView.getBoundingBox()));
        poiLayerRefreshTime = System.currentTimeMillis();
    }

    /**
     * Clears database overlays
     */
    private void clearOverlays() {
        clearTowers();
        clearWifis();
    }

    /**
     * Clears known wifis overlay
     */
    private void clearWifis() {
        synchronized (this) {

            if (wifisLayer != null) {
                this.mapView.getLayerManager().getLayers().remove(wifisLayer);
            }
            wifisLayer = null;
        }
    }

    /**
     * Clears towers overlay
     */
    private void clearTowers() {
        synchronized (this) {

            if (towersLayer != null) {
                this.mapView.getLayerManager().getLayers().remove(towersLayer);
            }
            towersLayer = null;
            AndroidResourceBitmap.clearResourceBitmaps();
        }
    }

    /**
     *
     */
    private void clearSessionLayer() {
        if (sessionObjects == null) {
            return;
        }

        if (mapView == null) {
            sessionObjects = null;
            return;
        }

        for (final Iterator<Layer> iterator = sessionObjects.iterator(); iterator.hasNext(); ) {
            final Layer layer = iterator.next();
            this.mapView.getLayerManager().getLayers().remove(layer);
        }
        sessionObjects.clear();
    }

    /**
     * Clears GPX layer objects
     */
    private void clearGpxLayer() {
        if (gpxObjects != null && mapView != null) {
            synchronized (this) {
                // clear layer
                mapView.getLayerManager().getLayers().remove(gpxObjects);
            }
        } else {
            gpxObjects = null;
        }
    }

    /**
     * Checks whether layer is too old or too far away from previous position
     * @param current new location
     * @return true if wifis layer needs a refresh
     */
    private boolean poiLayerNeedsUpdate(final Location current) {
        if (current == null) {
            // fail safe: draw if something went wrong
            return true;
        }

        return (
                (poiLayerRefreshLocation.distanceTo(current) > CATALOG_REFRESH_DISTANCE)
                        && ((System.currentTimeMillis() - poiLayerRefreshTime) > CATALOG_REFRESH_INTERVAL)
        );
    }

    /**
     * Updates session layer.
     * If another refresh is in progress, update is skipped.
     *
     * @param location
     */
    protected final void requestSessionUpdate(final Location location) {
        if (!isUpdatingSession && isVisible()) {
            Log.d(TAG, "Updating session layer");
            isUpdatingSession = true;
            startSessionDatabaseTask(null);
            sessionObjectsRefreshTime = System.currentTimeMillis();
            sessionObjectsRefreshLocation = location;
        } else if (!isVisible()) {
            Log.v(TAG, "Not visible, skipping refresh");
        } else {
            Log.v(TAG, "Session layer is refreshing. Skipping refresh..");
        }
    }

    /**
     * Is new location far enough from last refresh location and is last refresh to old?
     * @return true if session layer needs refresh
     */
    private boolean sessionLayerNeedsUpdate(final Location current) {
        if (current == null) {
            // fail safe: draw if something went wrong
            return true;
        }

        return ((sessionObjectsRefreshLocation.distanceTo(current) > SESSION_REFRESH_DISTANCE)
                        && ((System.currentTimeMillis() - sessionObjectsRefreshTime) > SESSION_REFRESH_INTERVAL));
    }

    /**
     * Loads session wifis in visible range.
     * Will call onSessionLoaded callback upon completion
     *
     * @param highlight If highlight is specified only this wifi is displayed
     */
    private void startSessionDatabaseTask(final WifiRecord highlight) {
        if (mapView == null) {
            return;
        }

        final BoundingBox bbox = MapPositionUtil.getBoundingBox(
                mapView.getModel().mapViewPosition.getMapPosition(),
                mapView.getDimension(), mapView.getModel().displayModel.getTileSize());

        if (highlight == null) {

            final List<Integer> sessions = new ArrayList<>();
            /*if (allLayerSelected()) {
                // load all session wifis
                sessions = new DataHelper(this).getSessionList();
            } else {*/
            sessions.add(sessionId);
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
            final List<Integer> sessions = new ArrayList<>();
            sessions.add(sessionId);

            final SessionObjectsLoader task = new SessionObjectsLoader(getActivity().getApplicationContext(), this, sessions);
            task.execute(bbox.minLatitude, bbox.maxLatitude, bbox.minLongitude, bbox.maxLatitude, highlight.getBssid());
        }
    }

    @Subscribe
    public void onEvent(onPoiUpdateAvailable event){
        if (event.pois == null) {
            Log.d(TAG, "No POI founds");
            return;
        }

        Log.d(TAG, event.pois.size() + " POI found");

        clearTowers();
        clearWifis();

        // sort results into their corresponding group layer
        wifisLayer = new LegacyGroupLayer();
        towersLayer = new LegacyGroupLayer();
        for (final CatalogObject poi : event.pois) {
            if ("Radiocells.org".equals(poi.category)) {
                final Circle allSymbol = new FixedPixelCircle(poi.latLong, 8, PAINT, null);
                wifisLayer.layers.add(allSymbol);
            } else if ("Own".equals(poi.category)) {
                final Circle ownSymbol = new FixedPixelCircle(poi.latLong, 8, PAINT_RED, null);
                wifisLayer.layers.add(ownSymbol);
            } else if ("Towers".equals(poi.category)){
                // Handling tower markers
                //final Drawable drawable = ContextCompat.getDrawable(activity.getActivity(), R.drawable.icon);
                final Drawable towerIcon = ResourcesCompat.getDrawable(getResources(), R.drawable.icon, null);
                Bitmap bitmap = AndroidGraphicFactory.convertToBitmap(towerIcon);
                bitmap.incrementRefCount();
                final Marker marker = new Marker(poi.latLong, bitmap, 0, -bitmap.getHeight() / 2);
                towersLayer.layers.add(marker);
            } else {
                Log.w(TAG, "Unknown marker category:" + poi.category);
            }
        }

        if (wifisLayer != null) {
            mapView.getLayerManager().getLayers().add(wifisLayer);
        }
        if (towersLayer != null) {
            mapView.getLayerManager().getLayers().add(towersLayer);
        }
        mapView.getLayerManager().redrawLayers();
    }

    /* (non-Javadoc)
     * @see org.openbmap.utils.SessionMapObjectsLoader.OnSessionLoadedListener#onSessionLoaded(java.util.ArrayList)
     */
    @Override
    public final void onSessionLoaded(final List<SessionLatLong> points) {
        Log.d(TAG, "Loaded session objects");

        if (mapView == null) {
            return;
        }

        final Layers layers = this.mapView.getLayerManager().getLayers();

        clearSessionLayer();

        for (final SessionLatLong point : points) {
            if (point.getSession() == sessionId) {
                // current session objects are larger
                final Circle circle = new Circle(point, CIRCLE_SESSION_WIDTH, activeSessionFill, null);
                sessionObjects.add(circle);
            } else {
                // other session objects are smaller and in other color
                final Circle circle = new Circle(point, CIRCLE_OTHER_SESSION_WIDTH, otherSessionFill, null);
                sessionObjects.add(circle);
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
        isUpdatingSession = false;
        Log.d(TAG, "Drawed catalog objects");
    }

    /**
     * @param location
     */
    private void refreshGpxTrace(final Location location) {
        if (!isUpdatingGpx && isVisible()) {
            Log.d(TAG, "Updating gpx layer");
            isUpdatingGpx = true;
            startGpxDatabaseTask();
            gpxRefreshedAt = System.currentTimeMillis();
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
    private boolean gpxNeedsUpdate() {
        return ((System.currentTimeMillis() - gpxRefreshedAt) > GPX_REFRESH_INTERVAL);
    }

    /*
     * Loads gpx points in visible range.
     */
    private void startGpxDatabaseTask() {
        if (mapView == null) {
            return;
        }

        final BoundingBox bbox = MapPositionUtil.getBoundingBox(
                mapView.getModel().mapViewPosition.getMapPosition(),
                mapView.getDimension(), mapView.getModel().displayModel.getTileSize());
        final GpxMapObjectsLoader task = new GpxMapObjectsLoader(getActivity().getApplicationContext(), this);
        // query with some extra space
        task.execute(sessionId, bbox.minLatitude - 0.01, bbox.maxLatitude + 0.01, bbox.minLongitude - 0.15, bbox.maxLatitude + 0.15);
    }

    /**
     * Callback function for loadGpxObjects()
     */
    @Override
    public final void onGpxLoaded(final List<LatLong> points) {
        Log.d(TAG, "Loading " + points.size() + " gpx objects");

        clearGpxLayer();

        if (mapView == null) {
            return;
        }

        gpxObjects = new Polyline(MapUtils.createPaint(AndroidGraphicFactory.INSTANCE.createColor(Color.GREEN), STROKE_GPX_WIDTH,
                Style.STROKE), AndroidGraphicFactory.INSTANCE);

        for (final LatLong point : points) {
            gpxObjects.getLatLongs().add(point);
        }

        synchronized (this) {
            mapView.getLayerManager().getLayers().add(gpxObjects);
        }

        isUpdatingGpx = false;
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
        if (isUpdatingCompass) {
            return;
        }
        isUpdatingCompass = true;

        final Integer id = directionSymbol.getTag() == null ? 0 : (Integer) directionSymbol.getTag();

        if (location.hasBearing()) {
            // determine which drawable we currently use
            drawCompass(directionSymbol, id, location.getBearing());
        } else {
            // refresh only if needed
            if (id != R.drawable.cross) {
                directionSymbol.setImageResource(R.drawable.cross);
            }

            //Log.i(TAG, "Can't draw direction marker: no bearing provided");
        }
        isUpdatingCompass = false;
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
     * Creates a tile cache for the baselayer
     *
     * @return
     */
    protected final TileCache createTileCache() {
        if (mapView == null) {
            return null;
        }
        return AndroidUtil.createTileCache(
                getActivity().getApplicationContext(),
                "mapcache",
                mapView.getModel().displayModel.getTileSize(),
                1f,
                mapView.getModel().frameBufferModel.getOverdrawFactor());
    }

    /**
     * @return the id that is used to save this mapview
     */
    protected final String getPersistableId() {
        return this.getClass().getSimpleName();
    }

    /**
     * Cleans up mapsforge events
     * Calls mapsforge destroy events and sets map-related object to
     * null to enable garbage collection.
     */
    private void releaseMap() {
        Log.i(TAG, "Cleaning mapsforge components");

        if (tileCache != null) {
            tileCache.destroy();
        }

        if (mapView != null) {
            // save map settings
            mapView.getModel().save(preferencesFacade);
            preferencesFacade.save();
            mapView.destroyAll();
            mapView = null;
        }

        // release zoom / move observer for gc
        this.mapObserver = null;

        MapUtils.clearAndroidRessources();
    }

    /* (non-Javadoc)
     * @see com.actionbarsherlock.app.ActionBar.OnNavigationListener#onNavigationItemSelected(int, long)
     */
    @Override
    public boolean onNavigationItemSelected(final int itemPosition, final long itemId) {
        updateAllLayers();
        return true;
    }

    /* (non-Javadoc)
     * @see org.openbmap.utils.MapUtils.onLongPressHandler#onLongPress(org.mapsforge.core.model.LatLong, org.mapsforge.core.model.Point, org.mapsforge.core.model.Point)
     */
    @Override
    public void onLongPress(final LatLong tapLatLong, final Point thisXY, final Point tapXY) {
        Toast.makeText(this.getActivity(), this.getActivity().getString(R.string.saved_waypoint)  + "\n" + tapLatLong.toString(), Toast.LENGTH_LONG).show();

        final DataHelper dbHelper = new DataHelper(getActivity().getApplicationContext());

        final PositionRecord pos = new PositionRecord(GeometryUtils.toLocation(tapLatLong), sessionId, RadioBeacon.PROVIDER_USER_DEFINED, true);
        dbHelper.storePosition(pos);

        // beep once point has been saved
        ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
    }

    /**
     * Creates a long-press enabled offline map layer
     */
    private void addOfflineLayer() {
        final Layer offlineLayer = MapUtils.createTileRendererLayer(
                this.tileCache,
                this.mapView.getModel().mapViewPosition,
                MapUtils.getMapFile(this.getActivity()),
                MapUtils.getRenderTheme(this.getActivity()),
                this
        );

        if (offlineLayer != null) this.mapView.getLayerManager().getLayers().add(offlineLayer);
    }

    /**
     * Creates a long-press enabled offline map layer
     */
    private void addOnlineLayer() {
        final OnlineTileSource onlineTileSource = MapUtils.createOnlineTileSource();

        mapDownloadLayer = new TileDownloadLayer(tileCache,
                mapView.getModel().mapViewPosition, onlineTileSource,
                AndroidGraphicFactory.INSTANCE) {
            @Override
            public boolean onLongPress(LatLong tapLatLong, Point thisXY, Point tapXY) {
                    onLongPress(tapLatLong, thisXY, tapXY);
                    return true;
            }
        };
        mapView.getLayerManager().getLayers().add(mapDownloadLayer);
        mapDownloadLayer.onResume();
    }
}

