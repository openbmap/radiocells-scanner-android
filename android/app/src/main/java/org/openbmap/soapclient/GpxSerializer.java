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

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.util.Log;

import org.apache.commons.lang3.StringEscapeUtils;
import org.openbmap.Constants;
import org.openbmap.db.DatabaseHelper;
import org.openbmap.db.Schema;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Writes GPX file for session
 * Inspired by Nicolas Guillaumin
 */
public class GpxSerializer {

    private static final String TAG = GpxSerializer.class.getSimpleName();

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
    //@formatter:off
    private static final String TAG_GPX =
            "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\""
          + " version=\"1.1\""
          + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
          + " xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd \">\n";

    private static final String TAG_GPX_CLOSE = "</gpx>";

    private static final String TRACKPOINT_SQL_QUERY1 =
            "SELECT " + Schema.COL_LATITUDE + ", "
                  + Schema.COL_LONGITUDE + ", " + " " +
                    Schema.COL_ALTITUDE + ", " +
                    Schema.COL_ACCURACY + ", " +
                    Schema.COL_TIMESTAMP + ", " +
                    Schema.COL_BEARING
            + ", " + Schema.COL_SPEED + ", " +
                    Schema.COL_SESSION_ID + ", " +
                    Schema.COL_SOURCE + " "
            + " FROM " +
                    Schema.TBL_POSITIONS +
                    " WHERE " +
                    Schema.COL_SESSION_ID + " = ?"
                    + " AND source != '" + Constants.PROVIDER_USER_DEFINED + "' "
                + " ORDER BY " + Schema.COL_TIMESTAMP + " LIMIT " + CURSOR_SIZE
                + " OFFSET ?";

    private static final String WAYPOINT_SQL_QUERY =
            "SELECT " + Schema.COL_LATITUDE + ", " +
                    Schema.COL_LONGITUDE + ", "
            + " " + Schema.COL_ALTITUDE + ", " +
                    Schema.COL_ACCURACY + ", " +
                    Schema.COL_TIMESTAMP + ", " +
                    Schema.COL_BEARING
            + ", " + Schema.COL_SPEED + ", " +
                    Schema.COL_SESSION_ID + ", " +
                    Schema.COL_SOURCE + " "
            + " FROM " + Schema.TBL_POSITIONS +
                    " WHERE " + Schema.COL_SESSION_ID + " = ?"
                    + " AND source = '" + Constants.PROVIDER_USER_DEFINED + "' "
                  + " ORDER BY " + Schema.COL_TIMESTAMP + " LIMIT " + CURSOR_SIZE
                  + " OFFSET ?";

    private static final String WIFI_POINTS_SQL_QUERY =
            "SELECT w.rowid as " + Schema.COL_ID + ", w." +Schema.COL_BSSID + ", w." + Schema.COL_SSID + ", "
            + " MAX(" + Schema.COL_LEVEL + "), w." + Schema.COL_TIMESTAMP + ", "
            + " b." + Schema.COL_LATITUDE + ", b." + Schema.COL_LONGITUDE + ", b." + Schema.COL_ALTITUDE + ", b." + Schema.COL_ACCURACY
            + " FROM " + Schema.TBL_WIFIS + " as w JOIN positions as b ON request_pos_id = b._id "
            + " WHERE w." + Schema.COL_SESSION_ID + " = ? GROUP BY w." + Schema.COL_BSSID
            + " LIMIT " + CURSOR_SIZE + " OFFSET ?";

