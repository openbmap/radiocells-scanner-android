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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;

import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.models.Session;

/**
 * Activity for displaying basic session infos (# of cells, wifis, etc.)
 */
public class StatsActivity extends Fragment {

	private static final String TAG = StatsActivity.class.getSimpleName();

	/**
	 * Fade message after which time (in millis)
	 */
	private static final long FADE_TIME = 10 * 1000;

	/**
	 * Display periodic messages how often (in millis)
	 */
	private final static int REFRESH_INTERVAL = 1000 * 2;

	/**
	 * UI controls
	 */
	private TextView tvCellDescription;
	private TextView tvCellStrength;
	private TextView tvWifiDescription;
	private TextView tvWifiStrength;
    private TextView tvTechnology;
    private TextView tvIgnored;
	private TextView tvFree;
	private ImageView ivFree;
	private ImageView ivAlert;
	private GraphView graphView;

	GraphViewSeries measurements;

	private Session mSession;

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
	private final Handler mFadeIgnoreHandler = new Handler();

	private Runnable mFadeFreeTask;
	private final Handler mFadeFreeHandler = new Handler();
	/**
	 * Update certain infos at periodic intervals
	 */
	private final Handler mRefreshHandler = new Handler();
	private Runnable mPeriodicRefreshTask;

    private int mCurrentStrength;

    private String mLastTechnology;

