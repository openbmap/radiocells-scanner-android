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

package org.openbmap.services.position.providers;

import org.openbmap.Preferences;
import org.openbmap.services.position.LocationService;
import org.openbmap.services.position.LocationServiceFactory;
import org.openbmap.services.position.PositioningService;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class GpsProvider extends LocationProviderImpl implements Listener, LocationListener {

	@SuppressWarnings("unused")
	private static final String	TAG	= GpsProvider.class.getSimpleName();

	private final Context	mContext;

	/**
	 * LocationManager
	 */
	private LocationManager lmgr;

	private boolean	isGpsEnabled;

	private long gpsLoggingInterval;

	private long mLastGPSTimestamp;
	private Location mLastLocation = new Location("dummy");

	// TODO: use barometer if available
	public GpsProvider(final Context ctx) {
		this(ctx, LocationServiceFactory.getLocationService());
	}

	public GpsProvider(final Context ctx, final LocationService locationService) {
		super(ctx, locationService);
		mContext = ctx.getApplicationContext();
	}

	@Override
	public final void start(final PositioningService positioningService) {
		super.start(positioningService);

		//read the logging interval from preferences
		gpsLoggingInterval = Long.parseLong(PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext()).getString(
				Preferences.KEY_GPS_LOGGING_INTERVAL, Preferences.VAL_GPS_LOGGING_INTERVAL)) * 1000;

		// Register ourselves for location updates
		lmgr = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

		// To request updates only once, disable any previous updates before starting 
		disableUpdates();
		enableUpdates();
	}

	/**
	 * Request GPS update notification
	 */
	private void enableUpdates() {
		if (lmgr != null) {
			lmgr.addGpsStatusListener(this);
			lmgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, gpsLoggingInterval, 0, this);
		}
	}

	/**
	 * Cancels GPS update notification
	 */
	private void disableUpdates() {
		if (lmgr != null) {
			lmgr.removeGpsStatusListener(this);
			lmgr.removeUpdates(this);
		}
	}

	@Override
	public final void stop() {
		Log.i(TAG, "GpsProvider received stop signal. Releasing location updates");
		disableUpdates();
		super.stop();
	}


	/* (non-Javadoc)
	 * @see android.location.LocationListener#onProviderDisabled(java.lang.String)
	 */
	@Override
	public final void onProviderDisabled(final String provider) {
		isGpsEnabled = false;
	}

	/* (non-Javadoc)
	 * @see android.location.LocationListener#onProviderEnabled(java.lang.String)
	 */
	@Override
	public final void onProviderEnabled(final String provider) {
		isGpsEnabled = true;
	}

	/* (non-Javadoc)
	 * @see android.location.LocationListener#onStatusChanged(java.lang.String, int, android.os.Bundle)
	 */
	@Override
	public void onStatusChanged(final String provider, final int status, final Bundle extras) {
		if (mListener != null) {
			mListener.onStatusChanged(provider, status, extras);
		}
	}

	/* (non-Javadoc)
	 * @see android.location.LocationListener#onLocationChanged(android.location.Location)
	 */
	@Override
	public final void onLocationChanged(final Location location) {		
		// We're receiving location, so GPS is enabled
		isGpsEnabled = true;

		// first of all we check if the time from the last used fix to the current fix is greater than the logging interval
		if ((mLastGPSTimestamp + gpsLoggingInterval) < System.currentTimeMillis()) {
			mLastGPSTimestamp = System.currentTimeMillis(); // save the time of this fix			
			mLastLocation = location;

			locationService.updateLocation(location);

			if (mListener != null) {
				mListener.onLocationChange(location);
			}
		}
	}

	/* (non-Javadoc)
	 * @see android.location.GpsStatus.Listener#onGpsStatusChanged(int)
	 */
	@Override
	public void onGpsStatusChanged(final int event) {
		int satCount = -1;

		switch (event) {
			case GpsStatus.GPS_EVENT_FIRST_FIX:
				satCount = 0;
				break;
			case GpsStatus.GPS_EVENT_STARTED:
				satCount = -1;
				break;
			case GpsStatus.GPS_EVENT_STOPPED:
				satCount = -1;
				break;
			case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
				// first of all we check if the time from the last used fix to the current fix is greater than the logging interval
				if ((event != GpsStatus.GPS_EVENT_SATELLITE_STATUS)
						|| (mLastGPSTimestamp + gpsLoggingInterval) < System.currentTimeMillis()) {
					mLastGPSTimestamp = System.currentTimeMillis(); // save the time of this fix

					final GpsStatus status = lmgr.getGpsStatus(null);

					// Count active satellites
					satCount = 0;
					for (@SuppressWarnings("unused") final GpsSatellite sat:status.getSatellites()) {
						satCount++;
					}
				}
				break;
			default:
				break;
		}

		if (mListener != null) {
			mListener.onSatInfo(satCount);
		}

	}

}
