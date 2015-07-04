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

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.Session;
import org.openbmap.services.ServiceManager;
import org.openbmap.services.position.GpxLoggerService;
import org.openbmap.services.position.PositioningService;
import org.openbmap.services.position.PositioningService.State;
import org.openbmap.services.wireless.WirelessLoggerService;
import org.openbmap.utils.ActivityUtils;

import java.lang.ref.WeakReference;

/**
 * HostActity for "tracking" mode. It hosts the tabs "Stats", "Wifi Overview", "Cell Overview" and "Map".
 * HostActity is also in charge of service communication. 
 * Services are automatically started onCreate() and onResume()
 * They can be manually stopped by calling INTENT_STOP_TRACKING.
 * 
 */
public class HostActivity extends ActionBarActivity {
	private static final String	TAG	= HostActivity.class.getSimpleName();

	/**
	 * Tab pager
	 */
	private CustomViewPager mPager;

	private Tab mTab;

	/**
	 * Keeps the SharedPreferences.
	 */
	private SharedPreferences mPrefs = null;

	/**
	 * Background service collecting cell and wifi infos.
	 */
	private ServiceManager mWirelessServiceManager;

	/**
	 * Background gps location service.
	 */
	private ServiceManager mPositionServiceManager;

	/**
	 * Background gpx logger server
	 */
	private ServiceManager mGpxLoggerServiceManager;

	/**
	 * Database helper used for session handling here.
	 */
	private DataHelper mDataHelper;

	/**
	 * Selected navigation provider, default GPS
	 */
	private State mSelectedProvider = State.GPS;

	private ActionBar mActionBar;

	/**
	 * Receives GPS location updates 
	 */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

