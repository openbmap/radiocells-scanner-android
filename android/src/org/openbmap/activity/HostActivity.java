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

package org.openbmap.activity;

import java.lang.ref.WeakReference;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.model.Session;
import org.openbmap.service.ServiceManager;
import org.openbmap.service.position.PositioningService;
import org.openbmap.service.position.GpxLoggerService;
import org.openbmap.service.position.PositioningService.State;
import org.openbmap.service.wireless.WirelessLoggerService;
import org.openbmap.utils.ActivityHelper;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TabActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TabHost;

/**
 * HostActity for "tracking" mode. It hosts the tabs "Stats", "Wifi Overview", "Cell Overview" and "Map".
 * HostActity is also in charge of service communication. 
 * Services are automatically started onCreate() and onResume()
 * They can be manually stopped by calling INTENT_STOP_SERVICES.
 * 
 */
public class HostActivity extends TabActivity {
	private static final String	TAG	= HostActivity.class.getSimpleName();

	/**
	 * System notification id.
	 */
	private static final int NOTIFICATION_ID = 0;

	/**
	 * Keeps the SharedPreferences.
	 */
	private SharedPreferences prefs = null;
	/**
	 * Background service collecting cell and wifi infos.
	 */
	private ServiceManager wirelessServiceManager;

	/**
	 * Background gps location service.
	 */
	private ServiceManager positionServiceManager;

	/**
	 * Background gps logger server
	 */
	private ServiceManager gpxLoggerServiceManager;

	/**
	 * Database helper used for session handling here.
	 */
	private DataHelper mDataHelper;

	/**
	 * Selected navigation provider, default GPS
	 */
	private State mSelectedProvider = State.GPS;

