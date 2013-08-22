package org.openbmap.db.model;

import java.text.SimpleDateFormat;
import java.util.Locale;

import org.openbmap.RadioBeacon;

import android.location.Location;

public class PositionRecord extends AbstractLogEntry<PositionRecord> {

	private double mLatitude;
	private double mLongitude;
	private double mAltitude;
	private double mAccuracy;
	private long mTimestamp;
	private double mBearing;
	private double mSpeed;
	private int	mSession;
	private String	mSource;

	/**
	 * Creates position record from Android Location.
	 * @param mLocation
	 */
	public PositionRecord(final Location loc, final int session, final String source) {
		setLatitude(loc.getLatitude());
		setLongitude(loc.getLongitude());
		setAltitude(loc.getAltitude());
		setAccuracy(loc.getAccuracy());
		setTimestamp(loc.getTime());
		setBearing(loc.getBearing());
		setSpeed(loc.getSpeed());
		setSession(session);
		setSource(source);
	}

	/**
	 * 
	 */
	public PositionRecord() {
		// set default values
		setSession(RadioBeacon.SESSION_NOT_TRACKING);
		setSource("N/A");
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


	public final long getTimestamp() {
		return mTimestamp;
	}


	/**
	 * Sets timestamp. The timestamp is converted from UTC to human readable format here,
	 * i.e. YYYYMMDDHHMMSS
	 * @param timestamp Timestamp in UTC, i.e. in milliseconds since January 1, 1970. 
	 */
	public final void setTimestamp(final long timestamp) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
		this.mTimestamp = Long.valueOf(formatter.format(timestamp));
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
	
	public final String toString() {
		return "Lat " + getLatitude() + " / Lon " + getLongitude() + " / Alt " + getAltitude() + " / Acc " + getAccuracy();
	}

	public final boolean equals(final PositionRecord aCell) {
		PositionRecord oneCell = aCell;

		return (getLatitude() == oneCell.getLatitude())
				&& (getLongitude() == oneCell.getLongitude())
				&& (getAltitude() == oneCell.getAltitude())
				&& (getAccuracy() == oneCell.getAccuracy())
				&& (getTimestamp() == oneCell.getTimestamp())
				&& (getSession() == oneCell.getSession());
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
		result += result * PRIME + (int) (getTimestamp() ^ (getTimestamp() >>> 32));
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
