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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.preference.PreferenceManager;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.Preferences;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.CellRecord;
import org.openbmap.db.models.LogFile;
import org.openbmap.db.models.PositionRecord;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.db.models.WifiRecord.CatalogStatus;
import org.openbmap.events.onBlacklisted;
import org.openbmap.events.onCellChanged;
import org.openbmap.events.onCellSaved;
import org.openbmap.events.onFreeWifi;
import org.openbmap.events.onLocationUpdate;
import org.openbmap.events.onStartWireless;
import org.openbmap.events.onStopTracking;
import org.openbmap.events.onWifisAdded;
import org.openbmap.services.AbstractService;
import org.openbmap.services.wireless.blacklists.BlacklistReasonType;
import org.openbmap.services.wireless.blacklists.LocationBlackList;
import org.openbmap.services.wireless.blacklists.SsidBlackList;
import org.openbmap.utils.GeometryUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static android.os.Build.VERSION.SDK_INT;

/**
 * ScannerService takes care of wireless logging, i.e. cell & wifi logging
 * There are two external triggers
 * 1) gps signal (@see BroadcastReceiver.onReceive)
 * 2) message bus (@see AbstractService.onReceiveMessage), which takes care of starting and stopping service
 */
public class ScannerService extends AbstractService {

    public static final String TAG = ScannerService.class.getSimpleName();

    public static final String LOCATION_PARCEL = "android.location.Location";

    /**
     * Keeps the SharedPreferences
     */
    private SharedPreferences prefs = null;

    /*
     * minimum time interval between two cells update in milliseconds
     */
    // TODO: move to preferences
    protected static final long MIN_CELL_TIME_INTERVAL = 2000;

    /**
     * in demo mode wifis and cells are recorded continuously, regardless of minimum wifi distance set
     * ALWAYS set DEMO_MODE to false in production release
     */
    protected static final boolean DEMO_MODE = false;

    /**
     * Phone state listeners to receive cell updates
     */
    private TelephonyManager telephonyManager;

    private PhoneStateListener phoneStateListener;

    /*
     * 	Cells Strength information
     */
    private static int INVALID_STRENGTH = -1;
    private int gsmStrengthDbm = INVALID_STRENGTH;
    private int gsmStrengthAsu = INVALID_STRENGTH;
    private int cdmaStrengthDbm = INVALID_STRENGTH;
    private int cdmaEcIo = INVALID_STRENGTH;
    private int signalStrengthEvdodBm = INVALID_STRENGTH;
    private int signalStrengthEvdoEcio = INVALID_STRENGTH;
    private int signalStrengthSnr = INVALID_STRENGTH;
    private int gsmBitErrorRate = INVALID_STRENGTH;
    private final int signalStrengthGsm = INVALID_STRENGTH;
    private final boolean signalStrengthIsGsm = false;

    /*
     * last known location
     */
    private Location lastLocation = new Location("DUMMY");
    private String lastLocationProvider;

    /*
     * location of last saved cell
     */
    private Location cellsSavedAt = new Location("DUMMY");

    /*
     * Location of last saved wifi
     */
    private Location mWifiSavedAt = new Location("DUMMY");

    /*
     * Position where wifi scan has been initiated.
     */
    private Location startScanLocation;

    /**
     * Location provider's name (e.g. GPS)
     */
    private String startScanLocationProvider;

    /*
     * WifisRadiocells Manager
     */
    private WifiManager wifiManager;

    /**
     * Are we currently tracking ?
     */
    private boolean isTracking = false;

    /**
     * Current session id
     */
    private int sessionId = RadioBeacon.SESSION_NOT_TRACKING;

    /**
     * Is WifiRecord enabled ?
     */
    private final boolean isWifiEnabled = false;

    /**
     * WifisRadiocells scan is asynchronous. pendingWifiScanResults ensures that only one scan is mIsRunning
     **/
    private boolean pendingWifiScanResults = false;

    /**
     * Current serving cell
     */
    private CellInfo currentCell;

    /**
     * WifisRadiocells scan result callback
     */
    private WifiScanCallback wifiScanResults;

    /**
     * WifiLock to prevent wifi from going into sleep mode
     */
    private WifiLock mWifiLock;

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
     * WifisRadiocells catalog database (used for checking if new wifi)
     */
    private SQLiteDatabase wifiCatalog;

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

    @Override
    public final void onCreate() {
        Log.d(TAG, "ScannerService created");
        super.onCreate();

        // get shared preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (!prefs.getString(Preferences.KEY_CATALOG_FILE, Preferences.VAL_CATALOG_FILE).equals(Preferences.VAL_CATALOG_NONE)) {
            final String catalogPath = prefs.getString(Preferences.KEY_WIFI_CATALOG_FOLDER,
                    getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.CATALOG_SUBDIR)
                    + File.separator + prefs.getString(Preferences.KEY_CATALOG_FILE, Preferences.VAL_CATALOG_FILE);

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

        registerWakeLocks();

        registerPhoneStateManager();

        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);