	/**
	 * Receives GPS location updates 
	 */
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		/**
		 * Handles start and stop service requests.
		 */
		@Override
		public void onReceive(final Context context, final Intent intent) {
			Log.d(TAG, "Received intent " + intent.getAction().toString());
			if (RadioBeacon.INTENT_START_SERVICES.equals(intent.getAction())) {
				Log.d(TAG, "INTENT_START_SERVICES received");
				startServices();
			} else if (RadioBeacon.INTENT_STOP_SERVICES.equals(intent.getAction())) {
				Log.d(TAG, "INTENT_STOP_SERVICES received");
				// invalidates active track
				stopActiveSession();
				// stops background services
				stopServices();
			}
		}
	};

	/**
	 * Reacts on messages from gps location service 
	 */
	private static class GpsLocationHandler extends Handler {
		private WeakReference<HostActivity> mActivity;

		GpsLocationHandler(final HostActivity activity) {
			mActivity = new WeakReference<HostActivity>(activity);
		}

		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
				case RadioBeacon.MSG_SERVICE_READY:
					// start tracking immediately after service is ready 
					if (mActivity != null) {
						HostActivity tab =  mActivity.get();
						tab.requestPosition(State.GPS);
						tab.startNotification();
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
		private WeakReference<HostActivity> mActivity;

		WirelessHandler(final HostActivity activity) {
			mActivity = new WeakReference<HostActivity>(activity);
		}

		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
				case RadioBeacon.MSG_SERVICE_READY:
					// start tracking immediately after service is ready 
					if (mActivity != null) {
						HostActivity tab =  mActivity.get();
						tab.requestWirelessTracking();
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
		private WeakReference<HostActivity> mActivity;

		GpxLoggerHandler(final HostActivity activity) {
			mActivity = new WeakReference<HostActivity>(activity);
		}

		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
				case RadioBeacon.MSG_SERVICE_READY:
					// start tracking immediately after service is ready 
					if (mActivity != null) {
						HostActivity tab =  mActivity.get();
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

		mDataHelper = new DataHelper(this);

		// get shared preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		// UI related stuff
		setContentView(R.layout.tab_host);
		boolean keepScreenOn = prefs.getBoolean(Preferences.KEY_KEEP_SCREEN_ON, false);
		ActivityHelper.setKeepScreenOn(this, keepScreenOn);

		initControls();

		// service related stuff
		verifyGPSProvider();

		// TODO: show warning if wifi is not enabled
		// TODO: show warning if GSM is not enabled

		// setup GPS and wireless logger services
		startServices();
	}

	@Override
	protected final void onResume() {
		//Log.d(TAG, "onResume called");
		setupBroadcastReceiver();

		/* TODO: sort out, what is needed here
		 * // Check GPS status if (checkGPSFlag &&
		 * prefs.getBoolean(OSMTracker.Preferences.KEY_GPS_CHECKSTARTUP, OSMTracker.Preferences.VAL_GPS_CHECKSTARTUP)) { checkGPSProvider(); }
		 */

		// Register GPS status update for upper controls
		((GpsStatusRecord) findViewById(R.id.gpsStatus)).requestLocationUpdates(true);

		startServices();
		requestPosition(mSelectedProvider);
		startNotification();

		super.onResume();
	}

	@Override
	protected final void onPause() {

		// When paused, stop updating GPS signal strength in action bar.
		((GpsStatusRecord) findViewById(R.id.gpsStatus)).requestLocationUpdates(false);
		updateSessionStats();
		super.onPause();
	}

	@Override
	protected final void onStop() {
		Log.d(TAG, "onStop called");
		// TODO: if receivers are unregistered on stop, there is no way to start/stop services via receivers from outside
		unregisterReceiver();
		super.onStop();
	}

	@Override
	protected final void onDestroy() {
		Log.d(TAG, "OnDestroy called");

		updateSessionStats();
		unregisterReceiver();

		if (positionServiceManager != null) { positionServiceManager.unbind();};
		if (wirelessServiceManager != null) { wirelessServiceManager.unbind();}
		if (gpxLoggerServiceManager != null) { gpxLoggerServiceManager.unbind();};

		stopNotification();
		super.onDestroy();
		}

		/**
		 * Unregisters receivers for GPS and wifi scan results.
		 */
		private void unregisterReceiver() {
			try {
				Log.i(TAG, "Unregistering broadcast receivers");
				unregisterReceiver(mReceiver);
			} catch (IllegalArgumentException e) {
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
			MenuInflater inflater = getMenuInflater();
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
					stopActiveSession();
					stopServices();
					stopNotification();
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
			Session active = mDataHelper.loadActiveSession();
			if (active != null) {
				active.setNumberOfWifis(mDataHelper.countWifis(active.getId()));
				active.setNumberOfCells(mDataHelper.countCells(active.getId()));
				mDataHelper.storeSession(active, false);
			}	
		}

		/**
		 * Called when GPS is enabled.
		 */
		public void onGpsEnabled() {

		}

		/**
		 * Called when GPS is disabled.
		 */
		public final void onGpsDisabled() {

		}

		/**
		 * Checks whether GPS is enabled.
		 * If not, user is asked whether to activate GPS.
		 */
		private void verifyGPSProvider() {
			LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
			if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				// GPS isn't enabled. Offer user to go enable it
				new AlertDialog.Builder(this)
				.setTitle("GPS switched off!")
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
		 * Request position broadcasting position.
		 * @param provider 
		 * @return false on error, otherwise true
		 */
		public final boolean requestPosition(final State provider) {
			// TODO check whether services have already been connected (i.e. received MSG_SERVICE_READY signal)
			Log.d(TAG, "Trying to retrieve active session from database and start tracking");
			try {
				if (positionServiceManager == null) {
					Log.w(TAG, "positionServiceManager is null. No message will be sent");
					return false;
				}

				Session active = mDataHelper.loadActiveSession();
				if (active == null) {
					Log.e(TAG, "Couldn't start tracking, no active session");
					return false;
				}

				/**
				 * Positioning service receives provider name to decide whether to use GPS or inertial
				 */
				Bundle aProviderBundle = new Bundle();
				aProviderBundle.putString("provider", provider.toString());

				Message msgPositioningUp = new Message(); 
				msgPositioningUp.what = RadioBeacon.MSG_START_TRACKING;
				msgPositioningUp.setData(aProviderBundle);

				positionServiceManager.sendAsync(msgPositioningUp);

				// update recording indicator
				//((GpsStatusRecord) findViewById(R.id.gpsStatus)).manageRecordingIndicator(true);

				updateUI();	

				mSelectedProvider = provider;
				return true;
			} catch (RemoteException e) {
				// service communication failed
				e.printStackTrace();
				return false;
			} catch (NumberFormatException e) {
				e.printStackTrace();
				return false;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}

		/**
		 * Starts wireless tracking.
		 * @return false on error, otherwise true
		 */
		public final boolean requestWirelessTracking() {

			// TODO check whether services have already been connected (i.e. received MSG_SERVICE_READY signal)
			try {
				if (wirelessServiceManager == null) {
					Log.w(TAG, "wirelessServiceManager is null. No message will be sent");
					return false;
				}

				Session active = mDataHelper.loadActiveSession();
				if (active == null) {
					Log.e(TAG, "Couldn't start tracking, no active session");
					return false;
				}

				Bundle aSessionIdBundle = new Bundle();
				aSessionIdBundle.putInt(RadioBeacon.MSG_KEY, active.getId());

				Message msgWirelessUp = new Message(); 
				msgWirelessUp.what = RadioBeacon.MSG_START_TRACKING;
				msgWirelessUp.setData(aSessionIdBundle);

				wirelessServiceManager.sendAsync(msgWirelessUp);

				updateUI();			
				return true;
			} catch (RemoteException e) {
				// service communication failed
				e.printStackTrace();
				return false;
			} catch (NumberFormatException e) {
				e.printStackTrace();
				return false;	
			} catch (Exception e) {
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
			Log.d(TAG, "Trying to retrieve active session from database and start tracking");
			try {
				if (positionServiceManager == null) {
					Log.w(TAG, "positionServiceManager is null. No message will be sent");
					return false;
				}

				Session active = mDataHelper.loadActiveSession();
				if (active == null) {
					Log.e(TAG, "Couldn't start tracking, no active session");
					return false;
				}

				Log.d(TAG, "Resuming session " + active.getId());

				Bundle aSessionIdBundle = new Bundle();
				aSessionIdBundle.putInt(RadioBeacon.MSG_KEY, active.getId());

				Message msgGpsUp = new Message(); 
				msgGpsUp.what = RadioBeacon.MSG_START_TRACKING;
				msgGpsUp.setData(aSessionIdBundle);

				positionServiceManager.sendAsync(msgGpsUp);

				return true;
			} catch (RemoteException e) {
				// service communication failed
				e.printStackTrace();
				return false;
			} catch (NumberFormatException e) {
				e.printStackTrace();
				return false;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}

		/**
		 * 
		 */
		private void stopActiveSession() {
			mDataHelper.invalidateActiveSessions();
			// update recording indicator
			// ((GpsStatusRecord) findViewById(R.id.gpsStatus)).manageRecordingIndicator(false);
		}

		private void setupBroadcastReceiver() {
			IntentFilter filter = new IntentFilter();
			filter.addAction(RadioBeacon.INTENT_START_SERVICES);
			filter.addAction(RadioBeacon.INTENT_STOP_SERVICES);
			registerReceiver(mReceiver, filter);		
		}

		/**
		 * Pauses GPS und wireless logger services.
		 */
		/* doesn't work: services aren't properly restarted / finally stopped
	private void requestPause() {
		// Pause tracking
		try {
			positionServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));
			wirelessServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));

			Session toPause = mDataHelper.loadActiveSession();
			if (toPause != null) {
				toPause.isActive(false);
				mDataHelper.storeSession(toPause, true);
			}

			((GpsStatusRecord) findViewById(R.id.gpsStatus)).manageRecordingIndicator(false); 
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
		 */

		/**
		 * Setups services.
		 */
		private void startServices() {
			if (positionServiceManager == null) {
				positionServiceManager = new ServiceManager(this, PositioningService.class, new GpsLocationHandler(this));
			}
			positionServiceManager.bindAndStart();

			if (wirelessServiceManager == null) {
				wirelessServiceManager = new ServiceManager(this, WirelessLoggerService.class, new WirelessHandler(this));
			}
			wirelessServiceManager.bindAndStart();

			// Gpx logger service is optional, it's only started when activated in settings
			if (prefs.getBoolean(Preferences.KEY_GPS_SAVE_COMPLETE_TRACK, Preferences.VAL_GPS_SAVE_COMPLETE_TRACK)) {
				if (gpxLoggerServiceManager == null) {
					gpxLoggerServiceManager = new ServiceManager(this, GpxLoggerService.class, new GpxLoggerHandler(this));
				}
				gpxLoggerServiceManager.bindAndStart();
			} else {
				Log.i(TAG, "gpxLoggerServiceManager has not been started. Optionally activate logger service in settings.");
			}
		}

		/**
		 * Stops GPS und wireless logger services.
		 */
		private void stopServices() {
			// Brute force: specific ServiceManager can be null, if service hasn't been started
			// Service status is ignored, stop message is send regardless of whether started or not
			try {
				positionServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));
				positionServiceManager.unbindAndStop();
			} catch (Exception e) {
				Log.w(TAG, "Failed to stop positionServiceManager. Is service runnign?" + e.getMessage());
				e.printStackTrace();
			}

			try {
				wirelessServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));
				wirelessServiceManager.unbindAndStop();
			} catch (Exception e) {
				Log.w(TAG, "Failed to stop wirelessServiceManager. Is service running?" + e.getMessage());
				e.printStackTrace();
			}

			try {
				gpxLoggerServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));
				gpxLoggerServiceManager.unbindAndStop();
			} catch (Exception e) {
				Log.w(TAG, "Failed to stop gpxLoggerServiceManager. Is service running?" + e.getMessage());
				e.printStackTrace();
			}

			this.finish();
		}

		/**
		 * Notifies the user that we're still tracking in background.
		 */
		private void startNotification() {
			Session active = mDataHelper.loadActiveSession();
			if (active == null) {
				stopNotification();
				return;
			}

			NotificationManager nmgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			Notification n = new Notification(R.drawable.icon_greyed_25x25, getString(R.string.notification_caption), System.currentTimeMillis());

			Intent startTrackLogger = new Intent(this, HostActivity.class);
			//startTrackLogger.putExtra(Schema.COL_SESSION_ID, currentTrackId);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, startTrackLogger, PendingIntent.FLAG_UPDATE_CURRENT);
			n.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
			n.setLatestEventInfo(
					getApplicationContext(),
					getString(R.string.notification).replace("{0}", Integer.toString(active.getId())),
					getString(R.string.hint_notification),
					contentIntent);

			nmgr.notify(NOTIFICATION_ID, n);
		}

		/**
		 * Stops notifying the user that we're tracking in the background.
		 */
		private void stopNotification() {
			NotificationManager nmgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			nmgr.cancel(NOTIFICATION_ID);
		}

		/**
		 * Configures tabs.
		 */
		private void initControls() {
			TabHost host = getTabHost();

			TabHost.TabSpec spec = host.newTabSpec("tag1");
			Intent intent1 = new Intent(this, StatsActivity.class);
			spec.setIndicator("Overview");
			spec.setContent(intent1);
			host.addTab(spec);

			TabHost.TabSpec spec2 = host.newTabSpec("tag2");
			Intent intent2 = new Intent(this, WifiListActivity.class);
			spec2.setIndicator("Wifis");
			spec2.setContent(intent2);
			host.addTab(spec2);

			TabHost.TabSpec spec3 = host.newTabSpec("tag3");
			Intent intent3 = new Intent(this, CellsListActivity.class);
			spec3.setIndicator("Cells");
			spec3.setContent(intent3);
			host.addTab(spec3);

			TabHost.TabSpec spec4 = host.newTabSpec("tag4");
			Intent intent4 = new Intent(this, MapViewActivity.class);
			spec4.setIndicator("Map");
			spec4.setContent(intent4);
			host.addTab(spec4);
		}

		/**
		 * Broadcasts requests for UI refresh on wifi and cell info.
		 */
		private void updateUI() {
			Intent intent1 = new Intent(RadioBeacon.INTENT_WIFI_UPDATE);
			sendBroadcast(intent1);
			Intent intent2 = new Intent(RadioBeacon.INTENT_CELL_UPDATE);
			sendBroadcast(intent2);
		}

	}