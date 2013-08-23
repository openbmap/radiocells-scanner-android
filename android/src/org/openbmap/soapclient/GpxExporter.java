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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.apache.commons.lang3.StringEscapeUtils;

import org.openbmap.db.DatabaseHelper;
import org.openbmap.db.Schema;

import android.content.Context;
import android.database.Cursor;

/**
 * Writes GPX file for session
 * Inspired by Nicolas Guillaumin
 */
// TODO: refactor to AsyncTask
public class GpxExporter {

	private static final String TAG = GpxExporter.class.getSimpleName();

	/**
	 * XML header.
	 */
	private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>";

	/**
	 * GPX opening tag
	 */
	private static final String TAG_GPX = "<gpx"
			+ " xmlns=\"http://www.topografix.com/GPX/1/1\""
			+ " version=\"1.1\""
			+ " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
			+ " xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd \">";

	private static final String POSITION_SQL_QUERY = "SELECT " + Schema.COL_LATITUDE + ", " + Schema.COL_LONGITUDE + ", "
			+ " " + Schema.COL_ALTITUDE + ", " + Schema.COL_ACCURACY + ", " + Schema.COL_TIMESTAMP + ", " + Schema.COL_BEARING
			+ ", " + Schema.COL_SPEED + ", " + Schema.COL_SESSION_ID + ", " + Schema.COL_SOURCE + " "
			+ " FROM " + Schema.TBL_POSITIONS + " WHERE " + Schema.COL_SESSION_ID + " = ?"
			+ " ORDER BY " + Schema.COL_TIMESTAMP;

	private static final String WIFI_POINTS_SQL_QUERY = "SELECT " + Schema.COL_LATITUDE + ", " + Schema.COL_LONGITUDE + ", "
			+ Schema.COL_ALTITUDE + ", " + Schema.COL_ACCURACY + ", " + Schema.COL_TIMESTAMP + ", \"WIFI \"||" + Schema.COL_SSID + " AS name "
			+ " FROM " + Schema.TBL_POSITIONS + " AS p LEFT JOIN " 
			+ " (SELECT " + Schema.COL_ID + ", " + Schema.COL_SSID + ", " + Schema.COL_BEGIN_POSITION_ID + " FROM " + Schema.TBL_WIFIS + " )"
			+ " AS w ON w." + Schema.COL_BEGIN_POSITION_ID + " = p._id WHERE w._id IS NOT NULL AND p." + Schema.COL_SESSION_ID + " = ?"
			+ " ORDER BY " + Schema.COL_TIMESTAMP;
	
	private static final String CELL_POINTS_SQL_QUERY = "SELECT " + Schema.COL_LATITUDE + ", " + Schema.COL_LONGITUDE + ", "
			+ Schema.COL_ALTITUDE + ", " + Schema.COL_ACCURACY + ", " + Schema.COL_TIMESTAMP + ", \"CELL \" ||" + Schema.COL_OPERATORNAME + " ||" + Schema.COL_CELLID + " AS name"
			+ " FROM " + Schema.TBL_POSITIONS + " AS p LEFT JOIN " 
			+ " (SELECT " + Schema.COL_ID + ", " + Schema.COL_OPERATORNAME + ", " + Schema.COL_CELLID + ", " + Schema.COL_BEGIN_POSITION_ID + " FROM " + Schema.TBL_CELLS + ") "
			+ " AS c ON c." + Schema.COL_BEGIN_POSITION_ID + " = p._id WHERE c._id IS NOT NULL AND p." + Schema.COL_SESSION_ID + " = ?"
			+ " ORDER BY " + Schema.COL_TIMESTAMP;

	/**
	 * Date format for a point timestamp.
	 */
	private static final SimpleDateFormat POINT_DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

	private int	mSession;

	private Context	mContext;

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
		final DatabaseHelper mDbHelper = new DatabaseHelper(mContext);
		Cursor cursorTracksPoints = mDbHelper.getReadableDatabase().rawQuery(POSITION_SQL_QUERY, new String[]{String.valueOf(mSession)});

