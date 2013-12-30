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

import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.Session;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;

/**
 * Activity for displaying basic session infos (# of cells, wifis, etc.)
 */
public class StatsActivity extends SherlockFragment {

	private static final String TAG = StatsActivity.class.getSimpleName();

	/**
	 * Fade message after which time (in millis)
	 */
	private static final long FADE_TIME	= 10 * 1000;

	/**
	 * Display periodic messages how often (in millis)
	 */
	private final static int REFRESH_INTERVAL = 1000 * 10; 

	/**
	 * UI controls
	 */
	private TextView	tvLastCell;
	private TextView	tvLastWifi;
	private TextView	tvSession;
	private TextView	tvCountCells;
	private TextView	tvCountWifis;
	private TextView	tvNewWifis;
	private TextView	tvIgnored;
	private TextView	tvFree;
	private ImageView	ivFree;
	private ImageView	ivAlert;

	private Session	mSession;

	/**
	 * Time of last new wifi in millis
	 */
	private long mLastWifiUpdate;

	/**
	 * Time of last new cell in millis
	 */
	private long mLastCellUpdate;

	/**
	 * Fades ignore messages after certain time
	 */
	private Runnable mFadeIgnoreTask;
	private Handler mFadeIgnoreHandler = new Handler();

	private Runnable mFadeFreeTask;
	private Handler mFadeFreeHandler = new Handler();
	/**
	 * Update certain infos at periodic intervals
	 */
	private Handler mRefreshHandler = new Handler();
	private Runnable mPeriodicRefreshTask;

	/**
	 * Receives cell / wifi news
	 */
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			
			// handle strange null values on some 4.4 devices
			if (intent == null) {
				Log.wtf(TAG, "Intent is null");
				return;
			}
			
			if (tvLastCell == null) {
				Log.wtf(TAG, "tvLastCell is null");
				return;
			}
			
			if (tvIgnored == null || ivAlert == null || tvFree == null || ivFree == null) {
				Log.wtf(TAG, "Some controls are null");
				return;
			}
			
			Log.d(TAG, "Received intent " + intent.getAction());

