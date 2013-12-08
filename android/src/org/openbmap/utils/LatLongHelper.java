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

import org.mapsforge.core.model.LatLong;

import android.location.Location;
import android.util.Log;

/**
 * Location helpers
 */
public final class LatLongHelper {

	private static final String	TAG	= LatLongHelper.class.getSimpleName();
	
	/**
	 * Maximum altitude (in meter), used for checking gps integrity
	 */
	public static final double	MAX_ALTITUDE	= 10000;

	/**
	 * Minimum altitude (in meter), used for checking gps integrity
	 */
	public static final double	MIN_ALTITUDE	= -500;

	/**
	 * Minimum speed (in meter/second !!!), used for checking gps integrity
	 */
	public static final float	MIN_SPEED	= 0;

	/**
	 * Maximum speed (in meter/second !!!), used for checking gps integrity
	 */
	public static final double	MAX_SPEED	= 100; // == 360 km/h

	/**
	 *  Minimum timestamp (in millis), used for checking gps integrity
	 */
	public static final long	MIN_TIMESTAMP	= 1325372400; // == 01.01.2012 00:00:00 o'clock

	/**
	 * Millis per day
	 */
	public static final int	MILLIS_PER_DAY	= 86400000;

	/**
	 * Converts LatLong to Location
	 * @throws IllegalArgumentException on invalid location
	 * @param latlon
	 * @return Corresponding Location
	 */
	public static Location toLocation(final LatLong latlon) {
		Location result = new Location("DUMMY");
		result.setLatitude(latlon.latitude);
		result.setLongitude(latlon.longitude);
		if (!isValidLocation(result)) {
			throw new IllegalArgumentException("Invalid location");
		}
		return result;
	}

	/**
	 * Converts Location to LatLong
	 * @throws IllegalArgumentException on invalid location
	 * @param location 
	 * @return Corresponding LatLong
	 */
	public static LatLong toLatLong(final Location location) {
		if (!isValidLocation(location)) {
			throw new IllegalArgumentException("Invalid location");
		}
		return new LatLong(location.getLatitude(), location.getLongitude());
	}

	/**
	 * Tests if location is not null, no default values and plausible speed, time and height values
	 * or has implausible values
	 * @param test location to test
	 * @return true, if valid location
	 */
	public static boolean isValidLocation(final Location test) {
		return isValidLocation(test, true);
	}

	/**
	 * Tests if location is valid.
	 * @param test location to test
	 * @param strictMode if strictMode = false only null and default values are checked, but no speed, time and height
	 * @return true, if valid location
	 */
	public static boolean isValidLocation(final Location test, final boolean strictMode) {
		// check the necessary components first
		if (test == null) {
			Log.w(TAG, "Invalid location: Location is null");
			return false;
		}

		if (test.getLatitude() == 0 && test.getLongitude() == 0) {
			Log.w(TAG, "Invalid location: only default values provided");
			return false;	
		}

		if (test.getLongitude() > 180 || test.getLongitude() < -180) {
			Log.w(TAG, "Invalid longitude: " + test.getLongitude());
			return false;
		}

		if (test.getLatitude() > 90 || test.getLatitude() < -90) {
			Log.w(TAG, "Invalid latitude: " + test.getLatitude());
			return false;
		}

		// now we can also check optional parameters (not available on every device)
		if (strictMode) {
			final long tomorrow = System.currentTimeMillis() + MILLIS_PER_DAY;
			if (test.getTime() < MIN_TIMESTAMP || test.getTime() > tomorrow) {
				Log.w(TAG, "Invalid timestamp: either to old or more than one day in the future");
				return false;
			}
			
			if ((test.hasAltitude()) && (test.getAltitude() < MIN_ALTITUDE || test.getAltitude() > MAX_ALTITUDE)) {
				Log.w(TAG, "Altitude out-of-range [" + MIN_ALTITUDE + ".." + MAX_ALTITUDE + "]:" + test.getAltitude());
				return false;
			}

			if ((test.hasSpeed()) && (test.getSpeed() < MIN_SPEED || test.getSpeed() > MAX_SPEED)) {
				Log.w(TAG, "Speed out-of-range [" + MIN_SPEED + ".." + MAX_SPEED + "]:" + test.getSpeed());
				return false;
			}
		}
		return true;
	}

	private LatLongHelper() {

	}
}