	/**
	 * Receives cell / wifi news
	 */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@SuppressLint("DefaultLocale")
		@Override
		public void onReceive(final Context context, final Intent intent) {

			// handle strange
			// null values on
			// some 4.4 devices
			if (intent == null) {
				Log.wtf(TAG, "Intent is null");
				return;
			}

			if (tvCellDescription == null) {
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
				final String currentOperator = intent.getStringExtra(RadioBeacon.MSG_OPERATOR);
                final int currentCellId = intent.getIntExtra(RadioBeacon.MSG_CELL_ID, -1);
                final String currentMcc = intent.getStringExtra(RadioBeacon.MSG_MCC);
                final String currentMnc = intent.getStringExtra(RadioBeacon.MSG_MNC);
                final int currentArea = intent.getIntExtra(RadioBeacon.MSG_AREA, -1);
                final String currentTechnology = intent.getStringExtra(RadioBeacon.MSG_TECHNOLOGY);
                mCurrentStrength = intent.getIntExtra(RadioBeacon.MSG_STRENGTH, -1);

                String description = "";

                if (currentOperator != null) {
                    description = currentOperator;
                }

                if (currentMcc != null && currentMnc != null) {
                    if (description.length() > 0) { description += "\n";}
                    description += String.format("%s/%s/%s/%s", currentMcc, currentMnc, currentArea, currentCellId);
                }

				tvCellDescription.setText(description);
				tvCellStrength.setText(String.format("%d dBm", mCurrentStrength));

                if (!currentTechnology.equals(mLastTechnology)) {
                    final Animation in = new AlphaAnimation(0.0f, 1.0f);
                    in.setDuration(2000);
                    final Animation out = new AlphaAnimation(1.0f, 0.0f);
                    out.setDuration(2000);
                    out.setAnimationListener(new AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            tvTechnology.setText(currentTechnology);
                            tvTechnology.startAnimation(in);
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {

                        }
                    });
                    tvTechnology.startAnimation(out);
                }

                mLastTechnology = currentTechnology;

				mLastCellUpdate = System.currentTimeMillis();

			} else
				if (RadioBeacon.INTENT_NEW_WIFI.equals(intent.getAction())) {
					final String wifiDescription = intent.getStringExtra(RadioBeacon.MSG_SSID);
					final int wifiStrength = intent.getIntExtra(RadioBeacon.MSG_STRENGTH, -1);
					if (wifiDescription != null ) {
						tvWifiDescription.setText(wifiDescription);
					} else {
						tvWifiDescription.setText(getString(R.string.n_a));
					}

					tvWifiStrength.setText(String.format("%d dBm", wifiStrength));

					mLastWifiUpdate = System.currentTimeMillis();

				} else
					if (RadioBeacon.INTENT_NEW_SESSION.equals(intent.getAction())) {
						final String id = intent.getStringExtra(RadioBeacon.MSG_KEY);
						// tbd
					} else
						if (RadioBeacon.INTENT_WIFI_BLACKLISTED.equals(intent.getAction())) {
							// let's display
							// warning for
							// 10 seconds
							mFadeIgnoreHandler.removeCallbacks(mFadeIgnoreTask);

							final String reason = intent.getStringExtra(RadioBeacon.MSG_KEY);
							String ssid = intent.getStringExtra(RadioBeacon.MSG_SSID);
							String bssid = intent.getStringExtra(RadioBeacon.MSG_BSSID);

							// can be null,
							// so set
							// default
							// values
							if (ssid == null) {
								ssid = "";
							}
							if (bssid == null) {
								bssid = "";
							}

							if (reason.equals(RadioBeacon.MSG_BSSID)) {
								tvIgnored.setText(ssid + " (" + bssid + ") " + getResources().getString(R.string.blacklisted_bssid));
							} else
								if (reason.equals(RadioBeacon.MSG_SSID)) {
									tvIgnored.setText(ssid + " (" + bssid + ") " + getResources().getString(R.string.blacklisted_ssid));
								} else
									if (reason.equals(RadioBeacon.MSG_LOCATION)) {
										tvIgnored.setText(R.string.blacklisted_area);
									}
							tvIgnored.setVisibility(View.VISIBLE);
							ivAlert.setVisibility(View.VISIBLE);
							mFadeIgnoreHandler.postDelayed(mFadeIgnoreTask, FADE_TIME);
						} else
							if (RadioBeacon.INTENT_WIFI_FREE.equals(intent.getAction())) {
								mFadeFreeHandler.removeCallbacks(mFadeFreeTask);
								final String ssid = intent.getStringExtra(RadioBeacon.MSG_SSID);
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

		mPeriodicRefreshTask = new Runnable() {
			@SuppressLint("DefaultLocale")
			@Override
			public void run() {
				updateTimeSinceUpdate();
				updateGraph();
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

	void startRepeatingTask() {
		mPeriodicRefreshTask.run();
	}

	void stopRepeatingTask() {
		mRefreshHandler.removeCallbacks(mPeriodicRefreshTask);
	}

	@Override
	public final void onPause() {
		unregisterReceiver();

		stopRepeatingTask();

		super.onPause();
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.stats, container, false);
		// setup UI controls
		initUi(view);

		return view;
	}

	/**
	 * Init UI contols
	 */
	private void initUi(final View view) {
		tvCellDescription = (TextView) view.findViewById(R.id.stats_cell_description);
		tvCellStrength = (TextView) view.findViewById(R.id.stats_cell_strength);

		tvWifiDescription = (TextView) view.findViewById(R.id.stats_wifi_description);
		tvWifiStrength = (TextView) view.findViewById(R.id.stats_wifi_strength);

		tvIgnored = (TextView) view.findViewById(R.id.stats_blacklisted);
		tvFree = (TextView) view.findViewById(R.id.stats_free);
		ivFree = (ImageView) view.findViewById(R.id.stats_icon_free);
		ivAlert = (ImageView) view.findViewById(R.id.stats_icon_alert);

		tvTechnology = (TextView) view.findViewById(R.id.tvTechnology);

		graphView = new LineGraphView(this.getActivity().getBaseContext() // context
				, "Cell strength (dBm)");
		graphView.setManualYAxisBounds(-50, -100); 
		graphView.getGraphViewStyle().setNumVerticalLabels(3);
		graphView.setHorizontalLabels(new String[] {""});
		graphView.setViewPort(2, 60000);
		graphView.setScrollable(true);

		final LinearLayout layout = (LinearLayout) view.findViewById(R.id.graph2);
		layout.addView(graphView);
	}

	/**
	 * Registers broadcast receivers.
	 */
	private void registerReceiver() {
		final IntentFilter filter = new IntentFilter();
		filter.addAction(RadioBeacon.INTENT_NEW_WIFI);
		filter.addAction(RadioBeacon.INTENT_NEW_CELL);
		filter.addAction(RadioBeacon.INTENT_SESSION_UPDATE);
		filter.addAction(RadioBeacon.INTENT_WIFI_BLACKLISTED);
		filter.addAction(RadioBeacon.INTENT_WIFI_FREE);
		getActivity().registerReceiver(mReceiver, filter);
	}

	/**
	 * Unregisters receivers for GPS and wifi scan results.
	 */
	private void unregisterReceiver() {
		try {
			getActivity().unregisterReceiver(mReceiver);
		} catch (final IllegalArgumentException e) {
			// do nothing here {@see
			// http://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android}
		}
	}

	/**
	 * Displays time since last cell/wifi update
	 */
	private void updateTimeSinceUpdate() {
		final String deltaCellString = String.format(getString(R.string.time_since_last_cell_update), getTimeSinceLastUpdate(mLastCellUpdate));
		final String deltaWifiString = String.format(getString(R.string.time_since_last_wifi_update), getTimeSinceLastUpdate(mLastWifiUpdate));

		Log.v(TAG, deltaCellString);
		Log.v(TAG, deltaWifiString);
	}

	/**
	 * 
	 */
	private void updateGraph() {
		if (measurements == null) {
			// TODO proper release
			measurements = new GraphViewSeries(new GraphViewData[] { new GraphViewData(System.currentTimeMillis(), -100.0d) });
			graphView.addSeries(measurements);
		}

		if (mCurrentStrength != -1) {
			measurements.appendData(new GraphViewData(System.currentTimeMillis() , mCurrentStrength), true, 60);
		} else {
			measurements.appendData(new GraphViewData(System.currentTimeMillis(), -100d), true, 60);
		}
		mCurrentStrength = -1;
	}

	/**
	 * Returns time since base value as human-readable string
	 * 
	 * @return
	 */
	private String getTimeSinceLastUpdate(final long base) {
		String deltaString = "";

		// no previous updates
		if (base == 0) {
			return deltaString;
		}

		final long delta = (System.currentTimeMillis() - base);

		if (delta < 60000) {
			deltaString = String.valueOf(delta / 1000) + getString(R.string.seconds);
		} else {
			deltaString = String.valueOf(delta / 60000) + getString(R.string.minutes);
		}
		return deltaString;
	}
}