			// handling cell and wifi broadcasts
			if (RadioBeacon.INTENT_NEW_CELL.equals(intent.getAction())) {
				String lastCell = intent.getStringExtra(RadioBeacon.MSG_KEY);
				if (lastCell != null) {
					tvLastCell.setText(lastCell);
				}
				refreshObjectCount();
				mLastCellUpdate = System.currentTimeMillis();

			} else if (RadioBeacon.INTENT_NEW_WIFI.equals(intent.getAction())) {
				String lastWifi = intent.getStringExtra(RadioBeacon.MSG_SSID);
				String extraInfo = intent.getStringExtra(RadioBeacon.MSG_KEY);
				if (lastWifi != null && extraInfo != null) {
					tvLastWifi.setText(lastWifi + " " + extraInfo);
				}
				refreshObjectCount();
				mLastWifiUpdate = System.currentTimeMillis();

			} else if (RadioBeacon.INTENT_NEW_SESSION.equals(intent.getAction())) {
				String id = intent.getStringExtra(RadioBeacon.MSG_KEY);
				refreshObjectCount();

			} else if (RadioBeacon.INTENT_WIFI_BLACKLISTED.equals(intent.getAction())) {
				// let's display warning for 10 seconds
				mFadeIgnoreHandler.removeCallbacks(mFadeIgnoreTask);

				String reason = intent.getStringExtra(RadioBeacon.MSG_KEY);
				String ssid = intent.getStringExtra(RadioBeacon.MSG_SSID);
				String bssid = intent.getStringExtra(RadioBeacon.MSG_BSSID);

				// can be null, so set default values
				if (ssid == null) {
					ssid = "";
				}
				if (bssid == null) {
					bssid = "";
				}

				if (reason.equals(RadioBeacon.MSG_BSSID)) {
					tvIgnored.setText(ssid + " (" + bssid + ")\n" + getResources().getString(R.string.blacklisted_bssid));
				} else if (reason.equals(RadioBeacon.MSG_SSID)) {
					tvIgnored.setText(ssid + " (" + bssid + ")\n" + getResources().getString(R.string.blacklisted_ssid));
				} else if (reason.equals(RadioBeacon.MSG_LOCATION)) {
					tvIgnored.setText(R.string.blacklisted_area);
				}
				tvIgnored.setVisibility(View.VISIBLE);
				ivAlert.setVisibility(View.VISIBLE);
				mFadeIgnoreHandler.postDelayed(mFadeIgnoreTask, FADE_TIME);
			} else if (RadioBeacon.INTENT_WIFI_FREE.equals(intent.getAction())){
				mFadeFreeHandler.removeCallbacks(mFadeFreeTask);
				String ssid = intent.getStringExtra(RadioBeacon.MSG_SSID);
				if (ssid != null) {
					tvFree.setText(getResources().getString(R.string.free_wifi) + "\n" + ssid);
				}
				tvFree.setVisibility(View.VISIBLE);
				ivFree.setVisibility(View.VISIBLE);
				mFadeFreeHandler.postDelayed(mFadeFreeTask, FADE_TIME);
			}
		}
	};

	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mFadeIgnoreTask = new Runnable() {
			@Override
			public void run() {
				tvIgnored.setVisibility(View.INVISIBLE); 
				ivAlert.setVisibility(View.INVISIBLE);
			}
		};
		
		mFadeFreeTask = new Runnable() {
			@Override
			public void run() {
				tvFree.setVisibility(View.INVISIBLE); 
				ivFree.setVisibility(View.INVISIBLE);
			}
		};

		mPeriodicRefreshTask = new Runnable()
		{
			@SuppressLint("DefaultLocale")
			@Override 
			public void run() {
				refreshTimeSinceUpdate();

				mRefreshHandler.postDelayed(mPeriodicRefreshTask, REFRESH_INTERVAL);
			}
		};

		// setup broadcast filters
		registerReceiver();	
	}
	
	@Override
	public final void onResume() {
		super.onResume();

		registerReceiver();	

		startRepeatingTask();
	}

	void startRepeatingTask()
	{
		mPeriodicRefreshTask.run(); 
	}

	void stopRepeatingTask()
	{
		mRefreshHandler.removeCallbacks(mPeriodicRefreshTask);
	}

	@Override
	public final void onPause() {
		unregisterReceiver();

		stopRepeatingTask();

		super.onPause();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.stats, container, false);
		// setup UI controls
		initUi(view);
		
		refreshObjectCount();
		return view;
	}

	/**
	 * Init UI contols
	 */
	private void initUi(View view) {
		tvLastCell = (TextView) view.findViewById(R.id.stats_cell_description);
		tvLastWifi = (TextView) view.findViewById(R.id.stats_wifi_description);
		tvSession = (TextView) view.findViewById(R.id.stats_session_description);
		tvCountCells = (TextView) view.findViewById(R.id.stats_cell_total);
		tvCountWifis = (TextView) view.findViewById(R.id.stats_wifi_total);
		tvNewWifis = (TextView) view.findViewById(R.id.stats_new_wifis);
		tvIgnored = (TextView) view.findViewById(R.id.stats_blacklisted);
		tvFree = (TextView) view.findViewById(R.id.stats_free);
		ivFree = (ImageView) view.findViewById(R.id.stats_icon_free);
		ivAlert = (ImageView) view.findViewById(R.id.stats_icon_alert);
	}

	/**
	 * Registers broadcast receivers.
	 */
	private void registerReceiver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(RadioBeacon.INTENT_NEW_WIFI);
		filter.addAction(RadioBeacon.INTENT_NEW_CELL);
		filter.addAction(RadioBeacon.INTENT_SESSION_UPDATE);
		filter.addAction(RadioBeacon.INTENT_WIFI_BLACKLISTED);
		filter.addAction(RadioBeacon.INTENT_WIFI_FREE);
		getSherlockActivity().registerReceiver(mReceiver, filter);
	}

	/**
	 * Unregisters receivers for GPS and wifi scan results.
	 */
	private void unregisterReceiver() {
		try {
			getSherlockActivity().unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android}
		}
	}

	/**
	 * Refreshes session id, number of cells and wifis.
	 */
	private void refreshObjectCount() {
		DataHelper dataHelper = new DataHelper(getSherlockActivity());
		int session = dataHelper.getActiveSessionId();

		if (session != RadioBeacon.SESSION_NOT_TRACKING) {
			tvSession.setText(String.valueOf(session));
			tvCountCells.setText(
					"(" + String.valueOf(dataHelper.countCells(session)) + ")");	
			tvCountWifis.setText(
					"(" + String.valueOf(dataHelper.countWifis(session)) + ")");
			tvNewWifis.setText(String.valueOf(dataHelper.countNewWifis(session)));
		} else {
			tvSession.setText("-");
			tvCountCells.setText("(--)");
			tvCountWifis.setText("(--)");
		}
	}

	/**
	 * Displays time since last cell/wifi update
	 */
	private void refreshTimeSinceUpdate() {
		String deltaCellString = String.format(getString(R.string.time_since_last_cell_update), getTimeSinceLastUpdate(mLastCellUpdate));  
		String deltaWifiString =String.format(getString(R.string.time_since_last_wifi_update), getTimeSinceLastUpdate(mLastWifiUpdate));  

		Log.i(TAG, deltaCellString);
		Log.i(TAG, deltaWifiString);
	}

	/**
	 * Returns time since base value as human-readable string
	 * @return
	 */
	private String getTimeSinceLastUpdate(long base) {
		String deltaString = "";

		// no previous updates
		if (base == 0) {
			return deltaString;
		}

		long delta = (System.currentTimeMillis() - base);

		if (delta < 60000) {
			deltaString = String.valueOf(delta / 1000) + getString(R.string.seconds);
		} else {
			deltaString = String.valueOf(delta / 60000) + getString(R.string.minutes);
		}
		return deltaString;
	}
}
