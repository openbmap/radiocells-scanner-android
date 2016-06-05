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

package org.openbmap.utils;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Helper for getting current location.
 * All kudos to http://stackoverflow.com/questions/3145089/what-is-the-simplest-and-most-robust-way-to-get-the-users-current-location-in-a
 *
 */
public class CurrentLocationHelper {
	private Timer mTimer;
	private LocationManager mLocationManager;
	private LocationResult mLocationResult;

	private boolean mGpsEnabled = false;
	private boolean mNetworkEnabled = false;

	public boolean getLocation(final Context context, final LocationResult result) {
		//I use LocationResult callback class to pass location value from CurrentLocationHelper to user code.
		mLocationResult = result;
		if (mLocationManager ==  null)
			mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

		//exceptions will be thrown if provider is not permitted.
		try {
			mGpsEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		} catch (final Exception ex) {

		}

		try {
			mNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
		} catch(final Exception ex){

		}

		//don't start listeners if no provider is enabled
		if (!mGpsEnabled && !mNetworkEnabled)
			return false;

		if (mGpsEnabled)
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListenerGps);
		if (mNetworkEnabled)
			mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListenerNetwork);
		mTimer = new Timer();
		mTimer.schedule(new GetLastLocation(), 20000);
		return true;
	}

	LocationListener locationListenerGps = new LocationListener() {
		public void onLocationChanged(final Location location) {
			mTimer.cancel();
			mLocationResult.gotLocation(location);
			mLocationManager.removeUpdates(this);
			mLocationManager.removeUpdates(locationListenerNetwork);
		}
		public void onProviderDisabled(final String provider) {}
		public void onProviderEnabled(final String provider) {}
		public void onStatusChanged(final String provider, final int status, final Bundle extras) {}
	};

	LocationListener locationListenerNetwork = new LocationListener() {
		public void onLocationChanged(final Location location) {
			mTimer.cancel();
			mLocationResult.gotLocation(location);
			mLocationManager.removeUpdates(this);
			mLocationManager.removeUpdates(locationListenerGps);
		}
		public void onProviderDisabled(final String provider) {}
		public void onProviderEnabled(final String provider) {}
		public void onStatusChanged(final String provider, final int status, final Bundle extras) {}
	};

	class GetLastLocation extends TimerTask {
		@Override
		public void run() {
			mLocationManager.removeUpdates(locationListenerGps);
			mLocationManager.removeUpdates(locationListenerNetwork);

			Location net_loc  = null, gps_loc  = null;
			if (mGpsEnabled)
				gps_loc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if (mNetworkEnabled)
				net_loc = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

			//if there are both values use the latest one
			if (gps_loc != null && net_loc != null){
				if (gps_loc.getTime() > net_loc.getTime())
					mLocationResult.gotLocation(gps_loc);
				else
					mLocationResult.gotLocation(net_loc);
				return;
			}

			if (gps_loc != null) {
				mLocationResult.gotLocation(gps_loc);
				return;
			}

			if (net_loc != null) {
				mLocationResult.gotLocation(net_loc);
				return;
			}
			mLocationResult.gotLocation(null);
		}
	}

	public static abstract class LocationResult{
		public abstract void gotLocation(Location location);
	}
}
