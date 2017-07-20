package org.openbmap.utils;
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

import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoLte;
import android.telephony.NeighboringCellInfo;
import android.telephony.gsm.GsmCellLocation;

/**
 * Various helper methods for dealing with cell ids
 */
public class CellValidator {
    /**
     * A valid gsm cell must have cell id != -1
     * Note: cells with cid > max value 0xffff are accepted (typically UMTS cells. We handle them separately)
     *
     * @param gsmIdentity {@link CellIdentityGsm}
     * @return true if valid gsm cell
     */
    private static boolean isValidGsmCell(final CellIdentityGsm gsmIdentity) {
        if (gsmIdentity == null) {
            return false;
        }

        final Integer cid = gsmIdentity.getCid();
        return (cid > 0 && cid != Integer.MAX_VALUE);
    }

    /**
     * A valid gsm cell must have cell id != -1
     * Use only in legacy mode, i.e. when telephonyManager.getAllCellInfos() isn't implemented on devices (some SAMSUNGS e.g.)
     * Note: cells with cid > max value 0xffff are accepted (typically UMTS cells. We handle them separately
     *
     * @param gsmLocation {@link GsmCellLocation}
     * @return true if valid gsm cell
     */
    @Deprecated
    private static boolean isValidGsmCell(final GsmCellLocation gsmLocation) {
        if (gsmLocation == null) {
            return false;
        }
        final Integer cid = gsmLocation.getCid();
        return (cid > 0 && cid != Integer.MAX_VALUE);
    }

    /**
     * A valid wcdma cell must have cell id set
     *
     * @param wcdmaInfo {@link CellIdentityWcdma}
     * @return true if valid cdma id
     */
    private static boolean isValidWcdmaCell(final CellIdentityWcdma wcdmaInfo) {
        if (wcdmaInfo == null) {
            return false;
        }
        final Integer cid = wcdmaInfo.getCid();
        return ((cid > -1) && (cid < Integer.MAX_VALUE));
    }

    /**
     * A valid LTE cell must have cell id set
     *
     * @param lteInfo {@link CellIdentityLte}
     * @return true if valid cdma id
     */
    private static boolean isValidLteCell(final CellIdentityLte lteInfo) {
        if (lteInfo == null) {
            return false;
        }
        final Integer cid = lteInfo.getCi();
        return ((cid > -1) && (cid < Integer.MAX_VALUE));
    }

    /**
     * A valid cdma location must have base station id, network id and system id set
     *
     * @param cdmaIdentity {@link CellInfoCdma}
     * @return true if valid cdma id
     */
    private static boolean isValidCdmaCell(final CellIdentityCdma cdmaIdentity) {
        if (cdmaIdentity == null) {
            return false;
        }
        return ((cdmaIdentity.getBasestationId() != -1) && (cdmaIdentity.getNetworkId() != -1) && (cdmaIdentity.getSystemId() != -1));
    }

    /**
     * Tests if given cell is a valid neigbor cell
     * Check is required as some modems only return dummy values for neighboring cells
     * <p>
     * Note: PSC is not checked, as PSC may be -1 on GSM networks
     * see https://developer.android.com/reference/android/telephony/gsm/GsmCellLocation.html#getPsc()
     *
     * @param cell Neighbor cell
     * @return true if cell has a valid cell id and lac
     */
    public static boolean isValidNeigbor(final NeighboringCellInfo cell) {
        if (cell == null) {
            return false;
        }
        return (
                (cell.getCid() != NeighboringCellInfo.UNKNOWN_CID && cell.getCid() < 0xffff) &&
                        (cell.getLac() != NeighboringCellInfo.UNKNOWN_CID && cell.getLac() < 0xffff));
    }

    public static boolean isValidCell(Object cell) {
        if (cell instanceof CellIdentityGsm) {
            return isValidGsmCell((CellIdentityGsm) cell);
        } else if (cell instanceof CellIdentityWcdma) {
            return isValidWcdmaCell((CellIdentityWcdma) cell);
        } else if (cell instanceof CellInfoCdma) {
            return isValidCdmaCell((CellIdentityCdma) cell);
        } else if (cell instanceof CellInfoLte) {
            return isValidLteCell((CellIdentityLte) cell);
        } else if (cell instanceof GsmCellLocation) {
            return isValidGsmCell((GsmCellLocation) cell);
        } else {
            throw new IllegalArgumentException("Unknown cell object type");
        }
    }
    }