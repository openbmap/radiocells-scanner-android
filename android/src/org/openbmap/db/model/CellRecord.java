package org.openbmap.db.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.openbmap.RadioBeacon;

import android.telephony.TelephonyManager;

public class CellRecord extends AbstractLogEntry<CellRecord> {

	/**
	 * GSM cell id, -1 if unknown, 0xffff max legal value 
	 */
	private int mCid;

	/**
	 * Alphabetic name of current registered operator. 
	 */
	private String mOperatorName;

	/**
	 * Numeric name (MCC+MNC) of current registered operator
	 */
	private String mOperator;

	/**
	 * Mobile Country Code
	 */
	private String mMcc;

	/**
	 *  Mobile network code
	 */
	private String mMnc;

	/**
	 * Base id in CDMA mode
	 */
	private String mBaseId;

	/**
	 * Network id in CDMA mode
	 */
	private String mNetworkId;

	/**
	 * System id in CDMA mode
	 */
	private String mSystemId;



	private int mNetworkType;

	/**
	 * GSM location area code, -1 if unknown, 0xffff max legal value 
	 */
	private int mLac;

	/**
	 * Primary scrambling code, -1 if in GSM mode
	 */
	private int mPsc;

	/**
	 * Signal strength in dBm
	 */
	private int mStrengthdBm;

	private long mTimestamp;

	private boolean mIsCdma;

	private boolean mIsServing;

	private boolean mIsNeighbor;

	private PositionRecord mBeginPosition;

	private PositionRecord mEndPosition;

	private int mSessionId;

	public CellRecord() {
		this(RadioBeacon.SESSION_NOT_TRACKING);
	}

	public CellRecord(final int session) {
		setSessionId(session);

		setCid(-1);
		setLac(-1);
		setNetworkType(TelephonyManager.NETWORK_TYPE_UNKNOWN);
		setPsc(-1);
	}

	/**
	 * used for contains method
	 */
	public final boolean equals(final Object aCell) {
		CellRecord oneCell = (CellRecord) aCell;

		return (getMcc().equals(oneCell.getMcc()))
				&& (getMnc().equals(oneCell.getMnc()))
				&& (getLac() == oneCell.getLac())
				&& (getCid() == oneCell.getCid());
	}

	public final int getSessionId() {
		return mSessionId;
	}

	public final void setSessionId(final int session) {
		this.mSessionId = session;
	}

	@Override
	public final String toString() {
		return "Cell " + mCid + " / Operator " + mOperatorName + ", " + mOperator + " / MCC " + mMcc + " / MNC " +  mMnc 
				+ " / Type " + mNetworkType + " / LAC " + mLac + " / PSc " + mPsc + " / dbm " + mStrengthdBm + " / Session Id " + mSessionId;
	}

	@Override
	public final int compareTo(final CellRecord aCell) {
		if (this.equals(aCell)) {
			return 0;
		}
		return -1;
	}

	public final String getOperatorName() {
		return mOperatorName;
	}

	public final void setOperatorName(final String operatorName) {
		this.mOperatorName = operatorName;
	}

	public final String getOperator() {
		return mOperator;
	}

	public final void setOperator(final String operator) {
		this.mOperator = operator;
	}

	public final String getMcc() {
		return mMcc;
	}

	public final void setMcc(final String mcc) {
		this.mMcc = mcc;
	}

	public final String getMnc() {
		return mMnc;
	}

	public final void setMnc(final String mnc) {
		this.mMnc = mnc;
	}

	public final int getNetworkType() {
		return mNetworkType;
	}

	public final void setNetworkType(final int networkType) {
		this.mNetworkType = networkType;
	}

	public final int getCid() {
		return mCid;
	}

	/**
	 * sets cell id
	 * @param cid gsm cell id, -1 if unknown, 0xffff max legal value
	 */
	public final void setCid(final int cid) {
		this.mCid = cid;
	}

	public final int getLac() {
		return mLac;
	}

	/**
	 * set location area code
	 * @param lac gsm gsm location area code, -1 if unknown, 0xffff max legal value
	 */
	public final void setLac(final int lac) {
		this.mLac = lac;
	}

	public final int getPsc() {
		return mPsc;
	}

	/**
	 * set primary scrambling code for UMTS
	 * @param psc primary scrambling code for UMTS, -1 if unknown or GSM
	 */
	public final void setPsc(final int psc) {
		this.mPsc = psc;
	}

	public final int getStrengthdBm() {
		return mStrengthdBm;
	}

	public final void setStrengthdBm(final int strengthdBm) {
		this.mStrengthdBm = strengthdBm;
	}

	public final boolean isCdma() {
		return mIsCdma;
	}

	public final void setIsCdma(final boolean isCdma) {
		this.mIsCdma = isCdma;
	}

	public final boolean isServing() {
		return mIsServing;
	}

	public final void setIsServing(final boolean isServing) {
		this.mIsServing = isServing;
	}

	public final boolean isNeighbor() {
		return mIsNeighbor;
	}

	public final void setIsNeighbor(final boolean isNeighbor) {
		this.mIsNeighbor = isNeighbor;
	}

