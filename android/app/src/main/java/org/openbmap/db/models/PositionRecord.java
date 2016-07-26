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

package org.openbmap.db.models;

import android.location.Location;

import org.openbmap.RadioBeacon;
import org.openbmap.utils.GeometryUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Model position record
 */
public class PositionRecord extends AbstractLogEntry<PositionRecord> {

	private double mLatitude = 0.0;
	private double mLongitude = 0.0;
	private double mAltitude = 0.0;
	private double mAccuracy = 0.0;

	/**
	 * Timestamp in openbmap format: YYYYMMDDHHMMSS
	 */
	private long mOpenBmapTimestamp;

	/**
	 * Timestamp in millis
	 */
	private long mMillisTimestamp = 0;

	private double mBearing = 0.0;
	private double mSpeed = 0.0;
	private int	mSession = RadioBeacon.SESSION_NOT_TRACKING;
	private String mSource = RadioBeacon.PROVIDER_NONE;
	private boolean mIsWaypoint = false;

	/**
	 * Creates position record from Android Location.
	 */
	public PositionRecord(final Location location, final int session, final String source) {
		if (!GeometryUtils.isValidLocation(location)) {
			throw new IllegalArgumentException("Invalid location " + location.toString());
		}
		setLatitude(location.getLatitude());
		setLongitude(location.getLongitude());
		setAltitude(location.getAltitude());
		setAccuracy(location.getAccuracy());
		setTimestampByMillis(location.getTime());
		setBearing(location.getBearing());
		setSpeed(location.getSpeed());
		setSession(session);
		setSource(source);
		setIsWaypoint(false);
	}

	public PositionRecord(){

	}

	/**
	 * @param location
	 * @param session Session Id
	 * @param source Provider used
	 * @param isWaypoint Point is a waypoint
	 */
	public PositionRecord(final Location location, final int session, final String source, final boolean isWaypoint) {
		// don't use strict mode here:_waypoints may lack certain properties!
		if (!GeometryUtils.isValidLocation(location, false)) {
			throw new IllegalArgumentException("Invalid location " + location.toString());
		}
		setLatitude(location.getLatitude());
		setLongitude(location.getLongitude());
		setAltitude(location.getAltitude());
		setAccuracy(location.getAccuracy());
		setTimestampByMillis(location.getTime());
		setBearing(location.getBearing());
		setSpeed(location.getSpeed());
		setSession(session);
		setSource(source);
		setIsWaypoint(isWaypoint);
	}

	public final double getLatitude() {
		return mLatitude;
	}

	public final void setLatitude(final double latitude) {
		this.mLatitude = latitude;
	}

	public final double getLongitude() {
		return mLongitude;
	}

	public final void setLongitude(final double longitude) {
		this.mLongitude = longitude;
	}

	public final double getAltitude() {
		return mAltitude;
	}

	public final void setAltitude(final double altitude) {
		this.mAltitude = altitude;
	}

	public final double getAccuracy() {
		return mAccuracy;
	}

	public final void setAccuracy(final double accuracy) {
		this.mAccuracy = accuracy;
	}

	/**
	 * Gets time stamp in OpenBmap format: YYYYMMDDHHMMSS
	 * @return time stamp
	 */
	public final long getOpenBmapTimestamp() {
		return mOpenBmapTimestamp;
	}

	/**
	 * Gets time stamp in Millis
	 * @return time stamp
	 */
	public final long getMillisTimestamp() {
		return mMillisTimestamp;
	}

	/**
	 * Sets time stamp. The time stamp is converted from UTC to human readable format here, i.e. YYYYMMDDHHMMSS <p>
	 * The conversion itself is considered bad. It's considered as good practice to store time stamp UTC system mills,
	 * but this conversion is necessary for historical reasons and the fact that the openbmap server expects
	 * time stamps in YYYYMMDDHHMMSS format <p>
	 * If you change something here, don't forget reworking the gpx exporter
	 * @param millis Time stamp in UTC, i.e. in milliseconds since January 1, 1970.
	 */
	public final void setTimestampByMillis(final long millis) {
		this.mMillisTimestamp = millis;
		final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
		this.mOpenBmapTimestamp = Long.valueOf(formatter.format(millis));
	}

	 /**
	 * Sets time stamp
	 *  @param openbmap time stamp in OpenBmap format YYYYMMDDHHMMSS
	 */
	public final void setTimestampByOpenbmap(final long openbmap) {
		this.mOpenBmapTimestamp = openbmap;
		final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
		try {
			final Date converted =  formatter.parse(String.valueOf(openbmap));
			this.mMillisTimestamp = converted.getTime();
		} catch (ParseException e) {
			this.mMillisTimestamp = 0;
		}
	}

	public final double getBearing() {
		return mBearing;
	}

	public final void setBearing(final double bearing) {
		this.mBearing = bearing;
	}

	public final double getSpeed() {
		return mSpeed;
	}

	public final void setSpeed(final double speed) {
		this.mSpeed = speed;
	}

	public final int getSession() {
		return mSession;
	}

	public final void setSession(final int session) {
		this.mSession = session;
	}

	public final String getSource() {
		return mSource;
	}

	public final void setSource(final String source) {
		this.mSource = source;
	}

	/**
	 * @param isWaypoint
	 */
	private void setIsWaypoint(boolean isWaypoint) {
		mIsWaypoint = isWaypoint;
	}

	private boolean isWaypoint() {
		return mIsWaypoint;
	}

	public final String toString() {
		return "Lat " + getLatitude() + " / Lon " + getLongitude() + " / Alt " + getAltitude() + " / Acc " + getAccuracy();
	}

	public final boolean equals(final PositionRecord aCell) {

		return (getLatitude() == aCell.getLatitude())
				&& (getLongitude() == aCell.getLongitude())
				&& (getAltitude() == aCell.getAltitude())
				&& (getAccuracy() == aCell.getAccuracy())
				&& (getOpenBmapTimestamp() == aCell.getOpenBmapTimestamp())
				&& (getSession() == aCell.getSession());
	}

	/**
	 * taken from http://stackoverflow.com/questions/113511/hash-code-implementation
	 */
	public final int hashCode() {
		final int PRIME = 37;
		final int SEED = 5;
		int result = SEED;
		result += result * PRIME + Double.doubleToLongBits(getLatitude());
		result += result * PRIME + Double.doubleToLongBits(getLongitude());
		result += result * PRIME + Double.doubleToLongBits(getAltitude());
		result += result * PRIME + Double.doubleToLongBits(getAccuracy());
		result += result * PRIME + (int) (getOpenBmapTimestamp() ^ (getOpenBmapTimestamp() >>> 32));
		result += result * PRIME + getSession();
		return result;
	}

	@Override
	public final int compareTo(final PositionRecord aCell) {
		if (this.equals(aCell)) {
			return 0;
		}
		return -1;
	}

}