        initBlacklists();
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
                + RadioBeacon.DEFAULT_LOCATION_BLOCK_FILE);

        ssidBlackList = new SsidBlackList();
        ssidBlackList.openFile(
                path + File.separator + RadioBeacon.DEFAULT_SSID_BLOCK_FILE,
                path + File.separator + RadioBeacon.CUSTOM_SSID_BLOCK_FILE);
    }

    /**
     * Register phone state listener for permanently measuring cell signal strength
     */
    private void registerPhoneStateManager() {
        Log.i(TAG, "Booting telephony manager");
        telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);

        printDebugInfos();

        phoneStateListener = new PhoneStateListener() {
            /**
             * Save signal strength changes in real-time
             * @param signalStrength
             */
            @Override
            public void onSignalStrengthsChanged(final SignalStrength signalStrength) {
                // TODO we need a timestamp for signal strength
                try {
                    cdmaStrengthDbm = signalStrength.getCdmaDbm();
                    cdmaEcIo = signalStrength.getCdmaEcio();
                } catch (final Exception e) {
                    Log.e(TAG, e.toString(), e);
                }
                try {
                    signalStrengthEvdodBm = signalStrength.getEvdoDbm();
                    signalStrengthEvdoEcio = signalStrength.getEvdoEcio();
                    signalStrengthSnr = signalStrength.getEvdoSnr();
                } catch (final Exception e) {
                    Log.e(TAG, e.toString(), e);
                }

                try {
                    gsmBitErrorRate = signalStrength.getGsmBitErrorRate();
                    gsmStrengthAsu = signalStrength.getGsmSignalStrength();
                    gsmStrengthDbm = -113 + 2 * gsmStrengthAsu; // conversion ASU in dBm
                } catch (final Exception e) {
                    Log.e(TAG, e.toString(), e);
                }
            }

            @Override
            public void onServiceStateChanged(final ServiceState serviceState) {
                switch (serviceState.getState()) {
                    case ServiceState.STATE_POWER_OFF:
                        try {
                            Log.i(TAG, "Service state: power off");
                            final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
                            final Date now = new Date();
                            final String date = formatter.format(now);
                        } catch (final Exception e) {
                            Log.e(TAG, e.toString(), e);
                        }
                        break;
                    case ServiceState.STATE_OUT_OF_SERVICE:
                        try {
                            Log.i(TAG, "Service state: out of service");
                            final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
                            final Date now = new Date();
                            final String date = formatter.format(now);
                        } catch (final Exception e) {
                            Log.e(TAG, e.toString(), e);
                        }
                        break;
                    default:
                }
            }

            @Override
            public void onCellInfoChanged(List<CellInfo> cellInfo) {
                Log.d(TAG, "onCellInfoChanged fired");
                if (cellInfo != null && cellInfo.size() > 0) {
                    CellInfo first = cellInfo.get(0);
                    if (!first.equals(currentCell)) {
                        Log.v(TAG, "Cell info changed: " + first.toString());
                        EventBus.getDefault().post(new onCellChanged(first));
                    }
                    currentCell = first;
                }
                super.onCellInfoChanged(cellInfo);
            }

            @Override
            public void onCellLocationChanged(CellLocation location) {
                Log.d(TAG, "onCellLocationChanged fired");
                EventBus.getDefault().post(new onCellChanged(null));
                super.onCellLocationChanged(location);
            }
        };

        /**
         *	Register TelephonyManager updates
         */
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_CELL_LOCATION);
    }

    /**
     * Outputs some debug infos to catlog
     */
    private void printDebugInfos() {
        if (SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            final PackageManager pm = getApplicationContext().getPackageManager();
            //Log.i(TAG, telephonyManager.getPhoneCount() == 1 ? "Single SIM mode" : "Dual SIM mode");
            Log.wtf(TAG, "------------ YOU MAY WANT TO REDACT INFO BELOW BEFORE POSTING THIS TO THE INTERNET ------------ ");
            Log.i(TAG, "GPS support: " + pm.hasSystemFeature("android.hardware.location.gps"));
            Log.i(TAG, "GSM support: " + pm.hasSystemFeature("android.hardware.telephony.gsm"));
            Log.i(TAG, "Wifi support: " + pm.hasSystemFeature("android.hardware.wifi"));

            Log.i(TAG, "SIM operator: " + telephonyManager.getSimOperator());
            Log.i(TAG, telephonyManager.getSimOperatorName());
            Log.i(TAG, "Network operator: " + telephonyManager.getNetworkOperator());
            Log.i(TAG, telephonyManager.getNetworkOperatorName());
            Log.i(TAG, "Roaming: " + telephonyManager.isNetworkRoaming());
            Log.wtf(TAG, "------------ YOU MAY WANT TO REDACT INFO BELOW ABOVE POSTING THIS TO THE INTERNET ------------ ");
        }
    }

    /**
     * Unregisters PhoneStateListener
     */
    private void unregisterPhoneStateManager() {
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    /**
     * Register wifilock to prevent wifi adapter going into sleep mode
     */
    private void registerWakeLocks() {
        if (mWifiLock != null) {

            int mode = WifiManager.WIFI_MODE_SCAN_ONLY;

            if (Integer.parseInt(prefs.getString(Preferences.KEY_WIFI_SCAN_MODE, Preferences.VAL_WIFI_SCAN_MODE)) == 2) {
                Log.i(TAG, "Scanning in full power mode");
                mode = WifiManager.WIFI_MODE_FULL;
            } else if (Integer.parseInt(prefs.getString(Preferences.KEY_WIFI_SCAN_MODE, Preferences.VAL_WIFI_SCAN_MODE)) == 3) {
                Log.i(TAG, "Scanning in full high perf mode");
                /**
                 * WARNING POSSIBLE HIGH POWER DRAIN!!!!
                 * see https://github.com/wish7code/openbmap/issues/130
                 */
                mode = WifiManager.WIFI_MODE_FULL_HIGH_PERF;
            }

            mWifiLock = wifiManager.createWifiLock(mode, WIFILOCK_NAME);
            mWifiLock.acquire();
        }
    }

    /**
     * Unregisters wifilock
     */
    private void releaseWakeLocks() {
        if (mWifiLock != null && mWifiLock.isHeld()) {
            mWifiLock.release();
        }
        mWifiLock = null;
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
        Log.d(TAG, "Destroying ScannerService");
        if (isTracking) {
            stopTracking();
            unregisterPhoneStateManager();
        }

        releaseWakeLocks();
        unregisterReceivers();

        if (wifiCatalog != null && wifiCatalog.isOpen()) {
            wifiCatalog.close();
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
        if (!pendingWifiScanResults) {
            Log.d(TAG, "Initiated wifi scan. Waiting for results..");
            wifiManager.startScan();
            pendingWifiScanResults = true;

            // initialize wifi scan callback if needed
            if (this.wifiScanResults == null) {
                this.wifiScanResults = new WifiScanCallback() {
                    public void onWifiResultsAvailable() {
                        Log.d(TAG, "Wifi results are available now.");

                        // Is wifi tracking disabled?
                        if (!prefs.getBoolean(Preferences.KEY_LOG_WIFIS, Preferences.VAL_SAVE_WIFIS)) {
                            Log.i(TAG, "Didn't save wifi: wifi tracking is disabled.");
                            return;
                        }

                        if (pendingWifiScanResults) {
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
                                    mWifiSavedAt = startScanLocation;
                                    broadcastBlacklisted(BlacklistReasonType.BadLocation, null);
                                    return;
                                }

                                final ArrayList<WifiRecord> wifis = new ArrayList<>();
                                final PositionRecord begin = new PositionRecord(startScanLocation, sessionId, startScanLocationProvider);
                                final PositionRecord end = new PositionRecord(lastLocation, sessionId, lastLocationProvider);

                                // Generates a list of wifis from scan results
                                for (final ScanResult r : scanlist) {
                                    boolean skipThis = false;
                                    if (ssidBlackList.contains(r.SSID)) {
                                        Log.i(TAG, "Ignored " + r.SSID + " (on SSID blacklist)");
                                        broadcastBlacklisted(BlacklistReasonType.BadSSID, r.SSID + "/" + r.BSSID);
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
                                                sessionId,
                                                checkCatalogStatus(r.BSSID));

                                        wifis.add(wifi);
                                        if (wifi.isFree()) {
                                            Log.i(TAG, "Found free wifi");
                                            broadcastFree(r.SSID);
                                        }

                                    }
                                }
                                // Log.i(TAG, "Saving wifis");
                                dataHelper.storeWifiScanResults(begin, end, wifis);


                                // take last seen wifi and broadcast infos in ui
                                if (wifis.size() > 0) {
                                    broadcastWifiDetails(wifis);
                                }

                                mWifiSavedAt = startScanLocation;
                            } else {
                                // @see http://code.google.com/p/android/issues/detail?id=19078
                                Log.e(TAG, "WifiManager.getScanResults returned null");
                            }
                            pendingWifiScanResults = false;
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
     * Processes cell related information and saves them to database
     *
     * @param here         current location
     * @param providerName Name of the position provider (e.g. gps)
     * @return true if at least one cell has been saved
     */
    private boolean updateCells(final Location here, final String providerName) {
        // Is cell tracking disabled?
        if (!prefs.getBoolean(Preferences.KEY_LOG_CELLS, Preferences.VAL_SAVE_CELLS)) {
            Log.i(TAG, "Didn't save cells: cells tracking is disabled.");
            return false;
        }

        // Do we have gps?
        if (!GeometryUtils.isValidLocation(here)) {
            Log.e(TAG, "GPS location invalid (null or default value)");
            return false;
        }

        // if we're in blocked area, skip everything
        // set cellsSavedAt nevertheless, so next scan can be scheduled properly
        if (locationBlacklist.contains(here)) {
            Log.i(TAG, "Didn't save cells: location blacklisted");
            cellsSavedAt = here;
            return false;
        }

        final ArrayList<CellRecord> cells = new ArrayList<>();
        // Common data across all cells from scan:
        // 		All cells share same position
        // 		Neighbor cells share mnc, mcc and operator with serving cell
        final PositionRecord pos = new PositionRecord(here, sessionId, providerName);
        final List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();

        if (cellInfoList != null && cellInfoList.size() > 0) {
            Log.v(TAG, "Found " + cellInfoList.size() + " cells");
            for (final CellInfo info : cellInfoList) {
                final CellRecord cell = serializeCell(info, pos);
                if (cell != null) {
                    cells.add(cell);
                }
            }
            // now persist list of cell records in database
            // Caution: So far we set end position = begin position
            dataHelper.storeCellsScanResults(cells, pos, pos);
            currentCell = cellInfoList.get(0);

            if (cells.size() > 0) {
                broadcastCellInfo(cells.get(0));
            }

            return (cells.size() > 0);
        } else {
            // crappy SAMSUNG devices don't implement getAllCellInfo(), so try alternative solution
            Log.v(TAG, "Collecting cell infos (API <= 17)");
            final CellLocation fallback = telephonyManager.getCellLocation();
            if (fallback instanceof GsmCellLocation) {
                final CellRecord serving = serializeCellLegacy(fallback, pos);
                if (serving != null) {
                    cells.add(serving);
                }

                final ArrayList<CellRecord> neigbors = processNeighbors(serving, pos);
                cells.addAll(neigbors);
                dataHelper.storeCellsScanResults(cells, pos, pos);

                if (serving != null) {
                    broadcastCellInfo(serving);
                }
                return true;
            }
            return false;
        }

    }

    /**
     * Create a {@link CellRecord} by parsing {@link CellInfo}
     *
     * @param cell     {@linkplain CellInfo}
     * @param position {@linkplain PositionRecord Current position}
     * @return {@link CellRecord}
     */
    private CellRecord serializeCell(final CellInfo cell, final PositionRecord position) {
        if (cell instanceof CellInfoGsm) {
            /*
			 * In case of GSM network set GSM specific values
			 */
            final CellIdentityGsm gsmIdentity = ((CellInfoGsm) cell).getCellIdentity();

            if (isValidGsmCell(gsmIdentity)) {
                Log.i(TAG, "Processing gsm cell " + gsmIdentity.getCid());
                final CellRecord result = new CellRecord(sessionId);
                result.setIsCdma(false);

                // generic cell info
                result.setNetworkType(telephonyManager.getNetworkType());
                // TODO: unelegant: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
                result.setOpenBmapTimestamp(position.getOpenBmapTimestamp());
                result.setBeginPosition(position);
                // so far we set end position = begin position
                result.setEndPosition(position);
                result.setIsServing(true);

                result.setIsNeighbor(!cell.isRegistered());

                // GSM specific
                result.setLogicalCellId(gsmIdentity.getCid());

                // add UTRAN ids, if needed
                if (gsmIdentity.getCid() > 0xFFFFFF) {
                    result.setUtranRnc(gsmIdentity.getCid() >> 16);
                    result.setActualCid(gsmIdentity.getCid() & 0xFFFF);
                } else {
                    result.setActualCid(gsmIdentity.getCid());
                }

                // at least for Nexus 4, even HSDPA networks broadcast psc
                result.setPsc(gsmIdentity.getPsc());

                final String operator = telephonyManager.getNetworkOperator();
                // getNetworkOperator() may return empty string, probably due to dropped connection
                if (operator.length() > 3) {
                    result.setOperator(operator);
                    result.setMcc(operator.substring(0, 3));
                    result.setMnc(operator.substring(3));
                } else {
                    Log.e(TAG, "Couldn't determine network operator, skipping cell");
                    return null;
                }

                final String networkOperatorName = telephonyManager.getNetworkOperatorName();
                if (networkOperatorName != null) {
                    result.setOperatorName(telephonyManager.getNetworkOperatorName());
                } else {
                    Log.e(TAG, "Error retrieving network operator's name, skipping cell");
                    return null;
                }

                result.setArea(gsmIdentity.getLac());
                result.setStrengthdBm(((CellInfoGsm) cell).getCellSignalStrength().getDbm());
                result.setStrengthAsu(((CellInfoGsm) cell).getCellSignalStrength().getAsuLevel());

                return result;
            }
        } else if (cell instanceof CellInfoWcdma) {
            final CellIdentityWcdma wcdmaIdentity = ((CellInfoWcdma) cell).getCellIdentity();
            if (isValidWcdmaCell(wcdmaIdentity)) {
                Log.i(TAG, "Processing wcdma cell " + wcdmaIdentity.getCid());
                final CellRecord result = new CellRecord(sessionId);
                result.setIsCdma(false);

                // generic cell info
                result.setNetworkType(telephonyManager.getNetworkType());
                // TODO: unelegant: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
                result.setOpenBmapTimestamp(position.getOpenBmapTimestamp());
                result.setBeginPosition(position);
                // so far we set end position = begin position
                result.setEndPosition(position);
                result.setIsServing(true);

                result.setIsNeighbor(!cell.isRegistered());

                result.setLogicalCellId(wcdmaIdentity.getCid());

                // add UTRAN ids, if needed
                if (wcdmaIdentity.getCid() > 0xFFFFFF) {
                    result.setUtranRnc(wcdmaIdentity.getCid() >> 16);
                    result.setActualCid(wcdmaIdentity.getCid() & 0xFFFF);
                } else {
                    result.setActualCid(wcdmaIdentity.getCid());
                }

                if (SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    // at least for Nexus 4, even HSDPA networks broadcast psc
                    result.setPsc(wcdmaIdentity.getPsc());
                }

                final String operator = telephonyManager.getNetworkOperator();
                // getNetworkOperator() may return empty string, probably due to dropped connection
                if (operator.length() > 3) {
                    result.setOperator(operator);
                    result.setMcc(operator.substring(0, 3));
                    result.setMnc(operator.substring(3));
                } else {
                    Log.e(TAG, "Couldn't determine network operator, skipping cell");
                    return null;
                }

                final String networkOperatorName = telephonyManager.getNetworkOperatorName();
                if (networkOperatorName != null) {
                    result.setOperatorName(telephonyManager.getNetworkOperatorName());
                } else {
                    Log.e(TAG, "Error retrieving network operator's name, skipping cell");
                    return null;
                }

                result.setArea(wcdmaIdentity.getLac());
                result.setStrengthdBm(((CellInfoWcdma) cell).getCellSignalStrength().getDbm());
                result.setStrengthAsu(((CellInfoWcdma) cell).getCellSignalStrength().getAsuLevel());

                return result;
            }
        } else if (cell instanceof CellInfoCdma) {
            final CellIdentityCdma cdmaIdentity = ((CellInfoCdma) cell).getCellIdentity();
            if (isValidCdmaCell(cdmaIdentity)) {
				/*
				 * In case of CDMA network set CDMA specific values
				 * Assume CDMA network, if cdma location and basestation, network and system id are available
				 */
                Log.i(TAG, "Processing cdma cell " + cdmaIdentity.getBasestationId());
                final CellRecord result = new CellRecord(sessionId);
                result.setIsCdma(true);

                // generic cell info
                result.setNetworkType(telephonyManager.getNetworkType());
                // TODO: unelegant: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
                result.setOpenBmapTimestamp(position.getOpenBmapTimestamp());
                result.setBeginPosition(position);
                // so far we set end position = begin position
                result.setEndPosition(position);
                result.setIsServing(true);
                result.setIsNeighbor(false);

                // getNetworkOperator can be unreliable in CDMA networks, thus be careful
                // {@link http://developer.android.com/reference/android/telephony/TelephonyManager.html#getNetworkOperator()}
                final String operator = telephonyManager.getNetworkOperator();
                if (operator.length() > 3) {
                    result.setOperator(operator);
                    result.setMcc(operator.substring(0, 3));
                    result.setMnc(operator.substring(3));
                } else {
                    Log.i(TAG, "Couldn't determine network operator, this might happen in CDMA network");
                    result.setMcc("");
                    result.setMnc("");
                }

                final String networkOperatorName = telephonyManager.getNetworkOperatorName();
                if (networkOperatorName != null) {
                    result.setOperatorName(telephonyManager.getNetworkOperatorName());
                } else {
                    Log.i(TAG, "Error retrieving network operator's name, this might happen in CDMA network");
                    result.setOperatorName("");
                }

                // CDMA specific
                result.setBaseId(String.valueOf(cdmaIdentity.getBasestationId()));
                result.setNetworkId(String.valueOf(cdmaIdentity.getNetworkId()));
                result.setSystemId(String.valueOf(cdmaIdentity.getSystemId()));

                result.setStrengthdBm(((CellInfoCdma) cell).getCellSignalStrength().getCdmaDbm());
                result.setStrengthAsu(((CellInfoCdma) cell).getCellSignalStrength().getAsuLevel());
                return result;
            }
        } else if (cell instanceof CellInfoLte) {
            final CellIdentityLte lteIdentity = ((CellInfoLte) cell).getCellIdentity();
            if (isValidLteCell(lteIdentity)) {
                Log.i(TAG, "Processing LTE cell " + lteIdentity.getCi());
                final CellRecord result = new CellRecord(sessionId);
                result.setIsCdma(false);

                // generic cell info
                result.setNetworkType(telephonyManager.getNetworkType());
                // TODO: unelegant: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
                result.setOpenBmapTimestamp(position.getOpenBmapTimestamp());
                result.setBeginPosition(position);
                // so far we set end position = begin position
                result.setEndPosition(position);
                result.setIsServing(true);

                result.setIsNeighbor(!cell.isRegistered());
                result.setLogicalCellId(lteIdentity.getCi());

                // set Actual Cid = Logical Cid (as we don't know better at the moment)
                result.setActualCid(result.getLogicalCellId());

                final String operator = telephonyManager.getNetworkOperator();
                // getNetworkOperator() may return empty string, probably due to dropped connection
                if (operator.length() > 3) {
                    result.setOperator(operator);
                    result.setMcc(operator.substring(0, 3));
                    result.setMnc(operator.substring(3));
                } else {
                    Log.e(TAG, "Couldn't determine network operator, skipping cell");
                    return null;
                }

                final String networkOperatorName = telephonyManager.getNetworkOperatorName();
                if (networkOperatorName != null) {
                    result.setOperatorName(telephonyManager.getNetworkOperatorName());
                } else {
                    Log.e(TAG, "Error retrieving network operator's name, skipping cell");
                    return null;
                }

                // LTE specific
                result.setArea(lteIdentity.getTac());
                result.setPsc(lteIdentity.getPci());

                result.setStrengthdBm(((CellInfoLte) cell).getCellSignalStrength().getDbm());
                result.setStrengthAsu(((CellInfoLte) cell).getCellSignalStrength().getAsuLevel());
                return result;
            }
        }
        return null;
    }

    /**
     * Legacy mode: use only if you can't get cell infos via telephonyManager.getAllCellInfo()
     *
     * @param cell
     * @param position
     * @return
     */
    @Deprecated
    private CellRecord serializeCellLegacy(final CellLocation cell, final PositionRecord position) {
        if (cell instanceof GsmCellLocation) {
            /*
			 * In case of GSM network set GSM specific values
			 */
            final GsmCellLocation gsmLocation = (GsmCellLocation) cell;

            if (isValidGsmCell(gsmLocation)) {
                Log.i(TAG, "Assuming gsm (assumption based on cell-id" + gsmLocation.getCid() + ")");
                final CellRecord serving = processGsm(position, gsmLocation);

                if (serving == null) {
                    return null;
                }

                return serving;
            }
        }
        return null;
    }

    private CellRecord processGsm(PositionRecord position, GsmCellLocation gsmLocation) {
        final CellRecord serving = new CellRecord(sessionId);
        serving.setIsCdma(false);

        // generic cell info
        serving.setNetworkType(telephonyManager.getNetworkType());
        // TODO: unelegant: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
        serving.setOpenBmapTimestamp(position.getOpenBmapTimestamp());
        serving.setBeginPosition(position);
        // so far we set end position = begin position
        serving.setEndPosition(position);
        serving.setIsServing(true);
        serving.setIsNeighbor(false);

        // GSM specific
        serving.setLogicalCellId(gsmLocation.getCid());

        // add UTRAN ids, if needed
        if (gsmLocation.getCid() > 0xFFFFFF) {
            serving.setUtranRnc(gsmLocation.getCid() >> 16);
            serving.setActualCid(gsmLocation.getCid() & 0xFFFF);
        } else {
            serving.setActualCid(gsmLocation.getCid());
        }

        if (SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            // at least for Nexus 4, even HSDPA networks broadcast psc
            serving.setPsc(gsmLocation.getPsc());
        }

        final String operator = telephonyManager.getNetworkOperator();
        // getNetworkOperator() may return empty string, probably due to dropped connection
        if (operator != null && operator.length() > 3) {
            serving.setOperator(operator);
            serving.setMcc(operator.substring(0, 3));
            serving.setMnc(operator.substring(3));
        } else {
            Log.e(TAG, "Error retrieving network operator, skipping cell");
            return null;
        }

        final String networkOperatorName = telephonyManager.getNetworkOperatorName();
        if (networkOperatorName != null) {
            serving.setOperatorName(networkOperatorName);
        } else {
            Log.e(TAG, "Error retrieving network operator's name, skipping cell");
            return null;
        }

        serving.setArea(gsmLocation.getLac());
        serving.setStrengthdBm(gsmStrengthDbm);
        serving.setStrengthAsu(gsmStrengthAsu);
        return serving;
    }

    /**
     * Returns an array list with neighboring cells
     * Please note:
     * 1) Not all cell phones deliver neighboring cells (e.g. Samsung Galaxy)
     * 2) If in 3G mode, most devices won't deliver cell ids
     *
     * @param serving {@link CellRecord}
     *                Serving cell, used to complete missing api information for neighbor cells (mcc, mnc, ..)
     * @param cellPos {@link PositionRecord}
     *                Current position
     * @return list of neigboring cell records
     */
    private ArrayList<CellRecord> processNeighbors(final CellRecord serving, final PositionRecord cellPos) {
        final ArrayList<CellRecord> neighbors = new ArrayList<>();

        final ArrayList<NeighboringCellInfo> neighboringCellInfos = (ArrayList<NeighboringCellInfo>) telephonyManager.getNeighboringCellInfo();

        if (serving == null) {
            Log.e(TAG, "Can't process neighbor cells: we need a serving cell first");
            return neighbors;
        }

        if (neighboringCellInfos == null) {
            Log.i(TAG, "Neigbor cell list null. Maybe not supported by your phone..");
            return neighbors;
        }

        // TODO: neighbor cell information in 3G mode is unreliable: lots of n/a in data.. Skip neighbor cell logging when in 3G mode or try to autocomplete missing data
        for (final NeighboringCellInfo ci : neighboringCellInfos) {
            final boolean skip = !isValidNeigbor(ci);
            if (!skip) {
                // add neigboring cells
                final CellRecord neighbor = new CellRecord(sessionId);
                // TODO: unelegant: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
                neighbor.setOpenBmapTimestamp(cellPos.getOpenBmapTimestamp());
                neighbor.setBeginPosition(cellPos);
                // so far we set end position = begin position
                neighbor.setEndPosition(cellPos);
                neighbor.setIsServing(false);
                neighbor.setIsNeighbor(true);

				/*
				 * TelephonyManager doesn't provide all data for NEIGHBOURING cells:
				 * MCC, MNC, Operator and Operator name are missing.
				 * Thus, use data from last SERVING cell.
				 *
				 * In typical cases this won't cause any problems, nevertheless problems can occur
				 * 	- near country border, where NEIGHBOURING cell can have a different MCC, MNC or Operator
				 *   - ... ?
				 */

                neighbor.setMnc(serving.getMnc());
                neighbor.setMcc(serving.getMcc());
                neighbor.setOperator(serving.getOperator());
                neighbor.setOperatorName(serving.getOperatorName());

                final int networkType = ci.getNetworkType();
                neighbor.setNetworkType(networkType);

                if (networkType == TelephonyManager.NETWORK_TYPE_GPRS || networkType == TelephonyManager.NETWORK_TYPE_EDGE) {
                    // GSM cell
                    neighbor.setIsCdma(false);

                    neighbor.setLogicalCellId(ci.getCid());
                    neighbor.setArea(ci.getLac());
                    neighbor.setStrengthdBm(-113 + 2 * ci.getRssi());
                    neighbor.setStrengthAsu(ci.getRssi());

                } else if (networkType == TelephonyManager.NETWORK_TYPE_UMTS || networkType == TelephonyManager.NETWORK_TYPE_HSDPA
                        || networkType == TelephonyManager.NETWORK_TYPE_HSUPA || networkType == TelephonyManager.NETWORK_TYPE_HSPA) {
                    // UMTS cell
                    neighbor.setIsCdma(false);
                    neighbor.setPsc(ci.getPsc());

                    neighbor.setStrengthdBm(ci.getRssi());

                    // There are several approaches on calculating ASU strength in UMTS:
                    // 1) Wikipedia: ASU = dBm + 116
                    // 	  @see http://en.wikipedia.org/wiki/Mobile_phone_signal#ASU
                    // 2) Android way: ASU = (dbm + 113) / 2
                    //	  @see TelephonyManager.getAllCellInfo.getCellStrength.getAsu() TelephonyManager.getAllCellInfo.getCellStrength.getDbm()
                    // 	  @see http://developer.android.com/reference/android/telephony/NeighboringCellInfo.html#getRssi()
                    final int asu = (int) Math.round((ci.getRssi() + 113.0) / 2.0);
                    neighbor.setStrengthAsu(asu);
                } else if (networkType == TelephonyManager.NETWORK_TYPE_CDMA) {
                    // TODO what's the strength in cdma mode? API unclear
                    neighbor.setIsCdma(true);
                }

                // not available for neighboring cells
                //cell.setMcc(mcc);
                //cell.setMnc(mnc);
                //cell.setOperator(operator);
                //cell.setOperatorName(operatorName);

                neighbors.add(neighbor);
            }
        }

        return neighbors;
    }

    /**
     * A valid gsm cell must have cell id != -1
     * Note: cells with cid > max value 0xffff are accepted (typically UMTS cells. We handle them separately)
     *
     * @param gsmIdentity {@link CellIdentityGsm}
     * @return true if valid gsm cell
     */
    private boolean isValidGsmCell(final CellIdentityGsm gsmIdentity) {
        if (gsmIdentity == null) {
            return false;
        }

        final Integer cid = gsmIdentity.getCid();
        return (cid > 0 && cid != Integer.MAX_VALUE);
    }

    /**
     * A valid gsm cell must have cell id != -1
     * Use only in legacy mode, i.e. when telephonyManager.getAllCellInfos() isn't implemented on devices (some SAMSUNGS e.g.)
     * Note: cells with cid > max value 0xffff are accepted (typically UMTS cells. We handle them separately
     *
     * @param gsmLocation {@link GsmCellLocation}
     * @return true if valid gsm cell
     */
    @Deprecated
    private boolean isValidGsmCell(final GsmCellLocation gsmLocation) {
        if (gsmLocation == null) {
            return false;
        }
        final Integer cid = gsmLocation.getCid();
        return (cid > 0 && cid != Integer.MAX_VALUE);
    }

    /**
     * A valid wcdma cell must have cell id set
     *
     * @param wcdmaInfo {@link CellIdentityWcdma}
     * @return true if valid cdma id
     */
    private boolean isValidWcdmaCell(final CellIdentityWcdma wcdmaInfo) {
        if (wcdmaInfo == null) {
            return false;
        }
        final Integer cid = wcdmaInfo.getCid();
        return ((cid > -1) && (cid < Integer.MAX_VALUE));
    }

    /**
     * A valid LTE cell must have cell id set
     *
     * @param lteInfo {@link CellIdentityLte}
     * @return true if valid cdma id
     */
    private boolean isValidLteCell(final CellIdentityLte lteInfo) {
        if (lteInfo == null) {
            return false;
        }
        final Integer cid = lteInfo.getCi();
        return ((cid > -1) && (cid < Integer.MAX_VALUE));
    }

    /**
     * A valid cdma location must have base station id, network id and system id set
     *
     * @param cdmaIdentity {@link CellInfoCdma}
     * @return true if valid cdma id
     */
    private boolean isValidCdmaCell(final CellIdentityCdma cdmaIdentity) {
        if (cdmaIdentity == null) {
            return false;
        }
        return ((cdmaIdentity.getBasestationId() != -1) && (cdmaIdentity.getNetworkId() != -1) && (cdmaIdentity.getSystemId() != -1));
    }

    /**
     * Tests if given cell is a valid neigbor cell
     * Check is required as some modems only return dummy values for neighboring cells
     * <p>
     * Note: PSC is not checked, as PSC may be -1 on GSM networks
     * see https://developer.android.com/reference/android/telephony/gsm/GsmCellLocation.html#getPsc()
     *
     * @param cell Neighbor cell
     * @return true if cell has a valid cell id and lac
     */
    private boolean isValidNeigbor(final NeighboringCellInfo cell) {
        if (cell == null) {
            return false;
        }
        return (
                (cell.getCid() != NeighboringCellInfo.UNKNOWN_CID && cell.getCid() < 0xffff) &&
                        (cell.getLac() != NeighboringCellInfo.UNKNOWN_CID && cell.getLac() < 0xffff));
    }

    /**
     * Broadcasts human-readable description of last cell.
     *
     * @param cell Cell info
     */
    private void broadcastCellInfo(final CellRecord cell) {
        if (cell == null) {
            Log.e(TAG, "Broadcasting error: cell record was null");
            return;
        }

        Log.v(TAG, "Broadcasting cell " + cell.toString());

        String cellId = String.valueOf(cell.getLogicalCellId());
        if (cell.isCdma()) {
            cellId = String.format("%s-%s%s", cell.getSystemId(), cell.getNetworkId(), cell.getBaseId());
        }

        EventBus.getDefault().post(new onCellSaved(
                cell.getOperatorName(),
                cell.getMcc(),
                cell.getMnc(),
                cell.getSystemId(),
                cell.getNetworkId(),
                cell.getBaseId(),
                cell.getArea(),
                cellId,
                CellRecord.TECHNOLOGY_MAP().get(cell.getNetworkType()),
                cell.getStrengthdBm()));
    }

    @Override
    public final void onStartService() {
        Log.d(TAG, "Starting ScannerService");
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        } else {
            Log.w(TAG, "Event bus receiver already registered");
        }
        registerReceivers();

        // Set cellsSavedAt and mWifiSavedAt to default values (Lat 0, Lon 0)
        // Thus MIN_CELL_DISTANCE and MIN_WIFI_DISTANCE filters are always fired on startup
        cellsSavedAt = new Location("DUMMY");
        mWifiSavedAt = new Location("DUMMY");
    }

    @Override
    public final void onStopService() {
        Log.d(TAG, "Stopping ScannerService");
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        unregisterReceivers();
        /**
         * TODO: is it really stopForeground only???????????????????????????????
         * TODO: MAY LEAD TO UNWANTED POWER DRAIN
         */
        stopForeground(true);
    }

    /**
     * Starts wireless tracking
     *
     * @param sessionId
     */
    private void startTracking(final int sessionId) {
        Log.d(TAG, "Start tracking on session " + sessionId);
        isTracking = true;
        this.sessionId = sessionId;

        // invalidate current wifi scans
        pendingWifiScanResults = false;

        dataHelper.storeLogFile(new LogFile(
                Build.MANUFACTURER,
                Build.MODEL,
                Build.VERSION.RELEASE,
                RadioBeacon.SWID,
                RadioBeacon.SW_VERSION,
                sessionId));
    }

    /**
     * Stops wireless Logging
     */
    private void stopTracking() {
        Log.d(TAG, "Stop tracking on session " + sessionId);
        isTracking = false;
        sessionId = RadioBeacon.SESSION_NOT_TRACKING;
        wifiScanResults = null;
    }

    @Subscribe
    public void onEvent(onStartWireless event) {
        sessionId = event.session;
        startTracking(sessionId);
    }

    @Subscribe
    public void onEvent(onStopTracking event) {
        stopTracking();
        // before manager stopped the service
        this.stopSelf();
    }

    @Subscribe
    public void onEvent(onLocationUpdate event) {
        if (!isTracking) {
            return;
        }

        final Location location = event.location;
        final String source = location.getProvider();

        // do nothing, if required minimum gps accuracy is not given
        if (!acceptableAccuracy(location)) {
            Log.i(TAG, "GPS accuracy to bad (" + location.getAccuracy() + "m). Ignoring");
            return;
        }

        /*
         * two criteria are required for cells updates
         * 		distance > MIN_CELL_DISTANCE
         * 		elapsed time (in milli seconds) > MIN_CELL_TIME_INTERVAL
         */
        if (acceptableCellDistance(location, cellsSavedAt)) {
            Log.d(TAG, "Cell update. Distance " + location.distanceTo(cellsSavedAt));
            final boolean resultOk = updateCells(location, source);

            if (resultOk) {
                //Log.i(TAG, "Successfully saved cell");
                cellsSavedAt = location;
            }
        } else {
            Log.i(TAG, "Cell update skipped: either to close to last location or interval < " + MIN_CELL_TIME_INTERVAL / 2000 + " seconds");
        }

        /*
         * required criteria for wifi updates
         * 		distance to last cell measurement > MIN_WIFI_DISTANCE
         * when in demo mode wifi updates take place regardless of MIN_WIFI_DISTANCE
         */
        if (acceptableWifiDistance(location, mWifiSavedAt)) {
            Log.d(TAG, "Wifis update. Distance " + location.distanceTo(mWifiSavedAt));
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
                prefs.getString(Preferences.KEY_REQ_GPS_ACCURACY, Preferences.VAL_REQ_GPS_ACCURACY)));
    }

    /**
     * Ensures that there is a minimum distance and minimum time delay between two cell updates.
     * If in DEMO_MODE this behaviour is overridden and cell updates are triggered as fast as possible
     *
     * @param current current position
     * @param last    position of last cell update
     * @return true if distance and time since last cell update are ok or if in demo mode
     */
    private boolean acceptableCellDistance(final Location current, final Location last) {
        return (current.distanceTo(last) > Float.parseFloat(
                prefs.getString(Preferences.KEY_MIN_CELL_DISTANCE, Preferences.VAL_MIN_CELL_DISTANCE))
                && (current.getTime() - last.getTime() > MIN_CELL_TIME_INTERVAL) || DEMO_MODE);
    }

    /**
     * Ensures that cell there is a minimum distance between two wifi scans.
     * If in DEMO_MODE this behaviour is overridden and wifi scans are triggered as fast as possible
     *
     * @param current current position
     * @param last    position of last wifi scan
     * @return true if distance and time since last cell update are ok or if in demo mode
     */
    private boolean acceptableWifiDistance(final Location current, final Location last) {
        return (current.distanceTo(last) > Float.parseFloat(
                prefs.getString(Preferences.KEY_MIN_WIFI_DISTANCE, Preferences.VAL_MIN_WIFI_DISTANCE))
                || DEMO_MODE);
    }
}
