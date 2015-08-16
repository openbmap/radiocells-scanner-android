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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
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
import android.os.Bundle;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
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
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import org.openbmap.Preferences;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.CellRecord;
import org.openbmap.db.models.LogFile;
import org.openbmap.db.models.PositionRecord;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.db.models.WifiRecord.CatalogStatus;
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
import java.util.Locale;

/**
 * WirelessLoggerService takes care of wireless logging, i.e. cell & wifi logging
 * 
 * There are two external triggers
 * 	1) gps signal (@see BroadcastReceiver.onReceive)
 *  2) message bus (@see AbstractService.onReceiveMessage), which takes care of starting and stopping service
 */
public class WirelessLoggerService extends AbstractService {

	public static final String TAG = WirelessLoggerService.class.getSimpleName();

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
	private TelephonyManager mTelephonyManager;

	private PhoneStateListener mPhoneListener;

	/*	
	 * 	Cells Strength information
	 */
	private int gsmStrengthDbm = 0;
	// TODO NeighboringCellInfo.UNKNOWN_RSSI is not adequate
	private int gsmStrengthAsu = NeighboringCellInfo.UNKNOWN_RSSI;
	private int cdmaStrengthDbm = 0;
	private int cdmaEcIo = 0;
	private int signalStrengthEvdodBm = 0;
	private int signalStrengthEvdoEcio = 0;
	private int signalStrengthSnr = 0;
	private int gsmBitErrorRate = 0;
	private final int signalStrengthGsm = 0;
	private final boolean signalStrengthIsGsm = false;

	/*
	 * last known location
	 */
	private Location mMostCurrentLocation = new Location("DUMMY");
	private String mMostCurrentLocationProvider;

	/*
	 * location of last saved cell
	 */
	private Location mCellSavedAt = new Location("DUMMY");

	/*
	 * Location of last saved wifi
	 */
	private Location mWifiSavedAt = new Location("DUMMY");

	/*
	 * Position where wifi scan has been initiated.
	 */
	private Location mBeginLocation;

	/**
	 * Location provider's name (e.g. GPS) 
	 */
	private String mBeginLocationProvider;

	/*
	 * Wifi Manager
	 */
	private WifiManager mWifiManager;

	/**
	 * Are we currently tracking ?
	 */
	private boolean mIsTracking = false;

	/**
	 * Current session id
	 */
	private int mSessionId = RadioBeacon.SESSION_NOT_TRACKING;

	/**
	 * Is WifiRecord enabled ?
	 */
	private final boolean mIsWifiEnabled = false;

	/*
	 * Wifi scan is asynchronous. pendingWifiScanResults ensures that only one scan is mIsRunning
	 */
	private boolean pendingWifiScanResults = false;

	/**
	 * Wifi scan result callback
	 */
	private WifiScanCallback mWifiScanResults;

	/**
	 * WakeLock to prevent cpu from going into sleep mode
	 */
	private WakeLock mWakeLock;

	/**
	 * WakeLock Tag
	 */
	private static final String	WAKELOCK_NAME	= "WakeLock.CPU";

	/**
	 * WifiLock to prevent wifi from going into sleep mode 
	 */
	private WifiLock mWifiLock;

	/**
	 * WifiLock tag
	 */
	private static final String	WIFILOCK_NAME = "WakeLock.WIFI";

	/**
	 * Database value for missing data
	 * Cave eat: Old api (CellLocation) reports missing data as -1, whereas new api (getAllCellInfo) reports missing data as Integer.MAX_VALUE
	 * Regardless which api is used invalid values are saved with INVALID_ID
	 */
	private static final int INVALID_VALUE	= -1;

	/*
	 * DataHelper for persisting recorded information in database
	 */
	private DataHelper mDataHelper;

	private LogFile mLogFile;

	/**
	 * List of blocked ssids, e.g. moving wlans
	 * Supports wild card like operations: begins with and ends with
	 */
	private SsidBlackList mSsidBlackList;

	/**
	 * List of blocked areas, e.g. home zone
	 */
	private LocationBlackList mLocationBlacklist;

	/**
	 * Wifi catalog database (used for checking if new wifi)
	 */
	private SQLiteDatabase	mRefDb;

