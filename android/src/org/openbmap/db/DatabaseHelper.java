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

import org.openbmap.RadioBeacon;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Takes care of database creation
 * 
 * Reminder:
 * In an earlier version, database has been provisioned via sdcard
 * @see http://www.reigndesign.com/blog/using-your-own-sqlite-database-in-android-applications/
 */

public class DatabaseHelper extends SQLiteOpenHelper {

	private static final String TAG = DatabaseHelper.class.getSimpleName();

	static final String DB_NAME = "radiobeacon";

	/**
	 * SQL for creating table CELLS
	 */
	private static final String SQL_CREATE_TABLE_CELLS = ""
			+ "CREATE TABLE " + Schema.TBL_CELLS + " ("
			+ Schema.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
			+ Schema.COL_NETWORKTYPE + " INTEGER DEFAULT 0, "
			+ Schema.COL_IS_CDMA + " INTEGER DEFAULT 0," 
			+ Schema.COL_IS_SERVING + " INTEGER DEFAULT 0," 
			+ Schema.COL_IS_NEIGHBOR + " INTEGER DEFAULT 0," 
			+ Schema.COL_LOGICAL_CELLID + " INTEGER DEFAULT -1, "
			+ Schema.COL_ACTUAL_CELLID + " INTEGER DEFAULT -1,"
			+ Schema.COL_UTRAN_RNC + " INTEGER DEFAULT -1,"
			+ Schema.COL_LAC + " INTEGER DEFAULT 0, "
			+ Schema.COL_MCC + " TEXT, "
			+ Schema.COL_MNC + " TEXT, "
			+ Schema.COL_PSC + " INTEGER DEFAULT -1, "
			+ Schema.COL_CDMA_BASEID + " INTEGER DEFAULT -1,"
			+ Schema.COL_CDMA_NETWORKID  + " INTEGER DEFAULT -1,"
			+ Schema.COL_CDMA_SYSTEMID + " INTEGER DEFAULT -1,"
			+ Schema.COL_OPERATORNAME + " TEXT, "
			+ Schema.COL_OPERATOR + " TEXT, "
			+ Schema.COL_STRENGTHDBM + " INTEGER DEFAULT 0, "
			+ Schema.COL_STRENGTHASU + " INTEGER DEFAULT 0, "
			+ Schema.COL_TIMESTAMP + " LONG NOT NULL, "
			+ Schema.COL_BEGIN_POSITION_ID + " INTEGER NOT NULL, "
			+ Schema.COL_END_POSITION_ID + " INTEGER NOT NULL, "
			+ Schema.COL_SESSION_ID + " INTEGER, "
			+ " FOREIGN KEY (" + Schema.COL_SESSION_ID + ") REFERENCES " + Schema.TBL_SESSIONS + "( " + Schema.COL_ID + ") ON DELETE CASCADE, "
			+ " FOREIGN KEY (" + Schema.COL_BEGIN_POSITION_ID + ") REFERENCES " + Schema.TBL_POSITIONS + "( " + Schema.COL_ID + ") "
			+ " FOREIGN KEY (" + Schema.COL_END_POSITION_ID + ") REFERENCES " + Schema.TBL_POSITIONS + "( " + Schema.COL_ID + ") "
			+  ")";