		FileWriter fw = new FileWriter(target);

		fw.write(XML_HEADER + "\n");
		fw.write(TAG_GPX + "\n");

		writeTrackPoints(trackName, fw, cursorTracksPoints);

		Cursor wifis = mDbHelper.getReadableDatabase().rawQuery(WIFI_POINTS_SQL_QUERY, new String[]{String.valueOf(mSession)});
		wifis.moveToFirst();
		writeWayPoints(fw, wifis);
		wifis.close();
		
		Cursor cells = mDbHelper.getReadableDatabase().rawQuery(CELL_POINTS_SQL_QUERY, new String[]{String.valueOf(mSession)});
		cells.moveToFirst();
		writeWayPoints(fw, cells);
		cells.close();
		
		fw.write("</gpx>");
		fw.close();
		
		mDbHelper.close();
	}

	/**
	 * Iterates on track points and write them.
	 * @param trackName Name of the track (metadata).
	 * @param fw Writer to the target file.
	 * @param c Cursor to track points.
	 * @throws IOException
	 */
	private void writeTrackPoints(final String trackName, final FileWriter fw, final Cursor c) throws IOException {
		final int colLatitude = c.getColumnIndex(Schema.COL_LATITUDE);
		final int colLongitude = c.getColumnIndex(Schema.COL_LONGITUDE);
		final int colAltitude = c.getColumnIndex(Schema.COL_ALTITUDE);
		final int colTimestamp = c.getColumnIndex(Schema.COL_TIMESTAMP);

		fw.write("\t" + "<trk>");
		fw.write("\t\t" + "<name>" + trackName + "</name>" + "\n");

		fw.write("\t\t" + "<trkseg>" + "\n");
		c.moveToFirst();
		while (!c.isAfterLast()) {
			StringBuffer out = new StringBuffer();
			out.append("\t\t\t" + "<trkpt lat=\"" 
					+ c.getDouble(colLatitude) + "\" "
					+ "lon=\"" + c.getDouble(colLongitude) + "\">");
			out.append("<ele>" + c.getDouble(colAltitude) + "</ele>");
			out.append("<time>" + POINT_DATE_FORMATTER.format(new Date(c.getLong(colTimestamp))) + "</time>");


			out.append("</trkpt>" + "\n");
			fw.write(out.toString());

			c.moveToNext();
		}

		fw.write("\t\t" + "</trkseg>" + "\n");
		fw.write("\t" + "</trk>" + "\n");
	}

	/**
	 * Iterates on way points and write them.
	 * @param fw Writer to the target file.
	 * @param c Cursor to way points.
	 * @throws IOException
	 */
	private void writeWayPoints(final FileWriter fw, final Cursor c) throws IOException {
		final int colLatitude = c.getColumnIndex(Schema.COL_LATITUDE);
		final int colLongitude = c.getColumnIndex(Schema.COL_LONGITUDE);
		final int colAltitude = c.getColumnIndex(Schema.COL_ALTITUDE);
		final int colTimestamp = c.getColumnIndex(Schema.COL_TIMESTAMP);
		final int colName = c.getColumnIndex("name");
		c.moveToFirst();
		while (!c.isAfterLast()) {
			StringBuffer out = new StringBuffer();
			out.append("\t" + "<wpt lat=\""
					+ c.getDouble(colLatitude) + "\" "
					+ "lon=\"" + c.getDouble(colLongitude) + "\">" + "\n");
			out.append("\t\t" + "<ele>" + c.getDouble(colAltitude) + "</ele>" + "\n");
			out.append("\t\t" + "<time>" + POINT_DATE_FORMATTER.format(new Date(c.getLong(colTimestamp))) + "</time>" + "\n");
			out.append("\t\t" + "<name>" + StringEscapeUtils.escapeXml(c.getString(colName)) + "</name>" + "\n");
			out.append("\t" + "</wpt>" + "\n");
			
			fw.write(out.toString());

			c.moveToNext();
		}
	}

}
