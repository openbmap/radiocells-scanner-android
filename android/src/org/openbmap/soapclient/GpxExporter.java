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

package org.openbmap.soapclient;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.lang3.StringEscapeUtils;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DatabaseHelper;
import org.openbmap.db.Schema;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

/**
 * Writes GPX file for session
 * Inspired by Nicolas Guillaumin
 */
// TODO: refactor to AsyncTask
public class GpxExporter {

	private static final String TAG = GpxExporter.class.getSimpleName();

	/**
	 * Cursor windows size, to prevent running out of mem on to large cursor
	 */
	private static final int CURSOR_SIZE = 1000;

	/**
	 * XML header.
	 */
	private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n";

	/**
	 * GPX opening tag
	 */
	private static final String TAG_GPX = "<gpx"
			+ " xmlns=\"http://www.topografix.com/GPX/1/1\""
			+ " version=\"1.1\""
			+ " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
			+ " xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd \">\n";

	private static final String	TAG_GPX_CLOSE	= "</gpx>";

	private static final String TRACKPOINT_SQL_QUERY1 = "SELECT " + Schema.COL_LATITUDE + ", " + Schema.COL_LONGITUDE + ", "
			+ " " + Schema.COL_ALTITUDE + ", " + Schema.COL_ACCURACY + ", " + Schema.COL_TIMESTAMP + ", " + Schema.COL_BEARING
			+ ", " + Schema.COL_SPEED + ", " + Schema.COL_SESSION_ID + ", " + Schema.COL_SOURCE + " "
			+ " FROM " + Schema.TBL_POSITIONS + " WHERE " + Schema.COL_SESSION_ID + " = ?"
			+ " AND source != '" + RadioBeacon.PROVIDER_USER_DEFINED + "' " 
			+ " ORDER BY " + Schema.COL_TIMESTAMP + " LIMIT " + CURSOR_SIZE
			+ " OFFSET ?";
	
	private static final String WAYPOINT_SQL_QUERY = "SELECT " + Schema.COL_LATITUDE + ", " + Schema.COL_LONGITUDE + ", "
			+ " " + Schema.COL_ALTITUDE + ", " + Schema.COL_ACCURACY + ", " + Schema.COL_TIMESTAMP + ", " + Schema.COL_BEARING
			+ ", " + Schema.COL_SPEED + ", " + Schema.COL_SESSION_ID + ", " + Schema.COL_SOURCE + " "
			+ " FROM " + Schema.TBL_POSITIONS + " WHERE " + Schema.COL_SESSION_ID + " = ?"
			+ " AND source = '" + RadioBeacon.PROVIDER_USER_DEFINED + "' " 
			+ " ORDER BY " + Schema.COL_TIMESTAMP + " LIMIT " + CURSOR_SIZE
			+ " OFFSET ?";

	/*
	private static final String WIFI_POINTS_SQL_QUERY = "SELECT " + Schema.COL_LATITUDE + ", " + Schema.COL_LONGITUDE + ", "
			+ Schema.COL_ALTITUDE + ", " + Schema.COL_ACCURACY + ", " + Schema.COL_TIMESTAMP + ", \"WIFI \"||" + Schema.COL_SSID + " AS name "
			+ " FROM " + Schema.TBL_POSITIONS + " AS p LEFT JOIN " 
			+ " (SELECT " + Schema.COL_ID + ", " + Schema.COL_SSID + ", " + Schema.COL_BEGIN_POSITION_ID + " FROM " + Schema.TBL_WIFIS + " )"
			+ " AS w ON w." + Schema.COL_BEGIN_POSITION_ID + " = p._id WHERE w._id IS NOT NULL AND p." + Schema.COL_SESSION_ID + " = ?"
			+ " ORDER BY " + Schema.COL_TIMESTAMP
			+ " LIMIT " + CURSOR_SIZE + " OFFSET ?";*/
	
	private static final String WIFI_POINTS_SQL_QUERY = "SELECT w.rowid as " + Schema.COL_ID + ", w." + Schema.COL_BSSID + ", w." + Schema.COL_SSID + ", " 
			+ " MAX(" + Schema.COL_LEVEL + "), w." + Schema.COL_TIMESTAMP + ", "
			+ " b." + Schema.COL_LATITUDE + ", b." + Schema.COL_LONGITUDE + ", b." + Schema.COL_ALTITUDE + ", b." + Schema.COL_ACCURACY 
			+ " FROM " + Schema.TBL_WIFIS + " as w JOIN positions as b ON request_pos_id = b._id "
			+ " WHERE w." + Schema.COL_SESSION_ID + " = ? GROUP BY w." + Schema.COL_BSSID
			+ " LIMIT " + CURSOR_SIZE + " OFFSET ?";