	/**
	 * SQL for creating table WIFIS
	 */
	private static final String SQL_CREATE_TABLE_WIFIS = ""
			+  "CREATE TABLE " + Schema.TBL_WIFIS + " ("
			+  Schema.COL_ID	 +  " INTEGER PRIMARY KEY AUTOINCREMENT,"
			+  Schema.COL_BSSID + " TEXT,"		
			+  Schema.COL_SSID + " TEXT,"
			+  Schema.COL_MD5_SSID + " TEXT,"
			+  Schema.COL_CAPABILITIES  + " TEXT,"
			+  Schema.COL_FREQUENCY + " INTEGER DEFAULT 0,"
			+  Schema.COL_LEVEL + " INTEGER DEFAULT 0,"
			+  Schema.COL_TIMESTAMP + " LONG NOT NULL,"
			+  Schema.COL_BEGIN_POSITION_ID + " INTEGER NOT NULL, "
			+  Schema.COL_END_POSITION_ID + " INTEGER NOT NULL, "
			+  Schema.COL_SESSION_ID + " INTEGER, "
			//+  Schema.COL_IS_NEW_WIFI + " INTEGER, "
			+  Schema.COL_KNOWN_WIFI + " INTEGER, "
			+  " FOREIGN KEY (" + Schema.COL_SESSION_ID + ") REFERENCES " + Schema.TBL_SESSIONS + "( " + Schema.COL_ID + ") ON DELETE CASCADE, "
			+  " FOREIGN KEY (" + Schema.COL_BEGIN_POSITION_ID + ") REFERENCES " + Schema.TBL_POSITIONS + "( " + Schema.COL_ID + "), "
			+  " FOREIGN KEY (" + Schema.COL_END_POSITION_ID + ") REFERENCES " + Schema.TBL_POSITIONS + "( " + Schema.COL_ID + ")" 
			+  ")";

	// Caution, stupid sqlite restriction! Have to provide name for each field, otherwise query fails
	// see http://stackoverflow.com/questions/3269199/sqlite-for-android-custom-table-view-sql-view-not-android-view-discrepancy
	private static final String SQL_CREATE_VIEW_WIFI_POSITIONS = "CREATE VIEW IF NOT EXISTS " + Schema.VIEW_WIFIS_EXTENDED + " AS " 
			+ " SELECT w." + Schema.COL_ID + ","
			+ " w." + Schema.COL_BSSID + " AS " + Schema.COL_BSSID + ","
			+ " w." + Schema.COL_SSID +  " AS " + Schema.COL_SSID + ","
			+ " w." + Schema.COL_MD5_SSID + " AS " + Schema.COL_MD5_SSID + ","
			+ " w." + Schema.COL_CAPABILITIES +  " AS " + Schema.COL_CAPABILITIES + ","
			+ " w." + Schema.COL_FREQUENCY +  " AS " + Schema.COL_FREQUENCY + ","
			+ " w." + Schema.COL_LEVEL +  " AS " + Schema.COL_LEVEL + ","
			+ " w." + Schema.COL_TIMESTAMP +  " AS " + Schema.COL_TIMESTAMP + ","
			+ " w." + Schema.COL_BEGIN_POSITION_ID +  " AS " + Schema.COL_BEGIN_POSITION_ID + ","
			+ " w." + Schema.COL_END_POSITION_ID +  " AS " + Schema.COL_END_POSITION_ID + ","
			+ " w." + Schema.COL_SESSION_ID +  " AS " + Schema.COL_SESSION_ID + ","
			//+ " w." + Schema.COL_IS_NEW_WIFI +  " AS " + Schema.COL_IS_NEW_WIFI + ","
			+ " w." + Schema.COL_KNOWN_WIFI +  " AS " + Schema.COL_KNOWN_WIFI + ","
			+ " b." + Schema.COL_LATITUDE + " as begin_latitude,"
			+ " b." + Schema.COL_LONGITUDE + " as begin_longitude,"
			+ " b." + Schema.COL_ALTITUDE + " as begin_altitude,"
			+ " b." + Schema.COL_ACCURACY + " as begin_accuracy,"
			+ " b." + Schema.COL_TIMESTAMP + " as begin_timestamp,"
			+ " b." + Schema.COL_BEARING + " as begin_bearing,"
			+ " b." + Schema.COL_SPEED + " as begin_speed,"
			+ " b." + Schema.COL_SOURCE + " as begin_source,"
			+ " e." + Schema.COL_LATITUDE + " as end_latitude," 
			+ " e." + Schema.COL_LONGITUDE + " as end_longitude,"
			+ " e." + Schema.COL_ALTITUDE + " as end_altitude,"
			+ " e." + Schema.COL_ACCURACY + " as end_accuracy,"
			+ " e." + Schema.COL_TIMESTAMP + " as end_timestamp," 
			+ " e." + Schema.COL_BEARING + " as end_bearing,"
			+ " e." + Schema.COL_SPEED + " as end_speed,"
			+ " e." + Schema.COL_SOURCE + " as end_source"
			+ " FROM " + Schema.TBL_WIFIS + " AS w"
			+ " LEFT JOIN " + Schema.TBL_POSITIONS + " AS b ON (w." + Schema.COL_BEGIN_POSITION_ID + " = b." + Schema.COL_ID + ")"
			+ " LEFT JOIN " + Schema.TBL_POSITIONS + " AS e ON (w." + Schema.COL_END_POSITION_ID + " = e." + Schema.COL_ID + ")";

