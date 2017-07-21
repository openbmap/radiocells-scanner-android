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

import android.support.annotation.NonNull;
import android.util.Log;

import org.openbmap.Constants;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Model for wifi records
 */
public class WifiRecord extends AbstractLogEntry<WifiRecord> {

	private static final String TAG = WifiRecord.class.getSimpleName();

    public enum CatalogStatus {NEW, OPENBMAP, LOCAL}

	private String mBSsid;
	private long mBssidLong;

	private String mSsid;
	private String mCapabilities;
	private int mFrequency;
	private int mLevel;

	/**
	 * Timestamp in openbmap format: YYYYMMDDHHMMSS
	 */
	private long mOpenBmapTimestamp;

	private PositionRecord mBeginPosition;
	private PositionRecord mEndPosition;
	private long mSessionID;

	private CatalogStatus mCatalogStatus;


    /**
     * Converts string bssid to numeric representation
     * @param bssid
     * @return -1 on invalid bssid
     */
    public static final long bssid2Long(final String bssid) {
        if (bssid == null) {
            return -1;
        }

        try {
            return Long.parseLong(bssid.toLowerCase().replace(":",""), 16);
        } catch (NumberFormatException e) {
            return  -1;
        }
    }

	/**
	 * used for contains method
	 */
	public final boolean equals(@NonNull final WifiRecord aWifi) {
		if (aWifi == null) { return false;}
		return getBssid().equals(aWifi.getBssid());
	}

	/**
	 * Initialises WifisRadiocells Record without setting session id
	 */
	public WifiRecord(String bssid,
                      long bssidLong,
                      String ssid,
                      String capabilities,
                      int frequency,
                      int level,
                      long timestamp,
                      PositionRecord request,
                      PositionRecord last,
                      CatalogStatus catalogStatus) {
		this(bssid, bssidLong, ssid, capabilities, frequency, level, timestamp, request, last, Constants.SESSION_NOT_TRACKING, catalogStatus);
	}

    /**
     * Creates new wifi record - bssid long is calculated automatically
     * @param bssid
     * @param ssid
     * @param capabilities
     * @param frequency
     * @param level
     * @param timestamp
     * @param request
     * @param last
     * @param session
     * @param catalogStatus
     */
    public WifiRecord(String bssid,
                      String ssid,
                      String capabilities,
                      int frequency,
                      int level,
                      long timestamp,
                      PositionRecord request,
                      PositionRecord last,
                      int session,
                      CatalogStatus catalogStatus) {
        this(bssid, -1, ssid, capabilities, frequency, level, timestamp, request, last, session, catalogStatus);
        setBssidLong(bssid);
    }

    /**
     * Creates new wifi record - full constructor, i.e. all fields can be set explicitly
	 *  @param bssid
	 * @param bssidLong
	 * @param ssid
	 * @param capabilities
	 * @param frequency
	 * @param level
	 * @param timestamp
	 * @param request
	 * @param last
	 * @param session
	 * @param catalogStatus
	 */
	public WifiRecord(String bssid,
					  long bssidLong,
					  String ssid,
					  String capabilities,
					  int frequency,
					  int level,
					  long timestamp,
					  PositionRecord request,
					  PositionRecord last,
					  long session,
					  CatalogStatus catalogStatus) {
		setBssid(bssid);
        setBssidLong(bssidLong);
        setSsid(ssid);
        setCapabilities(capabilities);
        setFrequency(frequency);
        setLevel(level);
        setOpenBmapTimestamp(timestamp);
        setBeginPosition(request);
        setEndPosition(last);
        setSessionId(session);
        //setNew(knownWifi);
        setCatalogStatus(catalogStatus);
    }


    @Override
	public final int compareTo(final WifiRecord aWifi) {
		if (this.equals(aWifi)) {
			return 0;
		}
		return -1;
	}


	public final long getSessionId() {
		return mSessionID;
	}


	public final void setSessionId(final long session) {
		this.mSessionID = session;
	}


	public final String toString() {
		return "BSSID " + mBSsid + "/ SSID " + mSsid + " / Capabilities " + mCapabilities + " / Frq. " + mFrequency + " / Level " + mLevel;
	}


    /**
     * Returns bssid
     * Please note: bssid are always convert to UPPERCASE
     */
	public final String getBssid() {
		return mBSsid.toUpperCase();
	}

    /**
     * Sets bssid
     * Please note: bssid are always convert to UPPERCASE
     * @param bssid
     */
	public final void setBssid(final String bssid) {
		this.mBSsid = bssid.toUpperCase();
	}

    public void setBssidLong(final long bssidLong) {
        this.mBssidLong = bssidLong;
    }

    public void setBssidLong(final String bssid) {
        this.mBssidLong = bssid2Long(bssid);
    }

    public long getBssidLong() {
        return mBssidLong;
    }

	public final String getSsid() {
		return mSsid;
	}

    /**
     * Returns hashed bssid
     * Please note: hashed bssid is always convert to UPPERCASE
     */
	public final String getMd5Ssid() {
		return md5(mSsid).toUpperCase();
	}

	public final void setSsid(final String ssid) {
		this.mSsid = ssid;
	}

	public final String getCapabilities() {
		return mCapabilities;
	}

	public final void setCapabilities(final String capabilities) {
		this.mCapabilities = capabilities;
	}

	public final boolean isFree() {
		return mCapabilities.equals("[ESS]");
	}

	public final int getFrequency() {
		return mFrequency;
	}

	public final void setFrequency(final int frequency) {
		this.mFrequency = frequency;
	}

	public final int getLevel() {
		return mLevel;
	}

	public final void setLevel(final int level) {
		this.mLevel = level;
	}

	public final long getOpenBmapTimestamp() {
		return mOpenBmapTimestamp;
	}

	public final void setOpenBmapTimestamp(final long timestamp) {
		this.mOpenBmapTimestamp = timestamp;
	}

	public final PositionRecord getBeginPosition() {
		return mBeginPosition;
	}

	public final void setBeginPosition(final PositionRecord begin) {
		this.mBeginPosition = begin;
	}

	public final PositionRecord getEndPosition() {
		return mEndPosition;
	}

	public final void setEndPosition(final PositionRecord end) {
		this.mEndPosition = end;
	}

	/**
	 * Is wifi new, in openbmap wifi catalog or in local wifi catalog
	 * @param catalogStatus WifisRadiocells's status
	 */
	public void setCatalogStatus(CatalogStatus catalogStatus) {
		mCatalogStatus = catalogStatus;
	}

	public CatalogStatus getCatalogStatus() {
		return mCatalogStatus;
	}

	public int getCatalogStatusInt() {
		return mCatalogStatus.ordinal();
	}

	public static String md5(final String source) {
		try {
			// Create MD5 Hash
			MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
			digest.update(source.getBytes());
			byte[] messageDigest = digest.digest();

			// Create Hex String
			StringBuffer hexString = new StringBuffer();
			for (int i = 0; i < messageDigest.length; i++) {
				hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
			}
			return hexString.toString();

		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, e.toString(), e);
		}
		return "";
	}


}

