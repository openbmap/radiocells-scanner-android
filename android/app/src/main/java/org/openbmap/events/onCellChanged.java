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

import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.util.Log;

public class onCellChanged {

    public static final String TAG = onCellChanged.class.getSimpleName();

    public onCellChanged(final CellInfo cell) {
        if (cell instanceof CellInfoGsm) {

        } else if (cell instanceof CellInfoWcdma) {

        } else if (cell instanceof CellInfoLte) {

        } else if (cell instanceof CellInfoCdma) {

        } else {
            Log.v(TAG, "Cell info null or unknown type");
        }
    }

}
