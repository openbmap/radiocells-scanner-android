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


package org.openbmap.services.wireless;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.IBinder;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.Constants;
import org.openbmap.Preferences;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.MetaData;
import org.openbmap.db.models.PositionRecord;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.db.models.WifiRecord.CatalogStatus;
import org.openbmap.events.onBlacklisted;
import org.openbmap.events.onFreeWifi;
import org.openbmap.events.onLocationUpdated;
import org.openbmap.events.onWifiScannerStart;
import org.openbmap.events.onWifiScannerStop;
import org.openbmap.events.onWifisAdded;
import org.openbmap.services.ManagerService;
import org.openbmap.services.wireless.blacklists.BlacklistReasonType;
import org.openbmap.services.wireless.blacklists.LocationBlackList;
import org.openbmap.services.wireless.blacklists.SsidBlackList;
import org.openbmap.utils.GeometryUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * CellScannerService takes care of cell logging
 */
public class WifiScannerService extends Service {

    public static final String TAG = WifiScannerService.class.getSimpleName();

    public static final String LOCATION_PARCEL = "android.location.Location";

    /**
     * Keeps the SharedPreferences
     */
    private SharedPreferences prefs = null;


    /**
     * Location of last saved wifi
     */
    private Location savedAt;

    /*
     * minimum time interval between two cells update in milliseconds
     */
    // TODO: move to preferences
    protected static final long MIN_CELL_TIME_INTERVAL = 2000;

    /**
     * in demo mode wifis are recorded continuously, regardless of minimum wifi distance set
     * ALWAYS set DEMO_MODE to false in production release
     */
    protected static final boolean DEMO_MODE = false;

    /*
     * last known location
     */
    private Location lastLocation = new Location("DUMMY");
    private String lastLocationProvider;

    /*
     * Position where wifi scan has been initiated.
     */
    private Location startScanLocation;

    /**
     * Location provider's name (e.g. GPS)
     */
    private String startScanLocationProvider;

    /*
     * Wifis Manager
     */
    private WifiManager wifiManager;

    /**
     * Are we currently tracking ?
     */
    private boolean isTracking = false;

    /**
     * Current session id
     */
    private long session = Constants.SESSION_NOT_TRACKING;

    /**
     * Is WifiRecord enabled ?
     */
    private final boolean isWifiEnabled = false;

    /**
     * Wifis scan is asynchronous. awaitingWifiScanResults ensures that only one scan is mIsRunning
     **/
    private boolean awaitingWifiScanResults = false;

    /**
     * Wifis scan result callback
     */
    private WifiScanCallback wifiScanResults;

    /**
     * WifiLock to prevent wifi from going into sleep mode
     */
    private WifiLock wifiLock;

    /**
     * WifiLock tag
     */
    private static final String WIFILOCK_NAME = "WakeLock.WIFI";

    /*
     * DataHelper for persisting recorded information in database
     */
    private DataHelper dataHelper;

    /**
     * List of blocked ssids, e.g. moving wlans
     * Supports wild card like operations: begins with and ends with
     */
    private SsidBlackList ssidBlackList;

    /**
     * List of blocked areas, e.g. home zone
     */
    private LocationBlackList locationBlacklist;

    /**
     * Wifis catalog database (used for checking if new wifi)
     */
    private SQLiteDatabase wifiCatalog;


    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger upstreamMessenger = new Messenger(new ManagerService.UpstreamHandler());


