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

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

import org.openbmap.RadioBeacon;

import java.util.ArrayList;
import java.util.List;

/**
 * Content provider
 * {@link android.content.ContentProvider} mechanism.
 */
public class ContentProvider extends android.content.ContentProvider {

	private static final String TAG = ContentProvider.class.getSimpleName();

	/**
	 * Authority for Uris
	 */
	public static final String AUTHORITY = RadioBeacon.class.getPackage().getName() + ".provider";

	/**
	 * Uri for wifi measurements
	 */
	public static final Uri CONTENT_URI_WIFI = Uri.parse("content://" + AUTHORITY + "/" + Schema.TBL_WIFIS);

	/**
	 * URI for wifis with position data
	 */
	public static final Uri CONTENT_URI_WIFI_EXTENDED = Uri.parse("content://" + AUTHORITY + "/" + Schema.VIEW_WIFIS_EXTENDED);

	/**
	 * URI for cells with position data
	 */
	public static final Uri CONTENT_URI_CELL_EXTENDED = Uri.parse("content://" + AUTHORITY + "/" + Schema.VIEW_CELLS_EXTENDED);

	/**
	 * Uri for cell
	 */
	public static final Uri CONTENT_URI_CELL = Uri.parse("content://" + AUTHORITY + "/" + Schema.TBL_CELLS);

	/**
	 * Uri for positions
	 */
	public static final Uri CONTENT_URI_POSITION = Uri.parse("content://" + AUTHORITY + "/" + Schema.TBL_POSITIONS);

	/**
	 * Uri for log files
	 */
	public static final Uri CONTENT_URI_LOGFILE = Uri.parse("content://" + AUTHORITY + "/" + Schema.TBL_LOGS);

	/**
	 * Uri for sessions
	 */
	public static final Uri CONTENT_URI_SESSION = Uri.parse("content://" + AUTHORITY + "/" + Schema.TBL_SESSIONS);

	/**
	 * Can be appended to certain URIs to get overview instead of all items
	 * Typically this are sort of SELECT DISTINCT queries
	 */
	public static final String CONTENT_URI_OVERVIEW_SUFFIX = "overview";

	/**
	 * Can be appended to certain URIs to get only items of specific session
	 */
	public static final String CONTENT_URI_SESSION_SUFFIX = "session";

	/**
	 * Can be appended to certain URIs to get only items of active session
	 */
	public static final String CONTENT_URI_ACTIVE_SUFFIX = "active";

	/**
	 * Can be appended to certain URIs to get only items of specific bssid
	 */
	public static final String CONTENT_URI_BSSID_SUFFIX = "bssid";

