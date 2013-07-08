/**
 * Takes care of database creation
 * 
 * Currently database is created by pure sql statements.
 * In an earlier version, database has been provisioned via sdcard
 * @see http://www.reigndesign.com/blog/using-your-own-sqlite-database-in-android-applications/
 */

package org.openbmap.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

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
			+ Schema.COL_CELLID + " INTEGER DEFAULT -1, "
			+ Schema.COL_LAC + " INTEGER DEFAULT 0, "
			+ Schema.COL_MCC + " TEXT, "
			+ Schema.COL_MNC + " TEXT, "
			+ Schema.COL_PSC + " INTEGER DEFAULT -1, "
			+ Schema.COL_BASEID + " INTEGER DEFAULT -1,"
			+ Schema.COL_NETWORKID  + " INTEGER DEFAULT -1,"
			+ Schema.COL_SYSTEMID + " INTEGER DEFAULT -1,"
			+ Schema.COL_OPERATORNAME + " TEXT, "
			+ Schema.COL_OPERATOR + " TEXT, "
			+ Schema.COL_STRENGTHDBM + " INTEGER DEFAULT 0, "
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
			+  Schema.COL_IS_NEW_WIFI + " INTEGER, "
			+  " FOREIGN KEY (" + Schema.COL_SESSION_ID + ") REFERENCES " + Schema.TBL_SESSIONS + "( " + Schema.COL_ID + ") ON DELETE CASCADE, "
			+  " FOREIGN KEY (" + Schema.COL_BEGIN_POSITION_ID + ") REFERENCES " + Schema.TBL_POSITIONS + "( " + Schema.COL_ID + "), "
			+  " FOREIGN KEY (" + Schema.COL_END_POSITION_ID + ") REFERENCES " + Schema.TBL_POSITIONS + "( " + Schema.COL_ID + ")" 
			+  ")";

	/**
	 * SQL for creating table POSITIONS
	 * COL_TIMESTAMP format: YYYYMMDDhhmmss not UTC!
	 */
	private static final String SQL_CREATE_TABLE_POSITIONS = ""
			+  "CREATE TABLE " + Schema.TBL_POSITIONS + " ("
			+  Schema.COL_ID	 +  " INTEGER PRIMARY KEY AUTOINCREMENT,"
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
			+  "CREATE INDEX idx_wifis_sessios_id ON "
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
			+  "CREATE INDEX idx_cells_sessios_id ON "
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
			+  Schema.COL_CELLID + ", "		
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
	private static final int VERSION = 1;

	public DatabaseHelper(final Context context) {
		super(context, DB_NAME, null, VERSION);
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
		// recreate from scratch
		onCreate(db);
	}

	@Override
	public final synchronized void close() {

		if (mDataBase != null) {
			mDataBase.close();
		}

		super.close();
	}

}