	private static final String SQL_CREATE_VIEW_CELL_POSITIONS = "CREATE VIEW IF NOT EXISTS " + Schema.VIEW_CELLS_EXTENDED + " AS " 
			+ " SELECT w."  + Schema.COL_ID + " AS " + Schema.COL_ID + ","
			+ " w." +  Schema.COL_NETWORKTYPE + " AS " + Schema.COL_NETWORKTYPE + ","
			+ " w." +  Schema.COL_IS_CDMA  + " AS " + Schema.COL_IS_CDMA + ","
			+ " w." +  Schema.COL_IS_SERVING  + " AS " + Schema.COL_IS_SERVING  + ","
			+ " w." +  Schema.COL_IS_NEIGHBOR  + " AS " + Schema.COL_IS_NEIGHBOR  + ","
			+ " w." +  Schema.COL_LOGICAL_CELLID  + " AS " + Schema.COL_LOGICAL_CELLID + ","
			+ " w." +  Schema.COL_ACTUAL_CELLID  + " AS " + Schema.COL_ACTUAL_CELLID + ","
			+ " w." +  Schema.COL_UTRAN_RNC  + " AS " + Schema.COL_UTRAN_RNC + ","
			+ " w." +  Schema.COL_LAC  + " AS " + Schema.COL_LAC + ","
			+ " w." +  Schema.COL_MCC  + " AS " + Schema.COL_MCC + ","
			+ " w." +  Schema.COL_MNC  + " AS " + Schema.COL_MNC + ","
			+ " w." +  Schema.COL_PSC  + " AS " + Schema.COL_PSC + ","
			+ " w." +  Schema.COL_CDMA_BASEID  + " AS " + Schema.COL_CDMA_BASEID + ","
			+ " w." +  Schema.COL_CDMA_NETWORKID   + " AS " + Schema.COL_CDMA_NETWORKID + ","
			+ " w." +  Schema.COL_CDMA_SYSTEMID  + " AS " + Schema.COL_CDMA_SYSTEMID + ","
			+ " w." +  Schema.COL_OPERATORNAME  + " AS " + Schema.COL_OPERATORNAME + ","
			+ " w." +  Schema.COL_OPERATOR  + " AS " + Schema.COL_OPERATOR + ","
			+ " w." +  Schema.COL_STRENGTHDBM  + " AS " + Schema.COL_STRENGTHDBM + ","
			+ " w." +  Schema.COL_STRENGTHASU  + " AS " + Schema.COL_STRENGTHASU + ","
			+ " w." +  Schema.COL_TIMESTAMP  + " AS " + Schema.COL_TIMESTAMP + ","
			+ " w." +  Schema.COL_BEGIN_POSITION_ID + " AS " + Schema.COL_BEGIN_POSITION_ID + ","
			+ " w." +  Schema.COL_END_POSITION_ID + " AS " + Schema.COL_END_POSITION_ID + ","
			+ " w." +  Schema.COL_SESSION_ID + " AS " + Schema.COL_SESSION_ID + ","
			+ " b." + Schema.COL_LATITUDE + " as begin_latitude,"
			+ " b." + Schema.COL_LONGITUDE + " as begin_longitude,"
			+ " b." + Schema.COL_ALTITUDE + " as begin_altitude,"
			+ " b." + Schema.COL_ACCURACY + " as begin_accuracy,"
			+ " b." + Schema.COL_TIMESTAMP + " as begin_timestamp,"
			+ " b." + Schema.COL_BEARING + " as begin_bearing,"
			+ " b." + Schema.COL_SPEED + " as begin_speed,"
			+ " b." + Schema.COL_SOURCE + " as begin_source,"
			+ " e." + Schema.COL_LATITUDE + " as end_latitude," 
			+ " e." + Schema.COL_LONGITUDE + " as end_longitude,"
			+ " e." + Schema.COL_ALTITUDE + " as end_altitude,"
			+ " e." + Schema.COL_ACCURACY + " as end_accuracy,"
			+ " e." + Schema.COL_TIMESTAMP + " as end_timestamp," 
			+ " e." + Schema.COL_BEARING + " as end_bearing,"
			+ " e." + Schema.COL_SPEED + " as end_speed,"
			+ " e." + Schema.COL_SOURCE + " as end_source"
			+ " FROM " + Schema.TBL_CELLS + " AS w"
			+ " LEFT JOIN " + Schema.TBL_POSITIONS + " AS b ON (w." + Schema.COL_BEGIN_POSITION_ID + " = b." + Schema.COL_ID + ")"
			+ " LEFT JOIN " + Schema.TBL_POSITIONS + " AS e ON (w." + Schema.COL_END_POSITION_ID + " = e." + Schema.COL_ID + ")";