    /**
     * Receives wifi scan result updates
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                if (wifiScanResults != null) {
                    wifiScanResults.onWifiResultsAvailable();
                } else {
                    // scan callback can be null after service has been stopped or another app has requested an update
                    Log.i(TAG, "Scan Callback is null, skipping message");
                }
            }
        }
    };


    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return upstreamMessenger.getBinder();
    }


    @Override
    public final void onCreate() {
        Log.d(TAG, "WifiScannerService created");
        super.onCreate();

        Log.d(TAG, "Starting CellScannerService");
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        } else {
            Log.w(TAG, "Event bus receiver already registered");
        }

        registerWakeLocks();

        registerReceivers();

        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);

        initBlacklists();

        // Set cellsSavedAt and mWifiSavedAt to default values (Lat 0, Lon 0)
        // Thus MIN_CELL_DISTANCE filters are always fired on startup
        savedAt = new Location("DUMMY");

        // get shared preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (!prefs.getString(Preferences.KEY_CATALOG_FILE, Preferences.DEFAULT_CATALOG_FILE).equals(Preferences.VAL_CATALOG_NONE)) {
            final String catalogPath = prefs.getString(Preferences.KEY_WIFI_CATALOG_FOLDER,
                    getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.CATALOG_SUBDIR)
                    + File.separator + prefs.getString(Preferences.KEY_CATALOG_FILE, Preferences.DEFAULT_CATALOG_FILE);

            if (!(new File(catalogPath)).exists()) {
                Log.w(TAG, "Selected catalog doesn't exist");
                wifiCatalog = null;
            } else {
                try {
                    wifiCatalog = SQLiteDatabase.openDatabase(catalogPath, null, SQLiteDatabase.OPEN_READONLY);
                } catch (final SQLiteCantOpenDatabaseException ex) {
                    Log.e(TAG, "Can't open wifi catalog database @ " + catalogPath);
                    wifiCatalog = null;
                }
            }
        } else {
            Log.w(TAG, "No wifi catalog selected. Can't compare scan results with openbmap dataset.");
        }

		/*
         * Setting up database connection
		 */
        dataHelper = new DataHelper(this);
    }

    @Subscribe
    public void onEvent(onWifiScannerStart event) {
        Log.d(TAG, "ACK onWifiScannerStart event");
        session = event.session;
        savedAt = new Location("DUMMY");
        startTracking(session);
    }

    @Subscribe
    public void onEvent(onWifiScannerStop event) {
        Log.d(TAG, "ACK onWifiScannerStop event");
        stopTracking();
        // before manager stopped the service
        //this.stopSelf();
    }


    /**
     * Setup SSID / location blacklists
     */
    private void initBlacklists() {

        final String path = getApplicationContext().getExternalFilesDir(null).getAbsolutePath()
                + File.separator
                + Preferences.BLACKLIST_SUBDIR;

        locationBlacklist = new LocationBlackList();
        locationBlacklist.openFile(path
                + File.separator
                + Constants.DEFAULT_LOCATION_BLOCK_FILE);

        ssidBlackList = new SsidBlackList();
        ssidBlackList.openFile(
                path + File.separator + Constants.DEFAULT_SSID_BLOCK_FILE,
                path + File.separator + Constants.CUSTOM_SSID_BLOCK_FILE);
    }


    /**
     * Register wifilock to prevent wifi adapter going into sleep mode
     */
    private void registerWakeLocks() {
        if (wifiLock != null) {

            int mode = WifiManager.WIFI_MODE_SCAN_ONLY;

            if (Integer.parseInt(prefs.getString(Preferences.KEY_WIFI_SCAN_MODE, Preferences.DEFAULT_WIFI_SCAN_MODE)) == 2) {
                Log.i(TAG, "Scanning in full power mode");
                mode = WifiManager.WIFI_MODE_FULL;
            } else if (Integer.parseInt(prefs.getString(Preferences.KEY_WIFI_SCAN_MODE, Preferences.DEFAULT_WIFI_SCAN_MODE)) == 3) {
                Log.i(TAG, "Scanning in full high perf mode");
                /**
                 * WARNING POSSIBLE HIGH POWER DRAIN!!!!
                 * see https://github.com/wish7code/openbmap/issues/130
                 */
                mode = WifiManager.WIFI_MODE_FULL_HIGH_PERF;
            }

            wifiLock = wifiManager.createWifiLock(mode, WIFILOCK_NAME);
            wifiLock.acquire();
        }
    }


    /**
     * Unregisters wifilock
     */
    private void releaseWakeLocks() {
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        wifiLock = null;
    }


    /**
     * Registers receivers for wifi scan results.
     */
    private void registerReceivers() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(receiver, filter);
    }


    /**
     * Unregisters receivers for GPS and wifi scan results.
     */
    private void unregisterReceivers() {
        try {
            unregisterReceiver(receiver);
        } catch (final IllegalArgumentException e) {
            // do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android}
        }
    }


    @Override
    public final void onDestroy() {
        Log.d(TAG, "Destroying WifiScannerService");
        if (isTracking) {
            stopTracking();
        }

        if (wifiCatalog != null && wifiCatalog.isOpen()) {
            wifiCatalog.close();
        }

        releaseWakeLocks();
        unregisterReceivers();

        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }

        super.onDestroy();
    }


    /**
     * Scans available wifis and calls
     * Scan is an asynchronous function, so first startScan() is triggered here,
     * then upon completion WifiScanCallback is called
     */
    private void updateWifis() {
        // cancel if wifi is disabled
        if (!wifiManager.isWifiEnabled() && !wifiManager.isScanAlwaysAvailable()) {
            Log.i(TAG, "Wifi disabled or is scan always available off, skipping wifi scan");
            return;
        }

        // only start new scan if previous scan results have already been processed
        if (!awaitingWifiScanResults) {
            Log.d(TAG, "Initiated wifi scan. Waiting for results..");
            wifiManager.startScan();
            awaitingWifiScanResults = true;

            // initialize wifi scan callback if needed
            if (this.wifiScanResults == null) {
                this.wifiScanResults = new WifiScanCallback() {
                    public void onWifiResultsAvailable() {
                        Log.d(TAG, "Wifi results are available now.");

                        // Is wifi tracking disabled?
                        if (!prefs.getBoolean(Preferences.KEY_LOG_WIFIS, Preferences.DEFAULT_SAVE_WIFIS)) {
                            Log.i(TAG, "Didn't save wifi: wifi tracking is disabled.");
                            return;
                        }

                        if (awaitingWifiScanResults) {
                            final List<ScanResult> scanlist = wifiManager.getScanResults();
                            if (scanlist != null) {
                                // Common position for all scan result wifis
                                if (!GeometryUtils.isValidLocation(startScanLocation) || !GeometryUtils.isValidLocation(lastLocation)) {
                                    Log.e(TAG, "Couldn't save wifi result: invalid location");
                                    return;
                                }

                                // if we're in blocked area, skip everything
                                // set mWifiSavedAt nevertheless, so next scan can be scheduled properly
                                if (locationBlacklist.contains(startScanLocation)) {
                                    savedAt = startScanLocation;
                                    broadcastBlacklisted(BlacklistReasonType.BadLocation, null);
                                    return;
                                }

                                final ArrayList<WifiRecord> wifis = new ArrayList<>();
                                final PositionRecord begin = new PositionRecord(startScanLocation, session, startScanLocationProvider);
                                final PositionRecord end = new PositionRecord(lastLocation, session, lastLocationProvider);

                                // Generates a list of wifis from scan results
                                for (final ScanResult r : scanlist) {
                                    boolean skipThis = false;
                                    if (ssidBlackList.contains(r.SSID)) {
                                        Log.i(TAG, "Ignored " + r.SSID + " (on SSID blacklist)");
                                        broadcastBlacklisted(BlacklistReasonType.BadSSID, r.SSID + "/" + r.BSSID);
                                        skipThis = true;
                                    }
                                    if (r.BSSID.equals("00:00:00:00:00:00")) {
                                        // Quick-fix for issue on some Samsung modems reporting a non existing AP
                                        Log.w(TAG, "Received bad bssid 00:00:00:00:00:00, ignoring..");
                                        skipThis = true;
                                    }

                                    if (!skipThis) {
                                        final WifiRecord wifi = new WifiRecord(
                                                r.BSSID,
                                                WifiRecord.bssid2Long(r.BSSID),
                                                r.SSID.toLowerCase(),
                                                r.capabilities,
                                                r.frequency,
                                                r.level,
                                                begin.getOpenBmapTimestamp(),
                                                begin,
                                                end,
                                                session,
                                                checkCatalogStatus(r.BSSID));

                                        wifis.add(wifi);
                                        if (wifi.isFree()) {
                                            Log.i(TAG, "Found free wifi");
                                            broadcastFree(r.SSID);
                                        }

                                    }
                                }
                                // Log.i(TAG, "Saving wifis");
                                dataHelper.saveWifiScanResults(wifis, begin, end);


                                // take last seen wifi and broadcast infos in ui
                                if (wifis.size() > 0) {
                                    broadcastWifiDetails(wifis);
                                }

                                savedAt = startScanLocation;
                            } else {
                                // @see http://code.google.com/p/android/issues/detail?id=19078
                                Log.e(TAG, "WifiManager.getScanResults returned null");
                            }
                            awaitingWifiScanResults = false;
                        }
                    }
                };
            }
        }
    }

    /**
     * Broadcasts human-readable description of last wifi.
     */
    private void broadcastWifiDetails(final ArrayList<WifiRecord> wifis) {
        EventBus.getDefault().post(new onWifisAdded(wifis));
    }

    /**
     * Broadcasts ignore message
     *
     * @param because reason (location / bssid / ssid
     * @param message additional info
     */
    private void broadcastBlacklisted(final BlacklistReasonType because,
                                      final String message) {
        EventBus.getDefault().post(new onBlacklisted(because, message));
    }

    /**
     * Broadcasts free wifi has been found
     *
     * @param ssid SSID of free wifi
     */
    private void broadcastFree(final String ssid) {
        EventBus.getDefault().post(new onFreeWifi(ssid));
    }

    /**
     * Starts wireless tracking
     *
     * @param sessionId
     */
    private void startTracking(final long sessionId) {
        Log.d(TAG, "Start tracking on session " + sessionId);
        isTracking = true;
        this.session = sessionId;

        // invalidate current wifi scans
        awaitingWifiScanResults = false;

        dataHelper.saveMetaData(new MetaData(
                Build.MANUFACTURER,
                Build.MODEL,
                Build.VERSION.RELEASE,
                Constants.SWID,
                Constants.SW_VERSION,
                sessionId));
    }

    /**
     * Stops wireless Logging
     */
    private void stopTracking() {
        Log.d(TAG, "Stop tracking on session " + session);
        isTracking = false;
        session = Constants.SESSION_NOT_TRACKING;
        wifiScanResults = null;
    }


    @Subscribe
    public void onEvent(onLocationUpdated event) {
        if (!isTracking) {
            return;
        }

        final Location location = event.location;
        if (location == null) {
            return;
        }

        final String source = location.getProvider();

        // do nothing, if required minimum gps accuracy is not given
        if (!acceptableAccuracy(location)) {
            Log.i(TAG, "GPS accuracy to bad (" + location.getAccuracy() + "m). Ignoring");
            return;
        }

        /*
         * required criteria for wifi updates
         * 		distance to last cell measurement > MIN_WIFI_DISTANCE
         * when in demo mode wifi updates take place regardless of MIN_WIFI_DISTANCE
         */
        if (acceptableDistance(location, savedAt)) {
            Log.d(TAG, "Wifis update. Distance " + location.distanceTo(savedAt));
            startScanLocation = location;
            startScanLocationProvider = source;
            updateWifis();
        } else {
            Log.i(TAG, "Wifis update skipped: either to close to last location or interval < " + MIN_CELL_TIME_INTERVAL / 2000 + " seconds");
        }

        lastLocation = location;
        lastLocationProvider = source;
    }

    /**
     * Checks, whether bssid exists in wifi catalog.
     *
     * @param bssid
     * @return Returns
     */
    private CatalogStatus checkCatalogStatus(final String bssid) {
        if (wifiCatalog == null) {
            Log.w(TAG, "Catalog not available");
            return CatalogStatus.NEW;
        }

        try {
            /*
			 * Caution:
			 * 		Requires wifi catalog's bssid in LOWER CASE. Otherwise no records are returned
			 *
			 *		If wifi catalog's bssid aren't in LOWER case, consider SELECT bssid FROM wifi_zone WHERE LOWER(bssid) = ?
			 *		Drawback: can't use indices then
			 */
            final Cursor exists = wifiCatalog.rawQuery("SELECT bssid, source FROM wifi_zone WHERE bssid = ?", new String[]{bssid.replace(":", "").toUpperCase()});
            if (exists.moveToFirst()) {
                final int source = exists.getInt(1);
                exists.close();
                if (source == CatalogStatus.OPENBMAP.ordinal()) {
                    Log.i(TAG, bssid + " is in openbmap reference database");
                    return CatalogStatus.OPENBMAP;
                } else {
                    Log.i(TAG, bssid + " is in local reference database");
                    return CatalogStatus.LOCAL;
                }
            } else {
                Log.i(TAG, bssid + " is NOT in reference database");
                exists.close();
                //mRefdb.close();
                return CatalogStatus.NEW;
            }
        } catch (final SQLiteException e) {
            Log.e(TAG, "Couldn't open catalog");
            return CatalogStatus.NEW;
        }
    }

    /**
     * Checks whether accuracy is good enough by testing whether accuracy is available and below settings threshold
     *
     * @param location
     * @return true if location has accuracy that is acceptable
     */
    private boolean acceptableAccuracy(final Location location) {
        return location.hasAccuracy() && (location.getAccuracy() <= Float.parseFloat(
                prefs.getString(Preferences.KEY_REQ_GPS_ACCURACY, Preferences.DEFAULT_REQ_GPS_ACCURACY)));
    }


    /**
     * Ensures that cell there is a minimum distance between two wifi scans.
     * If in DEMO_MODE this behaviour is overridden and wifi scans are triggered as fast as possible
     *
     * @param current current position
     * @param last    position of last wifi scan
     * @return true if distance and time since last cell update are ok or if in demo mode
     */
    private boolean acceptableDistance(final Location current, final Location last) {
        return (current.distanceTo(last) > Float.parseFloat(
                prefs.getString(Preferences.KEY_MIN_WIFI_DISTANCE, Preferences.DEFAULT_MIN_WIFI_DISTANCE))
                || DEMO_MODE);
    }
}