	/**
	 * Uri Matcher
	 */
	private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);


	/**
	 * URI matcher - defines how URIs are translated to URI_CODE which is used internally
	 */
	static {
		uriMatcher.addURI(AUTHORITY, Schema.TBL_SESSIONS, Schema.URI_CODE_SESSIONS);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_SESSIONS + "/#", Schema.URI_CODE_SESSION_ID);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_SESSIONS + "/" + CONTENT_URI_ACTIVE_SUFFIX, Schema.URI_CODE_SESSION_ACTIVE);

		uriMatcher.addURI(AUTHORITY, Schema.TBL_LOGS, Schema.URI_CODE_LOGS);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_LOGS + "/#", Schema.URI_CODE_LOG_ID);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_LOGS + "/" + CONTENT_URI_SESSION_SUFFIX + "/#", Schema.URI_CODE_LOGS_BY_SESSION);

		uriMatcher.addURI(AUTHORITY, Schema.TBL_CELLS, Schema.URI_CODE_CELLS);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_CELLS + "/#", Schema.URI_CODE_CELL_ID);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_CELLS + "/" + CONTENT_URI_OVERVIEW_SUFFIX + "/#", Schema.URI_CODE_CELL_OVERVIEW);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_CELLS + "/" + CONTENT_URI_SESSION_SUFFIX + "/#", Schema.URI_CODE_CELLS_BY_SESSION);
		uriMatcher.addURI(AUTHORITY, Schema.VIEW_CELLS_EXTENDED, Schema.URI_CODE_CELLS_EXTENDED);

		uriMatcher.addURI(AUTHORITY, Schema.TBL_WIFIS, Schema.URI_CODE_WIFIS);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_WIFIS + "/#", Schema.URI_CODE_WIFI_ID);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_WIFIS + "/" + CONTENT_URI_OVERVIEW_SUFFIX + "/#", Schema.URI_CODE_WIFI_OVERVIEW);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_WIFIS + "/" + CONTENT_URI_SESSION_SUFFIX + "/#", Schema.URI_CODE_WIFIS_BY_SESSION);
		uriMatcher.addURI(AUTHORITY, Schema.VIEW_WIFIS_EXTENDED, Schema.URI_CODE_WIFIS_EXTENDED);


		uriMatcher.addURI(AUTHORITY, Schema.TBL_POSITIONS, Schema.URI_CODE_POSITIONS);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_POSITIONS + "/#", Schema.URI_CODE_POSITION_ID);
        uriMatcher.addURI(AUTHORITY, Schema.TBL_POSITIONS + "/" + CONTENT_URI_SESSION_SUFFIX + "/#", Schema.URI_CODE_WAYPOINTS_BY_SESSION);
	}

	/**
	 * Database Helper
	 */
	private DatabaseHelper mDbHelper;

	@Override
	public final boolean onCreate() {
		mDbHelper = new DatabaseHelper(getContext().getApplicationContext());
		final SQLiteDatabase db = mDbHelper.getWritableDatabase();

		// Enable foreign key constraints (per connection)
		db.execSQL("PRAGMA foreign_keys = ON");
		return true;
	}

	/*
	 * Returns the MIME data type of the URI given as a parameter.
	 * @see android.content.ContentProvider#getType(android.net.Uri)
	 */
	@Override
	public final String getType(final Uri uri) {
		Log.v(TAG, "getType(), uri=" + uri);
		return null;
	}

	/**
	 * INSERT Statements
	 * @param uri base uri
	 * @return return uri + appended id
	 */
	@Override
	public final Uri insert(final Uri uri, final ContentValues values) {
		Log.d(TAG, "Uri " + uri.toString());
		// Select which data type to insert
		switch (uriMatcher.match(uri)) {
			case Schema.URI_CODE_CELLS:
				return insertCellMeasurement(uri, values);
			case Schema.URI_CODE_WIFIS:
				return insertWifiMeasurement(uri, values);
			case Schema.URI_CODE_POSITIONS:
				return insertPosition(uri, values);
			case Schema.URI_CODE_LOGS:
				return insertLog(uri, values);
			case Schema.URI_CODE_SESSIONS:
				return insertSession(uri, values);
			default:
				throw new IllegalArgumentException("Unknown URI: " + uri);
		}
	}

	/**
	 * Inserts a cell measurement
	 * @param baseUri
	 * @param values
	 */
	private Uri insertCellMeasurement(final Uri baseUri, final ContentValues values) {
		if (values.containsKey(Schema.COL_BEGIN_POSITION_ID)
				&& values.containsKey(Schema.COL_TIMESTAMP)) {
			final long rowId = mDbHelper.getWritableDatabase().insert(Schema.TBL_CELLS, null, values);
			if (rowId > 0) {
				final Uri cellUri = ContentUris.withAppendedId(baseUri, rowId);
				getContext().getContentResolver().notifyChange(ContentProvider.CONTENT_URI_CELL, null);
				return cellUri;
			}
		} else {
			throw new IllegalArgumentException("mandatory column missing");
		}
		return null;
	}

	/**
	 * Inserts a wifi measurement
	 * @param baseUri
	 * @param values
	 * @return
	 */
	private Uri insertWifiMeasurement(final Uri baseUri, final ContentValues values) {
		if (values.containsKey(Schema.COL_BEGIN_POSITION_ID) && values.containsKey(Schema.COL_END_POSITION_ID) && values.containsKey(Schema.COL_TIMESTAMP)) {
			final long rowId = mDbHelper.getWritableDatabase().insert(Schema.TBL_WIFIS, null, values);
			if (rowId > 0) {
				final Uri wifiUri = ContentUris.withAppendedId(baseUri, rowId);
				getContext().getContentResolver().notifyChange(ContentProvider.CONTENT_URI_WIFI, null);
				return wifiUri;
			}
		} else {
			throw new IllegalArgumentException("mandatory column missing");
		}
		return null;
	}

	/**
	 * Inserts a position
	 * @param baseUri
	 * @param values
	 * @return
	 */
	private Uri insertPosition(final Uri baseUri, final ContentValues values) {
		if (values.containsKey(Schema.COL_LONGITUDE)
				&& values.containsKey(Schema.COL_LATITUDE)
				&& values.containsKey(Schema.COL_TIMESTAMP)
				&& values.containsKey(Schema.COL_SESSION_ID)) {
			final long rowId = mDbHelper.getWritableDatabase().insert(Schema.TBL_POSITIONS, null, values);
			if (rowId > 0) {
				final Uri positionUri = ContentUris.withAppendedId(baseUri, rowId);
				getContext().getContentResolver().notifyChange(ContentProvider.CONTENT_URI_POSITION, null);
				return positionUri;
			}
		} else {
			throw new IllegalArgumentException("mandatory column missing");
		}
		return null;
	}

	/**
	 * Inserts a log record
	 * @param baseUri
	 * @param values
	 * @return
	 */
	private Uri insertLog(final Uri baseUri, final ContentValues values) {
		if (values.containsKey(Schema.COL_TIMESTAMP)) {
			final long rowId = mDbHelper.getWritableDatabase().insert(Schema.TBL_LOGS, null, values);
			if (rowId > 0) {
				final Uri logUri = ContentUris.withAppendedId(baseUri, rowId);
				getContext().getContentResolver().notifyChange(ContentProvider.CONTENT_URI_LOGFILE, null);
				return logUri;
			}
		} else {
			throw new IllegalArgumentException("mandatory column missing");
		}
		return null;
	}

	/**
	 * Inserts a session record
	 * @param baseUri
	 * @param values
	 * @return
	 */
	private Uri insertSession(final Uri baseUri, final ContentValues values) {
		// Check that mandatory columns are present.
		//if (values.containsKey(Schema.COL_TIMESTAMP)) {
		final long rowId = mDbHelper.getWritableDatabase().insert(Schema.TBL_SESSIONS, null, values);
		if (rowId > 0) {
			final Uri sessionUri = ContentUris.withAppendedId(baseUri, rowId);
			getContext().getContentResolver().notifyChange(ContentProvider.CONTENT_URI_SESSION, null);
			return sessionUri;
		}
		//} else {
		// throw new IllegalArgumentasdasdException("mandatory column missing");
		//}
		return null;
	}

	/**
	 * QUERY Statements
	 */
	@Override
	public final Cursor query(final Uri uri, final String[] projection, final String selectionIn, final String[] selectionArgsIn, final String sortOrder) {
		// Log.v(TAG, "query called, uri " + uri.toString());

		// Select which URI/datatype was requested
		switch (uriMatcher.match(uri)) {
			case Schema.URI_CODE_WIFIS:
				// Returns all recorded wifis.
				return queryTable(ContentProvider.CONTENT_URI_WIFI, Schema.TBL_WIFIS, projection, selectionIn, selectionArgsIn, sortOrder, null, null);
			case Schema.URI_CODE_WIFIS_EXTENDED:
				// Returns all wifis including position data
				return queryTable(ContentProvider.CONTENT_URI_WIFI_EXTENDED, Schema.VIEW_WIFIS_EXTENDED, projection, selectionIn, selectionArgsIn, sortOrder, null, null);
			case Schema.URI_CODE_CELLS_EXTENDED:
				// Returns all wifis including position data
				return queryTable(ContentProvider.CONTENT_URI_CELL_EXTENDED, Schema.VIEW_CELLS_EXTENDED, projection, selectionIn, selectionArgsIn, sortOrder, null, null);

			case Schema.URI_CODE_WIFI_OVERVIEW:
				/**
				 *  if several measurements for specific wifi bssid are available only strongest
				 *  measurement (criteria level) is returned
				 *  @author http://stackoverflow.com/questions/3800551/sql-select-first-row-in-each-group-by-group
				 */

                String tablesWifis = Schema.TBL_WIFIS + " as w "
                        + " JOIN " + Schema.TBL_POSITIONS + " as b ON " + Schema.COL_BEGIN_POSITION_ID + " = b." + Schema.COL_ID;

                String columnsWifis[] = {
                        "w.rowid as " + Schema.COL_ID,
                        "w." + Schema.COL_BSSID,
                        "w." + Schema.COL_MD5_SSID,
                        "w." + Schema.COL_SSID,
                        "MAX(" + Schema.COL_LEVEL + ")",
                        "w." + Schema.COL_CAPABILITIES,
                        "w." + Schema.COL_FREQUENCY,
                        "w." + Schema.COL_TIMESTAMP,
                        "w." + Schema.COL_BEGIN_POSITION_ID,
                        "w." + Schema.COL_END_POSITION_ID,
                        "w." + Schema.COL_KNOWN_WIFI
                };
                String orderByWifis = "w." + Schema.COL_TIMESTAMP;
                String groupByWifis = "w." + Schema.COL_BSSID + ", w." + Schema.COL_MD5_SSID;
                return queryTable(uri, tablesWifis, columnsWifis,
                        addColumntoSelection("w." + Schema.COL_SESSION_ID, selectionIn),
                        addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn),
                        orderByWifis,
                        groupByWifis,
                        null);
			case Schema.URI_CODE_WIFI_ID:
				// returns given wifi
				return queryTable(ContentProvider.CONTENT_URI_WIFI,
                        Schema.TBL_WIFIS, projection,
                        addColumntoSelection(Schema.COL_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn),
                        sortOrder,
                        null,
                        null);
			case Schema.URI_CODE_WIFIS_BY_SESSION:
				// returns wifis for given session.
				return queryTable(ContentProvider.CONTENT_URI_WIFI, Schema.TBL_WIFIS, projection, addColumntoSelection(Schema.COL_SESSION_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
			case Schema.URI_CODE_CELLS:
				//  returns all recorded cells.
				return queryTable(ContentProvider.CONTENT_URI_CELL, Schema.TBL_CELLS, projection, selectionIn, selectionArgsIn, sortOrder, null, null);
			case Schema.URI_CODE_CELL_OVERVIEW:
				/**
				 *  if several measurements for specific cell are available only strongest
				 *  measurement (criteria level) is returned
				 *  http://stackoverflow.com/questions/3800551/sql-select-first-row-in-each-group-by-group
				 */

				String tablesCells = Schema.TBL_CELLS + " as c "
						+ " JOIN " + Schema.TBL_POSITIONS + " as b ON " + Schema.COL_BEGIN_POSITION_ID + " = b." + Schema.COL_ID;

                String columnsCells[] = {
						"c.rowid as " + Schema.COL_ID,
                        Schema.COL_LOGICAL_CELLID ,
                        Schema.COL_ACTUAL_CELLID ,
                        Schema.COL_PSC ,
                        Schema.COL_CDMA_BASEID ,
                        Schema.COL_CDMA_SYSTEMID,
                        Schema.COL_CDMA_NETWORKID ,
                        Schema.COL_OPERATORNAME,
                        Schema.COL_OPERATOR,
                        Schema.COL_MCC ,
                        Schema.COL_MNC ,
                        Schema.COL_AREA ,
                        Schema.COL_PSC ,
                        Schema.COL_NETWORKTYPE ,
                        Schema.COL_IS_SERVING,
                        " MAX(" + Schema.COL_STRENGTHDBM + ") "
                };

                String whereCells =  "c." + Schema.COL_SESSION_ID + " = ? AND " + Schema.COL_LOGICAL_CELLID + " > ?";
                String[] whereArgs = {uri.getLastPathSegment(), "-1"};
                String orderByCells = Schema.COL_IS_SERVING + " DESC";
                String groupByCells = "c." + Schema.COL_LOGICAL_CELLID + ", "
                        + Schema.COL_PSC + ", " + Schema.COL_CDMA_SYSTEMID + ", " + Schema.COL_CDMA_NETWORKID + ", " + Schema.COL_CDMA_BASEID + ", "
                        + Schema.COL_IS_SERVING;

                return queryTable(uri, tablesCells, columnsCells,
                        whereCells,
                        whereArgs,
                        orderByCells,
                        groupByCells,
                        null);

			case Schema.URI_CODE_CELL_ID:
				//  Returns given cell.
				return queryTable(ContentProvider.CONTENT_URI_CELL, Schema.TBL_CELLS, projection, addColumntoSelection(Schema.COL_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
			case Schema.URI_CODE_CELLS_BY_SESSION:
				// Returns cells for given session.
				return queryTable(ContentProvider.CONTENT_URI_CELL, Schema.TBL_CELLS, projection, addColumntoSelection(Schema.COL_SESSION_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
			case Schema.URI_CODE_POSITIONS:
				// Returns all positions.
				return queryTable(ContentProvider.CONTENT_URI_POSITION, Schema.TBL_POSITIONS, projection, selectionIn, selectionArgsIn, sortOrder, null, null);
			case Schema.URI_CODE_POSITION_ID:
				// returns given position
				return queryTable(ContentProvider.CONTENT_URI_POSITION, Schema.TBL_POSITIONS, projection, addColumntoSelection(Schema.COL_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
            case Schema.URI_CODE_WAYPOINTS_BY_SESSION:
                // returns session's trackpoints (i.e long press points).
                String column = addColumntoSelection(Schema.COL_SESSION_ID, selectionIn);
                column = addColumntoSelection(Schema.COL_SOURCE, column);
                String[] args = addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn);
                args = addtoSelectionArgs(RadioBeacon.PROVIDER_USER_DEFINED, args);
       		    return queryTable(ContentProvider.CONTENT_URI_POSITION, Schema.TBL_POSITIONS, projection, column, args, sortOrder, null, null);
            case Schema.URI_CODE_LOGS_BY_SESSION:
				// Returns all log files for given session.
				return queryTable(ContentProvider.CONTENT_URI_LOGFILE, Schema.TBL_LOGS, projection, addColumntoSelection(Schema.COL_SESSION_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
			case Schema.URI_CODE_SESSIONS:
				// Returns all log files.
				return queryTable(ContentProvider.CONTENT_URI_SESSION, Schema.TBL_SESSIONS, projection, selectionIn, selectionArgsIn, sortOrder, null, null);
			case Schema.URI_CODE_SESSION_ID:
				return queryTable(ContentProvider.CONTENT_URI_SESSION, Schema.TBL_SESSIONS, projection, addColumntoSelection(Schema.COL_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
			case Schema.URI_CODE_SESSION_ACTIVE:
				return queryTable(ContentProvider.CONTENT_URI_SESSION, Schema.TBL_SESSIONS, projection, addColumntoSelection(Schema.COL_IS_ACTIVE, selectionIn), addtoSelectionArgs("1", selectionArgsIn), sortOrder, null, null);
			default:
				throw new IllegalArgumentException("Unknown URI: " + uri);
		}
	}

	/**
	 * Adds column as first statement to selection string.
	 * Always remember to include column in selectionArgs also
	 * @param colName  column name
	 * @param selectionIn selection string, to which column query will be added
	 * @return SQL select statement
	 */
	private String addColumntoSelection(final String colName, final String selectionIn) {
		String selection = colName + " = ?";
		// Deal with any additional selection info provided by the caller
		if (null != selectionIn) {
			selection += " AND " + selectionIn;
		}
		return selection;
	}

	/**
	 * Adds criteria to (existing) selection arguments
	 * Criteria is always added as FIRST element into (existing) selection arguments
	 * @param selectionArgsIn
	 * @param argValue
	 * @return
	 */
	private String[] addtoSelectionArgs(final String argValue, final String[] selectionArgsIn) {
		String[] selectionArgs = selectionArgsIn;
		List<String> selectionArgsList = new ArrayList<>();

		// add id as first element
		selectionArgsList.add(argValue);
		// Add the callers selection arguments, if any
		if (null != selectionArgsIn) {
			for (final String arg : selectionArgsIn) {
				selectionArgsList.add(arg);
			}
		}
		selectionArgs = selectionArgsList.toArray(new String[0]);
		// Finished with the temporary selection arguments list. release it for GC
		selectionArgsList.clear();
		selectionArgsList = null;
		return selectionArgs;
	}

	/**
	 * @param notifyUri
	 * @param projection
	 * @param selectionIn
	 * @param selectionArgsIn
	 * @param sortOrder
	 * @param limit
	 * @param groupBy
	 * @return
	 */
	private Cursor queryTable(
            final Uri notifyUri,
			final String tableName,
			final String[] projection,
			final String selectionIn,
			final String[] selectionArgsIn,
			final String sortOrder,
            final String groupBy, final String limit) {

		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(tableName);
		final Cursor cursor = qb.query(mDbHelper.getReadableDatabase(), projection, selectionIn, selectionArgsIn, groupBy, null, sortOrder, limit);
		qb = null;

		cursor.setNotificationUri(getContext().getContentResolver(), notifyUri);
		getContext().getContentResolver().notifyChange(notifyUri, null);
		return cursor;
	}

	@Override
	public final int update(final Uri uri, final ContentValues values,
			final String selectionIn, final String[] selectionArgsIn) {
		//Log.v(TAG, "update(), uri=" + uri);

		switch (uriMatcher.match(uri)) {
			case Schema.URI_CODE_SESSIONS:
				// updates all sessions
				return updateTable(uri, Schema.TBL_SESSIONS, values, selectionIn, selectionArgsIn);
			case Schema.URI_CODE_SESSION_ACTIVE:
				// sets active session
				if (values.containsKey(Schema.COL_IS_ACTIVE)) {
					return updateTable(uri, Schema.TBL_SESSIONS, values, selectionIn, selectionArgsIn);
				} else {
					throw new IllegalArgumentException("Mandatory column missing:" + Schema.COL_IS_ACTIVE);
				}
			case Schema.URI_CODE_SESSION_ID:
				return updateTable(uri, Schema.TBL_SESSIONS, values, addColumntoSelection(Schema.COL_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn));
			case Schema.URI_CODE_WIFIS:
				return updateTable(uri, Schema.TBL_WIFIS, values, selectionIn, selectionArgsIn);
			default:
				throw new IllegalArgumentException("Unknown URI: " + uri);
		}
	}

	/**
	 * id is passed as uri's last path segment
	 * @param uri
	 * @param values
	 * @param selection
	 * @param selectionArgs
	 * @return
	 */
	private int updateTable(final Uri uri, final String table, final ContentValues values,
			final String selection, final String[] selectionArgs) {

		final int rows = mDbHelper.getWritableDatabase().update(table, values, selection, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);
		return rows;
	}

	@Override
	public final int delete(final Uri uri, final String selection, final String[] selectionArgs) {
		Log.v(TAG, "delete(), uri=" + uri);

		// Select which data type to delete
		switch (uriMatcher.match(uri)) {
			case Schema.URI_CODE_WIFI_ID:
				// Delete selected wifi and delete all related entities (positions etc.).
				final String wifiId = Long.toString(ContentUris.parseId(uri));
				final int wRows = mDbHelper.getWritableDatabase().delete(Schema.TBL_WIFIS, Schema.COL_ID + " = ?", new String[] {wifiId});
				getContext().getContentResolver().notifyChange(ContentProvider.CONTENT_URI_WIFI, null);
				return wRows;
			case Schema.URI_CODE_SESSION_ID:
				// Deletes selected session.
				final String sessionId = Long.toString(ContentUris.parseId(uri));
				final int sRows = mDbHelper.getWritableDatabase().delete(Schema.TBL_SESSIONS, Schema.COL_ID + " = ?", new String[] {sessionId});
				getContext().getContentResolver().notifyChange(ContentProvider.CONTENT_URI_SESSION, null);
				return sRows;
			case Schema.URI_CODE_SESSIONS:
				// Deletes all sessions.
				final int aRows =  mDbHelper.getWritableDatabase().delete(Schema.TBL_SESSIONS, null, null);
				getContext().getContentResolver().notifyChange(ContentProvider.CONTENT_URI_SESSION, null);
				return aRows;
			default:
				throw new IllegalArgumentException("Unknown URI: " + uri);
		}
	}
}
