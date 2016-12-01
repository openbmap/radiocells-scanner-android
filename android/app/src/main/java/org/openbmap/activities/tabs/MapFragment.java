/*
 Radiobeacon - Openbmap wifi and cell scanner
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
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.support.v4.content.res.ResourcesCompat;
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
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidResourceBitmap;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.overlay.Circle;
import org.mapsforge.map.layer.overlay.FixedPixelCircle;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.model.common.Observer;
import org.mapsforge.map.util.MapPositionUtil;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.PositionRecord;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.events.onCatalogUpdateAvailable;
import org.openbmap.events.onCatalogUpdateRequested;
import org.openbmap.events.onGpxUpdateAvailable;
import org.openbmap.events.onLocationUpdate;
import org.openbmap.events.onSessionUpdateAvailable;
import org.openbmap.utils.CatalogObject;
import org.openbmap.utils.GeometryUtils;
import org.openbmap.utils.GpxMapObjectsLoader;
import org.openbmap.utils.LegacyGroupLayer;
import org.openbmap.utils.MapUtils;
import org.openbmap.utils.MapUtils.onLongPressHandler;
import org.openbmap.utils.SessionLatLong;
import org.openbmap.utils.SessionObjectsLoader;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Fragment for displaying map with session's GPX track and wifis
 * Caution: due to ViewPager default implementation, this fragment is loaded even before it becomes
 * visible
 */