	private static final String CELL_POINTS_SQL_QUERY = "SELECT " + Schema.COL_LATITUDE + ", " + Schema.COL_LONGITUDE + ", "
			+ Schema.COL_ALTITUDE + ", " + Schema.COL_ACCURACY + ", " + Schema.COL_TIMESTAMP + ", \"CELL \" ||" + Schema.COL_OPERATORNAME + " ||" + Schema.COL_LOGICAL_CELLID + " AS name"
			+ " FROM " + Schema.TBL_POSITIONS + " AS p LEFT JOIN " 
			+ " (SELECT " + Schema.COL_ID + ", " + Schema.COL_OPERATORNAME + ", " + Schema.COL_LOGICAL_CELLID + ", " + Schema.COL_BEGIN_POSITION_ID + " FROM " + Schema.TBL_CELLS + ") "
			+ " AS c ON c." + Schema.COL_BEGIN_POSITION_ID + " = p._id WHERE c._id IS NOT NULL AND p." + Schema.COL_SESSION_ID + " = ?"
			+ " ORDER BY " + Schema.COL_TIMESTAMP + " LIMIT " + CURSOR_SIZE
			+ " OFFSET ?";

	/**
	 * Date format as used internally
	 */
	private static final SimpleDateFormat INTERNAL_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);

	/**
	 * Date format for a gpx point timestamp.
	 * ISO 8601 format
	 */
	private static final SimpleDateFormat GPX_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

	private final int	mSession;

	private final Context	mContext;

	private DatabaseHelper	mDbHelper;

	public GpxExporter(final Context context, final int session) {
		mSession = session;
		mContext = context;
	}

	/**
	 * Writes the GPX file
	 * @param trackName Name of the GPX track (metadata)
	 * @param cTrackPoints Cursor to track points.
	 * @param cWayPoints Cursor to way points.
	 * @param target Target GPX file
	 * @throws IOException 
	 */
	public final void doExport(final String trackName, final File target) throws IOException {
		Log.i(TAG, "Finished building gpx file");
		mDbHelper = new DatabaseHelper(mContext);

		final BufferedWriter bw = new BufferedWriter(new FileWriter(target));

		bw.write(XML_HEADER);
		bw.write(TAG_GPX);
		
		writeWaypoints(mSession, bw);
		writeTrackpoints(mSession, trackName, bw);
		bw.flush();

		writeWifis(bw);
		bw.flush();
		writeCells(bw);
		bw.flush();

		bw.write(TAG_GPX_CLOSE);
		bw.close();

		mDbHelper.close();
		Log.i(TAG, "Finished building gpx file");
	}

	/**
	 * Iterates on track points and write them.
	 * @param trackName Name of the track (metadata).
	 * @param bw Writer to the target file.
	 * @param c Cursor to track points.
	 * @throws IOException
	 */
	private void writeWaypoints(final int session, final BufferedWriter bw) throws IOException {
		Log.i(TAG, "Writing trackpoints");

		Cursor c = mDbHelper.getReadableDatabase().rawQuery(WAYPOINT_SQL_QUERY, new String[]{String.valueOf(mSession), String.valueOf(0)});

		final int colLatitude = c.getColumnIndex(Schema.COL_LATITUDE);
		final int colLongitude = c.getColumnIndex(Schema.COL_LONGITUDE);
		final int colAltitude = c.getColumnIndex(Schema.COL_ALTITUDE);
		final int colTimestamp = c.getColumnIndex(Schema.COL_TIMESTAMP);

		long outer = 0;
		while (!c.isAfterLast()) {
			c.moveToFirst();
			//StringBuffer out = new StringBuffer(32 * 1024);
			while (!c.isAfterLast()) {
				bw.write("\t\t\t<wpt lat=\"");
				bw.write(String.valueOf(c.getDouble(colLatitude)));
				bw.write("\" ");
				bw.write("lon=\"");
				bw.write(String.valueOf(c.getDouble(colLongitude)));
				bw.write("\">");
				bw.write("<ele>");
				bw.write(String.valueOf(c.getDouble(colAltitude)));
				bw.write("</ele>");
				bw.write("<time>");
				// time stamp conversion to ISO 8601
				bw.write(getGpxDate(c.getLong(colTimestamp)));
				bw.write("</time>");
				bw.write("</wpt>\n");

				c.moveToNext();
			}
			//bw.write(out.toString());
			//out = null;

			// fetch next CURSOR_SIZE records
			outer += CURSOR_SIZE;
			c.close();
			c = mDbHelper.getReadableDatabase().rawQuery(WAYPOINT_SQL_QUERY, new String[]{String.valueOf(mSession), String.valueOf(outer)});
		}
		c.close();
		System.gc();
	}
	
	/**
	 * Iterates on track points and write them.
	 * @param trackName Name of the track (metadata).
	 * @param bw Writer to the target file.
	 * @param c Cursor to track points.
	 * @throws IOException
	 */
	private void writeTrackpoints(final int session, final String trackName, final BufferedWriter bw) throws IOException {
		Log.i(TAG, "Writing trackpoints");

		Cursor c = mDbHelper.getReadableDatabase().rawQuery(TRACKPOINT_SQL_QUERY1, new String[]{String.valueOf(mSession), String.valueOf(0)});

		final int colLatitude = c.getColumnIndex(Schema.COL_LATITUDE);
		final int colLongitude = c.getColumnIndex(Schema.COL_LONGITUDE);
		final int colAltitude = c.getColumnIndex(Schema.COL_ALTITUDE);
		final int colTimestamp = c.getColumnIndex(Schema.COL_TIMESTAMP);

		bw.write("\t<trk>");
		bw.write("\t\t<name>");
		bw.write(trackName);
		bw.write("</name>\n");
		bw.write("\t\t<trkseg>\n");

		long outer = 0;
		while (!c.isAfterLast()) {
			c.moveToFirst();
			//StringBuffer out = new StringBuffer(32 * 1024);
			while (!c.isAfterLast()) {
				bw.write("\t\t\t<trkpt lat=\"");
				bw.write(String.valueOf(c.getDouble(colLatitude)));
				bw.write("\" ");
				bw.write("lon=\"");
				bw.write(String.valueOf(c.getDouble(colLongitude)));
				bw.write("\">");
				bw.write("<ele>");
				bw.write(String.valueOf(c.getDouble(colAltitude)));
				bw.write("</ele>");
				bw.write("<time>");
				// time stamp conversion to ISO 8601
				bw.write(getGpxDate(c.getLong(colTimestamp)));
				bw.write("</time>");
				bw.write("</trkpt>\n");

				c.moveToNext();
			}
			//bw.write(out.toString());
			//out = null;

			// fetch next CURSOR_SIZE records
			outer += CURSOR_SIZE;
			c.close();
			c = mDbHelper.getReadableDatabase().rawQuery(TRACKPOINT_SQL_QUERY1, new String[]{String.valueOf(mSession), String.valueOf(outer)});
		}
		c.close();

		bw.write("\t\t</trkseg>\n");
		bw.write("\t</trk>\n");
		System.gc();
	}

	/**
	 * Iterates on way points and write them.
	 * @param bw Writer to the target file.
	 * @param c Cursor to way points.
	 * @throws IOException
	 */
	private void writeWifis(final BufferedWriter bw) throws IOException {
		Log.i(TAG, "Writing wifi waypoints");

		Cursor c = mDbHelper.getReadableDatabase().rawQuery(WIFI_POINTS_SQL_QUERY, new String[]{String.valueOf(mSession), String.valueOf(0)});
		//Log.i(TAG, WIFI_POINTS_SQL_QUERY);
		final int colLatitude = c.getColumnIndex(Schema.COL_LATITUDE);
		final int colLongitude = c.getColumnIndex(Schema.COL_LONGITUDE);
		final int colAltitude = c.getColumnIndex(Schema.COL_ALTITUDE);
		final int colTimestamp = c.getColumnIndex(Schema.COL_TIMESTAMP);
		final int colSsid = c.getColumnIndex(Schema.COL_SSID);

		long outer = 0;
		while (!c.isAfterLast()) {
			c.moveToFirst();
			//StringBuffer out = new StringBuffer();
			while (!c.isAfterLast()) {
				bw.write("\t<wpt lat=\"");
				bw.write(String.valueOf(c.getDouble(colLatitude)));
				bw.write("\" ");
				bw.write("lon=\"");
				bw.write(String.valueOf(c.getDouble(colLongitude)));
				bw.write("\">\n");
				bw.write("\t\t<ele>");
				bw.write(String.valueOf(c.getDouble(colAltitude)));
				bw.write("</ele>\n");
				bw.write("\t\t<time>");
				// time stamp conversion to ISO 8601
				bw.write(getGpxDate(c.getLong(colTimestamp)));
				bw.write("</time>\n");
				bw.write("\t\t<name>");
				bw.write(StringEscapeUtils.escapeXml(c.getString(colSsid)));
				bw.write("</name>\n");
				bw.write("\t</wpt>\n");

				c.moveToNext();
			}
			//bw.write(out.toString());
			//out = null;
			// fetch next CURSOR_SIZE records
			outer += CURSOR_SIZE;
			c.close();
			c = mDbHelper.getReadableDatabase().rawQuery(WIFI_POINTS_SQL_QUERY, new String[]{String.valueOf(mSession), String.valueOf(outer)});
		}
		c.close();
		System.gc();
	}

	/**
	 * Iterates on way points and write them.
	 * @param bw Writer to the target file.
	 * @param c Cursor to way points.
	 * @throws IOException
	 */
	private void writeCells(final BufferedWriter bw) throws IOException {
		Log.i(TAG, "Writing cell waypoints");
		Cursor c = mDbHelper.getReadableDatabase().rawQuery(CELL_POINTS_SQL_QUERY, new String[]{String.valueOf(mSession), String.valueOf(0)});

		final int colLatitude = c.getColumnIndex(Schema.COL_LATITUDE);
		final int colLongitude = c.getColumnIndex(Schema.COL_LONGITUDE);
		final int colAltitude = c.getColumnIndex(Schema.COL_ALTITUDE);
		final int colTimestamp = c.getColumnIndex(Schema.COL_TIMESTAMP);
		final int colName = c.getColumnIndex("name");

		long outer = 0;
		while (!c.isAfterLast()) {
			c.moveToFirst();
			//StringBuffer out = new StringBuffer();
			while (!c.isAfterLast()) {
				bw.write("\t<wpt lat=\"");
				bw.write(String.valueOf(c.getDouble(colLatitude)));
				bw.write("\" ");
				bw.write("lon=\"");
				bw.write(String.valueOf(c.getDouble(colLongitude)));
				bw.write("\">\n");
				bw.write("\t\t<ele>");
				bw.write(String.valueOf(c.getDouble(colAltitude)));
				bw.write("</ele>\n");
				bw.write("\t\t<time>");
				// time stamp conversion to ISO 8601
				bw.write(getGpxDate(c.getLong(colTimestamp)));
				bw.write("</time>\n");
				bw.write("\t\t<name>");
				bw.write(StringEscapeUtils.escapeXml(c.getString(colName)));
				bw.write("</name>\n");
				bw.write("\t</wpt>\n");

				c.moveToNext();
			}

			//bw.write(out.toString());
			//out = null;
			// fetch next CURSOR_SIZE records
			outer += CURSOR_SIZE;
			c.close();
			c = mDbHelper.getReadableDatabase().rawQuery(CELL_POINTS_SQL_QUERY, new String[]{String.valueOf(mSession), String.valueOf(outer)});
		}
		c.close();
		System.gc();
	}

	/**
	 * Converts from openbmap date format (YYYYMMDDHHMMSS) to gpx date format (ISO 8601)
	 * @param raw Openbmap date
	 * @return ISO 8601 date
	 */
	private static String getGpxDate(final long raw) {
		try {
			// for gpx files we need data in ISO 8601 format (e.g. 2011-12-31T23:59:59Z)
			// as opposed to openbmap database format YYYYMMDDHHMMSS
			final Date converted = INTERNAL_DATE_FORMAT.parse(String.valueOf(raw));
			GPX_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
			return (GPX_DATE_FORMAT.format(converted));
		} catch (final ParseException e) {
			// should never happen
			Log.e(TAG, "Error converting gpx date. Source " + raw);
			return "0000-00-00T00:00:00Z";
		}
	}
}