	/**
	 * SQL for creating table POSITIONS
	 * COL_TIMESTAMP format: YYYYMMDDhhmmss not UTC!
	 */
	private static final String SQL_CREATE_TABLE_POSITIONS = ""
			+  "CREATE TABLE " + Schema.TBL_POSITIONS + " ("
			+  Schema.COL_ID +  " INTEGER PRIMARY KEY AUTOINCREMENT,"
			+  Schema.COL_LATITUDE + " DOUBLE NOT NULL,"
			+  Schema.COL_LONGITUDE + " DOUBLE NOT NULL,"
			+  Schema.COL_ALTITUDE + " DOUBLE,"
			+  Schema.COL_ACCURACY + " DOUBLE,"
			+  Schema.COL_TIMESTAMP + " LONG NOT NULL,"
			+  Schema.COL_BEARING + " DOUBLE,"
			+  Schema.COL_SPEED + " DOUBLE,"
			+  Schema.COL_SESSION_ID + " INTEGER, "
			+  Schema.COL_SOURCE + " TEXT, "	
			+  " FOREIGN KEY (" + Schema.COL_SESSION_ID + ") REFERENCES " + Schema.TBL_SESSIONS + "( " + Schema.COL_ID + ") ON DELETE CASCADE "
			+  ")";

	/**
	 * SQL for creating table LOGS
	 * TODO cleanup, there's actually no need to persist this data
	 */
	private static final String SQL_CREATE_TABLE_LOGS = ""
			+ "CREATE TABLE " + Schema.TBL_LOGS + " ("
			+ Schema.COL_ID +  " INTEGER PRIMARY KEY AUTOINCREMENT,"
			+ Schema.COL_MANUFACTURER + "  TEXT, "
			+ Schema.COL_MODEL + "  TEXT, "
			+ Schema.COL_REVISION + "  TEXT, "
			+ Schema.COL_SWID + "  TEXT, "
			+ Schema.COL_SWVER + "  TEXT, "
			+ Schema.COL_TIMESTAMP + " LONG NOT NULL,"
			+ Schema.COL_SESSION_ID + " INTEGER, "
			+ " FOREIGN KEY (" + Schema.COL_SESSION_ID + ") REFERENCES " + Schema.TBL_SESSIONS + "( " + Schema.COL_ID + ") ON DELETE CASCADE"
			+ ")";

