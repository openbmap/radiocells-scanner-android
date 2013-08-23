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

import java.text.DecimalFormat;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Layout for the GPS status bar
 */
public class GpsStatusRecord extends LinearLayout implements Listener, LocationListener {

	private static final String TAG = GpsStatusRecord.class.getSimpleName();

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
	private HostActivity activity;

	/**
	 * Reference to LocationManager
	 */
	private LocationManager lmgr;

	/**
	 * the timestamp of the last GPS fix we used
	 */
	private long lastGPSTimestampStatus = 0;

	/**
	 * the timestamp of the last GPS fix we used for location updates
	 */
	private long lastGPSTimestampLocation = 0;

	/**
	 * the interval (in ms) to log GPS fixes defined in the preferences
	 */
	private final long gpsLoggingInterval;

	/**
	 * Is GPS active ?
	 */
	private boolean gpsActive = false;

	private String mProvider;

	public GpsStatusRecord(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		LayoutInflater.from(context).inflate(R.layout.gpsstatusrecord, this, true);

		//read the logging interval from preferences
		gpsLoggingInterval = Long.parseLong(PreferenceManager.getDefaultSharedPreferences(this.getContext()).getString(
				Preferences.KEY_GPS_LOGGING_INTERVAL, Preferences.VAL_GPS_LOGGING_INTERVAL)) * RadioBeacon.MILLIS_IN_SECOND;

		if (context instanceof HostActivity) {
			activity = (HostActivity) context;
			lmgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		}		
	}

	/**
	 * Registers for GPS location updates.
	 * @param enable
	 */
	public final void requestLocationUpdates(final boolean enable) {
		if (enable) {

			mProvider = LocationManager.GPS_PROVIDER;

			lmgr.requestLocationUpdates(mProvider, 0, 0, this);
			lmgr.addGpsStatusListener(this);
		} else {
			lmgr.removeUpdates(this);
			lmgr.removeGpsStatusListener(this);
		}
	}

	@Override
	public final void onGpsStatusChanged(final int event) {
		// Update GPS Status image according to event
		ImageView imgSatIndicator = (ImageView) findViewById(R.id.gpsstatus_record_imgSatIndicator);

		switch (event) {
			case GpsStatus.GPS_EVENT_FIRST_FIX:
				imgSatIndicator.setImageResource(R.drawable.sat_indicator_0);
				activity.onGpsEnabled();
				break;
			case GpsStatus.GPS_EVENT_STARTED:
				imgSatIndicator.setImageResource(R.drawable.sat_indicator_unknown);
				break;
			case GpsStatus.GPS_EVENT_STOPPED:
				imgSatIndicator.setImageResource(R.drawable.sat_indicator_off);
				activity.onGpsDisabled();
				break;
			case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
				// first of all we check if the time from the last used fix to the current fix is greater than the logging interval
				if ((event != GpsStatus.GPS_EVENT_SATELLITE_STATUS)
						|| (lastGPSTimestampStatus + gpsLoggingInterval) < System.currentTimeMillis()) {
					lastGPSTimestampStatus = System.currentTimeMillis(); // save the time of this fix

					GpsStatus status = lmgr.getGpsStatus(null);

					// Count active satellites
					int satCount = 0;
					for (@SuppressWarnings("unused") GpsSatellite sat:status.getSatellites()) {
						satCount++;
					}

					// Count how many bars should we draw
					int nbBars = 0;
					for (int i = 0; i < SAT_INDICATOR_TRESHOLD.length; i++) {
						if (satCount >= SAT_INDICATOR_TRESHOLD[i]) {
							nbBars = i;
						}
					}

					// Log.v(TAG, "Found " + satCount + " satellites. Will draw " + nbBars + " bars.");			
					imgSatIndicator.setImageResource(getResources().getIdentifier("drawable/sat_indicator_" + nbBars, null, RadioBeacon.class.getPackage().getName()));
				}
				break;
			default:
				break;
		}
	}

	@Override
	public final void onLocationChanged(final Location location) {
		// first of all we check if the time from the last used fix to the current fix is greater than the logging interval
		if ((lastGPSTimestampLocation + gpsLoggingInterval) < System.currentTimeMillis()) {
			lastGPSTimestampLocation = System.currentTimeMillis(); // save the time of this fix

			if (!gpsActive) {
				gpsActive = true;
				// GPS activated, activate UI
				activity.onGpsEnabled();
			}

			TextView tvAccuracy = (TextView) findViewById(R.id.gpsstatus_record_tvAccuracy);
			if (location.hasAccuracy()) {
				tvAccuracy.setText("Accuracy : " + ACCURACY_FORMAT.format(location.getAccuracy()) + " m");
			} else {
				tvAccuracy.setText("");
			}
		}
	}

	@Override
	public final void onProviderDisabled(final String provider) {
		Log.d(TAG, "Location provider " + provider + " disabled");
		gpsActive = false;
		((ImageView) findViewById(R.id.gpsstatus_record_imgSatIndicator)).setImageResource(R.drawable.sat_indicator_off);
		((TextView) findViewById(R.id.gpsstatus_record_tvAccuracy)).setText("");
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
		TextView tvAccuracy = (TextView) findViewById(R.id.gpsstatus_record_tvAccuracy);

		switch (status) {
			// Don't do anything for status AVAILABLE, as this event occurs frequently,
			// changing the graphics cause flickering .
			case LocationProvider.OUT_OF_SERVICE:
				imgSatIndicator.setImageResource(R.drawable.sat_indicator_off);
				tvAccuracy.setText("");
				gpsActive = false;
				activity.onGpsDisabled();
				break;
			case LocationProvider.TEMPORARILY_UNAVAILABLE:
				imgSatIndicator.setImageResource(R.drawable.sat_indicator_unknown);
				tvAccuracy.setText("");
				gpsActive = false;
				break;
			default:
				break;
		}
	}

	/**
	 * Manages the state of the recording indicator, depending if we're tracking or not.
	 * @param isTracking true if the indicator must show that we're tracking, otherwise false
	 */
/*	public final void manageRecordingIndicator(final boolean isTracking) {
		Log.d(TAG, "manageRecordingIndicator called, set to " + isTracking);
		ImageView recordStatus = (ImageView) findViewById(R.id.gpsstatus_record_animRec);
		if (isTracking) {
			recordStatus.setImageResource(R.drawable.record_red);
		} else {
			recordStatus.setImageResource(R.drawable.record_grey);
		}
	}
*/
}
