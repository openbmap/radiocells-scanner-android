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

public class onCellUpdated {

    public String operator;
    public String mcc;
    public String mnc;
    public int area;
    public int cell_id;
    public String technology;
    public int level;

    /**
     * Fired when new cells scan is available
     * @param operatorName
     * @param mcc
     * @param mnc
     * @param area
     * @param cell_id
     * @param technology
     * @param level
     */
    public onCellUpdated(final String operatorName, final String mcc, final String mnc, final int area, final int cell_id, final String technology, final int level) {
        this.operator = operatorName;
        this.mcc = mcc;
        this.mnc = mnc;
        this.area = area;
        this.cell_id = cell_id;
        this.technology = technology;
        this.level = level;
    }

}
