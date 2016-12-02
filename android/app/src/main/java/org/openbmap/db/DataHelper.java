package org.openbmap.db;

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

import org.openbmap.RadioBeacon;
import org.openbmap.db.models.CellRecord;
import org.openbmap.db.models.LogFile;
import org.openbmap.db.models.PositionRecord;
import org.openbmap.db.models.Session;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.db.models.WifiRecord.CatalogStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Data helper for talking to content resolver
 */
public class DataHelper {

	private static final String TAG = DataHelper.class.getSimpleName();

	/**
	 * ContentResolver to interact with content provider
	 */
	private final ContentResolver contentResolver;

	/**
	 * Constructor
	 *
	 * @param context (Application) context used for acquiring content resolver
	 */
	public DataHelper(final Context context) {
		contentResolver = context.getApplicationContext().getContentResolver();
	}

	/**
	 * Persists scan's wifis in database.
	 *
	 * @param begin begin position which is equal across all wifis
	 * @param end   end posistion which is equal across all wifis
	 * @param wifis list of wifis sharing same begin and end position (i.e. all wifis from one scan)
	 */
	public final void storeWifiScanResults(final PositionRecord begin, final PositionRecord end, final ArrayList<WifiRecord> wifis) {

		if (wifis == null || wifis.size() == 0) {
			return;
		} else {
			Log.d(TAG, "Inserting " + wifis.size() + " wifis with " + begin + end + " positions");
		}

		final ArrayList<ContentProviderOperation> operations = new ArrayList<>();

		// saving begin position
		operations.add(ContentProviderOperation.newInsert(ContentProvider.CONTENT_URI_POSITION)
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
		operations.add(ContentProviderOperation.newInsert(ContentProvider.CONTENT_URI_POSITION)
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

		for (final WifiRecord wifi : wifis) {
			operations.add(ContentProviderOperation.newInsert(ContentProvider.CONTENT_URI_WIFI)
					.withValue(Schema.COL_BSSID, wifi.getBssid())
                    .withValue(Schema.COL_BSSID_LONG, wifi.getBssidLong())
					.withValue(Schema.COL_SSID, wifi.getSsid())
					.withValue(Schema.COL_MD5_SSID, wifi.getMd5Ssid())
					.withValue(Schema.COL_ENCRYPTION, wifi.getCapabilities())
					.withValue(Schema.COL_FREQUENCY, wifi.getFrequency())
					.withValue(Schema.COL_LEVEL, wifi.getLevel())
					.withValue(Schema.COL_TIMESTAMP, wifi.getOpenBmapTimestamp())
					.withValueBackReference (Schema.COL_BEGIN_POSITION_ID, 0) /* Index is 0 because Foo A is the first operation in the array*/
					.withValueBackReference (Schema.COL_END_POSITION_ID, 1)
					.withValue(Schema.COL_SESSION_ID, wifi.getSessionId())
					//.withValue(Schema.COL_IS_NEW_WIFI, wifi.isNew() ? 1 : 0)
					.withValue(Schema.COL_KNOWN_WIFI, wifi.getCatalogStatusInt())
					.build());
		}

		try {
			final ContentProviderResult[] results = contentResolver.applyBatch("org.openbmap.provider", operations);
		} catch (final RemoteException e) {
			Log.e(TAG, e.toString(), e);
		} catch (final OperationApplicationException e) {
			Log.e(TAG, e.toString(), e);
		}
	}


	/**
	 * Counts number of wifis in session.
	 *
	 * @param session the session
	 * @return number of wifis
	 */
	public final int countWifis(final int session) {
		final Cursor cursor = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(
                        ContentProvider.CONTENT_URI_WIFI, ContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), session),
                new String[]{Schema.COL_ID}, null, null, null);
		final int count = cursor.getCount();
		cursor.close();
		return count;
	}

