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

import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

/**
 * Helper for getting current location.
 * All kudos to http://stackoverflow.com/questions/3145089/what-is-the-simplest-and-most-robust-way-to-get-the-users-current-location-in-a
 *
 */
public class CurrentLocationHelper {
	private Timer mTimer;
	private LocationManager lmgr;
	private LocationResult locationResult;

	private boolean gps_enabled  = false;
	private boolean network_enabled  = false;

	public boolean getLocation(Context context, LocationResult result) {
		//I use LocationResult callback class to pass location value from CurrentLocationHelper to user code.
		locationResult = result;
		if (lmgr ==  null)
			lmgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

		//exceptions will be thrown if provider is not permitted.
		try {
			gps_enabled  = lmgr.isProviderEnabled(LocationManager.GPS_PROVIDER);
		} catch (Exception ex) {

		}

		try {
			network_enabled = lmgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
		} catch(Exception ex){

		}

		//don't start listeners if no provider is enabled
		if (!gps_enabled && !network_enabled)
			return false;

		if (gps_enabled)
			lmgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListenerGps);
		if (network_enabled)
			lmgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListenerNetwork);
		mTimer = new Timer();
		mTimer.schedule(new GetLastLocation(), 20000);
		return true;
	}

	LocationListener locationListenerGps = new LocationListener() {
		public void onLocationChanged(Location location) {
			mTimer.cancel();
			locationResult.gotLocation(location);
			lmgr.removeUpdates(this);
			lmgr.removeUpdates(locationListenerNetwork);
		}
		public void onProviderDisabled(String provider) {}
		public void onProviderEnabled(String provider) {}
		public void onStatusChanged(String provider, int status, Bundle extras) {}
	};

	LocationListener locationListenerNetwork = new LocationListener() {
		public void onLocationChanged(Location location) {
			mTimer.cancel();
			locationResult.gotLocation(location);
			lmgr.removeUpdates(this);
			lmgr.removeUpdates(locationListenerGps);
		}
		public void onProviderDisabled(String provider) {}
		public void onProviderEnabled(String provider) {}
		public void onStatusChanged(String provider, int status, Bundle extras) {}
	};

	class GetLastLocation extends TimerTask {
		@Override
		public void run() {
			lmgr.removeUpdates(locationListenerGps);
			lmgr.removeUpdates(locationListenerNetwork);

			Location net_loc  = null, gps_loc  = null;
			if (gps_enabled)
				gps_loc = lmgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if (network_enabled)
				net_loc = lmgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

			//if there are both values use the latest one
			if (gps_loc != null && net_loc != null){
				if (gps_loc.getTime()>net_loc.getTime())
					locationResult.gotLocation(gps_loc);
				else
					locationResult.gotLocation(net_loc);
				return;
			}

			if (gps_loc != null) {
				locationResult.gotLocation(gps_loc);
				return;
			}
			
			if (net_loc != null) {
				locationResult.gotLocation(net_loc);
				return;
			}
			locationResult.gotLocation(null);
		}
	}

	public static abstract class LocationResult{
		public abstract void gotLocation(Location location);
	}
}