    private static final String CELL_POINTS_SQL_QUERY =
            "SELECT " +
                    Schema.COL_LATITUDE + ", " +
                    Schema.COL_LONGITUDE + ", " +
                    Schema.COL_ALTITUDE + ", " +
                    Schema.COL_ACCURACY + ", " +
                    Schema.COL_TIMESTAMP + ", \"CELL \" ||" +
                    Schema.COL_OPERATORNAME + " ||" +
                    Schema.COL_LOGICAL_CELLID + " AS name"
                + " FROM " + Schema.TBL_POSITIONS + " AS p LEFT JOIN "
                + " (SELECT " + Schema.COL_ID + ", " +
                    Schema.COL_OPERATORNAME + ", " +
                    Schema.COL_LOGICAL_CELLID + ", " +
                    Schema.COL_BEGIN_POSITION_ID +
                    " FROM " + Schema.TBL_CELLS + ") "
            + " AS c ON c." +
                    Schema.COL_BEGIN_POSITION_ID + " = p._id WHERE c._id IS NOT NULL " +
                    "AND p." + Schema.COL_SESSION_ID + " = ?"
            + " ORDER BY " + Schema.COL_TIMESTAMP + " LIMIT " + CURSOR_SIZE
            + " OFFSET ?";
    //@formatter:on
    /**
     * Date format as used internally
     */
    private static final SimpleDateFormat INTERNAL_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss",
                                                                                      Locale.US);

    /**
     * Date format for a gpx point timestamp.
     * ISO 8601 format
     */
    private static final SimpleDateFormat GPX_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                                                                                 Locale.US);

    /**
     * Levels of detail for GPX export
     */
    private static final int VERBOSITY_TRACK_AND_WAYPOINTS = 1;
    private static final int VERBOSITY_WAYPOINTS_ONLY = 2;
    private static final int VERBOSITY_ALL = 3;

    private final long session;

    private final Context context;

    private DatabaseHelper mDbHelper;

    public GpxSerializer(final Context context, final long session) {
        this.session = session;
        this.context = context;
    }

    /**
     * Writes the GPX file
     *
     * @param trackName
     *         Name of the GPX track (metadata)
     * @param target
     *         Target GPX file
     * @param verbosity
     *         GPX verbosity (see constants above)
     */
    public final void doExport(final String trackName, final File target, int verbosity) throws IOException {
        Log.i(TAG, "Exporting gpx file" + target.getAbsolutePath());
        mDbHelper = new DatabaseHelper(context.getApplicationContext());

        final BufferedWriter bw = new BufferedWriter(new FileWriter(target));

        bw.write(XML_HEADER);
        bw.write(TAG_GPX);

        if(verbosity == VERBOSITY_TRACK_AND_WAYPOINTS || verbosity == VERBOSITY_WAYPOINTS_ONLY
                   || verbosity == VERBOSITY_ALL) {
            writeWaypoints(bw);
        }

        if(verbosity == VERBOSITY_TRACK_AND_WAYPOINTS || verbosity == VERBOSITY_ALL) {
            writeTrackpoints(trackName, bw);
        }
        bw.flush();

        if(verbosity == VERBOSITY_ALL) {
            writeWifis(bw);
            bw.flush();
            writeCells(bw);
            bw.flush();
        }

        bw.write(TAG_GPX_CLOSE);
        bw.close();

        mDbHelper.close();
        Log.i(TAG, "Finished building gpx file");
    }

    /**
     * Iterates on track points and write them.
     *
     * @param bw
     *         Writer to the target file.
     */
    private void writeWaypoints(final BufferedWriter bw) throws IOException {
        Log.i(TAG, "Writing trackpoints");

        //@formatter:off
        Cursor c = mDbHelper.getReadableDatabase().rawQuery(WAYPOINT_SQL_QUERY,
                                                            new String[] {
                                                                    String.valueOf(session),
                                                                    String.valueOf(0)
                                                            });
        //@formatter:on

        final int colLatitude = c.getColumnIndex(Schema.COL_LATITUDE);
        final int colLongitude = c.getColumnIndex(Schema.COL_LONGITUDE);
        final int colAltitude = c.getColumnIndex(Schema.COL_ALTITUDE);
        final int colTimestamp = c.getColumnIndex(Schema.COL_TIMESTAMP);

        long outer = 0;
        while(!c.isAfterLast()) {
            c.moveToFirst();
            while(!c.isAfterLast()) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("<wpt lat=\"");
                stringBuilder.append(String.valueOf(c.getDouble(colLatitude)));
                stringBuilder.append("\" ");
                stringBuilder.append("lon=\"");
                stringBuilder.append(String.valueOf(c.getDouble(colLongitude)));
                stringBuilder.append("\">");
                stringBuilder.append("<ele>");
                stringBuilder.append(String.valueOf(c.getDouble(colAltitude)));
                stringBuilder.append("</ele>");
                stringBuilder.append("<time>");
                // time stamp conversion to ISO 8601
                stringBuilder.append(getGpxDate(c.getLong(colTimestamp)));
                stringBuilder.append("</time>");
                stringBuilder.append("</wpt>");

                bw.write(stringBuilder.toString());
                bw.flush(); //in case of exception we don't lose data
                c.moveToNext();
            }

            // fetch next CURSOR_SIZE records
            outer += CURSOR_SIZE;
            c.close();

            //@formatter:off
            c = mDbHelper.getReadableDatabase().rawQuery(WAYPOINT_SQL_QUERY,
                                                         new String[] {
                                                                 String.valueOf(session),
                                                                 String.valueOf(outer)
                                                         });
            //@formatter:on
        }

        c.close();
    }

    /**
     * Iterates on track points and write them.
     *
     * @param trackName
     *         Name of the track (metadata).
     * @param bw
     *         Writer to the target file.
     */
    private void writeTrackpoints(final String trackName, final BufferedWriter bw) throws IOException {
        Log.i(TAG, "Writing trackpoints");

        //@formatter:off
        Cursor c = mDbHelper.getReadableDatabase().rawQuery(TRACKPOINT_SQL_QUERY1,
                                                            new String[] {
                                                                    String.valueOf(session),
                                                                    String.valueOf(0)
                                                            });
        //@formatter:on

        final int colLatitude = c.getColumnIndex(Schema.COL_LATITUDE);
        final int colLongitude = c.getColumnIndex(Schema.COL_LONGITUDE);
        final int colAltitude = c.getColumnIndex(Schema.COL_ALTITUDE);
        final int colTimestamp = c.getColumnIndex(Schema.COL_TIMESTAMP);

        bw.write("<trk>");
        bw.write("<name>");
        bw.write(trackName);
        bw.write("</name>");
        bw.write("<trkseg>");

        long outer = 0;
        while(!c.isAfterLast()) {
            c.moveToFirst();
            while(!c.isAfterLast()) {
                StringBuffer out = new StringBuffer(32 * 1024);
                out.append("<trkpt lat=\"");
                out.append(String.valueOf(c.getDouble(colLatitude)));
                out.append("\" ");
                out.append("lon=\"");
                out.append(String.valueOf(c.getDouble(colLongitude)));
                out.append("\">");
                out.append("<ele>");
                out.append(String.valueOf(c.getDouble(colAltitude)));
                out.append("</ele>");
                out.append("<time>");
                // time stamp conversion to ISO 8601
                out.append(getGpxDate(c.getLong(colTimestamp)));
                out.append("</time>");
                out.append("</trkpt>");

                bw.write(out.toString());
                bw.flush();
                c.moveToNext();
            }

            // fetch next CURSOR_SIZE records
            outer += CURSOR_SIZE;
            c.close();
            //@formatter:off
            c = mDbHelper.getReadableDatabase().rawQuery(TRACKPOINT_SQL_QUERY1,
                                                         new String[] {
                                                                 String.valueOf(session),
                                                                 String.valueOf(outer)
                                                         });
            //@formatter:on
        }
        c.close();

        bw.write("</trkseg>");
        bw.write("</trk>");
        bw.flush();
    }

    /**
     * Iterates on way points and write them.
     *
     * @param bw
     *         Writer to the target file.
     */
    private void writeWifis(final BufferedWriter bw) throws IOException {
        Log.i(TAG, "Writing wifi waypoints");

        //@formatter:off
        Cursor c = mDbHelper.getReadableDatabase().rawQuery(WIFI_POINTS_SQL_QUERY,
                                                            new String[] {
                                                                    String.valueOf(session),
                                                                    String.valueOf(0)
                                                            });
        //@formatter:on

        final int colLatitude = c.getColumnIndex(Schema.COL_LATITUDE);
        final int colLongitude = c.getColumnIndex(Schema.COL_LONGITUDE);
        final int colAltitude = c.getColumnIndex(Schema.COL_ALTITUDE);
        final int colTimestamp = c.getColumnIndex(Schema.COL_TIMESTAMP);
        final int colSsid = c.getColumnIndex(Schema.COL_SSID);

        long outer = 0;
        while(!c.isAfterLast()) {
            c.moveToFirst();
            while(!c.isAfterLast()) {
                StringBuffer out = new StringBuffer();
                out.append("<wpt lat=\"");
                out.append(String.valueOf(c.getDouble(colLatitude)));
                out.append("\" ");
                out.append("lon=\"");
                out.append(String.valueOf(c.getDouble(colLongitude)));
                out.append("\">");
                out.append("<ele>");
                out.append(String.valueOf(c.getDouble(colAltitude)));
                out.append("</ele>\n");
                out.append("<time>");
                // time stamp conversion to ISO 8601
                out.append(getGpxDate(c.getLong(colTimestamp)));
                out.append("</time>");
                out.append("<name>");
                out.append(StringEscapeUtils.escapeXml10(c.getString(colSsid)));
                out.append("</name>");
                out.append("</wpt>");
                bw.write(out.toString());
                bw.flush();
                c.moveToNext();
            }
            // fetch next CURSOR_SIZE records
            outer += CURSOR_SIZE;
            c.close();
            //@formatter:off
            c = mDbHelper.getReadableDatabase().rawQuery(WIFI_POINTS_SQL_QUERY,
                                                         new String[] {
                                                                 String.valueOf(session),
                                                                 String.valueOf(outer)
                                                         });
            //@formatter:on
        }
        c.close();
    }

    /**
     * Iterates on way points and write them.
     *
     * @param bw
     *         Writer to the target file.
     */
    private void writeCells(final BufferedWriter bw) throws IOException {
        Log.i(TAG, "Writing cell waypoints");
        //@formatter:off
        Cursor c = mDbHelper.getReadableDatabase().rawQuery(CELL_POINTS_SQL_QUERY,
                                                            new String[] {
                                                                    String.valueOf(session),
                                                                    String.valueOf(0)
                                                            });
        //@formatter:on

        final int colLatitude = c.getColumnIndex(Schema.COL_LATITUDE);
        final int colLongitude = c.getColumnIndex(Schema.COL_LONGITUDE);
        final int colAltitude = c.getColumnIndex(Schema.COL_ALTITUDE);
        final int colTimestamp = c.getColumnIndex(Schema.COL_TIMESTAMP);
        final int colName = c.getColumnIndex("name");

        long outer = 0;
        while(!c.isAfterLast()) {
            c.moveToFirst();
            while(!c.isAfterLast()) {
                StringBuffer out = new StringBuffer();
                out.append("<wpt lat=\"");
                out.append(String.valueOf(c.getDouble(colLatitude)));
                out.append("\" ");
                out.append("lon=\"");
                out.append(String.valueOf(c.getDouble(colLongitude)));
                out.append("\">");
                out.append("<ele>");
                out.append(String.valueOf(c.getDouble(colAltitude)));
                out.append("</ele>");
                out.append("<time>");
                // time stamp conversion to ISO 8601
                out.append(getGpxDate(c.getLong(colTimestamp)));
                out.append("</time>");
                out.append("<name>");
                out.append(StringEscapeUtils.escapeXml10(c.getString(colName)));
                out.append("</name>");
                out.append("</wpt>");

                bw.write(out.toString());
                bw.flush();
                c.moveToNext();
            }

            //bw.write(out.toString());
            //out = null;
            // fetch next CURSOR_SIZE records
            outer += CURSOR_SIZE;
            c.close();
            //@formatter:off
            c = mDbHelper.getReadableDatabase().rawQuery(CELL_POINTS_SQL_QUERY,
                                                         new String[] {
                                                                 String.valueOf(session),
                                                                 String.valueOf(outer)
                                                         });
            //@formatter:on
        }
        c.close();
    }

    /**
     * Converts from openbmap date format (YYYYMMDDHHMMSS) to gpx date format (ISO 8601)
     *
     * @param raw
     *         Openbmap date
     *
     * @return ISO 8601 date
     */
    private static String getGpxDate(final long raw) {
        try {
            // for gpx files we need data in ISO 8601 format (e.g. 2011-12-31T23:59:59Z)
            // as opposed to openbmap database format YYYYMMDDHHMMSS
            final Date converted = INTERNAL_DATE_FORMAT.parse(String.valueOf(raw));
            GPX_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
            return (GPX_DATE_FORMAT.format(converted));
        } catch(final ParseException e) {
            // should never happen
            Log.e(TAG, "Error converting gpx date. Source " + raw);
            return "0000-00-00T00:00:00Z";
        }
    }

    @NonNull
    public static final String suggestGpxFilename(long id) {
        final SimpleDateFormat date = new SimpleDateFormat("yyyyMMddhhmmss", Locale.US);
        return date.format(new Date(System.currentTimeMillis())) + "(" + String.valueOf(
                id) + ")" + ".gpx";
    }
}