	/**
	 * Counts number of wifis in session.
	 *
	 * @param session the session
	 * @return number of wifis
	 */
	public final int countNewWifis(final int session) {
        //Log.d(TAG, "countNewWifis called");
		final Cursor cursor = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(
				ContentProvider.CONTENT_URI_WIFI, ContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), session),
				new String[]{Schema.COL_ID}, Schema.COL_KNOWN_WIFI + " = ?", new String[]{"0"}, null);
		final int count = cursor.getCount();
		cursor.close();
		return count;
	}

	/**
	 * Loads single wifi from TBL_WIFIS
	 *
	 * @param id wifi id to return
	 * @return WifiRecord wifi record
	 */
	public final WifiRecord loadWifiById(final int id) {
        // Log.d(TAG, "loadWifiById called");
		WifiRecord wifi = null;

		final Cursor cursor = contentResolver.query(ContentUris.withAppendedId(ContentProvider.CONTENT_URI_WIFI, id), null, null, null, null);
		//Log.d(TAG, "getWifiMeasurement returned " + ca.getCount() + " records");
		if (cursor.moveToNext()) {

            final int colBssid = cursor.getColumnIndex(Schema.COL_BSSID);
            final int colBssidLong = cursor.getColumnIndex(Schema.COL_BSSID_LONG);
            final int colSsid = cursor.getColumnIndex(Schema.COL_SSID);
            final int colEncryption = cursor.getColumnIndex(Schema.COL_ENCRYPTION);
            final int colFrequency = cursor.getColumnIndex(Schema.COL_FREQUENCY);
            final int colLevel = cursor.getColumnIndex(Schema.COL_MAX_LEVEL);
            final int colTime = cursor.getColumnIndex(Schema.COL_TIMESTAMP);
            final int colRequest = cursor.getColumnIndex(Schema.COL_BEGIN_POSITION_ID);
            final int colLast = cursor.getColumnIndex(Schema.COL_END_POSITION_ID);
            final int colStatus = cursor.getColumnIndex(Schema.COL_KNOWN_WIFI);

            wifi = new WifiRecord(
                    cursor.getString(colBssid),
                    cursor.getLong(colBssidLong),
                    cursor.getString(colSsid),
                    cursor.getString(colEncryption),
                    cursor.getInt(colFrequency),
                    cursor.getInt(colLevel),
                    cursor.getLong(colTime),
                    loadPositionById(cursor.getString(colRequest)),
                    loadPositionById(cursor.getString(colLast)),
                    CatalogStatus.values()[cursor.getInt(colStatus)]);
		}
		cursor.close();
		return wifi;
	}

	/**
	 * Gets wifis by BSSID
	 *
	 * @param bssid   the bssid
	 * @param session the session
	 * @return Array (of measurements) for that BSSID
	 */
	public final ArrayList<WifiRecord> loadWifisByBssid(final String bssid, final Integer session) {
        //Log.d(TAG, "loadWifisByBssid called");
		final ArrayList<WifiRecord> wifis = new ArrayList<>();

		String selectSql;
		if (session != null) {
			selectSql = Schema.COL_BSSID + " = \"" + bssid + "\" AND " + Schema.COL_SESSION_ID + " =\"" + session + "\"";
		} else {
			selectSql = Schema.COL_BSSID + " = \"" + bssid + "\"";
		}

		final Cursor cursor = contentResolver.query(ContentProvider.CONTENT_URI_WIFI, null, selectSql, null, null);

		// Performance tweaking: don't call ca.getColumnIndex on each iteration
		final int colBssid = cursor.getColumnIndex(Schema.COL_BSSID);
        final int colBssidLong = cursor.getColumnIndex(Schema.COL_BSSID_LONG);
		final int colSsid = cursor.getColumnIndex(Schema.COL_SSID);
		final int colEncryption = cursor.getColumnIndex(Schema.COL_ENCRYPTION);
		final int colFreq = cursor.getColumnIndex(Schema.COL_FREQUENCY);
		final int colLevel = cursor.getColumnIndex(Schema.COL_LEVEL);
		final int colTime = cursor.getColumnIndex(Schema.COL_TIMESTAMP);
		final int colRequest = cursor.getColumnIndex(Schema.COL_BEGIN_POSITION_ID);
		final int colLast = cursor.getColumnIndex(Schema.COL_END_POSITION_ID);
		final int colStatus = cursor.getColumnIndex(Schema.COL_KNOWN_WIFI);

		while (cursor.moveToNext()) {
            /*
            			final WifiRecord wifi = new WifiRecord(
                    bssid,
                    bssidLong,
                    ssid,
                    capa,
                    freq,
                    level,
                    time,
                    request,
                    last,
                    status)
             */
			final WifiRecord wifi = new WifiRecord(
                    cursor.getString(colBssid),
                    cursor.getLong(colBssidLong),
                    cursor.getString(colSsid),
                    cursor.getString(colEncryption),
                    cursor.getInt(colFreq),
                    cursor.getInt(colLevel),
                    cursor.getLong(colTime),
                    loadPositionById(cursor.getString(colRequest)),
                    loadPositionById(cursor.getString(colLast)),
                    CatalogStatus.values()[cursor.getInt(colStatus)]);

			wifis.add(wifi);
		}
		cursor.close();
		return wifis;
	}

	/**
	 * Returns strongest measurement for each wifi from TBL_WIFIS.
	 *
	 * @param session the session
	 * @return Arraylist<WifiRecord> array list
	 */
	public final ArrayList<WifiRecord> loadWifisOverview(final int session) {
		return loadWifisOverviewWithin(session, null, null, null, null);
	}

	/**
	 * Returns strongest measurement for each wifi within bounding box from TBL_WIFIS.
	 *
	 * @param session the session
	 * @param minLon  the min lon
	 * @param maxLon  the max lon
	 * @param minLat  the min lat
	 * @param maxLat  the max lat
	 * @return Arraylist<WifiRecord> array list
	 */
	public final ArrayList<WifiRecord> loadWifisOverviewWithin(final int session,
															   final Double minLon,
															   final Double maxLon,
															   final Double minLat,
															   final Double maxLat) {
		final ArrayList<WifiRecord> wifis = new ArrayList<>();

		String selection = null;
		String[] selectionArgs = null;

		if (minLon != null && maxLon != null && minLat != null && maxLat != null) {
			selection = "b." + Schema.COL_LONGITUDE + " >= ?"
					+ " AND b." + Schema.COL_LONGITUDE + " <= ?"
					+ " AND b." + Schema.COL_LATITUDE + " >= ?"
					+ " AND b." + Schema.COL_LATITUDE + " <= ?";
			selectionArgs = new String[]{String.valueOf(minLon), String.valueOf(maxLon), String.valueOf(minLat), String.valueOf(maxLat)};
		}

		final Cursor cursor = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(ContentProvider.CONTENT_URI_WIFI,
				ContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), session),
				null, selection, selectionArgs, null);

		// Performance tweaking: don't call ca.getColumnIndex on each iteration
		final int colBssid = cursor.getColumnIndex(Schema.COL_BSSID);
        final int colBssidLong = cursor.getColumnIndex(Schema.COL_BSSID_LONG);
		final int colSsid = cursor.getColumnIndex(Schema.COL_SSID);
		final int colEncryption = cursor.getColumnIndex(Schema.COL_ENCRYPTION);
		final int colFrequency = cursor.getColumnIndex(Schema.COL_FREQUENCY);
		final int colLevel = cursor.getColumnIndex(Schema.COL_MAX_LEVEL);
		final int colTime = cursor.getColumnIndex(Schema.COL_TIMESTAMP);
		final int colRequest = cursor.getColumnIndex(Schema.COL_BEGIN_POSITION_ID);
		final int colLast = cursor.getColumnIndex(Schema.COL_END_POSITION_ID);
		final int colStatus = cursor.getColumnIndex(Schema.COL_KNOWN_WIFI);

		while (cursor.moveToNext()) {

			final WifiRecord wifi = new WifiRecord(
                    cursor.getString(colBssid),
                    cursor.getLong(colBssidLong),
                    cursor.getString(colSsid),
                    cursor.getString(colEncryption),
                    cursor.getInt(colFrequency),
                    cursor.getInt(colLevel),
                    cursor.getLong(colTime),
                    loadPositionById(cursor.getString(colRequest)),
                    loadPositionById(cursor.getString(colLast)),
                    CatalogStatus.values()[cursor.getInt(colStatus)]);

			wifis.add(wifi);
		}

		cursor.close();
		//Log.d(TAG, "loadWifisOverviewWithiny executed (" + (System.currentTimeMillis() - start) + " ms)");
		return wifis;
	}

	/**
	 * Loads session by id.
	 *
	 * @param id Session to load
	 * @return On success session is returned, otherwise null.
	 */
	public final Session loadSession(final int id) {
		Session session = null;
		final Cursor cursor = contentResolver.query(ContentUris.withAppendedId(ContentProvider.CONTENT_URI_SESSION, id), null, null, null, null);
		if (cursor.moveToNext()) {
			session = new Session(
					cursor.getInt(cursor.getColumnIndex(Schema.COL_ID)),
					cursor.getLong(cursor.getColumnIndex(Schema.COL_CREATED_AT)),
					cursor.getLong(cursor.getColumnIndex(Schema.COL_LAST_UPDATED)),
					cursor.getString(cursor.getColumnIndex(Schema.COL_DESCRIPTION)),
					cursor.getInt(cursor.getColumnIndex(Schema.COL_HAS_BEEN_EXPORTED)),
					cursor.getInt(cursor.getColumnIndex(Schema.COL_IS_ACTIVE)),
					cursor.getInt(cursor.getColumnIndex(Schema.COL_NUMBER_OF_CELLS)),
					cursor.getInt(cursor.getColumnIndex(Schema.COL_NUMBER_OF_WIFIS)),
                    cursor.getInt(cursor.getColumnIndex(Schema.COL_NUMBER_OF_WAYPOINTS)));
		}
		cursor.close();
		return session;
	}

	/**
	 * Persists given session in database. If session already exists, session is updated, otherwise new session is created
	 *
	 * @param session          session to store
	 * @param invalidateActive shall all other active sessions will be deactivated?
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
		final Cursor cursor = contentResolver.query(ContentUris.withAppendedId(ContentProvider.CONTENT_URI_SESSION, session.getId()), null, null, null, null);
		if (!cursor.moveToNext()) {
			storeSession(session);
			cursor.close();
			return 1;
		} else {
			Log.d(TAG, "Updating existing session " + session.getId());
			final ContentValues values = new ContentValues();
			values.put(Schema.COL_CREATED_AT, session.getCreatedAt());
			values.put(Schema.COL_LAST_UPDATED, session.getLastUpdated());
			values.put(Schema.COL_DESCRIPTION, session.getDescription());
			values.put(Schema.COL_HAS_BEEN_EXPORTED, session.hasBeenExported());
			values.put(Schema.COL_IS_ACTIVE, session.isActive());
			values.put(Schema.COL_NUMBER_OF_CELLS, session.getCellsCount());
			values.put(Schema.COL_NUMBER_OF_WIFIS, session.getWifisCount());
            values.put(Schema.COL_NUMBER_OF_WAYPOINTS, session.getWaypointsCount());
			cursor.close();
			return contentResolver.update(ContentProvider.CONTENT_URI_SESSION, values,
					Schema.COL_ID + " = ?", new String[]{String.valueOf(session.getId())});
		}
	}

	/**
	 * Adds new session to TBL_SESSIONS.
	 * default values:
	 * COL_CREATED_AT = current time
	 * COL_HAS_BEEN_EXPORTED = 0 (false)
	 *
	 * @param newSession the new session
	 * @return uri of inserted row
	 */
	public final Uri storeSession(final Session newSession) {
		Log.d(TAG, "Storing new session " + newSession.toString());

		final ContentValues values = new ContentValues();
		// id is auto-assigned, remember to update new session manually
		values.put(Schema.COL_CREATED_AT, newSession.getCreatedAt());
		values.put(Schema.COL_LAST_UPDATED, newSession.getLastUpdated());
		values.put(Schema.COL_DESCRIPTION, newSession.getDescription());
		values.put(Schema.COL_HAS_BEEN_EXPORTED, newSession.hasBeenExported());
		values.put(Schema.COL_IS_ACTIVE, newSession.isActive());
		values.put(Schema.COL_NUMBER_OF_CELLS, 0);
		values.put(Schema.COL_NUMBER_OF_WIFIS, 0);
		return contentResolver.insert(ContentProvider.CONTENT_URI_SESSION, values);
	}

	/**
	 * Deletes a session. This will also delete all objects referencing this session as foreign key
	 *
	 * @param id Session to delete
	 * @return number of delete rows
	 */
	public final long deleteSession(final long id) {
		return contentResolver.delete(ContentUris.withAppendedId(ContentProvider.CONTENT_URI_SESSION, id), null, null);
	}

	/**
	 * Deletes all sessions. This will also delete all objects referencing this session as foreign key
	 *
	 * @return the long
	 */
	public final long deleteAllSession() {
		return contentResolver.delete(ContentProvider.CONTENT_URI_SESSION, null, null);
	}

	/**
	 * Loads current session.
	 * If you just need current session's id, you might consider {@link getActiveSessionId()}
	 *
	 * @return active session if any, null otherwise
	 */
	public final Session loadActiveSession() {
        // Log.d(TAG, "loadActiveSession called");
		Session session = null;
		final Cursor cursor = contentResolver.query(Uri.withAppendedPath(ContentProvider.CONTENT_URI_SESSION, "active"), null, null, null, null);
		if (cursor.moveToFirst()) {
			session = new Session(
					cursor.getInt(cursor.getColumnIndex(Schema.COL_ID)),
					cursor.getLong(cursor.getColumnIndex(Schema.COL_CREATED_AT)),
					cursor.getLong(cursor.getColumnIndex(Schema.COL_LAST_UPDATED)),
					cursor.getString(cursor.getColumnIndex(Schema.COL_DESCRIPTION)),
					cursor.getInt(cursor.getColumnIndex(Schema.COL_HAS_BEEN_EXPORTED)),
					cursor.getInt(cursor.getColumnIndex(Schema.COL_IS_ACTIVE)),
					cursor.getInt(cursor.getColumnIndex(Schema.COL_NUMBER_OF_CELLS)),
					cursor.getInt(cursor.getColumnIndex(Schema.COL_NUMBER_OF_WIFIS)),
					cursor.getInt(cursor.getColumnIndex(Schema.COL_NUMBER_OF_WAYPOINTS)));
			cursor.close();
			return session;
		} else {
			cursor.close();
			return null;
		}
	}

	/**
	 * Gets session ids of all sessions
	 *
	 * @return ArrayList with session ids
	 */
	public final ArrayList<Integer> getSessionList() {
        // Log.d(TAG, "getSessionList called");
		final ArrayList<Integer> sessions = new ArrayList<>();
		final Cursor cursor = contentResolver.query(ContentProvider.CONTENT_URI_SESSION, new String[]{Schema.COL_ID}, null, null, null);
		while (cursor.moveToNext()) {
			sessions.add(cursor.getInt(cursor.getColumnIndex(Schema.COL_ID)));
		}
		cursor.close();
		return sessions;
	}

	/**
	 * Returns Id of active session. (Faster than {@link loadActiveSession()} as no de-serialization to session object takes place
	 *
	 * @return session id if any active session, RadioBeacon.SESSION_NOT_TRACKING else
	 */
	public final int getActiveSessionId() {
        // Log.d(TAG, "getActiveSessionId called");
		final Cursor cursor = contentResolver.query(Uri.withAppendedPath(ContentProvider.CONTENT_URI_SESSION, "active"), null, null, null, null);
		if (cursor.moveToFirst()) {
            int session = cursor.getInt(cursor.getColumnIndex(Schema.COL_ID));
            cursor.close();
			return session;
		}
        cursor.close();
		return RadioBeacon.SESSION_NOT_TRACKING;
	}

	/**
	 * Counts session, which haven't been exported yet
	 *
	 * @return int
	 */
	public int countPendingExports() {
        // Log.d(TAG, "countPendingExports called");
		final Cursor cursor = contentResolver.query(ContentProvider.CONTENT_URI_SESSION, new String[]{Schema.COL_ID}, Schema.COL_HAS_BEEN_EXPORTED + "= 0", null, null);
		final int count = cursor.getCount();
		cursor.close();
		return count;
	}

	/**
	 * Persists one scan with cell and neighbor records in database.
	 * Automatically chooses, whether GSM or CDMA schema is used
	 *
	 * @param cells list of cells sharing same begin and end position (i.e. all cells from one scan)
	 * @param begin position which is equal across all cells (i.e. serving cell + neighbors)
	 * @param end   the end
	 */
	public final void storeCellsScanResults(final ArrayList<CellRecord> cells, final PositionRecord begin, final PositionRecord end) {
		if (cells == null || cells.size() == 0) {
			return;
		}

		// add a new ContentProviderOperation
		final ArrayList<ContentProviderOperation> operations = new ArrayList<>();

		//saving begin position
		operations.add(ContentProviderOperation.newInsert(ContentProvider.CONTENT_URI_POSITION)
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
		operations.add(ContentProviderOperation.newInsert(ContentProvider.CONTENT_URI_POSITION)
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

		for (final CellRecord cell: cells) {
			if (!cell.isCdma()) {
				// store GSM cell
				operations.add(ContentProviderOperation.newInsert(ContentProvider.CONTENT_URI_CELL)
						.withValue(Schema.COL_NETWORKTYPE, cell.getNetworkType())
						.withValue(Schema.COL_IS_CDMA, cell.isCdma())
						.withValue(Schema.COL_IS_SERVING, cell.isServing())
						.withValue(Schema.COL_IS_NEIGHBOR, cell.isNeighbor())
						.withValue(Schema.COL_LOGICAL_CELLID, cell.getLogicalCellId())
						.withValue(Schema.COL_ACTUAL_CELLID, cell.getActualCellId())
						.withValue(Schema.COL_UTRAN_RNC, cell.getUtranRnc())
						.withValue(Schema.COL_PSC, cell.getPsc())
						.withValue(Schema.COL_AREA, cell.getArea())
						.withValue(Schema.COL_MCC, cell.getMcc())
						.withValue(Schema.COL_MNC, cell.getMnc())
						.withValue(Schema.COL_OPERATORNAME, cell.getOperatorName())
						.withValue(Schema.COL_OPERATOR, cell.getOperator())
						.withValue(Schema.COL_STRENGTHDBM, cell.getStrengthdBm())
						.withValue(Schema.COL_STRENGTHASU, cell.getStrengthAsu())
						.withValue(Schema.COL_TIMESTAMP, cell.getOpenBmapTimestamp())
						.withValueBackReference (Schema.COL_BEGIN_POSITION_ID, 0) // Index is 0 because first operation stores cell position
						.withValueBackReference (Schema.COL_END_POSITION_ID, 1)
						.withValue(Schema.COL_SESSION_ID, cell.getSessionId())
						// set unused (CDMA) fields to default
						.withValue(Schema.COL_CDMA_BASEID, -1)
						.withValue(Schema.COL_CDMA_NETWORKID, -1)
						.withValue(Schema.COL_CDMA_SYSTEMID, -1)
						.withValue(Schema.COL_CDMA_BASEID, -1)

						.build());
			} else {
				// store CDMA cell
				operations.add(ContentProviderOperation.newInsert(ContentProvider.CONTENT_URI_CELL)
						.withValue(Schema.COL_NETWORKTYPE, cell.getNetworkType())
						.withValue(Schema.COL_IS_CDMA, cell.isCdma())
						.withValue(Schema.COL_IS_SERVING, cell.isServing())
						.withValue(Schema.COL_IS_NEIGHBOR, cell.isNeighbor())
						.withValue(Schema.COL_AREA, cell.getArea())
						.withValue(Schema.COL_MCC, cell.getMcc())
						.withValue(Schema.COL_MNC, cell.getMnc())
						.withValue(Schema.COL_PSC, cell.getPsc())
						.withValue(Schema.COL_CDMA_BASEID, cell.getBaseId())
						.withValue(Schema.COL_CDMA_NETWORKID, cell.getNetworkId())
						.withValue(Schema.COL_CDMA_SYSTEMID, cell.getSystemId())
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
						.withValue(Schema.COL_LOGICAL_CELLID, -1)
						.withValue(Schema.COL_AREA, -1)
						.build());
			}
		}

		try {
			final ContentProviderResult[] results = contentResolver.applyBatch("org.openbmap.provider", operations);
		} catch (final RemoteException e) {
			Log.e(TAG, e.toString(), e);
		} catch (final OperationApplicationException e) {
			Log.e(TAG, e.toString(), e);
		}
	}

	/**
	 * Loads CellRecord from database.
	 *
	 * @param id the id
	 * @return cell record
	 */
	public final CellRecord loadCellById(final int id) {
        // Log.d(TAG, "loadCellById called");
		CellRecord cell = null;

		final Cursor cursor = contentResolver.query(ContentUris.withAppendedId(ContentProvider.CONTENT_URI_CELL, id) , null, null, null, null);
		if (cursor.moveToNext()) {
			cell = cursorToCell(cursor);
		}
		cursor.close();
		return cell;
	}

	/**
	 * Loads session's cells.
	 *
	 * @param session Session Id
	 * @param sort    Sort criteria
	 * @return ArrayList<CellRecord>  with all cells for given session
	 */
	public final ArrayList<CellRecord> loadCellsBySession(final long session, final String sort) {
		final ArrayList<CellRecord> cells = new ArrayList<>();

		final Cursor cursor = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(
				ContentProvider.CONTENT_URI_CELL, ContentProvider.CONTENT_URI_SESSION_SUFFIX), session),
				null, null, null, sort);

		while (cursor.moveToNext()) {
			cells.add(cursorToCell(cursor));
		}

		cursor.close();
		return cells;
	}

	/**
	 * Creates a CellRecord from cursor row.
	 * @param cursor
	 * @return CellRecord
	 */
	private CellRecord cursorToCell(final Cursor cursor) {
		final CellRecord cell = new CellRecord();
		final int colNetworkType = cursor.getColumnIndex(Schema.COL_NETWORKTYPE);
		final int colIsCdma = cursor.getColumnIndex(Schema.COL_IS_CDMA);
		final int colIsServing = cursor.getColumnIndex(Schema.COL_IS_SERVING);
		final int colIsNeigbor = cursor.getColumnIndex(Schema.COL_IS_NEIGHBOR);
		final int colLogicalCellId = cursor.getColumnIndex(Schema.COL_LOGICAL_CELLID);
		final int colActualCellId = cursor.getColumnIndex(Schema.COL_ACTUAL_CELLID);
		final int colUtranRnc = cursor.getColumnIndex(Schema.COL_UTRAN_RNC);
		final int colPsc = cursor.getColumnIndex(Schema.COL_PSC);
		final int colOperatorName = cursor.getColumnIndex(Schema.COL_OPERATORNAME);
		final int colOperator = cursor.getColumnIndex(Schema.COL_OPERATOR);
		final int colMcc = cursor.getColumnIndex(Schema.COL_MCC);
		final int colMnc = cursor.getColumnIndex(Schema.COL_MNC);
		final int colLac = cursor.getColumnIndex(Schema.COL_AREA);
		final int colBaseId = cursor.getColumnIndex(Schema.COL_CDMA_BASEID);
		final int colNetworkId = cursor.getColumnIndex(Schema.COL_CDMA_NETWORKID);
		final int colSystemId = cursor.getColumnIndex(Schema.COL_CDMA_SYSTEMID);
		final int colStrengthDbm = cursor.getColumnIndex(Schema.COL_STRENGTHDBM);
		final int colStrengthAsu = cursor.getColumnIndex(Schema.COL_STRENGTHASU);
		final int colTimestamp = cursor.getColumnIndex(Schema.COL_TIMESTAMP);
		final int colBeginPositionId = cursor.getColumnIndex(Schema.COL_BEGIN_POSITION_ID);
		final int colEndPositionId = cursor.getColumnIndex(Schema.COL_END_POSITION_ID);
		final int columnIndex18 = cursor.getColumnIndex(Schema.COL_SESSION_ID);

		cell.setNetworkType(cursor.getInt(colNetworkType));
		cell.setIsCdma(cursor.getInt(colIsCdma) != 0);
		cell.setIsServing(cursor.getInt(colIsServing) != 0);
		cell.setIsNeighbor(cursor.getInt(colIsNeigbor) != 0);
		cell.setLogicalCellId(cursor.getInt(colLogicalCellId));
		cell.setActualCid(cursor.getInt(colActualCellId));
		cell.setUtranRnc(cursor.getInt(colUtranRnc));
		cell.setPsc(cursor.getInt(colPsc));
		cell.setOperatorName(cursor.getString(colOperatorName));
		cell.setOperator(cursor.getString(colOperator));
		cell.setMcc(cursor.getString(colMcc));
		cell.setMnc(cursor.getString(colMnc));
		cell.setArea(cursor.getInt(colLac));
		cell.setBaseId(cursor.getString(colBaseId));
		cell.setNetworkId(cursor.getString(colNetworkId));
		cell.setSystemId(cursor.getString(colSystemId));
		cell.setStrengthdBm(cursor.getInt(colStrengthDbm));
		cell.setStrengthAsu(cursor.getInt(colStrengthAsu));
		cell.setOpenBmapTimestamp(cursor.getLong(colTimestamp));
		// TODO: dirty ...
		cell.setBeginPosition(loadPositionById(cursor.getString(colBeginPositionId)));
		cell.setEndPosition(loadPositionById(cursor.getString(colEndPositionId)));
		cell.setSessionId(cursor.getInt(columnIndex18));

		return cell;
	}

	/**
	 * Counts session's number of cells.
	 *
	 * @param session the session
	 * @return number of wifis
	 */
	public final int countCells(final long session) {
		final Cursor cursor = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(
				ContentProvider.CONTENT_URI_CELL, ContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), session),
				new String[]{Schema.COL_ID}, null, null, null);
		final int count = cursor.getCount();
		cursor.close();
		return count;
	}

	/**
	 * Counts session's number of waypoints.
	 *
	 * @param session the session
	 * @return number of wifis
	 */
	public final int countWaypoints(final long session) {
		final Cursor cursor = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(
						ContentProvider.CONTENT_URI_POSITION, ContentProvider.CONTENT_URI_SESSION_SUFFIX), session),
				new String[]{Schema.COL_ID}, null, null, null);
		final int count = cursor.getCount();
		cursor.close();
		return count;
    }

	/**
	 * Loads positions from database.
	 *
	 * @param id Position id to return. If no id is provided, all positions are returned.
	 * @return ArrayList<PositionRecord> position record
	 */
	public final PositionRecord loadPositionById(final String id) {
        // Log.d(TAG, "loadPositionById called");

		if (id == null) {
			throw new IllegalArgumentException("Position id is null");
		}
		//long start = System.currentTimeMillis();

		String selection = null;
		String[] selectionArgs = null;

		//Log.d(TAG, "Loading single position with id " + id);
		selection = Schema.COL_ID + " = ?";
		selectionArgs = new String[]{id};

		final Cursor cursor = contentResolver.query(ContentProvider.CONTENT_URI_POSITION, null, selection, selectionArgs, null);

		PositionRecord position = new PositionRecord();
		if (cursor.moveToNext()) {
			position = positionFromCursor(cursor);
		}

		cursor.close();
		//Log.d(TAG, "loadPositionById executed (" + (System.currentTimeMillis() - start) + " ms)");
		return position;
	}

	/**
	 * Loads positions within certain arrea
	 *
	 * @param session the session
	 * @param minLat  the min lat
	 * @param maxLat  the max lat
	 * @param minLon  the min lon
	 * @param maxLon  the max lon
	 * @return array list
	 */
	public final ArrayList<PositionRecord> loadPositions(final int session, final Double minLat, final Double maxLat, final Double minLon, final Double maxLon) {
        // Log.d(TAG, "loadPositions called");
		final ArrayList<PositionRecord> positions = new ArrayList<>();
		String selection = Schema.COL_SESSION_ID + " = ?";

		Cursor cursor = null;
		List<String> selectionArgs = null;
		if (minLat != null & maxLat != null && minLon != null && maxLon != null) {
			// if boundaries provided..
			selectionArgs = new ArrayList<>();
			selectionArgs.add(String.valueOf(session));

			selectionArgs.add(String.valueOf(minLat));
			selectionArgs.add(String.valueOf(maxLat));

			selectionArgs.add(String.valueOf(minLon));
			selectionArgs.add(String.valueOf(maxLon));

			selection +=  "AND (" + Schema.COL_LATITUDE + " > ? AND " + Schema.COL_LATITUDE + " < ?) AND ("
					+ Schema.COL_LONGITUDE + " > ? AND " + Schema.COL_LONGITUDE + " < ?)";
			cursor = contentResolver.query(
					ContentProvider.CONTENT_URI_POSITION,
                    null, selection,
                    selectionArgs.toArray(new String[0]), Schema.COL_TIMESTAMP);

		} else {
			Log.v(TAG, "No boundaries provided, loading all positions");
			cursor = contentResolver.query(ContentProvider.CONTENT_URI_POSITION, null, null, null, Schema.COL_TIMESTAMP);
		}

		while (cursor.moveToNext()) {
			positions.add(positionFromCursor(cursor));
		}

		cursor.close();
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

		final PositionRecord position = new PositionRecord();
		position.setLatitude(cursor.getDouble(columnIndex));
		position.setLongitude(cursor.getDouble(columnIndex2));
		position.setAltitude(cursor.getDouble(columnIndex3));
		position.setAccuracy(cursor.getDouble(columnIndex4));
		position.setTimestampByMillis(cursor.getLong(columnIndex5));
		position.setBearing(cursor.getDouble(columnIndex6));
		position.setSpeed(cursor.getDouble(columnIndex7));
		position.setSession(cursor.getInt(columnIndex8));
		position.setSource(cursor.getString(columnIndex9));

		return position;
	}

	/**
	 * Loads session's log file.
	 *
	 * @param id Session id for which log file is returned
	 * @return LogFile log file
	 */
	public final LogFile loadLogFileBySession(final long id) {
        // Log.d(TAG, "loadLogFileBySession called");

		LogFile logFile = null;

		final Cursor cursor = contentResolver.query(ContentUris.withAppendedId(Uri.withAppendedPath(
				ContentProvider.CONTENT_URI_LOGFILE, ContentProvider.CONTENT_URI_SESSION_SUFFIX), id),
				null, null, null, null);

		if (cursor.moveToNext()) {
			logFile = new LogFile(
					cursor.getString(cursor.getColumnIndex(Schema.COL_MANUFACTURER)),
					cursor.getString(cursor.getColumnIndex(Schema.COL_MODEL)),
					cursor.getString(cursor.getColumnIndex(Schema.COL_REVISION)),
					cursor.getString(cursor.getColumnIndex(Schema.COL_SWID)),
					cursor.getString(cursor.getColumnIndex(Schema.COL_SWVER)),
					cursor.getInt(cursor.getColumnIndex(Schema.COL_SESSION_ID)));
		}

		cursor.close();
		return logFile;
	}

	/**
	 * Persists LogFile in database.
	 *
	 * @param logFile the log file
	 * @return uri
	 */
	public final Uri storeLogFile(final LogFile logFile) {
		final ContentValues values = new ContentValues();
		values.put(Schema.COL_MANUFACTURER, logFile.getManufacturer());
		values.put(Schema.COL_MODEL, logFile.getModel());
		values.put(Schema.COL_REVISION, logFile.getRevision());
		values.put(Schema.COL_SWID, logFile.getSwid());
		values.put(Schema.COL_SWVER, logFile.getSwVersion());
		values.put(Schema.COL_TIMESTAMP, System.currentTimeMillis());
		values.put(Schema.COL_SESSION_ID, logFile.getSessionId());

		return contentResolver.insert(ContentProvider.CONTENT_URI_LOGFILE, values);
	}

	/**
	 * Deactivates all active sessions.
	 *
	 * @return number of updated rows
	 */
	public final int invalidateActiveSessions() {
		final ContentValues values = new ContentValues();
		values.put(Schema.COL_IS_ACTIVE, 0);
		// disables all active sessions
		return contentResolver.update(ContentProvider.CONTENT_URI_SESSION, values,
				Schema.COL_IS_ACTIVE + " > 0" , null);
	}

	/**
	 * Stores position.
	 * This method is only used for separate positions. WifisRadiocells and cell positions are added in batch mode
	 * in storeCellsScanResults and storeWifiScanResults
	 *
	 * @param pos the pos
	 * @return uri
	 */
	public final Uri storePosition(final PositionRecord pos) {
		final ContentValues values = new ContentValues();
		values.put(Schema.COL_LATITUDE, pos.getLatitude());
		values.put(Schema.COL_LONGITUDE, pos.getLongitude());
		values.put(Schema.COL_ALTITUDE, pos.getAltitude());
		values.put(Schema.COL_TIMESTAMP, pos.getOpenBmapTimestamp());
		values.put(Schema.COL_ACCURACY, pos.getAccuracy());
		values.put(Schema.COL_BEARING, pos.getBearing());
		values.put(Schema.COL_SPEED, pos.getSpeed());
		values.put(Schema.COL_SESSION_ID, pos.getSession());
		values.put(Schema.COL_SOURCE, pos.getSource());

		return contentResolver.insert(ContentProvider.CONTENT_URI_POSITION, values);
	}
}
