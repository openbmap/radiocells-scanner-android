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

import java.text.DecimalFormat;

import org.openbmap.R;
import org.openbmap.RadioBeacon;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Layout for the GPS status bar
 */
public class GpsStatusRecord extends LinearLayout {

	private static final String TAG = GpsStatusRecord.class.getSimpleName();

	/**
	 * Refresh interval for gps status (in millis)
	 */
	private static final int STATUS_REFRESH_INTERVAL = 2000;

	/**
	 * Formatter for accuracy display.
	 */
	private static final DecimalFormat ACCURACY_FORMAT = new DecimalFormat("0");

	/**
	 * Keeps matching between satellite indicator bars to draw, and numbers
	 * of satellites for each bars;
	 */
	private static final int[] SAT_INDICATOR_TRESHOLD = {2, 3, 4, 6, 8};

	/**
	 * Containing activity
	 */
	private Context ctx;

	/**
	 * Is GPS active ?
	 */
	private boolean gpsActive = false;

	private String mProvider;

	private TextView	tvAccuracy;

	private ImageView	imgSatIndicator;

	public GpsStatusRecord(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		LayoutInflater.from(context).inflate(R.layout.gpsstatusrecord, this, true);
		tvAccuracy = (TextView) findViewById(R.id.gpsstatus_record_tvAccuracy);
		tvAccuracy.setText("");
		imgSatIndicator = (ImageView) findViewById(R.id.gpsstatus_record_imgSatIndicator);
		imgSatIndicator.setImageResource(R.drawable.sat_indicator_unknown);

		if (context instanceof HostActivity) {
			ctx = context;
			registerReceiver();
		}		
	}

	/**
	 * Receives GPS location updates.
	 */
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			// handling GPS broadcasts
			if (RadioBeacon.INTENT_POSITION_UPDATE.equals(intent.getAction())) {
				Location location = intent.getExtras().getParcelable("android.location.Location");

				if (location.hasAccuracy()) {
					tvAccuracy.setText(getResources().getString(R.string.accuracy) +  ": " + ACCURACY_FORMAT.format(location.getAccuracy()) + " m");
				} else {
					tvAccuracy.setText("");
				}

			} else if (RadioBeacon.INTENT_POSITION_SAT_INFO.equals(intent.getAction())) {

				String status = intent.getExtras().getString("STATUS");
				int satCount = intent.getExtras().getInt("SAT_COUNT");

				if (status.equals("UPDATE")) {
					// Count how many bars should we draw
					int nbBars = 0;
					for (int i = 0; i < SAT_INDICATOR_TRESHOLD.length; i++) {
						if (satCount >= SAT_INDICATOR_TRESHOLD[i]) {
							nbBars = i;
						}
					}
					imgSatIndicator.setImageResource(getResources().getIdentifier("drawable/sat_indicator_" + nbBars, null, RadioBeacon.class.getPackage().getName()));
				} else if (status.equals("OUT_OF_SERVICE")) {
					imgSatIndicator.setImageResource(R.drawable.sat_indicator_off);
					tvAccuracy.setText(getResources().getString(R.string.no_sat_signal));
				} else if (status.equals("TEMPORARILY_UNAVAILABLE")) {
					imgSatIndicator.setImageResource(R.drawable.sat_indicator_off);
					tvAccuracy.setText(getResources().getString(R.string.no_sat_signal));
				}
			}
		}
	};

	@Override 
	protected void onAttachedToWindow() {
		registerReceiver();
	}

	@Override
	protected void onDetachedFromWindow() {
		unregisterReceiver();
		super.onDetachedFromWindow();
	}

	private void registerReceiver() {
		if (ctx == null) {
			Log.e(TAG, "Can't register for gps status updates: context is null");
			return;
		}
		IntentFilter filter = new IntentFilter();
		filter.addAction(RadioBeacon.INTENT_POSITION_UPDATE);
		filter.addAction(RadioBeacon.INTENT_POSITION_SAT_INFO);
		ctx.registerReceiver(mReceiver, filter);  
	}

	/**
	 * Unregisters receivers for GPS and wifi scan results.
	 */
	private void unregisterReceiver() {
		if (ctx == null) {
			Log.e(TAG, "Can't unregister gps status updates: context is null");
			return;
		}

		try {
			ctx.unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-mReceiver-is-registered-in-android}
			return;
		}
	}

	/*
	@Override
	public final void onProviderDisabled(final String provider) {
		Log.d(TAG, "Location provider " + provider + " disabled");
		gpsActive = false;
		((ImageView) findViewById(R.id.gpsstatus_record_imgSatIndicator)).setImageResource(R.drawable.sat_indicator_off);
		tvAccuracy.setText(getResources().getString(R.string.no_sat_signal));
		activity.onGpsDisabled();
	}

	@Override
	public final void onProviderEnabled(final String provider) {
		Log.d(TAG, "Location provider " + provider + " enabled");
		((ImageView) findViewById(R.id.gpsstatus_record_imgSatIndicator)).setImageResource(R.drawable.sat_indicator_unknown);
	}

	@Override
	public final void onStatusChanged(final String provider, final int status, final Bundle extras) {
		// Update provider status image according to status
		Log.d(TAG, "Location provider " + provider + " status changed to: " + status);
		ImageView imgSatIndicator = (ImageView) findViewById(R.id.gpsstatus_record_imgSatIndicator);

		switch (status) {
			// Don't do anything for status AVAILABLE, as this event occurs frequently,
			// changing the graphics cause flickering .
			case LocationProvider.OUT_OF_SERVICE:
				imgSatIndicator.setImageResource(R.drawable.sat_indicator_off);
				tvAccuracy.setText(getResources().getString(R.string.no_sat_signal));
				gpsActive = false;
				activity.onGpsDisabled();
				break;
			case LocationProvider.TEMPORARILY_UNAVAILABLE:
				imgSatIndicator.setImageResource(R.drawable.sat_indicator_unknown);
				tvAccuracy.setText(getResources().getString(R.string.no_sat_signal));
				gpsActive = false;
				break;
			default:
				break;
		}
	}
	 */
}