	private static final String	SQL_CREATE_TABLE_SESSIONS =  ""
			+ "CREATE TABLE " + Schema.TBL_SESSIONS + " ("
			+ Schema.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
			+ Schema.COL_DESCRIPTION + " TEXT,"
			+ Schema.COL_CREATED_AT + " LONG, "
			+ Schema.COL_LAST_UPDATED + " LONG, "
			+ Schema.COL_HAS_BEEN_EXPORTED + " INTEGER, "
			+ Schema.COL_IS_ACTIVE + " INTEGER,"
			+ Schema.COL_NUMBER_OF_WIFIS + " INTEGER,"
			+ Schema.COL_NUMBER_OF_CELLS + " INTEGER"
			+ ")";

	/**
	 * SQL for creating index on WIFIS
	 */
	private static final String SQL_CREATE_IDX_WIFIS = ""
			+  "CREATE INDEX idx_wifis ON "
			+  Schema.TBL_WIFIS + "("
			+  Schema.COL_BSSID + ", "		
			+  Schema.COL_LEVEL + ""
			+  ")";

	private static final String SQL_CREATE_IDX_WIFIS_SESSION_ID = ""
			+  "CREATE INDEX idx_wifis_sessions_id ON "
			+  Schema.TBL_WIFIS + "("
			+  Schema.COL_SESSION_ID
			+  ")";

	private static final String SQL_CREATE_IDX_WIFIS_BEGIN_POSITION_ID = ""
			+  "CREATE INDEX idx_wifis_begin_position_id ON "
			+  Schema.TBL_WIFIS + "("
			+  Schema.COL_BEGIN_POSITION_ID
			+  ")";		

	private static final String SQL_CREATE_IDX_WIFIS_END_POSITION_ID = ""
			+  "CREATE INDEX idx_wifis_end_position_id ON "
			+  Schema.TBL_WIFIS + "("
			+  Schema.COL_END_POSITION_ID
			+  ")";		

	private static final String SQL_CREATE_IDX_CELLS_SESSION_ID = ""
			+  "CREATE INDEX idx_cells_sessions_id ON "
			+  Schema.TBL_CELLS + "("
			+  Schema.COL_SESSION_ID
			+  ")";

	private static final String SQL_CREATE_IDX_CELLS_BEGIN_POSITION_ID = ""
			+  "CREATE INDEX idx_cells_begin_position_id ON "
			+  Schema.TBL_CELLS + "("
			+  Schema.COL_BEGIN_POSITION_ID
			+  ")";	

	private static final String SQL_CREATE_IDX_CELLS_END_POSITION_ID = ""
			+  "CREATE INDEX idx_cells_end_position_id ON "
			+  Schema.TBL_CELLS + "("
			+  Schema.COL_END_POSITION_ID
			+  ")";	

	/**
	 * SQL for creating index on CELLS
	 */
	private static final String SQL_CREATE_IDX_CELLS = ""
			+  "CREATE INDEX idx_cells ON "
			+  Schema.TBL_CELLS + "("
			+  Schema.COL_LOGICAL_CELLID + ", "		
			+  Schema.COL_STRENGTHDBM + ""
			+  ")";

	/**
	 * SQL for creating index on Positions
	 */
	private static final String	SQL_CREATE_IDX_POSITIONS	= ""
			+  "CREATE INDEX idx_positions ON "
			+  Schema.TBL_POSITIONS + "("
			+  Schema.COL_LATITUDE + ", "		
			+  Schema.COL_LONGITUDE + ""
			+  ")";


	private SQLiteDatabase mDataBase; 

	public DatabaseHelper(final Context context) {
		super(context, DB_NAME, null, RadioBeacon.DATABASE_VERSION);
	}

