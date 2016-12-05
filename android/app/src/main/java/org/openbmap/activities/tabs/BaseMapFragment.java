package org.openbmap.activities.tabs;

import android.app.Fragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import org.mapsforge.core.graphics.Filter;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidPreferences;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.download.TileDownloadLayer;
import org.mapsforge.map.layer.download.tilesource.OnlineTileSource;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.utils.MapUtils;

import butterknife.BindView;
import butterknife.Unbinder;

/**
 * Base class for fragment with a mapsforge map. Class takes care of cache initialization,
 * map setup and cleanup.
 *
 * Important notes:
 * - Derived classes must call {@code initBaseMap()} in their onCreateView() method <p>
 * - Derived classes must all register themselves to Butterknife using {@code this.mUnbinder = ButterKnife.bind(this, view)}
 * in their onCreateView() method
 */
public abstract class BaseMapFragment extends Fragment implements MapUtils.onLongPressHandler {
    private static final String TAG = BaseMapFragment.class.getSimpleName();

    private boolean NIGHT_MODE = true;

    protected Unbinder mUnbinder;

    /**
     * Baselayer cache
     */
    private TileCache mTileCache;

    /**
     * Online tile layer, used when no offline map available
     */
    protected TileDownloadLayer mOnlineLayer = null;

    /**
     * Used for persisting zoom and position settings onPause / onDestroy
     */
    private AndroidPreferences mPreferencesFacade;

    /**
     * MapView
     */
    @BindView(R.id.map)
    public MapView mMapView;

    @Override
    public void onDestroyView() {

        releaseMap();

        if (this.mUnbinder != null) {
            this.mUnbinder.unbind();
        }

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        releaseMap();
        super.onDestroy();
    }

    @Override
    public void onResume(){
        super.onResume();
        if (mMapView == null) {
            initBaseMap();
        }
        if (mOnlineLayer != null) {
            mOnlineLayer.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mOnlineLayer != null) {
            mOnlineLayer.onPause();
        }

        //releaseMap();
        super.onPause();
    }

    protected abstract String getPersistableId();

    /**
     * Initializes map components
     * <p/>
     * Important note:
     * {@code initBaseMap()} must be called in onCreateView of subclasses
     */
    protected void initBaseMap() {

        if (mMapView == null) {
            Log.e(TAG, "MapView is null");
            return;
        }

        final SharedPreferences prefs = getActivity().getApplicationContext().getSharedPreferences(
                getPersistableId(), /*MODE_PRIVATE*/ 0);
        mPreferencesFacade = new AndroidPreferences(prefs);

        mMapView.getModel().init(mPreferencesFacade);
        mMapView.setClickable(true);
        mMapView.getMapScaleBar().setVisible(true);

        mTileCache = createTileCache();

        mMapView.getModel().displayModel.setFilter(NIGHT_MODE ? Filter.GRAYSCALE : Filter.NONE);

        // zoom to moderate zoom level on startup
        if (mMapView.getModel().mapViewPosition.getZoomLevel() < (byte) 10 || mMapView.getModel().mapViewPosition.getZoomLevel() > (byte) 18) {
            Log.i(TAG, "Reseting zoom level");
            mMapView.getModel().mapViewPosition.setZoomLevel((byte) 15);
        }

        if (MapUtils.hasOfflineMap(this.getActivity().getApplicationContext())) {
            Log.i(TAG, "Using offline map mode");
            mMapView.getLayerManager().getLayers().clear();
            addOfflineLayer();
        } else if (MapUtils.useOnlineMaps(this.getActivity().getApplicationContext())) {
            Log.i(TAG, "Using online map mode");
            Toast.makeText(this.getActivity(), R.string.info_using_online_map, Toast.LENGTH_LONG).show();
            addOnlineLayer();
        } else {
            Log.w(TAG, "Neither online mode activated, nor offline map avaibable");
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    PreferenceManager.getDefaultSharedPreferences(
                            getActivity()).edit().putString(Preferences.KEY_MAP_FILE, Preferences.VAL_MAP_ONLINE).apply();
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
    }
        /**
         * Creates a long-press enabled offline map layer
         */
    private void addOfflineLayer() {
        Log.i(TAG, "Adding offline tile layer");
        final Layer offlineLayer = MapUtils.createTileRendererLayer(
                this.mTileCache,
                this.mMapView.getModel().mapViewPosition,
                MapUtils.getMapFile(this.getActivity()),
                MapUtils.getRenderTheme(this.getActivity()),
                this
        );

        if (offlineLayer != null) this.mMapView.getLayerManager().getLayers().add(offlineLayer);
    }

    /**
     * Creates a long-press enabled offline map layer
     */
    private void addOnlineLayer() {
        final OnlineTileSource onlineTileSource = MapUtils.createOnlineTileSource();

        Log.i(TAG, "Adding online tile layer");
        mOnlineLayer = new TileDownloadLayer(mTileCache,
                mMapView.getModel().mapViewPosition, onlineTileSource,
                AndroidGraphicFactory.INSTANCE) {
            @Override
            public boolean onLongPress(LatLong tapLatLong, Point thisXY, Point tapXY) {
                BaseMapFragment.this.onLongPress(tapLatLong, thisXY, tapXY);
                return true;
            }
        };
        mMapView.getLayerManager().getLayers().add(mOnlineLayer);
    }

    /**
     * Creates a tile cache for the baselayer
     *
     * @return tile cache
     */
    protected final TileCache createTileCache() {
        if (mMapView == null) {
            return null;
        }
        Log.i(TAG, "Creating tile cache");

        return AndroidUtil.createTileCache(
                getActivity().getApplicationContext(),
                "mapcache",
                mMapView.getModel().displayModel.getTileSize(),
                1f,
                mMapView.getModel().frameBufferModel.getOverdrawFactor());
    }

    /**
     * Cleans up mapsforge events
     * Calls mapsforge destroy events and sets map-related object to
     * null to enable garbage collection.
     */
    private void releaseMap() {
        Log.i(TAG, "Cleaning mapsforge components");

        if (mTileCache != null) {
            mTileCache.destroy();
        }

        if (mMapView != null) {
            // save map settings
            mMapView.getModel().save(mPreferencesFacade);
            mPreferencesFacade.save();
            mMapView.destroyAll();
            mMapView = null;
        }

        MapUtils.clearAndroidRessources();
    }
}
