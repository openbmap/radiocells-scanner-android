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

import org.openbmap.db.models.CellRecord;

public class onCellSaved {

    public String operator;
    public String mcc = CellRecord.MCC_UNKNOWN;
    public String mnc = CellRecord.MNC_UNKNOWN;

    public String sid = CellRecord.SYSTEM_ID_UNKNOWN;
    public String bid = CellRecord.BASE_ID_UNKNOWN;
    public String nid = CellRecord.NETWORK_ID_UNKOWN;

    public int area = CellRecord.AREA_UNKNOWN;
    public String cell_id;
    public String technology;
    public int level;

    /**
     * Fired when new cells scan is available
     * @param operatorName
     * @param mcc MCC
     * @param mnc MNC
     * @param sid system id (CDMA only)
     * @param bid base station id (CDMA only)
     * @param nid network id (CDMA only)
     * @param area location area
     * @param cell_id cell id
     * @param technology cell technology
     * @param level level (in dbm)
     */
    public onCellSaved(final String operatorName, final String mcc, final String mnc, final String sid, final String nid, final String bid, final int area, final String cell_id, final String technology, final int level) {
        this.operator = operatorName;
        this.mcc = mcc;
        this.mnc = mnc;
        this.area = area;
        this.cell_id = cell_id;
        this.technology = technology;
        this.level = level;
        this.sid = sid;
        this.nid = nid;
        this.bid = bid;
    }

}