public class MapFragment extends BaseMapFragment implements
        ActionBar.OnNavigationListener, onLongPressHandler {

    private static final String TAG = MapFragment.class.getSimpleName();

    private static final boolean DEBUG_IGNORE_CATALOG = false;
    private static final boolean DEBUG_IGNORE_GPX = false;
    private boolean DEBUG_IGNORE_SESSION = false;

    /**
     * The Direction symbol.
     */
    @BindView(R.id.direction) ImageView directionSymbol;

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
    private static final Paint GPX_PAINT = MapUtils.createPaint(
            AndroidGraphicFactory.INSTANCE.createColor(128, 0, 0, 255),
            STROKE_GPX_WIDTH,
            Style.STROKE);

    private static final Paint ALL_POI_PAINT = MapUtils.createPaint(
            AndroidGraphicFactory.INSTANCE.createColor(ALPHA_WIFI_CATALOG_FILL,
                    120, 150, 120), 2, Style.FILL);

    private static final Paint OWN_POI_PAINT = MapUtils.createPaint(
            AndroidGraphicFactory.INSTANCE.createColor(ALPHA_WIFI_CATALOG_FILL, 255, 150, 120), 2,
            Style.FILL);


    /**
     * Paint style for active sessions objects
     */
    private static final Paint ACTIVE_SESSION_FILL = MapUtils.createPaint(
            AndroidGraphicFactory.INSTANCE.createColor(ALPHA_SESSION_FILL, 0, 0, 255), 2,
            Style.FILL);

    /**
     * Paint style for objects from other sessions
     */
    private static final  Paint OTHER_SESSIONS_FILL = MapUtils.createPaint(
            AndroidGraphicFactory.INSTANCE.createColor(ALPHA_OTHER_SESSIONS_FILL, 255, 0, 255), 2,
            Style.FILL);

    /**
     * Paint style accuracy circle
     */
    private static final Paint POSITION_FILL = MapUtils.createPaint(
            AndroidGraphicFactory.INSTANCE.createColor(96, 0, 0, 255), 0, Style.FILL);

    /**
     * Paint style accuracy circle
     */
    private static final Paint ACCURACY_FILL = MapUtils.createPaint(
            AndroidGraphicFactory.INSTANCE.createColor(48, 0, 0, 255), 0, Style.FILL);

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
    private int mSession;

    /**
     * Marks current position
     */
    private Marker mPositionMarker;
    /**
     * Marker which indicates current GPS accuracy
     */
    private Circle mAccuracyMarker;

    /**
     * System time of last gpx refresh (in millis)
     */
    private long gpxRefreshedAt;

    private byte lastZoom;

    /**
     * Layer with radiocells wifis
     */
    private LegacyGroupLayer wifisLayer;

    /**
     * Openstreetmap towers layer
     */
    private LegacyGroupLayer towersLayer;

    private List<Layer> sessionObjects;

    private Polyline gpxObjects;

    private boolean followLocation = true;

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
    private Observer mMapObserver;

    /**
     * Session layer is currently refreshing
     */
    private boolean isUpdatingSession = false;

    /**
     * Direction marker is currently updated
     */
    private boolean isUpdatingDirection;

    /**
     * Gpx layer is currently refreshing
     */
    private boolean isUpdatingGpx;

    /**
     * Catalog layer is currently refreshing
     */
    private boolean isUpdatingCatalog;

    /**
     * System time of last session layer refresh (in millis)
     */
    private long sessionObjectsRefreshTime;

    /**
     * System time of last catalog layer refresh (in millis)
     */
    private long catalogLayerRefreshTime;

    /**
     * Location of last session layer refresh
     */
    private Location sessionObjectsRefreshLocation = new Location("DUMMY");

    /**
     * Location of wifi layer refresh
     */
    private Location catalogLayerRefreshLocation = new Location("DUMMY");

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.mapview, container, false);
        this.mUnbinder = ButterKnife.bind(this, view);

        initMap();
        addMapActions();

        final Drawable posIcon = ResourcesCompat.getDrawable(getResources(), R.drawable.position, null);
        Bitmap bitmap = AndroidGraphicFactory.convertToBitmap(posIcon);
        bitmap.incrementRefCount();
        mPositionMarker = new Marker(null, bitmap, 0, -bitmap.getHeight() / 2);
        //add later on demand: mMapView.getLayerManager().getLayers().add(marker);

        mAccuracyMarker = new Circle(null, 0, ACCURACY_FILL, null);
        mMapView.getLayerManager().getLayers().add(mAccuracyMarker);


        // Register our gps broadcast mReceiver
        registerReceiver();

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

        sessionObjects = new ArrayList<>();
        gpxObjects = new Polyline(GPX_PAINT, AndroidGraphicFactory.INSTANCE);
    }

    @Override
    public final void onResume() {
        super.onResume();

        getSession();
        registerReceiver();
    }

    @Override
    public final void onPause() {
        if (mOnlineLayer != null) {
            mOnlineLayer.onPause();
        }

        clearSession();
        clearGpx();

        unregisterReceiver();

        super.onPause();
    }

    /**
     * Clean up layers and disable GPX events
     * Should be called before leaving
     *
     * @param isVisibleToUser
     */
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            registerReceiver();
            // TODO: currently no possible due to https://github.com/mapsforge/mapsforge/issues/659
            //initMap();
        } else {
            Log.d(TAG, "Map not visible, releasing");
            clearSession();
            clearGpx();
            // TODO: currently no possible due to https://github.com/mapsforge/mapsforge/issues/659
            //releaseMap();

            unregisterReceiver();
        }
    }

    @Override
    public final void onDestroy() {
        unregisterReceiver();
        super.onDestroy();
    }

    private void getSession() {
        final DataHelper dbHelper = new DataHelper(getActivity().getApplicationContext());
        mSession = dbHelper.getActiveSessionId();

        if (mSession != RadioBeacon.SESSION_NOT_TRACKING) {
            Log.i(TAG, "Displaying session " + mSession);
        } else {
            Log.w(TAG, "No active session?");
        }
    }

    private void addMapActions() {
        if (mMapView == null) {
            Log.e(TAG, "MapView is null");
            return;
        }

        mMapObserver = new Observer() {
            @Override
            public void onChange() {
                final byte zoom = mMapView.getModel().mapViewPosition.getZoomLevel();
                if (zoom != lastZoom && zoom >= MIN_OBJECT_ZOOM) {
                    // Zoom level changed
                    Log.v(TAG, "New zoom level " + zoom + ", reloading map objects");
                    updateAllLayers();
                    lastZoom = zoom;
                }

                if (!followLocation) {
                    // Free-move mode

                    final LatLong tmp = mMapView.getModel().mapViewPosition.getCenter();
                    final Location position = new Location("DUMMY");
                    position.setLatitude(tmp.latitude);
                    position.setLongitude(tmp.longitude);

                    if (sessionLayerNeedsUpdate(position)) {
                        requestSessionUpdate(position);
                    }

                    if (catalogLayerNeedsUpdate(position)) {
                        requestCatalogUpdate();
                    }

                    mPositionMarker.setVisible(true);
                }

                // hide direction indicator on manual move
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        displayDirections(false);
                    }
                });
            }
        };

        mMapView.getModel().mapViewPosition.addObserver(mMapObserver);
    }

    private void displayDirections(boolean show) {
        if (!show) {
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
                    displayDirections(true);
                    mPositionMarker.setVisible(false);
                } else {
                    displayDirections(false);
                    if (!mMapView.getLayerManager().getLayers().contains(mPositionMarker)) {
                        mMapView.getLayerManager().getLayers().add(mPositionMarker);
                    }
                    mPositionMarker.setVisible(true);
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
     *
     * @param event the event
     */
    @Subscribe
    public void onEvent(onLocationUpdate event) {
        // handling GPS broadcasts
        Location location = event.location;

        if (mMapView == null) {
            Log.e(TAG, "MapView is null");
            return;
        }

        // update layers
        if (GeometryUtils.isValidLocation(location)) {
            final LatLong currentPos = new LatLong(location.getLatitude(), location.getLongitude());

            mPositionMarker.setLatLong(currentPos);
            mAccuracyMarker.setLatLong(currentPos);
            if (location.getAccuracy() != 0) {
                mAccuracyMarker.setRadius(location.getAccuracy());
            } else {
                // on the emulator we do not get an accuracy
                mAccuracyMarker.setRadius(40);
            }
            /*
             * Update layers if necessary, but only if
             * 1.) current zoom level >= 12 (otherwise single points not visible, huge performance impact)
             * 2.) layer items haven't been refreshed for a while AND user has moved a bit
             */

            // if follow location mode is checked, move map
            if (followLocation) {
                mMapView.getModel().mapViewPosition.setCenter(currentPos);

                // might have been disabled by user - reactivate automatically
                displayDirections(true);
                mPositionMarker.setVisible(false);
            } else {
                mPositionMarker.setVisible(true);
            }

            if ((mMapView.getModel().mapViewPosition.getZoomLevel() >= MIN_OBJECT_ZOOM) && sessionLayerNeedsUpdate(location)) {
                requestSessionUpdate(location);
            }

            if ((mMapView.getModel().mapViewPosition.getZoomLevel() >= MIN_OBJECT_ZOOM) && catalogLayerNeedsUpdate(location)) {
                requestCatalogUpdate();
            }

            if (gpxNeedsUpdate()) {
                requestGpxUpdate(location);
            }

            // indicate direction
            updateDirection(location);
        } else {
            Log.e(TAG, "Invalid position! Cycle skipped");
        }
    }

    /**
     *
     */
    private void updateAllLayers() {
        if (mMapView == null) {
            Log.e(TAG, "MapView is null");
            return;
        }

        final Location mapCenter = new Location("DUMMY");
        mapCenter.setLatitude(mMapView.getModel().mapViewPosition.getCenter().latitude);
        mapCenter.setLongitude(mMapView.getModel().mapViewPosition.getCenter().longitude);

        requestSessionUpdate(mapCenter);
        requestCatalogUpdate();
    }

    private void requestCatalogUpdate() {
        if (DEBUG_IGNORE_CATALOG) {
            return;
        }

        if (!isUpdatingCatalog) {
            isUpdatingCatalog = true;
            EventBus.getDefault().post(new onCatalogUpdateRequested(this.mMapView.getBoundingBox()));
            catalogLayerRefreshTime = System.currentTimeMillis();
        }
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
                this.mMapView.getLayerManager().getLayers().remove(wifisLayer);
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
                this.mMapView.getLayerManager().getLayers().remove(towersLayer);
            }
            towersLayer = null;
            AndroidResourceBitmap.clearResourceBitmaps();
        }
    }

    /**
     *
     */
    private void clearSession() {
        if (sessionObjects == null) {
            return;
        }

        if (mMapView == null) {
            sessionObjects = null;
            return;
        }

        for (final Layer layer : sessionObjects) {
            this.mMapView.getLayerManager().getLayers().remove(layer);
        }
        sessionObjects.clear();
    }

    /**
     * Clears GPX layer objects
     */
    private void clearGpx() {
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
     * Checks whether layer is too old or too far away from previous position
     * @param current new location
     * @return true if wifis layer needs a refresh
     */
    private boolean catalogLayerNeedsUpdate(final Location current) {
        if (current == null) {
            // fail safe: draw if something went wrong
            return true;
        }

        boolean status = (catalogLayerRefreshLocation.distanceTo(current) > CATALOG_REFRESH_DISTANCE)
                && ((System.currentTimeMillis() - catalogLayerRefreshTime) > CATALOG_REFRESH_INTERVAL)
                && (followLocation);
        return status;
    }

    /**
     * Updates session layer.
     * If another refresh is in progress, update is skipped.
     *
     * @param location the location
     */
    protected final void requestSessionUpdate(final Location location) {
        if (DEBUG_IGNORE_SESSION) {
            return;
        }
        if (!isUpdatingSession && isVisible()) {
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
                && ((System.currentTimeMillis() - sessionObjectsRefreshTime) > SESSION_REFRESH_INTERVAL)
                && (followLocation));
    }

    /**
     * Loads session wifis in visible range.
     * Will call onSessionLoaded callback upon completion
     *
     * @param highlight If highlight is specified only this wifi is displayed
     */
    private void startSessionDatabaseTask(final WifiRecord highlight) {
        if (mMapView == null) {
            Log.e(TAG, "MapView is null");
            return;
        }

        final BoundingBox bbox = MapPositionUtil.getBoundingBox(
                mMapView.getModel().mapViewPosition.getMapPosition(),
                mMapView.getDimension(), mMapView.getModel().displayModel.getTileSize());

        if (highlight == null) {

            final List<Integer> sessions = new ArrayList<>();
            /*if (allLayerSelected()) {
                // load all session wifis
                sessions = new DataHelper(this).getSessionList();
            } else {*/
            sessions.add(mSession);
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

            final SessionObjectsLoader task = new SessionObjectsLoader(getActivity().getApplicationContext(), sessions);
            task.execute(minLatitude, maxLatitude, minLongitude, maxLongitude, null);
        } else {
            // draw specific wifi
            final List<Integer> sessions = new ArrayList<>();
            sessions.add(mSession);

            final SessionObjectsLoader task = new SessionObjectsLoader(getActivity().getApplicationContext(), sessions);
            task.execute(bbox.minLatitude, bbox.maxLatitude, bbox.minLongitude, bbox.maxLatitude, highlight.getBssid());
        }
    }

    /**
     * Fired when new catalog items are available.
     *
     * @param event the event
     */
    @Subscribe
    public void onEvent(onCatalogUpdateAvailable event){
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
                final Circle allSymbol = new FixedPixelCircle(poi.latLong, 8, ALL_POI_PAINT, null);
                wifisLayer.layers.add(allSymbol);
            } else if ("Own".equals(poi.category)) {
                final Circle ownSymbol = new FixedPixelCircle(poi.latLong, 8, OWN_POI_PAINT, null);
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
            mMapView.getLayerManager().getLayers().add(wifisLayer);
        }
        if (towersLayer != null) {
            mMapView.getLayerManager().getLayers().add(towersLayer);
        }
        mMapView.getLayerManager().redrawLayers();

        isUpdatingCatalog = false;
    }

    @Subscribe
    public void onEvent(onSessionUpdateAvailable event){
        if (mMapView == null) {
            Log.e(TAG, "MapView is null");
            return;
        }

        clearSession();

        for (final SessionLatLong point : event.items) {
            if (point.getSession() == mSession) {
                // current session objects are larger
                final Circle circle = new Circle(point, CIRCLE_SESSION_WIDTH, ACTIVE_SESSION_FILL, null);
                sessionObjects.add(circle);
            } else {
                // other session objects are smaller and in other color
                final Circle circle = new Circle(point, CIRCLE_OTHER_SESSION_WIDTH, OTHER_SESSIONS_FILL, null);
                sessionObjects.add(circle);
            }
        }

        /*
         *
        // if we have just loaded on point, set map center
        if (points.size() == 1) {
            mMapView.getModel().mapViewPosition.setCenter((LatLong) points.get(0));
        }
         */

        // enable next refresh
        isUpdatingSession = false;
        Log.d(TAG, "Drawed catalog objects");
    }

    @Subscribe
    public void onEvent(onGpxUpdateAvailable event){
        Log.v(TAG, "Loading " + event.items.size() + " gpx objects");

        clearGpx();

        if (mMapView == null) {
            Log.e(TAG, "MapView is null");
            return;
        }

        gpxObjects = new Polyline(GPX_PAINT, AndroidGraphicFactory.INSTANCE);

        for (final LatLong point : event.items) {
            gpxObjects.getLatLongs().add(point);
        }

        synchronized (this) {
            mMapView.getLayerManager().getLayers().add(gpxObjects);
        }

        isUpdatingGpx = false;
    }

    /**
     * @param location
     */
    private void requestGpxUpdate(final Location location) {
        if (DEBUG_IGNORE_GPX) {
            return;
        }

        if (!isUpdatingGpx && isVisible()) {
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
        return (
                ((System.currentTimeMillis() - gpxRefreshedAt) > GPX_REFRESH_INTERVAL)
                && (followLocation)
        );
    }

    /*
     * Loads gpx points in visible range.
     */
    private void startGpxDatabaseTask() {
        if (mMapView == null) {
            Log.e(TAG, "MapView is null");
            return;
        }

        final BoundingBox bbox = MapPositionUtil.getBoundingBox(
                mMapView.getModel().mapViewPosition.getMapPosition(),
                mMapView.getDimension(), mMapView.getModel().displayModel.getTileSize());
        final GpxMapObjectsLoader task = new GpxMapObjectsLoader(getActivity());
        // query with some extra space
        task.execute(mSession, bbox.minLatitude - 0.01, bbox.maxLatitude + 0.01, bbox.minLongitude - 0.15, bbox.maxLatitude + 0.15);
    }

    /**
     * Draws arrow in direction of travel. If another refresh is taking place, update is skipped
     *
     *  @param location current location (with bearing)
     */
    private void updateDirection(final Location location) {
        if (location == null) {
            return;
        }

        if (!isVisible()) {
            return;
        }

        // ensure that previous refresh has been finished
        if (isUpdatingDirection) {
            return;
        }
        isUpdatingDirection = true;

        final Integer id = directionSymbol.getTag() == null ? 0 : (Integer) directionSymbol.getTag();

        if (location.hasBearing()) {
            // determine which drawable we currently use
            rotateDirection(directionSymbol, id, location.getBearing());
        }

        isUpdatingDirection = false;
    }

    /**
     * Rotates direction image according to bearing
     *
     * @param iv          image view used for compass
     * @param resourceId resource id compass needle
     * @param bearing     bearing (azimuth)
     */
    private void rotateDirection(final ImageView iv, final Integer resourceId, final float bearing) {
        // refresh only if needed
        if (resourceId != R.drawable.direction) {
            iv.setImageResource(R.drawable.direction);
        }

        // rotate arrow
        final Matrix matrix = new Matrix();
        iv.setScaleType(ScaleType.MATRIX);   //required
        matrix.postRotate(bearing, iv.getWidth() / 2f, iv.getHeight() / 2f);
        iv.setImageMatrix(matrix);
    }

    /**
     * Gets persistable id.
     *
     * @return the id that is used to save this mapview
     */
    protected final String getPersistableId() {
        return this.getClass().getSimpleName();
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

        final PositionRecord pos = new PositionRecord(GeometryUtils.toLocation(tapLatLong), mSession, RadioBeacon.PROVIDER_USER_DEFINED, true);
        dbHelper.storePosition(pos);

        // beep once point has been saved
        ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
    }


}

