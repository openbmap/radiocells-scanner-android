package org.openbmap.db.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import org.openbmap.RadioBeacon;


public class WifiRecord extends AbstractLogEntry<WifiRecord> {
	

	@SuppressWarnings("unused")
	private static final String TAG = WifiRecord.class.getSimpleName();
	
	private String mBSsid;
	private String mSsid;
	private String mCapabilities;
	private int mFrequency;
	private int mLevel;
	private long mTimestamp;
	private boolean mIsNew;

	private PositionRecord mBeginPosition;
	private PositionRecord mEndPosition;
	private int mSessionID;

	/**
	 * used for contains method
	 */
	public final boolean equals(final Object aWifi) {
		WifiRecord oneWifi = (WifiRecord) aWifi;

		return getBssid().equals(oneWifi.getBssid());
	}

	public WifiRecord() {

	}

	/**
	 * Initialises Wifi Record without setting session id
	 * 
	 * @param bssid
	 * @param ssid
	 * @param capabilities
	 * @param frequency
	 * @param level
	 * @param timestamp
	 * @param request
	 * @param last
	 */
	public WifiRecord(String bssid, String ssid, String capabilities, int frequency, int level, long timestamp, PositionRecord request, PositionRecord last)
	{
		this(bssid, ssid, capabilities, frequency, level, timestamp, request, last, RadioBeacon.SESSION_NOT_TRACKING);
	}

	public WifiRecord(String bssid, String ssid, String capabilities, int frequency, int level, long timestamp, PositionRecord request, PositionRecord last, int session)
	{
		setBssid(bssid);
		setSsid(ssid);
		setCapabilities(capabilities);
		setFrequency(frequency);
		setLevel(level);
		setTimestamp(timestamp);
		setBeginPosition(request);
		setEndPosition(last);
		setSessionId(session);
	}

	@Override
	public final int compareTo(final WifiRecord aWifi) {
		if (this.equals(aWifi)) {
			return 0;
		}
		return -1;
	}


	public final int getSessionId() {
		return mSessionID;
	}


	public final void setSessionId(final int session) {
		this.mSessionID = session;
	}


	public final String toString() {
		return "BSSID " + mBSsid + "/ SSID " + mSsid + " / Capabilities " + mCapabilities + " / Frq. " + mFrequency + " / Level " + mLevel;
	}

	public final String getBssid() {
		return mBSsid;
	}

	public final void setBssid(final String bssid) {
		this.mBSsid = bssid;
	}

	public final String getSsid() {
		return mSsid;
	}

	public final String getMd5Ssid() {
		return md5(mSsid);
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

	public final long getTimestamp() {
		return mTimestamp;
	}

	public final void setTimestamp(final long timestamp) {
		this.mTimestamp = timestamp;
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

	public final boolean isNew() {
		return mIsNew;
	}

	public final void setNew(final boolean isNew) {
		this.mIsNew = isNew;
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
			e.printStackTrace();  
		}  
		return "";  
	}  

}

