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

package org.openbmap.events;

import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import org.openbmap.db.models.CellRecord;

public class onCellChanged {

    public static final String TAG = onCellChanged.class.getSimpleName();

    /**
     * Cell Id; may be null
     */
    public String cellId = null;

    public String technology = null;

    public onCellChanged(final Object info, final int tech) {
        CellRecord cell = new CellRecord();
        // Identity cell infos
        if (info instanceof CellInfoGsm) {
            cell.fromGsmIdentiy(((CellInfoGsm)info).getCellIdentity());
            cellId = String.valueOf(cell.getLogicalCellId());
            technology = CellRecord.TECHNOLOGY_MAP().get(tech);
        } else if (info instanceof CellInfoWcdma) {
            cell.fromWcdmaIdentity(((CellInfoWcdma)info).getCellIdentity());
            cellId = String.valueOf(cell.getLogicalCellId());
            technology = CellRecord.TECHNOLOGY_MAP().get(tech);
        } else if (info instanceof CellInfoLte) {
            cell.fromLteIdentity(((CellInfoLte)info).getCellIdentity());
            cellId = String.valueOf(cell.getLogicalCellId());
            technology = CellRecord.TECHNOLOGY_MAP().get(tech);
        } else if (info instanceof CellInfoCdma) {
            cell.fromCdmaIdentity(((CellInfoCdma)info).getCellIdentity());
            cellId = String.valueOf(cell.getSystemId() + "/" + cell.getNetworkId() + "/" + cell.getBaseId());
            technology = CellRecord.TECHNOLOGY_MAP().get(tech);
        } else if (info instanceof GsmCellLocation) {
            cell.fromGsmCellLocation((GsmCellLocation) info);
            cellId = String.valueOf(cell.getLogicalCellId());
            technology = CellRecord.TECHNOLOGY_MAP().get(tech);
        } else {
            Log.v(TAG, "Cell info null or unknown type: " + (info != null ? info.getClass().getSimpleName() : "null"));
            technology = CellRecord.TECHNOLOGY_MAP().get(tech);
        }
    }
}