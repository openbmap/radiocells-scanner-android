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

package org.openbmap.service.wireless;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.openbmap.Preferences;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.model.CellRecord;
import org.openbmap.db.model.LogFile;
import org.openbmap.db.model.PositionRecord;
import org.openbmap.db.model.WifiRecord;
import org.openbmap.service.AbstractService;
import org.openbmap.service.wireless.blacklists.BssidBlackList;
import org.openbmap.service.wireless.blacklists.SsidBlackList;
import org.openbmap.utils.LatLongHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

/**
 * WirelessLoggerService takes care of wireless logging, i.e. cell & wifi logging
 * 
 * There are two external triggers
 * 	1) gps signal (@see BroadcastReceiver.onReceive)
 *  2) message bus (@see AbstractService.onReceiveMessage), which takes care of starting and stopping service
 */
public class WirelessLoggerService extends AbstractService {

	public static final String TAG = WirelessLoggerService.class.getSimpleName();

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

	private PhoneStateListener mPhoneListener;
	private TelephonyManager mTelephonyManager;

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
	private int signalStrengthGsm = 0;
	private boolean signalStrengthIsGsm = false;

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
	private boolean mIsWifiEnabled = false;

	/*
	 * Wifi scan is asynchronous. pendingWifiScanResults ensures that only one scan is mIsRunning
	 */
	private boolean pendingWifiScanResults = false;
	private WifiScanCallback scanCallback;

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

	/*
	 * DataHelper for persisting recorded information in database
	 */
	private DataHelper mDataHelper;

	/*
	 * Filepath to wifi catalog database, null if not set
	 */
	private String mWifiCatalogPath = null;

	private LogFile mLogFile;

	/**
	 * List of blocked ssids, e.g. moving wlans
	 * Supports wild card like operations: begins with and ends with
	 */
	private SsidBlackList	mSsidBlackList;

	/**
	 * List of blocked macs, e.g. moving wlans
	 */
	private BssidBlackList	mBssidBlackList;