	@Override
	public final void onCreate(final SQLiteDatabase db) {
		Log.d(TAG, "Creating application database at " + db.getPath());
		this.mDataBase = db;

		if (!db.isReadOnly()) {

			// create general purpose tables
			db.execSQL("DROP TABLE IF EXISTS " + Schema.TBL_POSITIONS);
			db.execSQL(SQL_CREATE_TABLE_POSITIONS);
			db.execSQL(SQL_CREATE_IDX_POSITIONS);
			db.execSQL("DROP TABLE IF EXISTS " + Schema.TBL_LOGS);
			db.execSQL(SQL_CREATE_TABLE_LOGS);
			// TODO: find solution for Session NOT_TRACKING
			db.execSQL("DROP TABLE IF EXISTS " + Schema.TBL_SESSIONS);
			db.execSQL(SQL_CREATE_TABLE_SESSIONS);

			db.execSQL("DROP TABLE IF EXISTS " + Schema.TBL_CELLS);
			db.execSQL(SQL_CREATE_TABLE_CELLS);
			db.execSQL("DROP TABLE IF EXISTS " + Schema.TBL_WIFIS);
			db.execSQL(SQL_CREATE_TABLE_WIFIS);

			// create views
			db.execSQL("DROP VIEW IF EXISTS " + Schema.VIEW_WIFIS_EXTENDED);
			db.execSQL(SQL_CREATE_VIEW_WIFI_POSITIONS);

			db.execSQL("DROP VIEW IF EXISTS " + Schema.VIEW_CELLS_EXTENDED);
			db.execSQL(SQL_CREATE_VIEW_CELL_POSITIONS);

			// Create indices
			db.execSQL(SQL_CREATE_IDX_WIFIS);
			db.execSQL(SQL_CREATE_IDX_CELLS);
			db.execSQL(SQL_CREATE_IDX_WIFIS_SESSION_ID);
			db.execSQL(SQL_CREATE_IDX_WIFIS_BEGIN_POSITION_ID);
			db.execSQL(SQL_CREATE_IDX_WIFIS_END_POSITION_ID);
			db.execSQL(SQL_CREATE_IDX_CELLS_SESSION_ID);
			db.execSQL(SQL_CREATE_IDX_CELLS_BEGIN_POSITION_ID);
			db.execSQL(SQL_CREATE_IDX_CELLS_END_POSITION_ID);
		}
	}

	@Override
	public final void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
		Log.i(TAG, "Updating database scheme from " + oldVersion + " to " + newVersion);

		if (oldVersion <= 1) {
			// add wifi position view 
			db.execSQL("DROP VIEW IF EXISTS " + Schema.VIEW_WIFIS_EXTENDED);
			db.execSQL(SQL_CREATE_VIEW_WIFI_POSITIONS);
		} 

		if (oldVersion <= 2) {
			// add asu fields
			db.execSQL("ALTER TABLE " + Schema.TBL_CELLS + " ADD COLUMN " + Schema.COL_STRENGTHASU + " INTEGER DEFAULT 0");
		}

		if (oldVersion <= 3) {
			// add cell position view
			db.execSQL("DROP VIEW IF EXISTS " + Schema.VIEW_CELLS_EXTENDED);
			db.execSQL(SQL_CREATE_VIEW_CELL_POSITIONS);
		}

		if (oldVersion <= 4) {
			// add known wifi column (replacement for is_new_wifi)
			try {
				db.execSQL("ALTER TABLE " + Schema.TBL_WIFIS + " ADD COLUMN " + Schema.COL_KNOWN_WIFI + " INTEGER DEFAULT 0");
			}
			catch (SQLException e) {
				Log.i(TAG, "Nothing to do: is_known column already exists");
			}

			try {
				db.execSQL("UPDATE " + Schema.TBL_WIFIS + " SET " + Schema.COL_KNOWN_WIFI + " = 1 WHERE is_new_wifi = 0");
			} catch (SQLException e) {
				Log.w(TAG, "Can't find is_new_wifi column. Skipping update");
			}
			db.execSQL("DROP VIEW IF EXISTS " + Schema.VIEW_WIFIS_EXTENDED);
			db.execSQL(SQL_CREATE_VIEW_WIFI_POSITIONS);
		}

