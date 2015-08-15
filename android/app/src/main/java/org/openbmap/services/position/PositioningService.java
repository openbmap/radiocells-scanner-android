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

package org.openbmap.services.position;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import org.openbmap.RadioBeacon;
import org.openbmap.services.AbstractService;
import org.openbmap.services.position.providers.GpsProvider;
import org.openbmap.services.position.providers.LocationChangeListener;

/**
 * GPS position service.
 */
public class PositioningService extends AbstractService implements LocationChangeListener {

	private static final String TAG = PositioningService.class.getSimpleName();

	public enum State { OFF, GPS, INERTIAL };
	
	private State providerState;
	/**
	 * Are we currently tracking ?
	 */
	private boolean mIsTracking = false;

	/**
	 * Is GPS enabled ?
	 */
	private boolean isGpsEnabled = false;

	/**
	 * Timestamp of last GPS fix used
	 */
	private long mLastTimestamp = 0;

	/**
	 * Last known location
	 */
	private Location mLastLocation = new Location("dummy");

	/**
	 * the interval (in ms) to log GPS fixes defined in the preferences
	 */
	private long gpsLoggingInterval;

	/**
	 * Location provider name
	 */
	private String mProvider;

	private GpsProvider	gpsProvider;

	@Override
	public final void onCreate() {	
		super.onCreate();
		providerState = State.OFF;
	}

	@Override
	public final boolean onUnbind(final Intent intent) {
		// If we aren't currently tracking we can stop ourselves
		if (!mIsTracking) {
			Log.d(TAG, "GPS Service self-stopping");
			stopSelf();
		}

		// We don't want onRebind() to be called, so return false.
		return false;
	}

	@Override
	public final void onDestroy() {

		if (mIsTracking) {
			// If we're currently tracking, save user data.
			stopTracking();
		}

		super.onDestroy();
	}

	public final void startTracking(final State newState) throws Exception {

		if (newState.equals(providerState)) {
			Log.i(TAG, "Didn't change provider state: already in state (" + providerState + ")");
			return;
		}

		// reset all providers
		if (mIsTracking) {
			stopTracking();
		}

		if (newState.equals(State.OFF)) {
			stopTracking();
			return;
		} else if (newState.equals(State.GPS)) {
			// activate gps by default
			gpsProvider = new GpsProvider(this);
			gpsProvider.setLocationChangeListener(this);
			gpsProvider.start(this);

			providerState = State.GPS;
		} else { 
			mIsTracking = false;
			throw new Exception("Unknown state");
		}

		mIsTracking = true;
	}

	private void stopTracking() {
		mIsTracking = false;

		if (gpsProvider != null) {
			gpsProvider.stop();
			gpsProvider = null;
		}


		providerState = State.OFF;
	}

	/**
	 * Setter for mIsTracking.
	 * @return true if we're currently tracking, otherwise false.
	 */
	public final boolean isTracking() {
		return mIsTracking;
	}

	/**
	 * Getter for gpsEnabled.
	 * @return true if GPS is enabled, otherwise false.
	 */
	public final boolean isGpsEnabled() {
		return isGpsEnabled;
	}

	/**
	 * Controls position provider startup / stop
	 */
	@Override
	public final void onReceiveMessage(final Message msg) {
		switch(msg.what) {
			case RadioBeacon.MSG_START_TRACKING: 
				Log.d(TAG, "GPS logger received MSG_START_TRACKING signal");
				try {
					Bundle aBundle = msg.getData();
					String providerString = aBundle.getString("provider"); 
					State provider = null;
					if (providerString != null) {
						provider = State.valueOf(providerString);
					} else {
						Log.w(TAG, "No provider selected, using GPS as default");
						provider = State.GPS;
					}

					Log.i(TAG, "Received request to start provider " + providerString + "(" + provider.toString() + ")");
					startTracking(provider);
				} catch (Exception e) {
					Log.e(TAG, "Error starting provider: " + e.getLocalizedMessage());
				}
				break;
			case RadioBeacon.MSG_STOP_TRACKING:
				Log.d(TAG, "GPS logger received MSG_STOP_TRACKING signal");
				stopTracking();
				
				// before manager stopped the service
				PositioningService.this.stopSelf();
				break;
			default:
				Log.d(TAG, "Unrecognized message received: " + msg.what);
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.services.AbstractService#onStartService()
	 */
	@Override
	public void onStartService() {

	}

	/* (non-Javadoc)
	 * @see org.openbmap.services.AbstractService#onStopService()
	 */
	@Override
	public void onStopService() {

	}

	/* (non-Javadoc)
	 * @see org.openbmap.stepdetection.LocationChangeListener#onLocationChange(android.location.Location)
	 */
	@Override
	public final void onLocationChange(final Location location) {
		// Log.i(TAG, "Broadcasting position: lat " + location.getLatitude() + " lon " + location.getLongitude());
		// first of all we check if the time from the last used fix to the current fix is greater than the logging interval
		if (mIsTracking) {
			if ((mLastTimestamp + gpsLoggingInterval) < System.currentTimeMillis()) {
				mLastTimestamp = System.currentTimeMillis(); // save the time of this fix

				// broadcast GPS location, so wireless logger can use it
				Intent intent = new Intent(RadioBeacon.INTENT_POSITION_UPDATE);
				Bundle bundle = new Bundle();
				bundle.putParcelable("android.location.Location", location);
				intent.putExtras(bundle);

				sendBroadcast(intent);

				mLastTimestamp = location.getTime();
				mLastLocation = location;
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.services.position.providers.LocationChangeListener#onStatusChanged(java.lang.String, int, android.os.Bundle)
	 */
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		switch (status) {
			// Don't do anything for status AVAILABLE, as this event occurs frequently,
			// changing the graphics cause flickering .
			case android.location.LocationProvider.OUT_OF_SERVICE:
				Intent i1 = new Intent(RadioBeacon.INTENT_POSITION_SAT_INFO);
				Bundle b1 = new Bundle();
				b1.putString("STATUS", "OUT_OF_SERVICE");
				b1.putInt("SAT_COUNT", -1);
				i1.putExtras(b1);
				sendBroadcast(i1);

				break;
			case android.location.LocationProvider.TEMPORARILY_UNAVAILABLE:
				Intent i2 = new Intent(RadioBeacon.INTENT_POSITION_SAT_INFO);
				Bundle b2 = new Bundle();
				b2.putString("STATUS", "TEMPORARILY_UNAVAILABLE");
				b2.putInt("SAT_COUNT", -1);
				i2.putExtras(b2);
				sendBroadcast(i2);
				
				break;
			default:
				break;
		}	

	}

	/* (non-Javadoc)
	 * @see org.openbmap.services.position.providers.LocationChangeListener#onStatusChanged(int)
	 */
	@Override
	public void onSatInfo(int satCount) {
		Intent i = new Intent(RadioBeacon.INTENT_POSITION_SAT_INFO);
		Bundle b = new Bundle();
		b.putString("STATUS", "UPDATE");
		b.putInt("SAT_COUNT", satCount);
		i.putExtras(b);
		sendBroadcast(i);	
	}

}