	/**
	 * Receives location updates as well as wifi scan result updates
	 */
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			//Log.d(TAG, "Received intent " + intent.getAction());
			// handling gps broadcasts
			if (RadioBeacon.INTENT_BROADCAST_POSITION.equals(intent.getAction())) {
				if (!mIsTracking) {
					return;
				}

				Location location = intent.getExtras().getParcelable("android.location.Location");
				String source = location.getProvider();

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
					boolean resultOk = performCellsUpdate(location, source);
					
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
					initiatePendingWifiUpdate();
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
				if (scanCallback != null) {
					scanCallback.onWifiResultsAvailable();
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

		if (!prefs.getString(Preferences.KEY_WIFI_CATALOG, Preferences.VAL_REF_DATABASE).equals(Preferences.VAL_WIFI_CATALOG_NONE)) {
			mWifiCatalogPath = Environment.getExternalStorageDirectory().getPath()
					+ prefs.getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR)
					+ Preferences.WIFI_CATALOG_SUBDIR + "/" + prefs.getString(Preferences.KEY_WIFI_CATALOG, Preferences.VAL_REF_DATABASE);
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

		String mBlacklistPath = Environment.getExternalStorageDirectory().getPath()
				+ prefs.getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR) + File.separator 
				+ Preferences.BLACKLIST_SUBDIR;

		mSsidBlackList = new SsidBlackList();
		mSsidBlackList.openFile(
				mBlacklistPath + File.separator + "default_ssid.xml",
				mBlacklistPath + File.separator + "custom_ssid.xml");

		mBssidBlackList = new BssidBlackList();
		mBssidBlackList.openFile(
				mBlacklistPath + File.separator + "default_bssid.xml",
				mBlacklistPath + File.separator + "custom_bssid.xml");
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
				// TODO we need a timestamp for signalstrength
				try {
					cdmaStrengthDbm = signalStrength.getCdmaDbm();
					cdmaEcIo = signalStrength.getCdmaEcio();
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {				
					signalStrengthEvdodBm = signalStrength.getEvdoDbm();
					signalStrengthEvdoEcio = signalStrength.getEvdoEcio();
					signalStrengthSnr = signalStrength.getEvdoSnr();
				} catch (Exception e) {
					e.printStackTrace();
				}

				try {
					gsmBitErrorRate = signalStrength.getGsmBitErrorRate();
					gsmStrengthAsu = signalStrength.getGsmSignalStrength();
					gsmStrengthDbm = -113 + 2 * gsmStrengthAsu; // conversion ASU in dBm
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onCellLocationChanged(final CellLocation location) {

			}

			@Override
			public void onServiceStateChanged(final ServiceState serviceState) {

				switch(serviceState.getState()) {
					case ServiceState.STATE_POWER_OFF:
						try {
							SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
							Date now = new Date();
							String date = formatter.format(now);
						} catch (Exception e) {
							e.printStackTrace();
						}

						//	powerOff = true;
						break;
					case ServiceState.STATE_OUT_OF_SERVICE:
						try {
							SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
							Date now = new Date();
							String date = formatter.format(now);
						} catch (Exception e) {
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
		PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
		try {
			mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_NAME);
			mWakeLock.setReferenceCounted(true);
			mWakeLock.acquire();
		} catch (Exception e) {
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
		IntentFilter filter = new IntentFilter();

		filter.addAction(RadioBeacon.INTENT_BROADCAST_POSITION);
		filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

		registerReceiver(mReceiver, filter);
	}

	/**
	 * Unregisters receivers for GPS and wifi scan results.
	 */
	private void unregisterReceivers() {
		try {
			unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android}
		}
	}

	@Override
	public final void onDestroy() {
		if (mIsTracking) {
			// If we're currently tracking, save user data.
			stopTracking();
			unregisterPhoneStateManager();
		}

		unregisterWakeLocks();
		unregisterReceivers();
		super.onDestroy();
	}

	/**
	 * Scans available wifis and calls 
	 * Scan is an asynchronous function, so first startScan() is triggered here,
	 * then upon completion WifiScanCallback is called
	 */
	private void initiatePendingWifiUpdate() {

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

			this.scanCallback = new WifiScanCallback() {

				public void onWifiResultsAvailable() {
					Log.d(TAG, "Wifi results are available now.");

					// Is wifi tracking disabled?
					if (!prefs.getBoolean(Preferences.KEY_SAVE_WIFIS, Preferences.VAL_SAVE_WIFIS)) {
						Log.i(TAG, "Didn't save wifi: wifi tracking is disabled.");
						return;
					}

					if (pendingWifiScanResults) {
						Log.i(TAG, "Wifi scan results arrived..");
						List<ScanResult> scanlist = mWifiManager.getScanResults();
						if (scanlist != null) {

							ArrayList<WifiRecord> wifis = new ArrayList<WifiRecord>(); 

							// Common position for all scan result wifis
							if (!LatLongHelper.isValidLocation(mBeginLocation) || !LatLongHelper.isValidLocation(mMostCurrentLocation)) {
								Log.e(TAG, "Couldn't save wifi result: invalid location");
								return;
							}

							PositionRecord begin = new PositionRecord(mBeginLocation, mSessionId, mBeginLocationProvider);		
							PositionRecord end = new PositionRecord(mMostCurrentLocation, mSessionId, mMostCurrentLocationProvider);

							// Generates a list of wifis from scan results
							for (ScanResult r : scanlist) {
								if (mSsidBlackList.contains(r.SSID)) {
									// skip invalid wifis
									Log.i(TAG, "Ignored " + r.SSID + " (on ssid blacklist)");
									break;
								}
								if (mBssidBlackList.contains(r.BSSID)) {
									// skip invalid wifis
									Log.i(TAG, "Ignored " + r.BSSID + " (on bssid blacklist)");
									break;
								}
								WifiRecord wifi = new WifiRecord();
								wifi.setBssid(r.BSSID);
								wifi.setSsid(r.SSID);
								wifi.setCapabilities(r.capabilities);
								wifi.setFrequency(r.frequency);
								wifi.setLevel(r.level);
								// TODO: clumsy: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
								wifi.setOpenBmapTimestamp(begin.getOpenBmapTimestamp());
								wifi.setBeginPosition(begin);
								wifi.setEndPosition(end);
								wifi.setSessionId(mSessionId);
								wifi.setNew(checkIsNew(r.BSSID));
								wifis.add(wifi);
							}
							mDataHelper.storeWifiScanResults(begin, end, wifis);

							// take last seen wifi and broadcast infos in ui
							if (scanlist.size() > 0) {
								ScanResult recent = scanlist.get(scanlist.size() - 1);
								broadcastWifiInfos(recent);
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

	/**
	 * Broadcasts signal to refresh UI.
	 */
	private void broadcastWifiUpdate() {
		Intent intent = new Intent(RadioBeacon.INTENT_WIFI_UPDATE);
		sendBroadcast(intent);
	}

	/**
	 *  Broadcasts human-readable description of last wifi.
	 * @param recent
	 */
	private void broadcastWifiInfos(final ScanResult recent) {
		Intent intent1 = new Intent(RadioBeacon.INTENT_NEW_WIFI);
		intent1.putExtra(RadioBeacon.MSG_KEY, recent.SSID + " " + recent.level + "dBm");
		sendBroadcast(intent1);
	}

	/**
	 * Processes cell related information and saves them to database
	 * @param location
	 * @param providerName
	 * 		Name of the position provider (e.g. gps)
	 * @return true if at least one cell has been saved
	 */
	private boolean performCellsUpdate(final Location location, final String providerName) {
		// Is cell tracking disabled?
		if (!prefs.getBoolean(Preferences.KEY_SAVE_CELLS, Preferences.VAL_SAVE_CELLS)) {
			Log.i(TAG, "Didn't save cells: cells tracking is disabled.");
			return false;
		}

		// Do we have gps?
		if 	(!LatLongHelper.isValidLocation(location)) {
			Log.e(TAG, "GPS location invalid (null or default value)");
			return false;
		}

		// TODO with API > 17 there's also TelephonyManager.getAllCellInfos()
		// This might be an option for the future
		// TODO check, if signal strength update too old?

		/* 
		 * Determine, whether we are on GSM or CDMA network.
		 * Decision is based on voice-call technology used (i.e. getPhoneType instead of getNetworkType)
		 */
		GsmCellLocation gsmLocation = new GsmCellLocation();
		CdmaCellLocation cdmaLocation = new CdmaCellLocation();

		if (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_CDMA) {
			gsmLocation = (GsmCellLocation) mTelephonyManager.getCellLocation();	
		} else if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
			cdmaLocation = (CdmaCellLocation) mTelephonyManager.getCellLocation();
		}

		// Either CDMA OR GSM must be available ..
		// 		1) check for null values (typically airplane mode)
		//		2) check for valid cell / base station id
		if (!(isValidGsmLocation(gsmLocation) || isValidCdmaLocation(cdmaLocation))) {
			Log.e(TAG, "Neither CDMA nor GSM network.. Skipping cells update");
			return false;
		}

		ArrayList<CellRecord> cells = new ArrayList<CellRecord>(); 

		// Common data across all cells from scan:
		// 		All cells share same position
		// 		Neighbor cells share mnc, mcc and operator with serving cell
		PositionRecord pos = new PositionRecord(location, mSessionId, providerName);

		CellRecord serving = processServing(gsmLocation, cdmaLocation, pos);
		if (serving != null) {
			cells.add(serving);
		}

		ArrayList<CellRecord> neigbors = processNeighbors(serving, pos);
		cells.addAll(neigbors);

		// now persist cells in database
		// Please note: So far we set end position = begin position
		mDataHelper.storeCellsScanResults(cells, pos, pos);

		broadcastCellInfos(serving);
		broadcastCellUpdate();
		return (cells.size() > 0);
	}

	/**
	 * Returns a cell record with serving cell
	 * @param cdmaLocation 
	 * @param gsmLocation 
	 * @param cellPos 
	 * @return
	 */
	private CellRecord processServing(final GsmCellLocation gsmLocation, final CdmaCellLocation cdmaLocation, final PositionRecord cellPos) {
		/*
		 * In case of GSM network set GSM specific values
		 * Assume GSM network if gsm location and cell id are available
		 */
		if (isValidGsmLocation(gsmLocation)) {	
			Log.i(TAG, "Assuming gsm (assumption based on cell-id" + gsmLocation.getCid() + ")");
			CellRecord serving = new CellRecord(mSessionId);
			serving.setIsCdma(false);

			// generic cell info (equal for CDMA and GSM)
			serving.setNetworkType(mTelephonyManager.getNetworkType());
			// TODO: unelegant: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
			serving.setOpenBmapTimestamp(cellPos.getOpenBmapTimestamp());
			serving.setBeginPosition(cellPos);
			// so far we set end position = begin position 
			serving.setEndPosition(cellPos);
			serving.setIsServing(true);
			serving.setIsNeighbor(false);

			// GSM specific
			serving.setCid(gsmLocation.getCid());

			String operator = mTelephonyManager.getNetworkOperator();
			serving.setOperator(operator);
			// getNetworkOperator() may return empty string, probably due to dropped connection
			if (operator.length() > 3) {
				serving.setMcc(operator.substring(0, 3));
				serving.setMnc(operator.substring(3));
			} else {
				Log.e(TAG, "Couldn't determine network operator, skipping cell");
				return null;
			}
			serving.setOperatorName(mTelephonyManager.getNetworkOperatorName());

			serving.setLac(gsmLocation.getLac());
			serving.setStrengthdBm(gsmStrengthDbm);

			return serving;
		} else if (isValidCdmaLocation(cdmaLocation)) {
			/* 
			 * In case of CDMA network set CDMA specific values
			 * Assume CDMA network, if cdma location and basestation, network and system id are available
			 */
			Log.i(TAG, "Assuming cdma for cell " + cdmaLocation.getBaseStationId());
			CellRecord serving = new CellRecord(mSessionId);
			serving.setIsCdma(true);

			// generic cell info (equal for CDMA and GSM)
			serving.setNetworkType(mTelephonyManager.getNetworkType());
			// TODO: unelegant: implicit conversion from UTC to YYYYMMDDHHMMSS in begin.setTimestamp
			serving.setOpenBmapTimestamp(cellPos.getOpenBmapTimestamp());
			serving.setBeginPosition(cellPos);
			// so far we set end position = begin position 
			serving.setEndPosition(cellPos);
			serving.setIsServing(true);
			serving.setIsNeighbor(false);

			// getNetworkOperator can be unreliable in CDMA networks, thus be careful
			// {@link http://developer.android.com/reference/android/telephony/TelephonyManager.html#getNetworkOperator()}
			String operator = mTelephonyManager.getNetworkOperator();
			serving.setOperator(operator);
			if (operator.length() > 3) {
				serving.setMcc(operator.substring(0, 3));
				serving.setMnc(operator.substring(3));
			} else {
				Log.i(TAG, "Couldn't determine network operator, this might happen in CDMA network");
				serving.setMcc("");
				serving.setMnc("");
			}	
			serving.setOperatorName(mTelephonyManager.getNetworkOperatorName());

			// CDMA specific
			serving.setBaseId(String.valueOf(cdmaLocation.getBaseStationId()));
			serving.setNetworkId(String.valueOf(cdmaLocation.getNetworkId()));
			serving.setSystemId(String.valueOf(cdmaLocation.getSystemId()));

			serving.setStrengthdBm(cdmaStrengthDbm);
			return serving;
		}

		return null; 
	}

	/**
	 * Returns an array list with neighboring cells
	 * Please note:
	 * 		1) Not all cell phones deliver neighboring cells (e.g. Samsung Galaxy)
	 * 		2) If in 3G mode, most devices won't deliver cell ids
	 * @param serving 
	 * 		Serving cell, used to complete missing api information for neighbor cells (mcc, mnc, ..)
	 * @param cellPos 
	 * 		Current position
	 * @return
	 */
	private ArrayList<CellRecord> processNeighbors(final CellRecord serving, final PositionRecord cellPos) {
		ArrayList<CellRecord> neighbors = new ArrayList<CellRecord>();

		ArrayList<NeighboringCellInfo> neighboringCellInfos = (ArrayList<NeighboringCellInfo>) mTelephonyManager.getNeighboringCellInfo();
		// TODO: neighbor cell information in 3G mode is unreliable: lots of n/a in data.. Skip neighbor cell logging when in 3G mode or try to autocomplete missing data
		if (neighboringCellInfos != null) {

			for (NeighboringCellInfo ci : neighboringCellInfos) {
				// add neigboring cells		
				CellRecord neighbor = new CellRecord(mSessionId);
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

				int networkType = ci.getNetworkType();
				neighbor.setNetworkType(networkType);

				if (networkType == TelephonyManager.NETWORK_TYPE_GPRS || networkType ==  TelephonyManager.NETWORK_TYPE_EDGE) {
					// GSM cell
					neighbor.setIsCdma(false);

					neighbor.setCid(ci.getCid());
					neighbor.setLac(ci.getLac());
					neighbor.setStrengthdBm(-113 + 2 * ci.getRssi());

				} else if (networkType == TelephonyManager.NETWORK_TYPE_UMTS || networkType ==  TelephonyManager.NETWORK_TYPE_HSDPA
						|| networkType == TelephonyManager.NETWORK_TYPE_HSUPA || networkType == TelephonyManager.NETWORK_TYPE_HSPA) {
					// UMTS cell
					neighbor.setIsCdma(false);					
					neighbor.setPsc(ci.getPsc());
					// TODO do UMTS specific dbm conversion from ci.getRssi());
					// @see http://developer.android.com/reference/android/telephony/NeighboringCellInfo.html#getRssi()
					neighbor.setStrengthdBm(ci.getRssi());

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
	 * A valid cdma location must have basestation id, network id and system id set
	 * @param cdmaLocation
	 * @return
	 */
	private boolean isValidCdmaLocation(final CdmaCellLocation cdmaLocation) {
		if (cdmaLocation == null) {
			return false;
		}

		return (cdmaLocation.getBaseStationId() != -1) && (cdmaLocation.getNetworkId() != -1) && (cdmaLocation.getSystemId() != -1);
	}

	/**
	 * A valid gsm location must have cell id
	 * @param gsmLocation
	 * @return
	 */
	private boolean isValidGsmLocation(final GsmCellLocation gsmLocation) {
		if (gsmLocation == null) {
			return false;
		}

		return gsmLocation.getCid() != -1;
	}

	/**
	 * Broadcast signal to refresh UI.
	 */
	private void broadcastCellUpdate() {
		Intent intent = new Intent(RadioBeacon.INTENT_CELL_UPDATE);
		sendBroadcast(intent);
	}

	/**
	 *  Broadcasts human-readable description of last cell.
	 * @param recent
	 */
	private void broadcastCellInfos(final CellRecord recent) {
		Intent intent = new Intent(RadioBeacon.INTENT_NEW_CELL);
		intent.putExtra(RadioBeacon.MSG_KEY, recent.getOperatorName() 
				+ " " + recent.getCid()
				+ " " + CellRecord.NetworkTypeDescription.get(recent.getNetworkType())
				+ " " + recent.getStrengthdBm() + "dBm");
		sendBroadcast(intent);
	}

	@Override
	public final void onStartService() {
		registerReceivers();

		// Set mCellSavedAt and mWifiSavedAt to default values (Lat 0, Lon 0)
		// Thus MIN_CELL_DISTANCE and MIN_WIFI_DISTANCE filters are always fired on startup
		mCellSavedAt = new Location("DUMMY");
		mWifiSavedAt = new Location("DUMMY");
	}

	@Override
	public final void onStopService() {
		Log.d(TAG, "OnStopService called");
		unregisterReceivers();

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
		scanCallback = null;
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

				Bundle aBundle = msg.getData();
				int sessionId = aBundle.getInt(RadioBeacon.MSG_KEY, RadioBeacon.SESSION_NOT_TRACKING); 

				startTracking(sessionId);
				break;
			case RadioBeacon.MSG_STOP_TRACKING:
				Log.d(TAG, "Wireless logger received MSG_STOP_TRACKING signal");
				stopTracking();
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
	private boolean checkIsNew(final String bssid) {

		// default: return true, if ref database n/a
		if (mWifiCatalogPath == null) {
			Log.e(TAG, "Reference database not specified");
			return true;
		}

		try {
			SQLiteDatabase mRefdb = SQLiteDatabase.openDatabase(mWifiCatalogPath, null, SQLiteDatabase.OPEN_READONLY);
			Cursor exists = mRefdb.rawQuery("SELECT bssid FROM wifi_zone WHERE bssid = ?", new String[]{bssid});

			if (exists.moveToFirst()) {
				Log.i(TAG, bssid + " is in reference database");
				exists.close();
				mRefdb.close();
				return true;
			} else {
				Log.i(TAG, bssid + " is NOT in reference database");
				exists.close();
				mRefdb.close();
				return false;
			}

		} catch (SQLiteException e) {
			Log.e(TAG, "Couldn't open reference database");
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return true;
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
