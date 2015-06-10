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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.openbmap.RadioBeacon;

import android.annotation.SuppressLint;
import android.os.Build;
import android.telephony.TelephonyManager;

/**
 * Model for gsm, umts and cdma records
 */
public class CellRecord extends AbstractLogEntry<CellRecord> {

	/**
	 * GSM cell id; UMTS and other UTRAN networks LCID; -1 if unknown
	 * Please note: may contain values > 0xffff (max legal value) when in UTRAN network 
	 */
	private int mLogicalCellId;
	
	/**
	 * For GSM networks equals cellid. In UMTS and other UTRAN networks it's the actual cell id (as opposed to lcid).
	 */
	private int mActualCellId;
	
	/**
	 * Radio network controller id in UMTS and other UTRAN networks
	 * see http://en.wikipedia.org/wiki/Radio_Network_Controller
	 */
	private int mUtranRncId;
	
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

	/**
	 * Cell network type as defined by android.telephony.TelephonyManager
	 */
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

	/**
	 * Signal strength in Asu
	 * see e.g. http://www.lte-anbieter.info/technik/asu.php
	 */
	private int	mStrengthAsu;
	
	/**
	 * Timestamp in openbmap format: YYYYMMDDHHMMSS
	 */
	private long mOpenBmapTimestamp;

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

		setLogicalCellId(-1);
		setLac(-1);
		setUtranRnc(-1);
		setActualCid(-1);
		setPsc(-1);
		setSystemId("-1");
		setNetworkId("-1");
		setBaseId("-1");
		setNetworkType(TelephonyManager.NETWORK_TYPE_UNKNOWN);
		
	}

	/**
	 * used for contains method
	 */
	public final boolean equals(final Object aCell) {
		CellRecord oneCell = (CellRecord) aCell;

		return (getMcc().equals(oneCell.getMcc()))
				&& (getMnc().equals(oneCell.getMnc()))
				&& (getLac() == oneCell.getLac())
				&& (getLogicalCellId() == oneCell.getLogicalCellId());
	}

	public final int getSessionId() {
		return mSessionId;
	}

	public final void setSessionId(final int session) {
		this.mSessionId = session;
	}

	@Override
	public final String toString() {
		return "Cell " + mLogicalCellId + " / Operator " + mOperatorName + ", " + mOperator + " / MCC " + mMcc + " / MNC " +  mMnc 
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

	/**
	 * Returns cell id as returned by android.telephony.gsmlocation.getCid(). Be careful: by default this is the LCID)
	 * @return
	 */
	public final int getLogicalCellId() {
		return mLogicalCellId;
	}
	
	/**
	 * Sets cell id (as returned by android.telephony.gsmlocation.getCid(). Be careful: by default this is the LCID)
	 * @param logicalCellId GSM cell id; UMTS and other UTRAN networks LCID; -1 if unknown, 0xffff max legal value
	 */
	public final void setLogicalCellId(final int logicalCellId) {
		this.mLogicalCellId = logicalCellId;
	}
	
	/**
	 * For GSM networks equals cell id; for UMTS and other UTRAN networks this is the actual cell id (as opposed to lcid)
	 * @param cid
	 */
	public final int getActualCellId() {
		return mActualCellId;
	}
	
	/**
	 * On GSM networks equals cell id;
	 * On UMTS and other UTRAN networks this is the actual cell id (as opposed to lcid)
	 * On LTE this is the physical cell id
	 * 
	 * @param actualCellId
	 */
	public final void setActualCid(final int actualCellId) {
		this.mActualCellId = actualCellId;
	}

	public final int getUtranRnc() {
		return mUtranRncId;
	}
	
	public final void setUtranRnc(final int utranRncId) {
		this.mUtranRncId = utranRncId;
	}
	/**
	 * Gets location area code
	 * @return location area code
	 */
	public final int getLac() {
		return mLac;
	}

	/**
	 * Sets location area code
	 * @param lac 
	 * 		On GSM networks this is set to location area code
	 *		On LTE networks this is set to TAC (tracking area)
	 *  	-1 if unknown,
	 *		0xffff max legal value
	 */
	public final void setLac(final int lac) {
		this.mLac = lac;
	}

	public final int getPsc() {
		return mPsc;
	}

	/**
	 * Sets primary scrambling code for UMTS
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

	public final int getStrengthAsu() {
		return mStrengthAsu;
	}

	public final void setStrengthAsu(final int strengthAsu) {
		this.mStrengthAsu = strengthAsu;
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

	public final long getOpenBmapTimestamp() {
		return mOpenBmapTimestamp;
	}

	public final void setOpenBmapTimestamp(final long timestamp) {
		this.mOpenBmapTimestamp = timestamp;
	}

	/**
	 * @see http://sourceforge.net/apps/mediawiki/myposition/index.php?title=Log_format
	 * @return 
	 */
	@SuppressLint("InlinedApi")
	public static Map<Integer, String> TECHNOLOGY_MAP() {
		Map<Integer, String> result = new HashMap<Integer, String>();
		result.put(TelephonyManager.NETWORK_TYPE_UNKNOWN, "NA");
		// GPRS shall be mapped to "GSM"
		result.put(TelephonyManager.NETWORK_TYPE_GPRS, "GSM");
		result.put(TelephonyManager.NETWORK_TYPE_EDGE, "EDGE");
		result.put(TelephonyManager.NETWORK_TYPE_UMTS, "UMTS");
		result.put(TelephonyManager.NETWORK_TYPE_CDMA, "CDMA");
		result.put(TelephonyManager.NETWORK_TYPE_EVDO_0, "EDVO_0");
		result.put(TelephonyManager.NETWORK_TYPE_EVDO_A, "EDVO_A");
		result.put(TelephonyManager.NETWORK_TYPE_1xRTT, "1xRTT");
		
		result.put(TelephonyManager.NETWORK_TYPE_HSDPA, "HSDPA");
		result.put(TelephonyManager.NETWORK_TYPE_HSUPA, "HSUPA");
		result.put(TelephonyManager.NETWORK_TYPE_HSPA, "HSPA");
		result.put(TelephonyManager.NETWORK_TYPE_IDEN, "IDEN"); 

		// add new network types not available in all revisions
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			result.put(TelephonyManager.NETWORK_TYPE_EVDO_B, "EDV0_B");
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			result.put(TelephonyManager.NETWORK_TYPE_LTE, "LTE");
			result.put(TelephonyManager.NETWORK_TYPE_EHRPD, "eHRPD");
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			result.put(TelephonyManager.NETWORK_TYPE_HSPAP, "HSPA+");
		}
		
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

}