		if (oldVersion <= 5) {
			// F-Droid 0.7.7 second release
			// on some clients column from version 2 wasn't added
			try {
				db.execSQL("ALTER TABLE " + Schema.TBL_CELLS + " ADD COLUMN " + Schema.COL_STRENGTHASU + " INTEGER DEFAULT 0");
			} catch (SQLException e) {
				Log.i(TAG, "Nothing to do: asu column already exists");
			}

			try {
				db.execSQL("DROP VIEW IF EXISTS " + Schema.VIEW_CELLS_EXTENDED);
				db.execSQL(SQL_CREATE_VIEW_CELL_POSITIONS);
			} catch (SQLException e) {
				Log.w(TAG, "Couldn't create cell position view: maybe we've got some more pending migrations");
			}
		}

		if (oldVersion <= 6) {
			// add UTRAN radio network controller and UTRAN cid
			try {
				db.execSQL("ALTER TABLE " + Schema.TBL_CELLS + " ADD COLUMN " + Schema.COL_UTRAN_RNC + " INTEGER DEFAULT -1");
				db.execSQL("ALTER TABLE " + Schema.TBL_CELLS + " ADD COLUMN " + Schema.COL_ACTUAL_CELLID + " INTEGER DEFAULT -1");
			} catch (SQLException e) {
				Log.i(TAG, "Nothing to do: utran columns already exist");
			}

			try {
				db.execSQL("DROP VIEW IF EXISTS " + Schema.VIEW_CELLS_EXTENDED);
				db.execSQL(SQL_CREATE_VIEW_CELL_POSITIONS);
			} catch (SQLException e) {
				Log.w(TAG, "Couldn't create cell position view: maybe we've got some more pending migrations");
			}
		}

		if (oldVersion <= 7) {
			// fix broken migrations, see http://code.google.com/p/openbmap/issues/detail?id=55
			// basically all migration since 4 are repeated, error ignored
			
			// repeat migration 4
			try {
				db.execSQL("ALTER TABLE " + Schema.TBL_WIFIS + " ADD COLUMN " + Schema.COL_KNOWN_WIFI + " INTEGER DEFAULT 0");
			}
			catch (SQLException e) {
				Log.i(TAG, "Nothing to do: is_known column already exists");
			}
	
			try {
				db.execSQL("UPDATE " + Schema.TBL_WIFIS + " SET " + Schema.COL_KNOWN_WIFI + " = 1 WHERE is_new_wifi = 0");
			} catch (SQLException e) {
				Log.w(TAG, "Can't find is_new_wifi column. Skipping update");
			}
			
			// repeat migration 5
			try {
				db.execSQL("ALTER TABLE " + Schema.TBL_CELLS + " ADD COLUMN " + Schema.COL_STRENGTHASU + " INTEGER DEFAULT 0");
			} catch (SQLException e) {
				Log.i(TAG, "Nothing to do: asu column already exists");
			}
			
			// repeat migration 6
			try {
				db.execSQL("ALTER TABLE " + Schema.TBL_CELLS + " ADD COLUMN " + Schema.COL_UTRAN_RNC + " INTEGER DEFAULT -1");
				db.execSQL("ALTER TABLE " + Schema.TBL_CELLS + " ADD COLUMN " + Schema.COL_ACTUAL_CELLID + " INTEGER DEFAULT -1");
			} catch (SQLException e) {
				Log.i(TAG, "Nothing to do: utran columns already exist");
			}
			
			// recreate views
			db.execSQL("DROP VIEW IF EXISTS " + Schema.VIEW_WIFIS_EXTENDED);
			db.execSQL(SQL_CREATE_VIEW_WIFI_POSITIONS);
			
			db.execSQL("DROP VIEW IF EXISTS " + Schema.VIEW_CELLS_EXTENDED);
			db.execSQL(SQL_CREATE_VIEW_CELL_POSITIONS);
		}
	}

	@Override
	public final synchronized void close() {

		if (mDataBase != null) {
			mDataBase.close();
		}

		super.close();
	}

}
