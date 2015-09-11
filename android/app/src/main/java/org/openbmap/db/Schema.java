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

package org.openbmap.db;

/**
 * Data schemes: Table names, column names, URI
 */
public final class Schema {
	// Tables
	/**
	 * Table names.
	 */
	public static final String TBL_POSITIONS = "positions";
	public static final String TBL_CELLS = "cells";

	public static final String TBL_ESSIDS = "essids";
	public static final String TBL_WIFIS = "wifis";
	public static final String TBL_WIFI_POSITIONS = "wifi_zone";
	public static final String TBL_LOGS = "logs";
	public static final String TBL_SESSIONS = "sessions";
	
	// Views
	public static final String	VIEW_WIFIS_EXTENDED	= "wifis_positions";
	public static final String	VIEW_CELLS_EXTENDED	= "cells_positions";
	
	/**
	 * General columns used in several tables.
	 */
	public static final String COL_ID = "_id";
	public static final String COL_TIMESTAMP = "timestamp";
	public static final String COL_BEGIN_POSITION_ID = "request_pos_id";
	public static final String COL_END_POSITION_ID = "last_pos_id";
	
	/**
	 * Columns TBL_POSITIONS.
	 */
	public static final String COL_SESSION_ID = "session_id";
	public static final String COL_LONGITUDE = "longitude";
	public static final String COL_LATITUDE = "latitude";
	public static final String COL_ALTITUDE = "altitude";
	public static final String COL_ACCURACY = "accuracy";
	public static final String COL_BEARING = "bearing";
	public static final String COL_SPEED = "speed";
	public static final String COL_SOURCE = "source";

	/**
	 * Columns TBL_CELLS.
	 */
	public static final String COL_LOGICAL_CELLID = "cid";
	public static final String COL_UTRAN_RNC = "utran_rnc";
	public static final String COL_ACTUAL_CELLID = "actual_cid";
	public static final String COL_NETWORKTYPE = "type";
	public static final String COL_IS_CDMA = "is_cdma";
	public static final String COL_IS_SERVING = "is_serving";
	public static final String COL_IS_NEIGHBOR = "is_neighbor";
	public static final String COL_OPERATORNAME = "OperatorName";
	public static final String COL_OPERATOR = "Operator";
	public static final String COL_MCC = "mcc";
	public static final String COL_MNC = "mnc";
	public static final String COL_AREA = "lac";
	public static final String COL_PSC = "psc";
	public static final String COL_STRENGTHDBM = "dbm";
	public static final String	COL_STRENGTHASU	= "asu";
	
	/**
	 * Additional TBL_CDMACELLS, rest is same as TBL_CELLS.
	 */
	public static final String COL_CDMA_BASEID = "baseid";
	public static final String COL_CDMA_NETWORKID = "networkid";
	public static final String COL_CDMA_SYSTEMID = "systemid";
	
	/**
	 * Columns TBL_WIFIS.
	 */
	public static final String COL_BSSID = "bssid";
	public static final String COL_SSID = "ssid";
	public static final String COL_MD5_SSID = "md5ssid";
	public static final String COL_CAPABILITIES = "capabilities";
	public static final String COL_FREQUENCY = "frequency";
	public static final String COL_LEVEL = "level";
	public static final String COL_MAX_LEVEL = "MAX(" + COL_LEVEL + ")";
	//public static final String COL_IS_NEW_WIFI	= "is_new_wifi";	
	public static final String COL_KNOWN_WIFI = "is_known";
	
	/**
	 * Columns TBL_LOG_FILE.
	 */
	public static final String COL_MANUFACTURER  = "manufacturer";
	public static final String COL_MODEL = "model";
	public static final String COL_REVISION = "revision";
	public static final String COL_SWID = "swid";
	public static final String COL_SWVER = "swver";

	/**
	 * Columns TBL_SESSIONS
	 */
	public static final String COL_CREATED_AT = "created_at";
	public static final String COL_DESCRIPTION = "description";
	public static final String COL_LAST_UPDATED = "updated_at";
	public static final String COL_HAS_BEEN_EXPORTED = "exported";
	public static final String COL_IS_ACTIVE = "is_active";
	public static final String COL_NUMBER_OF_WIFIS = "no_wifis";
	public static final String COL_NUMBER_OF_CELLS = "no_cells";
	
	public static final int URI_CODE_CELLS = 0;
	public static final int URI_CODE_CELL_ID = 1;
	public static final int URI_CODE_CELL_OVERVIEW = 2;
	public static final int	URI_CODE_CELLS_BY_SESSION = 3;
	public static final int	URI_CODE_CELLS_EXTENDED	= 19;
	
	public static final int URI_CODE_WIFIS = 20;
	public static final int URI_CODE_WIFI_ID = 21;
	/**
	 * Get only strongest measurement for each bssid
	 */
	public static final int URI_CODE_WIFI_OVERVIEW = 22;
	/**
	 * Get all session wifis
	 */
	public static final int	URI_CODE_WIFIS_BY_SESSION = 23;
	
	/**
	 * Gets all wifis with given BSSID
	 */
	public static final int	URI_CODE_WIFIS_BY_BSSID	= 24;
	
	/**
	 * Get all wifis including position data
	 */
	public static final int	URI_CODE_WIFIS_EXTENDED	= 29;
	
	public static final int URI_CODE_POSITIONS = 30;
	public static final int URI_CODE_POSITION_ID = 31;
	
	public static final int URI_CODE_LOGS = 50;
	public static final int URI_CODE_LOG_ID = 51;
	public static final int URI_CODE_LOGS_BY_SESSION = 52;
	public static final int	URI_CODE_SESSIONS	= 70;
	public static final int	URI_CODE_SESSION_ID	= 71;
	public static final int	URI_CODE_SESSION_ACTIVE	= 79;
	
	


	/**
	 * Private dummy constructor
	 */
	private Schema() {
	}
}