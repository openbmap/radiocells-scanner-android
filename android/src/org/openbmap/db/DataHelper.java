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

import org.openbmap.db.model.CellRecord;
import org.openbmap.db.model.LogFile;
import org.openbmap.db.model.PositionRecord;
import org.openbmap.db.model.Session;
import org.openbmap.db.model.WifiRecord;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

/**
 * Data helper for talking to content resolver 
 */
public class DataHelper {

	private static final String TAG = DataHelper.class.getSimpleName();


	/**
	 * ContentResolver to interact with content provider
	 */
	private ContentResolver contentResolver;

	/**
	 * Constructor
	 * 
	 * @param mContext
	 *            Application mContext used for acquiring content resolver
	 */
	public DataHelper(final Context context) {
		contentResolver = context.getContentResolver();
	}

	/**
	 * Persists scan's wifis in database.
	 * @param begin	begin position which is equal across all wifis
	 * @param end end posistion which is equal across all wifis
	 * @param wifis list of wifis sharing same begin and end position (i.e. all wifis from one scan)
	 */	
	public final void storeWifiScanResults(final PositionRecord begin, final PositionRecord end, final ArrayList<WifiRecord> wifis) {

		if (wifis == null || wifis.size() == 0) {
			return;
		}

		ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();

		// saving begin position
		operations.add(ContentProviderOperation.newInsert(RadioBeaconContentProvider.CONTENT_URI_POSITION)
				.withValue(Schema.COL_LATITUDE, begin.getLatitude())
				.withValue(Schema.COL_LONGITUDE, begin.getLongitude())
				.withValue(Schema.COL_ALTITUDE, begin.getAltitude())
				.withValue(Schema.COL_TIMESTAMP, begin.getOpenBmapTimestamp())
				.withValue(Schema.COL_ACCURACY, begin.getAccuracy())
				.withValue(Schema.COL_BEARING, begin.getBearing())
				.withValue(Schema.COL_SPEED, begin.getSpeed())
				.withValue(Schema.COL_SESSION_ID, begin.getSession())
				.withValue(Schema.COL_SOURCE, begin.getSource())
				.build());

		// saving end position
		operations.add(ContentProviderOperation.newInsert(RadioBeaconContentProvider.CONTENT_URI_POSITION)
				.withValue(Schema.COL_LATITUDE, end.getLatitude())
				.withValue(Schema.COL_LONGITUDE, end.getLongitude())
				.withValue(Schema.COL_ALTITUDE, end.getAltitude())
				.withValue(Schema.COL_TIMESTAMP, end.getOpenBmapTimestamp())
				.withValue(Schema.COL_ACCURACY, end.getAccuracy())
				.withValue(Schema.COL_BEARING, end.getBearing())
				.withValue(Schema.COL_SPEED, end.getSpeed())
				.withValue(Schema.COL_SESSION_ID, end.getSession())
				.withValue(Schema.COL_SOURCE, end.getSource())
				.build());

		for (WifiRecord wifi : wifis) {
			operations.add(ContentProviderOperation.newInsert(RadioBeaconContentProvider.CONTENT_URI_WIFI)
					.withValue(Schema.COL_BSSID, wifi.getBssid())
					.withValue(Schema.COL_SSID, wifi.getSsid())
					.withValue(Schema.COL_MD5_SSID, wifi.getMd5Ssid())
					.withValue(Schema.COL_CAPABILITIES, wifi.getCapabilities())
					.withValue(Schema.COL_FREQUENCY, wifi.getFrequency())
					.withValue(Schema.COL_LEVEL, wifi.getLevel())
					.withValue(Schema.COL_TIMESTAMP, wifi.getOpenBmapTimestamp())
					.withValueBackReference (Schema.COL_BEGIN_POSITION_ID, 0) /* Index is 0 because Foo A is the first operation in the array*/
					.withValueBackReference (Schema.COL_END_POSITION_ID, 1)
					.withValue(Schema.COL_SESSION_ID, wifi.getSessionId())
					.withValue(Schema.COL_IS_NEW_WIFI, wifi.isNew() ? 1 : 0)
					.build());	
		}

		try {
			ContentProviderResult[] results = contentResolver.applyBatch("org.openbmap.provider", operations);
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (OperationApplicationException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Loads session's wifis.
	 * @param id
	 * 			Session to return
	 * @param sort	Sort criteria
	 * @return ArrayList<WifiRecord> with all wifis for given session
	 */
	public final ArrayList<WifiRecord> loadWifisBySession(final long id, final String sort) {
		ArrayList<WifiRecord> wifis = new ArrayList<WifiRecord>();

		Cursor ca = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(
				RadioBeaconContentProvider.CONTENT_URI_WIFI, RadioBeaconContentProvider.CONTENT_URI_SESSION_SUFFIX), id),
				null, null, null, sort);

		// Performance tweaking: don't call ca.getColumnIndex on each iteration 
		final int columnIndex = ca.getColumnIndex(Schema.COL_BSSID);
		final int columnIndex2 = ca.getColumnIndex(Schema.COL_SSID);
		final int columnIndex3 = ca.getColumnIndex(Schema.COL_CAPABILITIES);
		final int columnIndex4 = ca.getColumnIndex(Schema.COL_FREQUENCY);
		final int columnIndex5 = ca.getColumnIndex(Schema.COL_LEVEL);
		final int columnIndex6 = ca.getColumnIndex(Schema.COL_TIMESTAMP);
		final int columnIndex7 = ca.getColumnIndex(Schema.COL_BEGIN_POSITION_ID);
		final int columnIndex8 = ca.getColumnIndex(Schema.COL_END_POSITION_ID);

		while (ca.moveToNext()) {
			WifiRecord wifi = new WifiRecord();
			wifi.setBssid(ca.getString(columnIndex));
			wifi.setSsid(ca.getString(columnIndex2));
			wifi.setCapabilities(ca.getString(columnIndex3));
			wifi.setFrequency(ca.getInt(columnIndex4));
			wifi.setLevel(ca.getInt(columnIndex5));
			wifi.setOpenBmapTimestamp(ca.getLong(columnIndex6));

			// TODO: not too safe ..
			wifi.setBeginPosition(loadPositions(ca.getString(columnIndex7)).get(0));
			// TODO: not too safe ..
			wifi.setEndPosition(loadPositions(ca.getString(columnIndex8)).get(0));

			wifis.add(wifi);
		}
		ca.close();
		return wifis;
	}

	/**
	 * Counts number of wifis in session.
	 * @param session
	 * @return number of wifis
	 */
	public final int countWifis(final int session) {
		Cursor ca = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(
				RadioBeaconContentProvider.CONTENT_URI_WIFI, RadioBeaconContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), session),
				new String[]{Schema.COL_ID}, null, null, null);
		return ca.getCount();
	}

	/**
	 * Loads single wifi from TBL_WIFIS
	 * @param id
	 * 			wifi id to return
	 * @return WifiRecord
	 */
	public final WifiRecord loadWifiById(final int id) {
		WifiRecord wifi = null;

		Cursor ca = contentResolver.query(ContentUris.withAppendedId(RadioBeaconContentProvider.CONTENT_URI_WIFI, id) , null, null, null, null);
		//Log.d(TAG, "getWifiMeasurement returned " + ca.getCount() + " records");
		if (ca.moveToNext()) {
			wifi = new WifiRecord(
					ca.getString(ca.getColumnIndex(Schema.COL_BSSID)),
					ca.getString(ca.getColumnIndex(Schema.COL_SSID)),
					ca.getString(ca.getColumnIndex(Schema.COL_CAPABILITIES)),
					ca.getInt(ca.getColumnIndex(Schema.COL_FREQUENCY)),
					ca.getInt(ca.getColumnIndex(Schema.COL_LEVEL)),
					ca.getLong(ca.getColumnIndex(Schema.COL_TIMESTAMP)),
					// TODO: definitely not safe ..
					loadPositions(ca.getString(ca.getColumnIndex(Schema.COL_BEGIN_POSITION_ID))).get(0),
					// TODO: definitely not safe ..
					loadPositions(ca.getString(ca.getColumnIndex(Schema.COL_END_POSITION_ID))).get(0));
		}
		ca.close();
		return wifi;
	}


	/**
	 * Gets wifis by BSSID
	 * @param bssid
	 * @return Array (of measurements) for that BSSID
	 */
	public final ArrayList<WifiRecord> loadWifisByBssid(final String bssid) {
		ArrayList<WifiRecord> wifis = new ArrayList<WifiRecord>();

		Cursor ca = contentResolver.query(RadioBeaconContentProvider.CONTENT_URI_WIFI, null, Schema.COL_BSSID + " = \"" + bssid + "\"", null, null);

		// Performance tweaking: don't call ca.getColumnIndex on each iteration 
		final int columnIndex = ca.getColumnIndex(Schema.COL_BSSID);
		final int columnIndex2 = ca.getColumnIndex(Schema.COL_SSID);
		final int columnIndex3 = ca.getColumnIndex(Schema.COL_CAPABILITIES);
		final int columnIndex4 = ca.getColumnIndex(Schema.COL_FREQUENCY);
		final int columnIndex5 = ca.getColumnIndex(Schema.COL_LEVEL);
		final int columnIndex6 = ca.getColumnIndex(Schema.COL_TIMESTAMP);
		final int columnIndex7 = ca.getColumnIndex(Schema.COL_BEGIN_POSITION_ID);
		final int columnIndex8 = ca.getColumnIndex(Schema.COL_END_POSITION_ID);

		while (ca.moveToNext()) {
			WifiRecord wifi = new WifiRecord();
			wifi.setBssid(ca.getString(columnIndex));
			wifi.setSsid(ca.getString(columnIndex2));
			wifi.setCapabilities(ca.getString(columnIndex3));
			wifi.setFrequency(ca.getInt(columnIndex4));
			wifi.setLevel(ca.getInt(columnIndex5));
			wifi.setOpenBmapTimestamp(ca.getLong(columnIndex6));

			// TODO: not too safe ..
			wifi.setBeginPosition(loadPositions(ca.getString(columnIndex7)).get(0));
			// TODO: not too safe ..
			wifi.setEndPosition(loadPositions(ca.getString(columnIndex8)).get(0));

			wifis.add(wifi);
		}
		ca.close();
		return wifis;
	}


	/**
	 * Returns only strongest measurement for each wifi from TBL_WIFIS.
	 * @return Arraylist<WifiRecord>
	 */
	public final ArrayList<WifiRecord> loadWifisOverview(final long session) {
		ArrayList<WifiRecord> wifis = new ArrayList<WifiRecord>();

		Cursor ca = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(RadioBeaconContentProvider.CONTENT_URI_WIFI,
				RadioBeaconContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), session),
				null, null, null, null);

		// Performance tweaking: don't call ca.getColumnIndex on each iteration 
		final int columnIndex = ca.getColumnIndex(Schema.COL_BSSID);
		final int columnIndex2 = ca.getColumnIndex(Schema.COL_SSID);
		final int columnIndex3 = ca.getColumnIndex(Schema.COL_CAPABILITIES);
		final int columnIndex4 = ca.getColumnIndex(Schema.COL_FREQUENCY);
		final int columnIndex5 = ca.getColumnIndex(Schema.COL_MAX_LEVEL);
		final int columnIndex6 = ca.getColumnIndex(Schema.COL_TIMESTAMP);
		final int columnIndex7 = ca.getColumnIndex(Schema.COL_BEGIN_POSITION_ID);
		final int columnIndex8 = ca.getColumnIndex(Schema.COL_END_POSITION_ID);

		while (ca.moveToNext()) {	
			WifiRecord wifi = new WifiRecord();
			wifi.setBssid(ca.getString(columnIndex));
			wifi.setSsid(ca.getString(columnIndex2));
			wifi.setCapabilities(ca.getString(columnIndex3));
			wifi.setFrequency(ca.getInt(columnIndex4));
			wifi.setLevel(ca.getInt(columnIndex5));
			wifi.setOpenBmapTimestamp(ca.getLong(columnIndex6));

			// TODO: not too safe ..
			wifi.setBeginPosition(loadPositions(ca.getString(columnIndex7)).get(0));
			// TODO: not too safe ..
			wifi.setEndPosition(loadPositions(ca.getString(columnIndex8)).get(0));

			wifis.add(wifi);
		}

		ca.close();
		return wifis;
	}

	/**
	 * Adds new session to TBL_SESSIONS.
	 * default values:
	 * 		COL_CREATED_AT = current time
	 * 		COL_HAS_BEEN_EXPORTED = 0 (false)
	 * @return uri of inserted row
	 */
	public final Uri storeSession(final Session newSession) {
		Log.d(TAG, "Storing new session " + newSession.toString());

		ContentValues values = new ContentValues();
		// id is auto-assigned, remember to update new session manually
		values.put(Schema.COL_CREATED_AT, newSession.getCreatedAt());
		values.put(Schema.COL_LAST_UPDATED, newSession.getLastUpdated());
		values.put(Schema.COL_DESCRIPTION, newSession.getDescription());
		values.put(Schema.COL_HAS_BEEN_EXPORTED, newSession.hasBeenExported());
		values.put(Schema.COL_IS_ACTIVE, newSession.isActive());
		values.put(Schema.COL_NUMBER_OF_CELLS, 0);
		values.put(Schema.COL_NUMBER_OF_WIFIS, 0);
		return contentResolver.insert(RadioBeaconContentProvider.CONTENT_URI_SESSION, values);
	}

	/**
	 * Persists one scan with cell and neighbor records in database.
	 * Automatically chooses, whether GSM or CDMA schema is used
	 * @param begin position which is equal across all cells (i.e. serving cell + neighbors)
	 * @param cells list of cells sharing same begin and end position (i.e. all cells from one scan)
	 */
	public final void storeCellsScanResults(final ArrayList<CellRecord> cells, final PositionRecord begin, final PositionRecord end) {
		if (cells == null || cells.size() == 0) {
			return;
		}

		// add a new ContentProviderOperation
		ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();

		//saving begin position
		operations.add(ContentProviderOperation.newInsert(RadioBeaconContentProvider.CONTENT_URI_POSITION)
				.withValue(Schema.COL_LATITUDE, begin.getLatitude())
				.withValue(Schema.COL_LONGITUDE, begin.getLongitude())
				.withValue(Schema.COL_ALTITUDE, begin.getAltitude())
				.withValue(Schema.COL_TIMESTAMP, begin.getOpenBmapTimestamp())
				.withValue(Schema.COL_ACCURACY, begin.getAccuracy())
				.withValue(Schema.COL_BEARING, begin.getBearing())
				.withValue(Schema.COL_SPEED, begin.getSpeed())
				.withValue(Schema.COL_SESSION_ID, begin.getSession())
				.withValue(Schema.COL_SOURCE, begin.getSource())
				.build());

		// saving end position
		operations.add(ContentProviderOperation.newInsert(RadioBeaconContentProvider.CONTENT_URI_POSITION)
				.withValue(Schema.COL_LATITUDE, end.getLatitude())
				.withValue(Schema.COL_LONGITUDE, end.getLongitude())
				.withValue(Schema.COL_ALTITUDE, end.getAltitude())
				.withValue(Schema.COL_TIMESTAMP, end.getOpenBmapTimestamp())
				.withValue(Schema.COL_ACCURACY, end.getAccuracy())
				.withValue(Schema.COL_BEARING, end.getBearing())
				.withValue(Schema.COL_SPEED, end.getSpeed())
				.withValue(Schema.COL_SESSION_ID, end.getSession())
				.withValue(Schema.COL_SOURCE, end.getSource())
				.build());

		for (CellRecord cell: cells) {
			if (!cell.isCdma()) {
				// store GSM cell
				operations.add(ContentProviderOperation.newInsert(RadioBeaconContentProvider.CONTENT_URI_CELL)
						.withValue(Schema.COL_NETWORKTYPE, cell.getNetworkType())
						.withValue(Schema.COL_IS_CDMA, cell.isCdma())
						.withValue(Schema.COL_IS_SERVING, cell.isServing())
						.withValue(Schema.COL_IS_NEIGHBOR, cell.isNeighbor())
						.withValue(Schema.COL_CELLID, cell.getCid())
						.withValue(Schema.COL_LAC, cell.getLac())
						.withValue(Schema.COL_MCC, cell.getMcc())
						.withValue(Schema.COL_MNC, cell.getMnc())
						.withValue(Schema.COL_OPERATORNAME, cell.getOperatorName())
						.withValue(Schema.COL_OPERATOR, cell.getOperator())
						.withValue(Schema.COL_STRENGTHDBM, cell.getStrengthdBm())
						.withValue(Schema.COL_TIMESTAMP, cell.getOpenBmapTimestamp())
						.withValueBackReference (Schema.COL_BEGIN_POSITION_ID, 0) // Index is 0 because first operation stores cell position
						.withValueBackReference (Schema.COL_END_POSITION_ID, 1)
						.withValue(Schema.COL_SESSION_ID, cell.getSessionId())
						// set unused (CDMA) fields to default
						.withValue(Schema.COL_BASEID, -1)
						.withValue(Schema.COL_NETWORKID, -1)
						.withValue(Schema.COL_SYSTEMID, -1)
						.withValue(Schema.COL_BASEID, -1)
						.withValue(Schema.COL_PSC, -1)
						.build());	
			} else {
				// store CDMA cell
				operations.add(ContentProviderOperation.newInsert(RadioBeaconContentProvider.CONTENT_URI_CELL)
						.withValue(Schema.COL_NETWORKTYPE, cell.getNetworkType())
						.withValue(Schema.COL_IS_CDMA, cell.isCdma())
						.withValue(Schema.COL_IS_SERVING, cell.isServing())
						.withValue(Schema.COL_IS_NEIGHBOR, cell.isNeighbor())
						.withValue(Schema.COL_LAC, cell.getLac())
						.withValue(Schema.COL_MCC, cell.getMcc())
						.withValue(Schema.COL_MNC, cell.getMnc())
						.withValue(Schema.COL_PSC, cell.getPsc())
						.withValue(Schema.COL_BASEID, cell.getBaseId())
						.withValue(Schema.COL_NETWORKID, cell.getNetworkId())
						.withValue(Schema.COL_SYSTEMID, cell.getSystemId())
						.withValue(Schema.COL_PSC, cell.getPsc())
						.withValue(Schema.COL_PSC, cell.getPsc())
						.withValue(Schema.COL_OPERATORNAME, cell.getOperatorName())
						.withValue(Schema.COL_OPERATOR, cell.getOperator())
						.withValue(Schema.COL_STRENGTHDBM, cell.getStrengthdBm())
						.withValue(Schema.COL_TIMESTAMP, cell.getOpenBmapTimestamp())
						.withValueBackReference (Schema.COL_BEGIN_POSITION_ID, 0) // Index is 0 because first operation stores cell position
						.withValueBackReference (Schema.COL_END_POSITION_ID, 1)
						.withValue(Schema.COL_SESSION_ID, cell.getSessionId())

						// set unused (GSM) fields to -1
						.withValue(Schema.COL_CELLID, -1)
						.withValue(Schema.COL_LAC, -1)
						.build());	
			}
		}

		try {
			ContentProviderResult[] results = contentResolver.applyBatch("org.openbmap.provider", operations);
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (OperationApplicationException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Loads CellRecord from database.
	 * @param id
	 * @return
	 */
	public final CellRecord loadCellById(final int id) {
		CellRecord cell = null;

		Cursor ca = contentResolver.query(ContentUris.withAppendedId(RadioBeaconContentProvider.CONTENT_URI_CELL, id) , null, null, null, null);
		if (ca.moveToNext()) {
			cell = cursorToCell(ca);
		}
		ca.close();
		return cell;
	}

	/**
	 * Loads session's cells.
	 * @param session Session Id
	 * @param sort	Sort criteria
	 * @return ArrayList<CellRecord> with all cells for given session
	 */
	public final ArrayList<CellRecord> loadCellsBySession(final long session, final String sort) {
		ArrayList<CellRecord> cells = new ArrayList<CellRecord>();

		Cursor ca = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(
				RadioBeaconContentProvider.CONTENT_URI_CELL, RadioBeaconContentProvider.CONTENT_URI_SESSION_SUFFIX), session),
				null, null, null, sort);

		while (ca.moveToNext()) {
			cells.add(cursorToCell(ca));
		}

		ca.close();
		return cells;
	}

	/**
	 * Creates a CellRecord from cursor row.
	 * @param cursor 
	 * @return CellRecord
	 */
	private CellRecord cursorToCell(final Cursor cursor) {
		CellRecord cell = new CellRecord();
		final int colNetworkType = cursor.getColumnIndex(Schema.COL_NETWORKTYPE);
		final int colIsCdma = cursor.getColumnIndex(Schema.COL_IS_CDMA);
		final int colIsServing = cursor.getColumnIndex(Schema.COL_IS_SERVING);
		final int colIsNeigbor = cursor.getColumnIndex(Schema.COL_IS_NEIGHBOR);
		final int colCellId = cursor.getColumnIndex(Schema.COL_CELLID);
		final int colPsc = cursor.getColumnIndex(Schema.COL_PSC);
		final int colOperatorName = cursor.getColumnIndex(Schema.COL_OPERATORNAME);
		final int colOperator = cursor.getColumnIndex(Schema.COL_OPERATOR);
		final int colMcc = cursor.getColumnIndex(Schema.COL_MCC);
		final int colMnc = cursor.getColumnIndex(Schema.COL_MNC);
		final int colLac = cursor.getColumnIndex(Schema.COL_LAC);
		final int colBaseId = cursor.getColumnIndex(Schema.COL_BASEID);
		final int colNetworkId = cursor.getColumnIndex(Schema.COL_NETWORKID);
		final int colSystemId = cursor.getColumnIndex(Schema.COL_SYSTEMID);
		final int colStrengthDbm = cursor.getColumnIndex(Schema.COL_STRENGTHDBM);
		final int colTimestamp = cursor.getColumnIndex(Schema.COL_TIMESTAMP);
		final int colBeginPositionId = cursor.getColumnIndex(Schema.COL_BEGIN_POSITION_ID);
		final int colEndPositionId = cursor.getColumnIndex(Schema.COL_END_POSITION_ID);
		final int columnIndex18 = cursor.getColumnIndex(Schema.COL_SESSION_ID);

		cell.setNetworkType(cursor.getInt(colNetworkType));
		cell.setIsCdma(cursor.getInt(colIsCdma) != 0); 
		cell.setIsServing(cursor.getInt(colIsServing) != 0); 
		cell.setIsNeighbor(cursor.getInt(colIsNeigbor) != 0); 
		cell.setCid(cursor.getInt(colCellId));
		cell.setPsc(cursor.getInt(colPsc)); 
		cell.setOperatorName(cursor.getString(colOperatorName));
		cell.setOperator(cursor.getString(colOperator)); 
		cell.setMcc(cursor.getString(colMcc)); 
		cell.setMnc(cursor.getString(colMnc));
		cell.setLac(cursor.getInt(colLac)); 
		cell.setBaseId(cursor.getString(colBaseId));
		cell.setNetworkId(cursor.getString(colNetworkId));
		cell.setSystemId(cursor.getString(colSystemId));
		cell.setStrengthdBm(cursor.getInt(colStrengthDbm)); 
		cell.setOpenBmapTimestamp(cursor.getLong(colTimestamp)); 
		// TODO: dirty ...
		cell.setBeginPosition(loadPositions(cursor.getString(colBeginPositionId)).get(0));
		cell.setEndPosition(loadPositions(cursor.getString(colEndPositionId)).get(0));
		cell.setSessionId(cursor.getInt(columnIndex18));

		return cell;
	}

	/**
	 * Counts session's number of cells.
	 * @param id
	 * @return number of wifis
	 */
	public final int countCells(final long id) {
		Cursor ca = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(
				RadioBeaconContentProvider.CONTENT_URI_CELL, RadioBeaconContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), id),
				new String[]{Schema.COL_ID}, null, null, null);
		return ca.getCount();
	}

	/**
	 * Loads positions from database.
	 * @param id
	 * 			Position id to return. If no id is provided, all positions are returned.
	 * @return ArrayList<PositionRecord>
	 */
	public final ArrayList<PositionRecord> loadPositions(final String id) {
		ArrayList<PositionRecord> positions = new ArrayList<PositionRecord>();
		String selection = null;
		List<String> selectionArgs = new ArrayList<String>();
		if (id != null) {
			//Log.d(TAG, "Loading single position with id " + id);
			selection = Schema.COL_ID + " = ?";
			selectionArgs.add(id);
		}

		Cursor ca = contentResolver.query(RadioBeaconContentProvider.CONTENT_URI_POSITION, null, selection, (String[]) selectionArgs.toArray(new String[0]), null);

		while (ca.moveToNext()) {
			positions.add(positionFromCursor(ca));
		}

		ca.close();
		return positions;
	}

	public final ArrayList<PositionRecord> loadPositionsWithin(final int session, final double minLat, final double maxLat, final double minLon, final double maxLon) {
		ArrayList<PositionRecord> positions = new ArrayList<PositionRecord>();
		String selection = Schema.COL_SESSION_ID + " = ? AND (" + Schema.COL_LATITUDE + " > ? AND " + Schema.COL_LATITUDE + " < ?) AND ("
				+ Schema.COL_LONGITUDE + " > ? AND " + Schema.COL_LONGITUDE + " < ?)";
		List<String> selectionArgs = new ArrayList<String>();
		selectionArgs.add(String.valueOf(session));
		
		selectionArgs.add(String.valueOf(minLat));
		selectionArgs.add(String.valueOf(maxLat));

		selectionArgs.add(String.valueOf(minLon));
		selectionArgs.add(String.valueOf(maxLon));

		Cursor ca = contentResolver.query(RadioBeaconContentProvider.CONTENT_URI_POSITION, null, selection, (String[]) selectionArgs.toArray(new String[0]), Schema.COL_TIMESTAMP);

		while (ca.moveToNext()) {
			positions.add(positionFromCursor(ca));
		}

		ca.close();
		return positions;

	}

	/**
	 * Creates PositionRecord from cursor.
	 * @param cursor
	 * @return
	 */
	private PositionRecord positionFromCursor(final Cursor cursor) {

		// Performance tweaking: don't call ca.getColumnIndex on each iteration 
		final int columnIndex = cursor.getColumnIndex(Schema.COL_LATITUDE);
		final int columnIndex2 = cursor.getColumnIndex(Schema.COL_LONGITUDE);
		final int columnIndex3 = cursor.getColumnIndex(Schema.COL_ALTITUDE);
		final int columnIndex4 = cursor.getColumnIndex(Schema.COL_ACCURACY);
		final int columnIndex5 = cursor.getColumnIndex(Schema.COL_TIMESTAMP);
		final int columnIndex6 = cursor.getColumnIndex(Schema.COL_BEARING);
		final int columnIndex7 = cursor.getColumnIndex(Schema.COL_SPEED);
		final int columnIndex8 = cursor.getColumnIndex(Schema.COL_SESSION_ID);
		final int columnIndex9 = cursor.getColumnIndex(Schema.COL_SOURCE);

		PositionRecord position = new PositionRecord();
		position.setLatitude(cursor.getDouble(columnIndex));
		position.setLongitude(cursor.getDouble(columnIndex2));
		position.setAltitude(cursor.getDouble(columnIndex3));
		position.setAccuracy(cursor.getDouble(columnIndex4));
		position.setTimestampByMillis(cursor.getLong(columnIndex5));
		position.setBearing(cursor.getDouble(columnIndex6));
		position.setSpeed(cursor.getDouble(columnIndex7));
		position.setSession(cursor.getInt(columnIndex8));
		position.setSession(cursor.getInt(columnIndex9));
		return position;
	}

	/**
	 * Loads session's log file.
	 * @param id
	 * 		Session id for which log file is returned
	 * @return LogFile
	 */
	public final LogFile loadLogFileBySession(final long id) {
		LogFile logFile = null;

		Cursor ca = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(
				RadioBeaconContentProvider.CONTENT_URI_LOGFILE, RadioBeaconContentProvider.CONTENT_URI_SESSION_SUFFIX), id),
				null, null, null, null);

		if (ca.moveToNext()) {
			logFile = new LogFile(
					ca.getString(ca.getColumnIndex(Schema.COL_MANUFACTURER)),
					ca.getString(ca.getColumnIndex(Schema.COL_MODEL)),
					ca.getString(ca.getColumnIndex(Schema.COL_REVISION)),
					ca.getString(ca.getColumnIndex(Schema.COL_SWID)),
					ca.getString(ca.getColumnIndex(Schema.COL_SWVER)),
					ca.getInt(ca.getColumnIndex(Schema.COL_SESSION_ID)));
		}

		ca.close();
		return logFile;
	}

	/**
	 * Persists LogFile in database.
	 * @param logFile
	 * @param sessionId
	 * @return
	 */
	public final Uri storeLogFile(final LogFile logFile, final int sessionId) {
		ContentValues values = new ContentValues();
		values.put(Schema.COL_MANUFACTURER, logFile.getManufacturer());
		values.put(Schema.COL_MODEL, logFile.getModel());
		values.put(Schema.COL_REVISION, logFile.getRevision());
		values.put(Schema.COL_SWID, logFile.getSwid());
		values.put(Schema.COL_SWVER, logFile.getSwVersion());
		values.put(Schema.COL_TIMESTAMP, System.currentTimeMillis());
		values.put(Schema.COL_SESSION_ID, sessionId);

		return contentResolver.insert(RadioBeaconContentProvider.CONTENT_URI_LOGFILE, values);
	}

	/**
	 * Loads current session.
	 * @return active session if any, null otherwise
	 */
	public final Session loadActiveSession() {
		Session session = null;
		Cursor ca = contentResolver.query(Uri.withAppendedPath(RadioBeaconContentProvider.CONTENT_URI_SESSION, "active"), null, null, null, null);
		if (ca.moveToNext()) {
			session = new Session(
					ca.getInt(ca.getColumnIndex(Schema.COL_ID)), 
					ca.getLong(ca.getColumnIndex(Schema.COL_CREATED_AT)),
					ca.getLong(ca.getColumnIndex(Schema.COL_LAST_UPDATED)),
					ca.getString(ca.getColumnIndex(Schema.COL_DESCRIPTION)),
					ca.getInt(ca.getColumnIndex(Schema.COL_HAS_BEEN_EXPORTED)),
					ca.getInt(ca.getColumnIndex(Schema.COL_IS_ACTIVE)),
					ca.getInt(ca.getColumnIndex(Schema.COL_NUMBER_OF_CELLS)),
					ca.getInt(ca.getColumnIndex(Schema.COL_NUMBER_OF_WIFIS)));
			return session;
		} else {
			return null;
		}
	}

	/**
	 * Loads session by id.
	 * @param id
	 * 			Session to load
	 * @return On success session is returned, otherwise null.
	 */
	public final Session loadSession(final int id) {
		Session session = null;
		Cursor ca = contentResolver.query(ContentUris.withAppendedId(RadioBeaconContentProvider.CONTENT_URI_SESSION, id), null, null, null, null);
		if (ca.moveToNext()) {
			session = new Session(
					ca.getInt(ca.getColumnIndex(Schema.COL_ID)), 
					ca.getLong(ca.getColumnIndex(Schema.COL_CREATED_AT)),
					ca.getLong(ca.getColumnIndex(Schema.COL_LAST_UPDATED)),
					ca.getString(ca.getColumnIndex(Schema.COL_DESCRIPTION)),
					ca.getInt(ca.getColumnIndex(Schema.COL_HAS_BEEN_EXPORTED)),
					ca.getInt(ca.getColumnIndex(Schema.COL_IS_ACTIVE)),
					ca.getInt(ca.getColumnIndex(Schema.COL_NUMBER_OF_CELLS)),
					ca.getInt(ca.getColumnIndex(Schema.COL_NUMBER_OF_WIFIS)));
		} 

		return session;
	}

	/**
	 * Persists given session in database. If session already exists, session is updated, otherwise new session is created
	 * @param session
	 * 			session to store
	 * @param invalidateActive
	 * 			shall all other active sessions will be deactivated?
	 * @return Number of rows updated.
	 */
	public final int storeSession(final Session session, final boolean invalidateActive) {
		if (session == null) {
			Log.e(TAG, "Error storing session: Session is null");
			return 0;
		}

		if (invalidateActive) {
			// deactivate all active sessions
			invalidateActiveSessions();
		}

		// check, whether session already exists, then update, otherwise save new session
		Cursor ca = contentResolver.query(ContentUris.withAppendedId(RadioBeaconContentProvider.CONTENT_URI_SESSION, session.getId()), null, null, null, null);
		if (!ca.moveToNext()) {
			storeSession(session);
			return 1;
		} else {
			Log.d(TAG, "Updating existing session " + session.getId());
			ContentValues values = new ContentValues();
			values.put(Schema.COL_CREATED_AT, session.getCreatedAt());
			values.put(Schema.COL_LAST_UPDATED, session.getLastUpdated());
			values.put(Schema.COL_DESCRIPTION, session.getDescription());
			values.put(Schema.COL_HAS_BEEN_EXPORTED, session.hasBeenExported());
			values.put(Schema.COL_IS_ACTIVE, session.isActive());
			values.put(Schema.COL_NUMBER_OF_CELLS, session.getNumberOfCells());
			values.put(Schema.COL_NUMBER_OF_WIFIS, session.getNumberOfWifis());

			return contentResolver.update(RadioBeaconContentProvider.CONTENT_URI_SESSION, values,
					Schema.COL_ID + " = ?", new String[]{String.valueOf(session.getId())});
		}
	}

	/**
	 * Deletes a session. This will also delete all objects referencing this session as foreign key
	 * @param id
	 *            Session to delete
	 * @return number of delete rows
	 */
	public final long deleteSession(final long id) {	
		return contentResolver.delete(ContentUris.withAppendedId(RadioBeaconContentProvider.CONTENT_URI_SESSION, id), null, null);
	}

	/**
	 * Deletes all sessions. This will also delete all objects referencing this session as foreign key
	 */
	public final long deleteAllSession() {	
		return contentResolver.delete(RadioBeaconContentProvider.CONTENT_URI_SESSION, null, null);
	}

	/**
	 * Deactivates all active sessions.
	 * @return number of updated rows
	 */
	public final int invalidateActiveSessions() {
		ContentValues values = new ContentValues();
		values.put(Schema.COL_IS_ACTIVE, 0);
		// disables all active sessions
		return contentResolver.update(RadioBeaconContentProvider.CONTENT_URI_SESSION, values,
				Schema.COL_IS_ACTIVE + " > 0" , null);
	}

	/**
	 * Stores position.
	 * This method is only used for separate positions. Wifi and cell positions are added in batch mode
	 * in storeCellsScanResults and storeWifiScanResults
	 * @param mLocation
	 */
	public final Uri storePosition(final PositionRecord pos) {
		ContentValues values = new ContentValues();
		values.put(Schema.COL_LATITUDE, pos.getLatitude());
		values.put(Schema.COL_LONGITUDE, pos.getLongitude());
		values.put(Schema.COL_ALTITUDE, pos.getAltitude());
		values.put(Schema.COL_TIMESTAMP, pos.getOpenBmapTimestamp());
		values.put(Schema.COL_ACCURACY, pos.getAccuracy());
		values.put(Schema.COL_BEARING, pos.getBearing());
		values.put(Schema.COL_SPEED, pos.getSpeed());
		values.put(Schema.COL_SESSION_ID, pos.getSession());
		values.put(Schema.COL_SOURCE, pos.getSource());

		return contentResolver.insert(RadioBeaconContentProvider.CONTENT_URI_POSITION, values);
	}


}
