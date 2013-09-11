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

import java.util.ArrayList;
import java.util.List;

import org.openbmap.RadioBeacon;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

/**
 * Content provider
 * {@link ContentProvider} mechanism.
 */
public class RadioBeaconContentProvider extends ContentProvider {

	private static final String TAG = RadioBeaconContentProvider.class.getSimpleName();

	/**
	 * Authority for Uris
	 */
	public static final String AUTHORITY = RadioBeacon.class.getPackage().getName() + ".provider";

	/**
	 * Uri for wifi measurements
	 */
	public static final Uri CONTENT_URI_WIFI = Uri.parse("content://" + AUTHORITY + "/" + Schema.TBL_WIFIS);

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
	 * Uri Matcher
	 */
	private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

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

		uriMatcher.addURI(AUTHORITY, Schema.TBL_WIFIS, Schema.URI_CODE_WIFIS);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_WIFIS + "/#", Schema.URI_CODE_WIFI_ID);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_WIFIS + "/" + CONTENT_URI_OVERVIEW_SUFFIX + "/#", Schema.URI_CODE_WIFI_OVERVIEW);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_WIFIS + "/" + CONTENT_URI_SESSION_SUFFIX + "/#", Schema.URI_CODE_WIFIS_BY_SESSION);

		uriMatcher.addURI(AUTHORITY, Schema.TBL_POSITIONS, Schema.URI_CODE_POSITIONS);
		uriMatcher.addURI(AUTHORITY, Schema.TBL_POSITIONS + "/#", Schema.URI_CODE_POSITION_ID);		
	}

	/**
	 * Database Helper
	 */
	private DatabaseHelper mDbHelper;

	@Override
	public final boolean onCreate() {
		Log.d(TAG, "OnCreate@RadioBeaconContentprovider called");
		mDbHelper = new DatabaseHelper(getContext());
		// Enable foreign key constraints (per connection)
		mDbHelper.getWritableDatabase().execSQL("PRAGMA foreign_keys = ON"); 
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
				return insertCell(uri, values);
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
	 * @param baseUri
	 * @param values
	 */
	private Uri insertCell(final Uri baseUri, final ContentValues values) {
		if (values.containsKey(Schema.COL_BEGIN_POSITION_ID)
				&& values.containsKey(Schema.COL_TIMESTAMP)) {
			long rowId = mDbHelper.getWritableDatabase().insert(Schema.TBL_CELLS, null, values);
			if (rowId > 0) {
				Uri cellUri = ContentUris.withAppendedId(baseUri, rowId);
				getContext().getContentResolver().notifyChange(RadioBeaconContentProvider.CONTENT_URI_CELL, null);
				return cellUri;
			}
		} else {
			throw new IllegalArgumentException("mandatory column missing");
		}
		return null;
	}

	/**
	 * @param baseUri
	 * @param values
	 * @return 
	 */
	private Uri insertWifiMeasurement(final Uri baseUri, final ContentValues values) {
		if (values.containsKey(Schema.COL_BEGIN_POSITION_ID) && values.containsKey(Schema.COL_END_POSITION_ID) && values.containsKey(Schema.COL_TIMESTAMP)) {
			long rowId = mDbHelper.getWritableDatabase().insert(Schema.TBL_WIFIS, null, values);
			if (rowId > 0) {
				Uri wifiUri = ContentUris.withAppendedId(baseUri, rowId);
				getContext().getContentResolver().notifyChange(RadioBeaconContentProvider.CONTENT_URI_WIFI, null);
				return wifiUri;
			}
		} else {
			throw new IllegalArgumentException("mandatory column missing");
		}
		return null;
	}

	/**
	 * @param baseUri
	 * @param values
	 * @return 
	 */
	private Uri insertPosition(final Uri baseUri, final ContentValues values) {
		if (values.containsKey(Schema.COL_LONGITUDE)
				&& values.containsKey(Schema.COL_LATITUDE)
				&& values.containsKey(Schema.COL_TIMESTAMP)
				&& values.containsKey(Schema.COL_SESSION_ID)) {
			long rowId = mDbHelper.getWritableDatabase().insert(Schema.TBL_POSITIONS, null, values);
			if (rowId > 0) {
				Uri positionUri = ContentUris.withAppendedId(baseUri, rowId);
				getContext().getContentResolver().notifyChange(RadioBeaconContentProvider.CONTENT_URI_POSITION, null);
				return positionUri;
			}
		} else {
			throw new IllegalArgumentException("mandatory column missing");
		}
		return null;
	}

	/**
	 * @param baseUri
	 * @param values
	 * @return 
	 */
	private Uri insertLog(final Uri baseUri, final ContentValues values) {
		if (values.containsKey(Schema.COL_TIMESTAMP)) {
			long rowId = mDbHelper.getWritableDatabase().insert(Schema.TBL_LOGS, null, values);
			if (rowId > 0) {
				Uri logUri = ContentUris.withAppendedId(baseUri, rowId);
				getContext().getContentResolver().notifyChange(RadioBeaconContentProvider.CONTENT_URI_LOGFILE, null);
				return logUri;
			}
		} else {
			throw new IllegalArgumentException("mandatory column missing");
		}
		return null;
	}

	/**
	 * @param baseUri
	 * @param values
	 * @return 
	 */
	private Uri insertSession(final Uri baseUri, final ContentValues values) {
		// Check that mandatory columns are present.
		//if (values.containsKey(Schema.COL_TIMESTAMP)) {
		long rowId = mDbHelper.getWritableDatabase().insert(Schema.TBL_SESSIONS, null, values);
		if (rowId > 0) {
			Uri sessionUri = ContentUris.withAppendedId(baseUri, rowId);
			getContext().getContentResolver().notifyChange(RadioBeaconContentProvider.CONTENT_URI_SESSION, null);
			return sessionUri;
		}
		//} else {
		//	throw new IllegalArgumentasdasdException("mandatory column missing");
		//}
		return null;
	}

	/**
	 * QUERY Statements
	 */
	@Override
	public final Cursor query(final Uri uri, final String[] projection, final String selectionIn, final String[] selectionArgsIn, final String sortOrder) {
		//Log.d(TAG, "Query uri " + uri.toString());
		//String groupBy = null;
		//String limit = null;

		// Select which datatype was requested
		switch (uriMatcher.match(uri)) {
			case Schema.URI_CODE_WIFIS:
				// Returns all recorded wifis.
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_WIFI, Schema.TBL_WIFIS, projection, selectionIn, selectionArgsIn, sortOrder, null, null);
			case Schema.URI_CODE_WIFI_OVERVIEW:
				/**
				 *  if several measurements for specific wifi bssid are available only strongest
				 *  measurement (criteria level) is returned
				 *  @author http://stackoverflow.com/questions/3800551/sql-select-first-row-in-each-group-by-group
				 */
				final String wifiFields = 
						// pseudo-id has to be used, otherwise grouping fails
						"rowid AS _id, "
						+ Schema.COL_BSSID + ", " 
						+ Schema.COL_MD5_SSID + ", " 
						+ Schema.COL_SSID + ", " 
						+ "MAX(" + Schema.COL_LEVEL + "), " 
						+ Schema.COL_CAPABILITIES + ", "  
						+ Schema.COL_FREQUENCY + ", " 
						+ Schema.COL_TIMESTAMP + ", " 
						+ Schema.COL_BEGIN_POSITION_ID + ", " 
						+ Schema.COL_END_POSITION_ID + ", "
						+ Schema.COL_IS_NEW_WIFI + " "; 

				final String wifiOverviewQuery = "SELECT " + wifiFields + " FROM " + Schema.TBL_WIFIS + " "
						+ " WHERE " + Schema.COL_SESSION_ID + " = " + uri.getLastPathSegment() + " GROUP BY " + Schema.COL_BSSID + ", " + Schema.COL_MD5_SSID + " ORDER BY " + sortOrder;
				return queryRaw(wifiOverviewQuery, uri);
			case Schema.URI_CODE_WIFI_ID:
				// returns given wifi
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_WIFI, Schema.TBL_WIFIS, projection, addColumntoSelection(Schema.COL_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
			case Schema.URI_CODE_WIFIS_BY_SESSION:
				// returns wifis for given session.
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_WIFI, Schema.TBL_WIFIS, projection, addColumntoSelection(Schema.COL_SESSION_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
			case Schema.URI_CODE_CELLS:
				/**
				 *  Returns all recorded cells.
				 */
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_CELL, Schema.TBL_CELLS, projection, selectionIn, selectionArgsIn, sortOrder, null, null);

			case Schema.URI_CODE_CELL_OVERVIEW:
				/**
				 *  if several measurements for specific cell are available only strongest
				 *  measurement (criteria level) is returned
				 *  @author http://stackoverflow.com/questions/3800551/sql-select-first-row-in-each-group-by-group
				 */

				// TODO: this probably won't work for CDMA as they don't have cell_id
				final String cellFields =
				// TODO: implement as in wifiFields, i.e. "rowid AS _id, "
						Schema.COL_ID + ", "
						+ Schema.COL_CELLID + ", "
						+ Schema.COL_OPERATORNAME + ", "
						+ Schema.COL_OPERATOR + ", "
						+ Schema.COL_MCC + ", "
						+ Schema.COL_MNC + ", "
						+ Schema.COL_LAC + ", "
						+ Schema.COL_PSC + ", "
						+ Schema.COL_NETWORKTYPE + ", "
						+ Schema.COL_IS_SERVING + ", "
						+ " MAX(" + Schema.COL_STRENGTHDBM + ") ";

				final String cellOverviewQuery = "SELECT " + cellFields + " FROM cells "
						+ " WHERE session_id = " + uri.getLastPathSegment() + " AND " + Schema.COL_IS_SERVING + " = 1 GROUP BY cid"
						+ " UNION SELECT " + cellFields  + " FROM cells "
						+ " WHERE session_id = " + uri.getLastPathSegment() + " AND " + Schema.COL_IS_SERVING + " = 0 GROUP BY cid ORDER BY " + Schema.COL_IS_SERVING + " DESC";

				return queryRaw(cellOverviewQuery, RadioBeaconContentProvider.CONTENT_URI_CELL);
			case Schema.URI_CODE_CELL_ID:
				//  Returns given cell.
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_CELL, Schema.TBL_CELLS, projection, addColumntoSelection(Schema.COL_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
			case Schema.URI_CODE_CELLS_BY_SESSION:
				// Returns cells for given session.
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_CELL, Schema.TBL_CELLS, projection, addColumntoSelection(Schema.COL_SESSION_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
			case Schema.URI_CODE_POSITIONS:
				// Returns all positions.
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_POSITION, Schema.TBL_POSITIONS, projection, selectionIn, selectionArgsIn, sortOrder, null, null);
			case Schema.URI_CODE_POSITION_ID:
				// returns given position
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_POSITION, Schema.TBL_POSITIONS, projection, addColumntoSelection(Schema.COL_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);			
			case Schema.URI_CODE_LOGS_BY_SESSION:
				// Returns all log files for given session.
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_LOGFILE, Schema.TBL_LOGS, projection, addColumntoSelection(Schema.COL_SESSION_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
			case Schema.URI_CODE_SESSIONS:
				// Returns all log files.
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_SESSION, Schema.TBL_SESSIONS, projection, selectionIn, selectionArgsIn, sortOrder, null, null);
			case Schema.URI_CODE_SESSION_ID:
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_SESSION, Schema.TBL_SESSIONS, projection, addColumntoSelection(Schema.COL_ID, selectionIn), addtoSelectionArgs(uri.getLastPathSegment(), selectionArgsIn), sortOrder, null, null);
			case Schema.URI_CODE_SESSION_ACTIVE:
				return queryTable(RadioBeaconContentProvider.CONTENT_URI_SESSION, Schema.TBL_SESSIONS, projection, addColumntoSelection(Schema.COL_IS_ACTIVE, selectionIn), addtoSelectionArgs("1", selectionArgsIn), sortOrder, null, null);
			default:
				throw new IllegalArgumentException("Unknown URI: " + uri);
		}
	}

	/**
	 * @param rawQuery
	 * 		SQL statement
	 * @param notifyUri
	 * 		URI being notified on change
	 */
	private Cursor queryRaw(final String rawQuery, final Uri notifyUri) {

		Log.d(TAG, "Performing rawQuery " + rawQuery);
		Cursor newCursor = null;
		newCursor = mDbHelper.getReadableDatabase().rawQuery(rawQuery, null);
		newCursor.setNotificationUri(getContext().getContentResolver(), notifyUri);

		getContext().getContentResolver().notifyChange(notifyUri, null);
		return newCursor;
	}

	/**
	 * Adds column as first statement to selection string.
	 * Always remember to include column in SelectionArgs also
	 * @param colName
	 * 				column name
	 * @param selectionIn
	 * 				selection string, to which column query will be added
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
		List<String> selectionArgsList = new ArrayList<String>();

		// add id as first element
		selectionArgsList.add(argValue);
		// Add the callers selection arguments, if any
		if (null != selectionArgsIn) {
			for (String arg : selectionArgsIn) {
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
	private Cursor queryTable(final Uri notifyUri,
			final String tableName,
			final String[] projection,
			final String selectionIn,
			final String[] selectionArgsIn,
			final String sortOrder, final String groupBy, final String limit) {

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
		Log.v(TAG, "update(), uri=" + uri);

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

		int rows = mDbHelper.getWritableDatabase().update(table, values, selection, selectionArgs);
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
				String wifiId = Long.toString(ContentUris.parseId(uri));
				int wRows = mDbHelper.getWritableDatabase().delete(Schema.TBL_WIFIS, Schema.COL_ID + " = ?", new String[] {wifiId});
				getContext().getContentResolver().notifyChange(RadioBeaconContentProvider.CONTENT_URI_WIFI, null);
				return wRows;
			case Schema.URI_CODE_SESSION_ID:
				// Deletes selected session.
				String sessionId = Long.toString(ContentUris.parseId(uri));
				int sRows = mDbHelper.getWritableDatabase().delete(Schema.TBL_SESSIONS, Schema.COL_ID + " = ?", new String[] {sessionId});
				getContext().getContentResolver().notifyChange(RadioBeaconContentProvider.CONTENT_URI_SESSION, null);
				return sRows;
			case Schema.URI_CODE_SESSIONS:
				// Deletes all sessions.
				int aRows =  mDbHelper.getWritableDatabase().delete(Schema.TBL_SESSIONS, null, null);
				getContext().getContentResolver().notifyChange(RadioBeaconContentProvider.CONTENT_URI_SESSION, null);
				return aRows;
			default:
				throw new IllegalArgumentException("Unknown URI: " + uri);
		}
	}


}