		/**
		 * Handles start and stop service requests.
		 */
		@Override
		public void onReceive(final Context context, final Intent intent) {
			Log.d(TAG, "Received intent " + intent.getAction().toString());
			/*if (RadioBeacon.INTENT_START_TRACKING.equals(intent.getAction())) {
				startServices();
			} else*/
			if (RadioBeacon.INTENT_STOP_TRACKING.equals(intent.getAction())) {
				Log.d(TAG, "INTENT_STOP_TRACKING received");
				// invalidates active track
				closeActiveSession();
				// stops background services
				stopServices();

				HostActivity.this.finish();
			}

			if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())) {
				Log.d(TAG, "ACTION_BATTERY_LOW received");
				final boolean ignoreBattery = mPrefs.getBoolean(Preferences.KEY_IGNORE_BATTERY, Preferences.VAL_IGNORE_BATTERY);
				if (!ignoreBattery) {
					Toast.makeText(context, getString(R.string.battery_warning), Toast.LENGTH_LONG).show();
					updateSessionStats();
					// invalidates active track
					closeActiveSession();
					// stops background services
					stopServices();

					HostActivity.this.finish();
				} else {
					Log.i(HostActivity.TAG, "Battery low but ignoring due to settings");
				}

			}
		}
	};

	/**
	 * Reacts on messages from gps location service 
	 */
	private static class GpsLocationHandler extends Handler {
		private final WeakReference<HostActivity> mActivity;

		GpsLocationHandler(final HostActivity activity) {
			mActivity = new WeakReference<HostActivity>(activity);
		}

		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
				case RadioBeacon.MSG_SERVICE_READY:
					// start tracking immediately after service is ready 
					Log.d(TAG, "Positioning Service ready. Requesting position updates");
					if (mActivity != null) {	
						final HostActivity tab =  mActivity.get();
						tab.requestPositionUpdates(State.GPS);
					}
					break;

				default:
					super.handleMessage(msg);
			}
		}
	}

	/**
	 * Reacts on messages from wireless logging service 
	 */
	private static class WirelessHandler extends Handler {
		private final WeakReference<HostActivity> mActivity;

		WirelessHandler(final HostActivity activity) {
			mActivity = new WeakReference<HostActivity>(activity);
		}

		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
				case RadioBeacon.MSG_SERVICE_READY:
					// start tracking immediately after service is ready 
					Log.d(TAG, "WirelessLogger Service ready. Requesting wireless updates");
					if (mActivity != null) {
						final HostActivity tab =  mActivity.get();
						tab.requestWirelessUpdates();
					}
				default:
					super.handleMessage(msg);
			}
		}
	}

	/**
	 * Reacts on messages from gps logging service 
	 */
	private static class GpxLoggerHandler extends Handler {
		private final WeakReference<HostActivity> mActivity;

		GpxLoggerHandler(final HostActivity activity) {
			mActivity = new WeakReference<HostActivity>(activity);
		}

		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
				case RadioBeacon.MSG_SERVICE_READY:
					// start tracking immediately after service is ready 
					if (mActivity != null) {
						final HostActivity tab =  mActivity.get();
						tab.requestGpxTracking();
					}
				default:
					super.handleMessage(msg);
			}
		}
	}

	/** Called when the activity is first created. */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d(TAG, "Creating HostActivity");

		mDataHelper = new DataHelper(this);

		// get shared preferences
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		// UI related stuff
		setContentView(R.layout.host_activity);
		final boolean keepScreenOn = mPrefs.getBoolean(Preferences.KEY_KEEP_SCREEN_ON, false);
		ActivityUtils.setKeepScreenOn(this, keepScreenOn);

		initUi(savedInstanceState);

		// service related stuff
		verifyGPSProvider();

		// TODO: show warning if wifi is not enabled
		// TODO: show warning if GSM is not enabled
	}

	@Override
	protected final void onResume() {
		super.onResume();

		Log.d(TAG, "Resuming HostActivity");

		setupBroadcastReceiver();
		setupSession();

		/* TODO: sort out, what is needed here
		 * // Check GPS status if (checkGPSFlag &&
		 * prefs.getBoolean(OSMTracker.Preferences.KEY_GPS_CHECKSTARTUP, OSMTracker.Preferences.VAL_GPS_CHECKSTARTUP)) { checkGPSProvider(); }
		 */
		// Register GPS status update for upper controls
		//((StatusBar) findViewById(R.id.gpsStatus)).requestLocationUpdates(true);

		// setup GPS and wireless logger services
		startServices();

		// explicitly request updates, automatic resume isn't working smoothly 
		requestPositionUpdates(mSelectedProvider);
		requestWirelessUpdates();
	}

	@Override
	protected final void onPause() {
		Log.d(TAG, "Pausing HostActivity");
		updateSessionStats();
		super.onPause();
	}

	@Override
	protected final void onStop() {
		Log.d(TAG, "Stopping HostActivity");
		// TODO: if receivers are unregistered on stop, there is no way to start/stop services via receivers from outside
		unregisterReceiver();
		super.onStop();
	}

	@Override
	protected final void onDestroy() {
		Log.d(TAG, "Destroying HostActivity");

		updateSessionStats();
		unregisterReceiver();

		// change from unbind to unbindAndStop caused problems, when screen was locked
		if (mPositionServiceManager != null) { mPositionServiceManager.unbind();};
		if (mWirelessServiceManager != null) { mWirelessServiceManager.unbind();}
		if (mGpxLoggerServiceManager != null) { mGpxLoggerServiceManager.unbind();};

		super.onDestroy();
	}

	/**
	 * Unregisters receivers for GPS and wifi scan results.
	 */
	private void unregisterReceiver() {
		try {
			Log.i(TAG, "Unregistering broadcast receivers");
			unregisterReceiver(mReceiver);
		} catch (final IllegalArgumentException e) {
			// do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android}
			return;
		}
	}

	/**
	 * Create context menu with start and stop buttons for "tracking" mode.
	 * @param menu Menu to inflate
	 * @return always true
	 */
	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.control_menu, menu);
		return true;
	}

	/**
	 * Context menu, while in "tracking" mode
	 */
	@Override
	public final boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			/*
			case R.id.menu_starttracking:
				// kill previous session
				stopServices();

				requestPosition(PositioningService.State.GPS);
				requestWirelessTracking();
				requestGpxTracking();
				startNotification();
				break;
			case R.id.menu_pausetracking:
				updateSessionStats();
				requestPause();
				stopNotifyBackgroundService();
				break;*/
			case R.id.menu_stoptracking:
				updateSessionStats();
				closeActiveSession();
				stopServices();

				final Intent intent = new Intent(this, StartscreenActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
				this.finish();
				break;
			default:
				break; 
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Updates number of cells and wifis.
	 */
	private void updateSessionStats() {
		final Session active = mDataHelper.loadActiveSession();
		if (active != null) {
			active.setNumberOfWifis(mDataHelper.countWifis(active.getId()));
			active.setNumberOfCells(mDataHelper.countCells(active.getId()));
			mDataHelper.storeSession(active, false);
		}	
	}

	/**
	 * Checks whether GPS is enabled.
	 * If not, user is asked whether to activate GPS.
	 */
	private void verifyGPSProvider() {
		final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
		if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			// GPS isn't enabled. Offer user to go enable it
			new AlertDialog.Builder(this)
			.setTitle(R.string.no_gps)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setMessage(
					R.string.turnOnGpsQuestion)
					.setCancelable(true)
					.setPositiveButton(android.R.string.yes,
							new DialogInterface.OnClickListener() {
						@Override
						public void onClick(final DialogInterface dialog,
								final int which) {
							startActivity(new Intent(
									Settings.ACTION_LOCATION_SOURCE_SETTINGS));
						}
					})
					.setNegativeButton(android.R.string.no,
							new DialogInterface.OnClickListener() {
						@Override
						public void onClick(final DialogInterface dialog,
								final int which) {
							dialog.cancel();
						}
					}).create().show();
		}
	}


	/**
	 * Starts broadcasting GPS position.
	 * @param provider
	 * @return false on error, otherwise true
	 */
	public final boolean requestPositionUpdates(final State provider) {
		// TODO check whether services have already been connected (i.e. received MSG_SERVICE_READY signal)
		Log.d(TAG, "Requesting position updates");
		try {
			if (mPositionServiceManager == null) {
				Log.w(TAG, "gpsPositionServiceManager is null. No message will be sent");
				return false;
			}

			final int session = mDataHelper.getActiveSessionId();

			if (session == RadioBeacon.SESSION_NOT_TRACKING) {
				Log.e(TAG, "Couldn't start tracking, no active session");
				return false;
			}

			final Bundle aProviderBundle = new Bundle();
			aProviderBundle.putString("provider", provider.toString());

			final Message msgGpsUp = new Message(); 
			msgGpsUp.what = RadioBeacon.MSG_START_TRACKING;
			msgGpsUp.setData(aProviderBundle);

			mPositionServiceManager.sendAsync(msgGpsUp);

			// update recording indicator
			//((StatusBar) findViewById(R.id.gpsStatus)).manageRecordingIndicator(true);

			updateUI();	
			mSelectedProvider = provider;
			return true;
		} catch (final RemoteException e) {
			// service communication failed
			e.printStackTrace();
			return false;
		} catch (final NumberFormatException e) {
			e.printStackTrace();
			return false;
		} catch (final Exception e) {
			e.printStackTrace();
			return false;
		}
	}


	/**
	 * Starts wireless tracking.
	 * @return false on error, otherwise true
	 */
	public final boolean requestWirelessUpdates() {
		// TODO check whether services have already been connected (i.e. received MSG_SERVICE_READY signal)
		Log.d(TAG, "Requesting wireless updates");
		try {
			if (mWirelessServiceManager == null) {
				Log.w(TAG, "wirelessServiceManager is null. No message will be sent");
				return false;
			}

			final int session = mDataHelper.getActiveSessionId();

			if (session == RadioBeacon.SESSION_NOT_TRACKING) {
				Log.e(TAG, "Couldn't start tracking, no active session");
				return false;
			}

			final Bundle aSessionIdBundle = new Bundle();
			aSessionIdBundle.putInt(RadioBeacon.MSG_KEY, session);

			final Message msgWirelessUp = new Message(); 
			msgWirelessUp.what = RadioBeacon.MSG_START_TRACKING;
			msgWirelessUp.setData(aSessionIdBundle);

			mWirelessServiceManager.sendAsync(msgWirelessUp);

			updateUI();			
			return true;
		} catch (final RemoteException e) {
			// service communication failed
			e.printStackTrace();
			return false;
		} catch (final NumberFormatException e) {
			e.printStackTrace();
			return false;	
		} catch (final Exception e) {
			e.printStackTrace();
			return false;	
		}	
	}

	/**
	 * Starts GPX tracking.
	 * @return false on error, otherwise true
	 */
	public final boolean requestGpxTracking() {
		// TODO check whether services have already been connected (i.e. received MSG_SERVICE_READY signal)
		Log.d(TAG, "Requesting gpx tracking");
		try {
			if (mPositionServiceManager == null) {
				Log.w(TAG, "gpsPositionServiceManager is null. No message will be sent");
				return false;
			}

			final int session = mDataHelper.getActiveSessionId();

			if (session == RadioBeacon.SESSION_NOT_TRACKING) {
				Log.e(TAG, "Couldn't start tracking, no active session");
				return false;
			}

			Log.d(TAG, "Resuming session " + session);

			final Bundle aSessionIdBundle = new Bundle();
			aSessionIdBundle.putInt(RadioBeacon.MSG_KEY, session);

			final Message msgGpsUp = new Message(); 
			msgGpsUp.what = RadioBeacon.MSG_START_TRACKING;
			msgGpsUp.setData(aSessionIdBundle);

			mPositionServiceManager.sendAsync(msgGpsUp);

			return true;
		} catch (final RemoteException e) {
			// service communication failed
			e.printStackTrace();
			return false;
		} catch (final NumberFormatException e) {
			e.printStackTrace();
			return false;
		} catch (final Exception e) {
			e.printStackTrace();
			return false;
		}
	}


	/**
	 * Setups receiver for STOP_TRACKING and ACTION_BATTERY_LOW messages
	 */
	private void setupBroadcastReceiver() {
		final IntentFilter filter = new IntentFilter();
		//filter.addAction(RadioBeacon.INTENT_START_TRACKING);
		filter.addAction(RadioBeacon.INTENT_STOP_TRACKING);
		filter.addAction(Intent.ACTION_BATTERY_LOW);
		registerReceiver(mReceiver, filter);		
	}

	/**
	 * Pauses GPS und wireless logger services.
	 */
	/* doesn't work: services aren't properly restarted / finally stopped
	private void requestPause() {
		// Pause tracking
		try {
			gpsPositionServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));
			wirelessServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));

			Session toPause = mDataHelper.loadActiveSession();
			if (toPause != null) {
				toPause.isActive(false);
				mDataHelper.storeSession(toPause, true);
			}

			((StatusBar) findViewById(R.id.gpsStatus)).manageRecordingIndicator(false); 
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	 */

	/**
	 * Opens a new session object. If there's any active session or a specific session id is provided, this session is resumed,
	 * otherwise a new session created
	 */
	private void setupSession() {

		final Bundle extras = getIntent().getExtras();
		if (extras != null && extras.getInt("_id")  != 0) {
			// A session to resume was specified
			final int id = extras.getInt("_id");
			Log.i(TAG, "Opening specified session " + id);
			mDataHelper.invalidateActiveSessions();
			openExistingSession(id);
		} else {
			// search for any active session
			final int activeSession = mDataHelper.getActiveSessionId();
			if (activeSession != RadioBeacon.SESSION_NOT_TRACKING) {
				Log.i(TAG, "Resuming session " + activeSession);
				openExistingSession(activeSession);
			} else {
				Log.i(TAG, "Starting new session");
				openNewSession();
			}
		}
		updateSessionStats();
	}

	/**
	 * Opens new session
	 */
	private void openNewSession() {
		// invalidate all active session
		mDataHelper.invalidateActiveSessions();
		// Create a new session and activate it
		// Then start HostActivity. HostActivity onStart() and onResume() check active session
		// and starts services for active session
		final Session active = new Session();
		active.setCreatedAt(System.currentTimeMillis());
		active.setLastUpdated(System.currentTimeMillis());
		active.setDescription("No description yet");
		active.isActive(true);
		// id can only be set after session has been stored to database.
		final Uri result = mDataHelper.storeSession(active);
		active.setId(result);
	}

	/**
	 * Resumes specific session
	 * @param id
	 */
	private void openExistingSession(final int id) {
		// TODO: check whether we need a INTENT_START_SERVICE here
		final Session resume = mDataHelper.loadSession(id);

		if (resume == null) {
			Log.e(TAG, "Error loading session " + id);
			return;
		}

		resume.isActive(true);
		mDataHelper.storeSession(resume, true);

	}

	/**
	 * 
	 */
	private void closeActiveSession() {
		mDataHelper.invalidateActiveSessions();
		// update recording indicator
		// ((StatusBar) findViewById(R.id.gpsStatus)).manageRecordingIndicator(false);
	}

	/**
	 * Setups services. Any running services will be restart.
	 */
	private void startServices() {
		stopServices();

		Log.d(TAG, "Starting Services");
		if (mPositionServiceManager == null) {
			mPositionServiceManager = new ServiceManager(this, PositioningService.class, new GpsLocationHandler(this));
		}
		mPositionServiceManager.bindAndStart();

		if (mWirelessServiceManager == null) {
			mWirelessServiceManager = new ServiceManager(this, WirelessLoggerService.class, new WirelessHandler(this));
		}
		mWirelessServiceManager.bindAndStart();

		if (mGpxLoggerServiceManager == null) {
			mGpxLoggerServiceManager = new ServiceManager(this, GpxLoggerService.class, new GpxLoggerHandler(this));
		}
		mGpxLoggerServiceManager.bindAndStart();
	}

	/**
	 * Stops GPS und wireless logger services.
	 */
	private void stopServices() {
		Log.d(TAG, "Stopping Services");
		// Brute force: specific ServiceManager can be null, if service hasn't been started
		// Service status is ignored, stop message is send regardless of whether started or not
		try {
			mPositionServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));
			// deactivated: let's call this from the service itself
			// positionServiceManager.unbindAndStop();
		} catch (final Exception e) {
			Log.w(TAG, "Failed to stop gpsPositionServiceManager. Is service runnign?" /*+ e.getMessage()*/);
			//e.printStackTrace();
		}

		try {
			mWirelessServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));
			// deactivated: let's call this from the service itself
			// wirelessServiceManager.unbindAndStop();
		} catch (final Exception e) {
			Log.w(TAG, "Failed to stop wirelessServiceManager. Is service running?" /*+ e.getMessage()*/);
			//e.printStackTrace();
		}

		try {
			mGpxLoggerServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));
			// deactivated: let's call this from the service itself
			// gpxLoggerServiceManager.unbindAndStop();
		} catch (final Exception e) {
			Log.w(TAG, "Failed to stop gpxLoggerServiceManager. Is service running?" /*+ e.getMessage()*/);
			//e.printStackTrace();
		}
	}

	/**
	 * Configures UI elements.
	 * @param savedInstanceState 
	 */
	private void initUi(final Bundle savedInstanceState) {
		getSupportActionBar().setDisplayShowTitleEnabled(false);

		mActionBar = getSupportActionBar();
		mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		// Locate ViewPager in activity_main.xml
		mPager = (CustomViewPager) findViewById(R.id.pager);
		// Activate Fragment Manager
		final FragmentManager fm = getSupportFragmentManager();

		// Capture ViewPager page swipes
		final ViewPager.SimpleOnPageChangeListener ViewPagerListener = new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(final int position) {
				super.onPageSelected(position);
				// Find the ViewPager Position
				getSupportActionBar().setSelectedNavigationItem(position);
			}
		};

		mPager.setOnPageChangeListener(ViewPagerListener);

		final CustomViewPagerAdapter viewpageradapter = new CustomViewPagerAdapter(fm);
		mPager.setAdapter(viewpageradapter);

		// Capture tab button clicks
		final ActionBar.TabListener tabListener = new ActionBar.TabListener() {
			@Override
			public void onTabSelected(final Tab tab, final FragmentTransaction ft) {
				// Pass the position on tab click to ViewPager
				mPager.setCurrentItem(tab.getPosition());
			}

			@Override
			public void onTabUnselected(final Tab tab, final FragmentTransaction ft) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onTabReselected(final Tab tab, final FragmentTransaction ft) {
				// TODO Auto-generated method stub
			}
		};

		// Create tabs
		mTab = mActionBar.newTab().setText(R.string.overview).setTabListener(tabListener);
		getSupportActionBar().addTab(mTab);

		mTab = mActionBar.newTab().setText(R.string.wifis).setTabListener(tabListener);
		getSupportActionBar().addTab(mTab);

		mTab = mActionBar.newTab().setText(R.string.cells).setTabListener(tabListener);
		getSupportActionBar().addTab(mTab);

		mTab = mActionBar.newTab().setText(R.string.map).setTabListener(tabListener);
		getSupportActionBar().addTab(mTab);
	}

	/**
	 * Broadcasts requests for UI refresh on wifi and cell info.
	 */
	private void updateUI() {
		final Intent intent1 = new Intent(RadioBeacon.INTENT_WIFI_UPDATE);
		sendBroadcast(intent1);
		final Intent intent2 = new Intent(RadioBeacon.INTENT_CELL_UPDATE);
		sendBroadcast(intent2);
	}

}