	/**
	 * Receives location updates as well as wifi scan result updates
	 */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			//Log.d(TAG, "Received intent " + intent.getAction());
			// handling gps broadcasts
			if (RadioBeacon.INTENT_POSITION_UPDATE.equals(intent.getAction())) {
				if (!mIsTracking) {
					return;
				}

				final Location location = intent.getExtras().getParcelable(LOCATION_PARCEL);
				final String source = location.getProvider();

				// do nothing, if required minimum gps accuracy is not given
				if (!acceptableAccuracy(location)) {
					Log.i(TAG, "GPS accuracy to bad (" + location.getAccuracy() + "m). Skipping cycle");
					return;
				}

				/*
				 * two criteria are required for cells updates
				 * 		distance > MIN_CELL_DISTANCE
				 * 		elapsed time (in milli seconds) > MIN_CELL_TIME_INTERVAL 
				 */
				if (acceptableCellDistance(location, mCellSavedAt)) {
					Log.d(TAG, "Cell update. Distance " + location.distanceTo(mCellSavedAt));
					final boolean resultOk = performCellsUpdate(location, source);

					if (resultOk) {
						//Log.i(TAG, "Successfully saved cell");
						mCellSavedAt = location;	
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
					Log.d(TAG, "Wifi update. Distance " + location.distanceTo(mWifiSavedAt));
					mBeginLocation = location;
					mBeginLocationProvider = source;
					performWifiUpdate();
				} else {
					Log.i(TAG, "Wifi update skipped: either to close to last location or interval < " + MIN_CELL_TIME_INTERVAL / 2000 + " seconds");
				}

				mMostCurrentLocation = location;
				mMostCurrentLocationProvider = source;
			} 

			// handling wifi scan results
			if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
				Log.d(TAG, "Wifi manager signals wifi scan results.");
				// scan callback can be null after service has been stopped or another app has requested an update
				if (mWifiScanResults != null) {
					mWifiScanResults.onWifiResultsAvailable();
				} else {
					Log.i(TAG, "Scan Callback is null, skipping message");
				}
			} 
		}
	};

	@Override
	public final void onCreate() {		
		Log.d(TAG, "WirelessLoggerService created");
		super.onCreate();

		// get shared preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		if (!prefs.getString(Preferences.KEY_WIFI_CATALOG_FILE, Preferences.VAL_WIFI_CATALOG_FILE).equals(Preferences.VAL_WIFI_CATALOG_NONE)) {
			final String catalogPath = prefs.getString(Preferences.KEY_WIFI_CATALOG_FOLDER,
					getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.WIFI_CATALOG_SUBDIR)
					+ File.separator + prefs.getString(Preferences.KEY_WIFI_CATALOG_FILE, Preferences.VAL_WIFI_CATALOG_FILE);

			if (!(new File(catalogPath)).exists()) {
                Log.w(TAG, "Selected catalog doesn't exist");
                mRefDb = null;
            } else {
                try {
                    mRefDb = SQLiteDatabase.openDatabase(catalogPath, null, SQLiteDatabase.OPEN_READONLY);
                } catch (final SQLiteCantOpenDatabaseException ex) {
                    Log.e(TAG, "Can't open wifi catalog database @ " + catalogPath);
                    mRefDb = null;
                }
            }
		} else {
			Log.w(TAG, "No wifi catalog selected. Can't compare scan results with openbmap dataset.");
		}

		/*
		 * Setting up database connection
		 */
		mDataHelper = new DataHelper(this);

		registerWakeLocks();

		registerPhoneStateManager();

		registerWifiManager();

		initBlacklists();
	}

	/**
	 * 
	 */
	private void initBlacklists() {

		final String mBlacklistPath = getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + File.separator
				+ Preferences.BLACKLIST_SUBDIR;

		mLocationBlacklist = new LocationBlackList();
		mLocationBlacklist.openFile(
				mBlacklistPath + File.separator + RadioBeacon.DEFAULT_LOCATION_BLOCK_FILE);

		mSsidBlackList = new SsidBlackList();
		mSsidBlackList.openFile(
				mBlacklistPath + File.separator + RadioBeacon.DEFAULT_SSID_BLOCK_FILE,
				mBlacklistPath + File.separator + RadioBeacon.CUSTOM_SSID_BLOCK_FILE);
	}

	/** 
	 *	Register Wifi Manager to permanently measuring wifi strength
	 */
	private void registerWifiManager() {
		mWifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
	}

	/** 
	 *	Register PhoneStateListener to permanently measuring cell signal strength
	 */
	private void registerPhoneStateManager() {
		mTelephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
		mPhoneListener = new PhoneStateListener() {
			@Override
			public void onSignalStrengthsChanged(final SignalStrength signalStrength) {
				// TODO we need a timestamp for signal strength
				try {
					cdmaStrengthDbm = signalStrength.getCdmaDbm();
					cdmaEcIo = signalStrength.getCdmaEcio();
				} catch (final Exception e) {
					e.printStackTrace();
				}
				try {				
					signalStrengthEvdodBm = signalStrength.getEvdoDbm();
					signalStrengthEvdoEcio = signalStrength.getEvdoEcio();
					signalStrengthSnr = signalStrength.getEvdoSnr();
				} catch (final Exception e) {
					e.printStackTrace();
				}

				try {
					gsmBitErrorRate = signalStrength.getGsmBitErrorRate();
					gsmStrengthAsu = signalStrength.getGsmSignalStrength();
					gsmStrengthDbm = -113 + 2 * gsmStrengthAsu; // conversion ASU in dBm
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onServiceStateChanged(final ServiceState serviceState) {

				switch(serviceState.getState()) {
					case ServiceState.STATE_POWER_OFF:
						try {
							final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
							final Date now = new Date();
							final String date = formatter.format(now);
						} catch (final Exception e) {
							e.printStackTrace();
						}

						//	powerOff = true;
						break;
					case ServiceState.STATE_OUT_OF_SERVICE:
						try {
							final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
							final Date now = new Date();
							final String date = formatter.format(now);
						} catch (final Exception e) {
							e.printStackTrace();
						}

						//outOfService = true;
						break;
					default:
						//alreadyWrittenNoCellular = false;
						//outOfService = false;
						//powerOff = false;
				}
			}
		};

		/** 
		 *	Register TelephonyManager updates
		 */
		mTelephonyManager.listen(mPhoneListener,
				PhoneStateListener.LISTEN_SERVICE_STATE
				| PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
				| PhoneStateListener.LISTEN_CELL_LOCATION);
	}

	/**
	 * Unregisters PhoneStateListener
	 */
	private void unregisterPhoneStateManager() {
		mTelephonyManager.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
	}

	/**
	 * Register wakelock and wifilock to prevent phone going into sleep mode
	 */
	private void registerWakeLocks() {
		final PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
		try {
			mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_NAME);
			mWakeLock.setReferenceCounted(true);
			mWakeLock.acquire();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		if (mWifiLock != null) {
			mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY , WIFILOCK_NAME);
			mWifiLock.acquire();
		}
	}

	/**
	 * Unregisters wakelock and wifilock
	 */
	private void unregisterWakeLocks() {
		if (mWakeLock != null && mWakeLock.isHeld()) {
			mWakeLock.release();
		}
		mWakeLock = null;

		if (mWifiLock != null && mWifiLock.isHeld()) {
			mWifiLock.release();
		}
		mWifiLock = null;
	}

	/**
	 * Registers receivers for GPS and wifi scan results.
	 */
	private void registerReceivers() {
		final IntentFilter filter = new IntentFilter();

		filter.addAction(RadioBeacon.INTENT_POSITION_UPDATE);
		filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

		registerReceiver(mReceiver, filter);
	}

	/**
	 * Unregisters receivers for GPS and wifi scan results.
	 */
	private void unregisterReceivers() {
		try {
			unregisterReceiver(mReceiver);
		} catch (final IllegalArgumentException e) {
			// do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android}
		}
	}

	@Override
	public final void onDestroy() {
		Log.d(TAG, "Destroying WirelessLoggerService");
		if (mIsTracking) {
			// If we're currently tracking, save user data.
			stopTracking();
			unregisterPhoneStateManager();
		}

		unregisterWakeLocks();
		unregisterReceivers();

		if (mRefDb != null && mRefDb.isOpen()) {
			mRefDb.close();
		}

		super.onDestroy();
	}

	/**
	 * Scans available wifis and calls 
	 * Scan is an asynchronous function, so first startScan() is triggered here,
	 * then upon completion WifiScanCallback is called
	 */
	private void performWifiUpdate() {

		// cancel if wifi is disabled
		if (!mWifiManager.isWifiEnabled()) {
			Log.i(TAG, "Wifi disabled, can't initiate scan");
			return;
		}

		// only start new scan if previous scan results have already been processed
		if (!pendingWifiScanResults) {
			Log.d(TAG, "Initiated Wifi scan. Waiting for results..");
			mWifiManager.startScan();
			pendingWifiScanResults = true;

			// initialize wifi scan callback if needed
			if (this.mWifiScanResults == null) {
				this.mWifiScanResults = new WifiScanCallback() {
					public void onWifiResultsAvailable() {
						Log.d(TAG, "Wifi results are available now.");

						// Is wifi tracking disabled?
						if (!prefs.getBoolean(Preferences.KEY_LOG_WIFIS, Preferences.VAL_SAVE_WIFIS)) {
							Log.i(TAG, "Didn't save wifi: wifi tracking is disabled.");
							return;
						}

						if (pendingWifiScanResults) {
							Log.i(TAG, "Wifi scan results arrived..");
							final List<ScanResult> scanlist = mWifiManager.getScanResults();
							if (scanlist != null) {

								// Common position for all scan result wifis
								if (!GeometryUtils.isValidLocation(mBeginLocation) || !GeometryUtils.isValidLocation(mMostCurrentLocation)) {
									Log.e(TAG, "Couldn't save wifi result: invalid location");
									return;
								}

								// if we're in blocked area, skip everything
								// set mWifiSavedAt nevertheless, so next scan can be scheduled properly
								if (mLocationBlacklist.contains(mBeginLocation)) {
									mWifiSavedAt = mBeginLocation;
									broadcastBlacklisted(null, null, BlacklistReasonType.LocationBad);
									return;
								}

								final ArrayList<WifiRecord> wifis = new ArrayList<WifiRecord>(); 

								final PositionRecord begin = new PositionRecord(mBeginLocation, mSessionId, mBeginLocationProvider);		
								final PositionRecord end = new PositionRecord(mMostCurrentLocation, mSessionId, mMostCurrentLocationProvider);

								// Generates a list of wifis from scan results
								for (final ScanResult r : scanlist) {
									boolean skipThis = false;
									if (mSsidBlackList.contains(r.SSID)) {
										// skip invalid wifis
										Log.i(TAG, "Ignored " + r.SSID + " (on ssid blacklist)");
										broadcastBlacklisted(r.SSID, r.BSSID, BlacklistReasonType.SsidBlocked);
										skipThis = true;
									} else {
										Log.v(TAG, "Wifi not ssid blocked");
									}

									// skipSpecific = false;
									if (!skipThis) {
										Log.i(TAG, "Serializing wifi");
										final WifiRecord wifi = new WifiRecord();
										wifi.setBssid(r.BSSID);
										wifi.setSsid(r.SSID.toLowerCase(Locale.US));
										wifi.setCapabilities(r.capabilities);
										wifi.setFrequency(r.frequency);
										wifi.setLevel(r.level);
										// TODO: clumsy: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
										wifi.setOpenBmapTimestamp(begin.getOpenBmapTimestamp());
										wifi.setBeginPosition(begin);
										wifi.setEndPosition(end);
										wifi.setSessionId(mSessionId);
										//wifi.setNew(checkIsNew(r.BSSID));
										Log.i(TAG, "Checking catalog status");
										wifi.setCatalogStatus(checkCatalogStatus(r.BSSID));
										wifis.add(wifi);
										Log.i(TAG, "Serialisation finished");
										if (wifi.isFree()) {
											Log.i(TAG, "Found free wifi, broadcasting");
											broadcastFree(r.SSID);
										}

									}
								}
								Log.i(TAG, "Saving wifis");
								mDataHelper.storeWifiScanResults(begin, end, wifis);

								// take last seen wifi and broadcast infos in ui
								if (wifis.size() > 0) {
									broadcastWifiInfos(wifis.get(wifis.size() - 1));
									broadcastWifiUpdate();
								}

								mWifiSavedAt = mBeginLocation;
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
	 * Broadcasts signal to refresh UI.
	 */
	private void broadcastWifiUpdate() {
		final Intent intent = new Intent(RadioBeacon.INTENT_WIFI_UPDATE);
		sendBroadcast(intent);
	}

	/**
	 *  Broadcasts human-readable description of last wifi.
	 * @param recent
	 */
	private void broadcastWifiInfos(final WifiRecord recent) {
		final Intent intent = new Intent(RadioBeacon.INTENT_NEW_WIFI);
		intent.putExtra(RadioBeacon.MSG_SSID, recent.getSsid());
		intent.putExtra(RadioBeacon.MSG_STRENGTH, recent.getLevel());
		sendBroadcast(intent);
	}

	/**
	 *  Broadcasts ignore message
	 * @param ssid ssid of ignored wifi
	 * @param bssid bssid of ignored wifi
	 * @param because reason
	 */
	private void broadcastBlacklisted(final String ssid, final String bssid, final BlacklistReasonType because) {
		final Intent intent = new Intent(RadioBeacon.INTENT_WIFI_BLACKLISTED);

		// MSG_KEY contains the block reason:
		// 		RadioBeacon.MSG_BSSID for bssid blacklist,
		// 		RadioBeacon.MSG_SSID for ssid blacklist
		// 		RadioBeacon.MSG_LOCATION for location blacklist

		if (because == BlacklistReasonType.BssidBlocked) {
			// invalid bssid
			intent.putExtra(RadioBeacon.MSG_KEY, RadioBeacon.MSG_BSSID);
		} else if (because == BlacklistReasonType.SsidBlocked) {
			// invalid ssid
			intent.putExtra(RadioBeacon.MSG_KEY, RadioBeacon.MSG_SSID);
		} else if (because == BlacklistReasonType.LocationBad) {
			// invalid location
			intent.putExtra(RadioBeacon.MSG_KEY, RadioBeacon.MSG_LOCATION);
		} else {
			intent.putExtra(RadioBeacon.MSG_KEY, "Unknown reason");
		}

		if (bssid != null) {
			intent.putExtra(RadioBeacon.MSG_BSSID, bssid);
		}
		if (ssid != null) {
			intent.putExtra(RadioBeacon.MSG_SSID, ssid);
		}
		sendBroadcast(intent);
	}

	/**
	 * Broadcasts free wifi has been found
	 * @param ssid
	 */
	private void broadcastFree(final String ssid) {
		final Intent intent = new Intent(RadioBeacon.INTENT_WIFI_FREE);
		intent.putExtra(RadioBeacon.MSG_SSID, ssid);
		sendBroadcast(intent);
	}

	/**
	 * Processes cell related information and saves them to database
	 * @param location
	 * @param providerName
	 * 		Name of the position provider (e.g. gps)
	 * @return true if at least one cell has been saved
	 */
	@SuppressLint("NewApi")
	private boolean performCellsUpdate(final Location location, final String providerName) {
		// Is cell tracking disabled?
		if (!prefs.getBoolean(Preferences.KEY_LOG_CELLS, Preferences.VAL_SAVE_CELLS)) {
			Log.i(TAG, "Didn't save cells: cells tracking is disabled.");
			return false;
		}

		// Do we have gps?
		if 	(!GeometryUtils.isValidLocation(location)) {
			Log.e(TAG, "GPS location invalid (null or default value)");
			return false;
		}

		// if we're in blocked area, skip everything
		// set mCellSavedAt nevertheless, so next scan can be scheduled properly
		if (mLocationBlacklist.contains(location)) {
			mCellSavedAt = location;
			return false;
		}

		final ArrayList<CellRecord> cells = new ArrayList<CellRecord>(); 
		// Common data across all cells from scan:
		// 		All cells share same position
		// 		Neighbor cells share mnc, mcc and operator with serving cell
		final PositionRecord pos = new PositionRecord(location, mSessionId, providerName);

		// can't use new api for the moment, as new api doesn't expose network type for individual cells
		//if (mNewApiSupported) {
		//}

		Boolean newApiSupported = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			newApiSupported = isNewApiSupported();
			Log.v(TAG, "Collecting cell infos (New API > 18)");
			if (newApiSupported) {
				final List<CellInfo> cellInfoList = mTelephonyManager.getAllCellInfo();
				Log.v(TAG, "Found " + cellInfoList.size() + " cells");
				for (final CellInfo info : cellInfoList) {
					final CellRecord cell = processCellInfo(info, pos);
					if (cell != null) {
						cells.add(cell);
					}
					broadcastCellInfos(cell);
					broadcastCellUpdate();
				}
			}
		}

		if (!newApiSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
			// in theory we could use getAllCellInfos even in 17
			// but wcdma support is lacking on 17
			Log.v(TAG, "Collecting cell infos (API <= 17)");
			final CellLocation cellLocation = mTelephonyManager.getCellLocation();

			if (cellLocation instanceof GsmCellLocation || cellLocation instanceof CdmaCellLocation) {

				final CellRecord serving = processServingCellLocation(cellLocation, pos);
				if (serving != null) {
					cells.add(serving);
				}

				final ArrayList<CellRecord> neigbors = processNeighbors(serving, pos);
				cells.addAll(neigbors);

				broadcastCellInfos(serving);
				broadcastCellUpdate();
			}
		}

		// now persist list of cell records in database
		// Caution: So far we set end position = begin position
		mDataHelper.storeCellsScanResults(cells, pos, pos);

		return (cells.size() > 0);
	}

	/**
	 * Checks if new cell info api is supported (i.e. TelephonyManager.getAllCellInfo() returning not null values)
	 * @return true if new api is supported by phone
	 */
	@SuppressLint("NewApi")
	private boolean isNewApiSupported() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			if (mTelephonyManager.getAllCellInfo() != null) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	/**
	 * Create a {@link CellRecord} for the serving cell by parsing {@link CellLocation}
	 * @param cell {@link CellLocation} 
	 * @param position {@link PositionRecord} Current position
	 * @return Serialized cell record
	 */
	@SuppressLint("NewApi")
	private CellRecord processServingCellLocation(final CellLocation cell, final PositionRecord position) {
		if (cell instanceof GsmCellLocation) {
			/*
			 * In case of GSM network set GSM specific values
			 */
			final GsmCellLocation gsmLocation = (GsmCellLocation) cell;

			if (isValidGsmCell(gsmLocation)) {	
				Log.i(TAG, "Assuming gsm (assumption based on cell-id" + gsmLocation.getCid() + ")");
				final CellRecord serving = new CellRecord(mSessionId);
				serving.setIsCdma(false);

				// generic cell info
				serving.setNetworkType(mTelephonyManager.getNetworkType());
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

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
					// at least for Nexus 4, even HSDPA networks broadcast psc
					serving.setPsc(gsmLocation.getPsc());
				}

				final String operator = mTelephonyManager.getNetworkOperator();
				// getNetworkOperator() may return empty string, probably due to dropped connection
				if (operator != null && operator.length() > 3) {
					serving.setOperator(operator);
					serving.setMcc(operator.substring(0, 3));
					serving.setMnc(operator.substring(3));
				} else {
					Log.e(TAG, "Error retrieving network operator, skipping cell");
					return null;
				}

				final String networkOperatorName = mTelephonyManager.getNetworkOperatorName();
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
		} else if (cell instanceof CdmaCellLocation) { 
			final CdmaCellLocation cdmaLocation = (CdmaCellLocation) cell;
			if (isValidCdmaCell(cdmaLocation)) {
				/* 
				 * In case of CDMA network set CDMA specific values
				 * Assume CDMA network, if cdma location and basestation, network and system id are available
				 */
				Log.i(TAG, "Assuming cdma for cell " + cdmaLocation.getBaseStationId());
				final CellRecord serving = new CellRecord(mSessionId);
				serving.setIsCdma(true);

				// generic cell info
				serving.setNetworkType(mTelephonyManager.getNetworkType());
				// TODO: unelegant: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
				serving.setOpenBmapTimestamp(position.getOpenBmapTimestamp());
				serving.setBeginPosition(position);
				// so far we set end position = begin position 
				serving.setEndPosition(position);
				serving.setIsServing(true);
				serving.setIsNeighbor(false);

				// getNetworkOperator can be unreliable in CDMA networks, thus be careful
				// {@link http://developer.android.com/reference/android/telephony/TelephonyManager.html#getNetworkOperator()}
				final String operator = mTelephonyManager.getNetworkOperator();
				if (operator.length() > 3) {
					serving.setOperator(operator);
					serving.setMcc(operator.substring(0, 3));
					serving.setMnc(operator.substring(3));
				} else {
					Log.i(TAG, "Error retrieving network operator, this might happen in CDMA network");
					serving.setMcc("");
					serving.setMnc("");
				}	

				final String networkOperatorName = mTelephonyManager.getNetworkOperatorName();
				if (networkOperatorName != null) {
					serving.setOperatorName(mTelephonyManager.getNetworkOperatorName());
				} else {
					Log.i(TAG, "Error retrieving network operator's name, this might happen in CDMA network");
					serving.setOperatorName("");
				}

				// CDMA specific
				serving.setBaseId(String.valueOf(cdmaLocation.getBaseStationId()));
				serving.setNetworkId(String.valueOf(cdmaLocation.getNetworkId()));
				serving.setSystemId(String.valueOf(cdmaLocation.getSystemId()));

				serving.setStrengthdBm(cdmaStrengthDbm);
				return serving;
			}
		}
		return null; 
	}

	/**
	 * Create a {@link CellRecord} by parsing {@link CellInfo}
	 * @param cell {@linkplain CellInfo}
	 * @param position {@linkplain PositionRecord Current position}
	 * @return {@link CellRecord}
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private CellRecord processCellInfo(final CellInfo cell, final PositionRecord position) {
		if (cell instanceof CellInfoGsm) {
			/*
			 * In case of GSM network set GSM specific values
			 */
			final CellIdentityGsm gsmIdentity = ((CellInfoGsm) cell).getCellIdentity();

			if (isValidGsmCell(gsmIdentity)) {	
				Log.i(TAG, "Processing gsm cell " + gsmIdentity.getCid());
				final CellRecord result = new CellRecord(mSessionId);
				result.setIsCdma(false);

				// generic cell info
				result.setNetworkType(mTelephonyManager.getNetworkType());
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

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
					// at least for Nexus 4, even HSDPA networks broadcast psc
					result.setPsc(gsmIdentity.getPsc());
				}

				final String operator = mTelephonyManager.getNetworkOperator();
				// getNetworkOperator() may return empty string, probably due to dropped connection
				if (operator.length() > 3) {
					result.setOperator(operator);
					result.setMcc(operator.substring(0, 3));
					result.setMnc(operator.substring(3));
				} else {
					Log.e(TAG, "Couldn't determine network operator, skipping cell");
					return null;
				}

				final String networkOperatorName = mTelephonyManager.getNetworkOperatorName();
				if (networkOperatorName != null) {
					result.setOperatorName(mTelephonyManager.getNetworkOperatorName());
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
			final CellIdentityWcdma wcdmaIdentity = ((CellInfoWcdma)cell).getCellIdentity();
			if (isValidWcdmaCell(wcdmaIdentity)) {	
				Log.i(TAG, "Processing wcdma cell " + wcdmaIdentity.getCid());
				final CellRecord result = new CellRecord(mSessionId);
				result.setIsCdma(false);

				// generic cell info
				result.setNetworkType(mTelephonyManager.getNetworkType());
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

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
					// at least for Nexus 4, even HSDPA networks broadcast psc
					result.setPsc(wcdmaIdentity.getPsc());
				}

				final String operator = mTelephonyManager.getNetworkOperator();
				// getNetworkOperator() may return empty string, probably due to dropped connection
				if (operator.length() > 3) {
					result.setOperator(operator);
					result.setMcc(operator.substring(0, 3));
					result.setMnc(operator.substring(3));
				} else {
					Log.e(TAG, "Couldn't determine network operator, skipping cell");
					return null;
				}

				final String networkOperatorName = mTelephonyManager.getNetworkOperatorName();
				if (networkOperatorName != null) {
					result.setOperatorName(mTelephonyManager.getNetworkOperatorName());
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
				final CellRecord result = new CellRecord(mSessionId);
				result.setIsCdma(true);

				// generic cell info
				result.setNetworkType(mTelephonyManager.getNetworkType());
				// TODO: unelegant: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
				result.setOpenBmapTimestamp(position.getOpenBmapTimestamp());
				result.setBeginPosition(position);
				// so far we set end position = begin position 
				result.setEndPosition(position);
				result.setIsServing(true);
				result.setIsNeighbor(false);

				// getNetworkOperator can be unreliable in CDMA networks, thus be careful
				// {@link http://developer.android.com/reference/android/telephony/TelephonyManager.html#getNetworkOperator()}
				final String operator = mTelephonyManager.getNetworkOperator();
				if (operator.length() > 3) {
					result.setOperator(operator);
					result.setMcc(operator.substring(0, 3));
					result.setMnc(operator.substring(3));
				} else {
					Log.i(TAG, "Couldn't determine network operator, this might happen in CDMA network");
					result.setMcc("");
					result.setMnc("");
				}	

				final String networkOperatorName = mTelephonyManager.getNetworkOperatorName();
				if (networkOperatorName != null) {
					result.setOperatorName(mTelephonyManager.getNetworkOperatorName());
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
                final CellRecord result = new CellRecord(mSessionId);
                result.setIsCdma(false);

                // generic cell info
                result.setNetworkType(mTelephonyManager.getNetworkType());
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

                final String operator = mTelephonyManager.getNetworkOperator();
                // getNetworkOperator() may return empty string, probably due to dropped connection
                if (operator.length() > 3) {
                    result.setOperator(operator);
                    result.setMcc(operator.substring(0, 3));
                    result.setMnc(operator.substring(3));
                } else {
                    Log.e(TAG, "Couldn't determine network operator, skipping cell");
                    return null;
                }

                final String networkOperatorName = mTelephonyManager.getNetworkOperatorName();
                if (networkOperatorName != null) {
                    result.setOperatorName(mTelephonyManager.getNetworkOperatorName());
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
	 * Returns an array list with neighboring cells
	 * Please note:
	 * 		1) Not all cell phones deliver neighboring cells (e.g. Samsung Galaxy)
	 * 		2) If in 3G mode, most devices won't deliver cell ids
	 * @param serving {@link CellRecord} 
	 * 		Serving cell, used to complete missing api information for neighbor cells (mcc, mnc, ..)
	 * @param cellPos {@link PositionRecord}
	 * 		Current position
	 * @return list of neigboring cell records
	 */
	private ArrayList<CellRecord> processNeighbors(final CellRecord serving, final PositionRecord cellPos) {		
		final ArrayList<CellRecord> neighbors = new ArrayList<CellRecord>();

		final ArrayList<NeighboringCellInfo> neighboringCellInfos = (ArrayList<NeighboringCellInfo>) mTelephonyManager.getNeighboringCellInfo();

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
				final CellRecord neighbor = new CellRecord(mSessionId);
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

				if (networkType == TelephonyManager.NETWORK_TYPE_GPRS || networkType ==  TelephonyManager.NETWORK_TYPE_EDGE) {
					// GSM cell
					neighbor.setIsCdma(false);

					neighbor.setLogicalCellId(ci.getCid());
					neighbor.setArea(ci.getLac());
					neighbor.setStrengthdBm(-113 + 2 * ci.getRssi());
					neighbor.setStrengthAsu(ci.getRssi());

				} else if (networkType == TelephonyManager.NETWORK_TYPE_UMTS || networkType ==  TelephonyManager.NETWORK_TYPE_HSDPA
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
					final int asu = (int) Math.round((ci.getRssi() + 113.0)/2.0);
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
	 * Note: cells with cid > max value 0xffff are accepted (typically UMTS cells. We handle them separately
	 * @param gsmLocation {@link GsmCellLocation}
	 * @return true if valid gsm cell
	 */
	private boolean isValidGsmCell(final GsmCellLocation gsmLocation) {
		if (gsmLocation == null) {
			return false;
		}
		final Integer cid = gsmLocation.getCid();
		return (cid > 0 && cid != Integer.MAX_VALUE);
	}

	/**
	 * A valid gsm cell must have cell id != -1
	 * Note: cells with cid > max value 0xffff are accepted (typically UMTS cells. We handle them separately
	 * @param gsmIdentity {@link CellIdentityGsm}
	 * @return true if valid gsm cell
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private boolean isValidGsmCell(final CellIdentityGsm gsmIdentity) {
		if (gsmIdentity == null) {
			return false;
		}

		final Integer cid = gsmIdentity.getCid();
		return (cid > 0 && cid != Integer.MAX_VALUE);
	}

	/**
	 * A valid cdma cell must have basestation id, network id and system id set
	 * @param cdmaLocation {@link CdmaCellLocation}
	 * @return true if valid cdma id
	 */
	private boolean isValidCdmaCell(final CdmaCellLocation cdmaLocation) {
		if (cdmaLocation == null) {
			return false;
		}
		return ((cdmaLocation.getBaseStationId() != -1) && (cdmaLocation.getNetworkId() != -1) && (cdmaLocation.getSystemId() != -1));
	}

	/**
	 * A valid wcdma cell must have cell id set
	 * @param wcdmaInfo {@link CellIdentityWcdma}
	 * @return true if valid cdma id
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private boolean isValidWcdmaCell(final CellIdentityWcdma wcdmaInfo) {
		if (wcdmaInfo == null) {
			return false;
		}
		final Integer cid = wcdmaInfo.getCid();
		return ((cid > -1) && (cid < Integer.MAX_VALUE));
	}

	/**
	 * A valid LTE cell must have cell id set
	 * @param lteInfo {@link CellIdentityLte}
	 * @return true if valid cdma id
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private boolean isValidLteCell(final CellIdentityLte lteInfo) {
		if (lteInfo == null) {
			return false;
		}
		final Integer cid = lteInfo.getCi();
		return ((cid > -1) && (cid < Integer.MAX_VALUE));
	}


	/**
	 * A valid cdma location must have basestation id, network id and system id set
	 * @param cdmaIdentity {@link CellInfoCdma}
	 * @return true if valid cdma id
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private boolean isValidCdmaCell(final CellIdentityCdma cdmaIdentity) {
		if (cdmaIdentity == null) {
			return false;
		}
		return ((cdmaIdentity.getBasestationId() != -1) && (cdmaIdentity.getNetworkId() != -1) && (cdmaIdentity.getSystemId() != -1));
	}

	/**
	 * @param ci
	 * @return
	 */
	private boolean isValidNeigbor(final NeighboringCellInfo ci) {
		if (ci == null) {
			return false;
		}
		return (ci.getCid() !=  NeighboringCellInfo.UNKNOWN_CID || ci.getLac() != NeighboringCellInfo.UNKNOWN_CID || ci.getPsc() !=  NeighboringCellInfo.UNKNOWN_CID);
	}

	/**
	 * Broadcast signal to refresh UI.
	 */
	private void broadcastCellUpdate() {
		final Intent intent = new Intent(RadioBeacon.INTENT_CELL_UPDATE);
		sendBroadcast(intent);
	}

	/**
	 *  Broadcasts human-readable description of last cell.
	 * @param recent
	 */
	private void broadcastCellInfos(final CellRecord recent) {
		if (recent == null) {
			Log.e(TAG, "Broadcasting error: cell record was null");
			return;
		}

		final Intent intent = new Intent(RadioBeacon.INTENT_NEW_CELL);
		/*
		intent.putExtra(RadioBeacon.MSG_KEY, recent.getOperatorName() 
				+ " " + recent.getLogicalCellId()
				+ " " + CellRecord.NETWORKTYPE_MAP().get(recent.getNetworkType())
				+ " " + recent.getStrengthdBm() + "dBm");
		 */

		intent.putExtra(RadioBeacon.MSG_OPERATOR, recent.getOperatorName());
		intent.putExtra(RadioBeacon.MSG_MCC, recent.getMcc());
		intent.putExtra(RadioBeacon.MSG_MNC, recent.getMnc());
		intent.putExtra(RadioBeacon.MSG_AREA, recent.getArea());
		intent.putExtra(RadioBeacon.MSG_CELL_ID, recent.getLogicalCellId());
		intent.putExtra(RadioBeacon.MSG_TECHNOLOGY, CellRecord.TECHNOLOGY_MAP().get(recent.getNetworkType()));
		intent.putExtra(RadioBeacon.MSG_STRENGTH, recent.getStrengthdBm());

		sendBroadcast(intent);
	}

	@Override
	public final void onStartService() {
		Log.d(TAG,"Starting WirelessLoggerService");
		registerReceivers();

		// Set mCellSavedAt and mWifiSavedAt to default values (Lat 0, Lon 0)
		// Thus MIN_CELL_DISTANCE and MIN_WIFI_DISTANCE filters are always fired on startup
		mCellSavedAt = new Location("DUMMY");
		mWifiSavedAt = new Location("DUMMY");
	}

	@Override
	public final void onStopService() {
		Log.d(TAG, "Stopping WirelessLoggerService");
		unregisterReceivers();
		stopForeground(true);
	}

	/**
	 * Starts wireless tracking.
	 * @param sessionId 
	 */
	private void startTracking(final int sessionId) {
		Log.d(TAG, "Start tracking on session " + sessionId);
		mIsTracking = true;
		mSessionId = sessionId;
		// invalidate current wifi scans
		pendingWifiScanResults = false;

		mLogFile = new LogFile(
				Build.MANUFACTURER,
				Build.MODEL,
				Build.VERSION.RELEASE,
				RadioBeacon.SWID,
				RadioBeacon.SW_VERSION, sessionId);

		mDataHelper.storeLogFile(mLogFile, sessionId);
	}

	/**
	 * Stops wireless Logging
	 */
	private void stopTracking() {
		Log.d(TAG, "Stop tracking on session " + mSessionId);
		mIsTracking = false;
		mSessionId = RadioBeacon.SESSION_NOT_TRACKING;
		mWifiScanResults = null;
		mLogFile = null;
	}

	/**
	 * Setter for mIsTracking
	 * @return true if we're currently tracking, otherwise false.
	 */
	public final boolean isTracking() {
		return mIsTracking;
	}

	/**
	 * Getter for wifiEnabled
	 * @return true if WifiRecord is enabled, otherwise false.
	 */
	public final boolean isWifiEnabled() {
		return mIsWifiEnabled;
	}

	/**
	 * Message mReceiver
	 */
	@Override
	public final void onReceiveMessage(final Message msg) {
		switch(msg.what) {
			case RadioBeacon.MSG_START_TRACKING: 
				Log.d(TAG, "Wireless logger received MSG_START_TRACKING signal");

				final Bundle aBundle = msg.getData();
				final int sessionId = aBundle.getInt(RadioBeacon.MSG_KEY, RadioBeacon.SESSION_NOT_TRACKING); 

				startTracking(sessionId);
				break;
			case RadioBeacon.MSG_STOP_TRACKING:
				Log.d(TAG, "Wireless logger received MSG_STOP_TRACKING signal");
				stopTracking();

				// before manager stopped the service
				WirelessLoggerService.this.stopSelf();
				break;
			default:
				Log.d(TAG, "Unrecognized message received: " + msg.what);
		}
	}

	/**
	 * Checks, whether bssid exists in wifi catalog.
	 * @param bssid
	 * @return Returns
	 */
	@SuppressLint("DefaultLocale")
	private CatalogStatus checkCatalogStatus(final String bssid) {

		// default: return true, if ref database n/a
		if (mRefDb == null) {
			Log.e(TAG, "Reference database not specified");
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
			final Cursor exists = mRefDb.rawQuery("SELECT bssid, source FROM wifi_zone WHERE bssid = ?", new String[]{bssid.replace(":", "").toLowerCase()});
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
			Log.e(TAG, "Couldn't open reference database");
			return CatalogStatus.NEW;
		}
	}

	/**
	 * Checks whether accuracy is good enough by testing whether accuracy is available and below settings threshold
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
	 * @param current
			current position
	 * @param last
			position of last cell update
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
	 * @param current
			current position
	 * @param last
			position of last wifi scan
	 * @return true if distance and time since last cell update are ok or if in demo mode
	 */
	private boolean acceptableWifiDistance(final Location current, final Location last) {
		return (current.distanceTo(last) > Float.parseFloat(
				prefs.getString(Preferences.KEY_MIN_WIFI_DISTANCE, Preferences.VAL_MIN_WIFI_DISTANCE))
				|| DEMO_MODE);
	}
}