	public final PositionRecord getBeginPosition() {
		return mBeginPosition;
	}

	public final void setBeginPosition(final PositionRecord position) {
		this.mBeginPosition = position;
	}
	
	public final PositionRecord getEndPosition() {
		return mEndPosition;
	}

	public final void setEndPosition(final PositionRecord position) {
		this.mEndPosition = position;
	}

	public final long getTimestamp() {
		return mTimestamp;
	}

	public final void setTimestamp(final long timestamp) {
		this.mTimestamp = timestamp;
	}

	/**
	 * @see http://sourceforge.net/apps/mediawiki/myposition/index.php?title=Log_format
	 * @return 
	 */
	@Deprecated
	public static Map<Integer, String> NETWORKTYPE_MAP() {
		Map<Integer, String> result = new HashMap<Integer, String>();
		result.put(TelephonyManager.NETWORK_TYPE_UNKNOWN, "NA");
		// GPRS shall be mapped to "GSM"
		result.put(TelephonyManager.NETWORK_TYPE_GPRS, "GSM");
		result.put(TelephonyManager.NETWORK_TYPE_EDGE, "EDGE");
		result.put(TelephonyManager.NETWORK_TYPE_UMTS, "UMTS");
		// TODO: original openbmap client had comment "4 TelephonyManager.NETWORK_TYPE_HSDPA not in API"
		// actually 4 as used in original openbmap client is actually TelephonyManager.NETWORK_TYPE_CDMA
		// check whether this has to be changed
		result.put(TelephonyManager.NETWORK_TYPE_CDMA, "CDMA");
		result.put(TelephonyManager.NETWORK_TYPE_1xRTT, "1xRTT");
		result.put(TelephonyManager.NETWORK_TYPE_HSDPA, "HSDPA");
		result.put(TelephonyManager.NETWORK_TYPE_HSUPA, "HSUPA");

		//result.put(TelephonyManager.NETWORK_TYPE_EHRPD, "eHRPD");
		//result.put(TelephonyManager.NETWORK_TYPE_HSPAP, "HSPA+"); 
		//result.put(TelephonyManager.NETWORK_TYPE_LTE, "LTE");

		result.put(TelephonyManager.NETWORK_TYPE_EVDO_0, "EDV0_0");
		result.put(TelephonyManager.NETWORK_TYPE_EVDO_A, "EDV0_A");
		result.put(TelephonyManager.NETWORK_TYPE_EVDO_B, "EDV0_B");
		result.put(TelephonyManager.NETWORK_TYPE_HSPA, "HSPA");
		result.put(TelephonyManager.NETWORK_TYPE_IDEN, "IDEN"); 

		return Collections.unmodifiableMap(result);

	}

	public final String getBaseId() {
		return mBaseId;
	}

	public final void setBaseId(final String baseId) {
		this.mBaseId = baseId;
	}

	public final String getNetworkId() {
		return mNetworkId;
	}

	public final void setNetworkId(final String networkId) {
		this.mNetworkId = networkId;
	}

	public final String getSystemId() {
		return mSystemId;
	}

	public final void setSystemId(final String systemId) {
		this.mSystemId = systemId;
	}

	public static final Map<Integer, String> NetworkTypeDescription = new HashMap<Integer, String>() {
		private static final long	serialVersionUID	= 1L;
	{
		put(TelephonyManager.NETWORK_TYPE_GPRS, "GSM");
		// GPRS shall be mapped to "GSM"
		put(TelephonyManager.NETWORK_TYPE_GPRS, "GSM");
		put(TelephonyManager.NETWORK_TYPE_EDGE, "EDGE");
		put(TelephonyManager.NETWORK_TYPE_UMTS, "UMTS");
		// TODO: original openbmap client had comment "4 TelephonyManager.NETWORK_TYPE_HSDPA not in API"
		// actually 4 as used in original openbmap client is actually TelephonyManager.NETWORK_TYPE_CDMA
		// check whether this has to be changed
		put(TelephonyManager.NETWORK_TYPE_CDMA, "CDMA");
		put(TelephonyManager.NETWORK_TYPE_1xRTT, "1xRTT");
		put(TelephonyManager.NETWORK_TYPE_HSDPA, "HSDPA");
		put(TelephonyManager.NETWORK_TYPE_HSUPA, "HSUPA");

		//result.put(TelephonyManager.NETWORK_TYPE_EHRPD, "eHRPD");
		//result.put(TelephonyManager.NETWORK_TYPE_HSPAP, "HSPA+"); 
		//result.put(TelephonyManager.NETWORK_TYPE_LTE, "LTE");

		put(TelephonyManager.NETWORK_TYPE_EVDO_0, "EDV0_0");
		put(TelephonyManager.NETWORK_TYPE_EVDO_A, "EDV0_A");
		put(TelephonyManager.NETWORK_TYPE_EVDO_B, "EDV0_B");
		put(TelephonyManager.NETWORK_TYPE_HSPA, "HSPA");
		put(TelephonyManager.NETWORK_TYPE_IDEN, "IDEN"); 
	} };

}
