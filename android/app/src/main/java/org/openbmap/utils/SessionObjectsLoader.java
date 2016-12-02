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

package org.openbmap.utils;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.openbmap.db.DatabaseHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.models.PositionRecord;
import org.openbmap.events.onSessionUpdateAvailable;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads session wifis asynchronously.
 */
public class SessionObjectsLoader extends AsyncTask<Object, Void, List<SessionLatLong>> {

    private static final String TAG = SessionObjectsLoader.class.getSimpleName();

    private static final String SELECT_SQL = "SELECT w.rowid as " + Schema.COL_ID + ", "+
            " MAX(" + Schema.COL_LEVEL + ")," +
            " w." + Schema.COL_SESSION_ID + ", " +
            " b." + Schema.COL_LATITUDE + ", b." + Schema.COL_LONGITUDE +
            " FROM " + Schema.TBL_WIFIS + " as w " +
            " JOIN " + Schema.TBL_POSITIONS + " as b" +
            " ON " + Schema.COL_BEGIN_POSITION_ID + " = b." + Schema.COL_ID;

    private static final String BBOX_CRITERIA = " WHERE w." + Schema.COL_SESSION_ID + " IN (%s) AND " +
            " b.longitude >= %s AND " +
            " b.longitude <= %s AND " +
            " b.latitude >= %s AND " +
            " b.latitude <= %s";

    private static final String SSID_CRITERIA = "WHERE w." + Schema.COL_SSID + "= %s";

    private static final String GROUP_BY = " GROUP BY w." + Schema.COL_BSSID;

    private final DatabaseHelper mDbHelper;

    private static final int MIN_LAT_COL = 0;
    private static final int MAX_LAT_COL = 1;
    private static final int MIN_LON_COL = 2;
    private static final int MAX_LON_COL = 3;
    private static final int HIGHLIGHT_WIFI_COL = 4;

    /**
     * Sessions to load, by default only active session
     */
    private final List<Integer> mToLoad;

    public SessionObjectsLoader(final Context context, final List<Integer> sessions) {
        mDbHelper = new DatabaseHelper(context.getApplicationContext());
        mToLoad = sessions;
    }

    /**
     * Queries reference database for all wifis within specified range around map centre.
     *
     * @param args
     *         Args is an object array containing
     *         MIN_LAT_COL, MAX_LAT_COL, MIN_LON_COL, MIN_MAX_COL
     */
    @Override
    protected final List<SessionLatLong> doInBackground(final Object... args) {
        Log.d(TAG, "Loading session wifis");
        final List<SessionLatLong> points = new ArrayList<>();

        if(args[HIGHLIGHT_WIFI_COL] == null) {
            // Draw either all session wifis ...

            final StringBuilder selected = new StringBuilder();
            for(int i = 0; i < mToLoad.size(); i++) {
                selected.append(mToLoad.get(i));

                if(i < mToLoad.size() - 1) {
                    selected.append(", ");
                }
            }

            // use raw query for performance reasons
            final String query = SELECT_SQL + String.format(BBOX_CRITERIA,
                    selected,
                    args[MIN_LON_COL],
                    args[MAX_LON_COL],
                    args[MIN_LAT_COL],
                    args[MAX_LAT_COL]
            ) + GROUP_BY;

            final Cursor cursor = mDbHelper.getReadableDatabase().rawQuery(query, null);
            final int colLat = cursor.getColumnIndex(Schema.COL_LATITUDE);
            final int colLon = cursor.getColumnIndex(Schema.COL_LONGITUDE);
            final int colSession = cursor.getColumnIndex(Schema.COL_SESSION_ID);

            while(cursor.moveToNext()) {
                points.add(new SessionLatLong(cursor.getDouble(colLat), cursor.getDouble(colLon),
                                              cursor.getInt(colSession)));
            }
            Log.d(TAG, cursor.getCount() + " session points loaded");
            cursor.close();

        } else {
            // ... or only selected
            // use raw query for performance reasons
            final String query = SELECT_SQL + String.format(SSID_CRITERIA,mToLoad.get(0));

            final Cursor cursor = mDbHelper.getReadableDatabase().rawQuery(query, null);
            final int colLat = cursor.getColumnIndex(Schema.COL_LATITUDE);
            final int colLon = cursor.getColumnIndex(Schema.COL_LONGITUDE);
            final int colSession = cursor.getColumnIndex(Schema.COL_SESSION_ID);

            if (cursor.moveToNext()) {
                points.add(new SessionLatLong(cursor.getDouble(colLat), cursor.getDouble(colLon),
                        cursor.getInt(colSession)));
            }
            Log.d(TAG, cursor.getCount() + " session points loaded");
            cursor.close();
        }

        return points;
    }

    /**
     * Informs activity on available results by calling mListener.
     */
    @Override
    protected final void onPostExecute(final List<SessionLatLong> points) {
        EventBus.getDefault().post(new onSessionUpdateAvailable(points));
    }

    /**
     * Checks whether location is within (visible) bounding box.
     * The bounding box, whose dimension are determined in activity, is described by min & max parameters
     *
     * @param minLat
     *         minimum latitude
     * @param maxLat
     *         maximum latitude
     * @param minLon
     *         minimum longitude
     * @param maxLon
     *         maximum longitude
     *
     * @return true if location is on screen
     */
    private boolean isLocationVisible(final PositionRecord loc, final double minLat, final double maxLat, final double minLon, final double maxLon) {
        return (loc.getLatitude() > minLat && loc.getLatitude() < maxLat
                        && loc.getLongitude() > minLon && loc.getLongitude() < maxLon);

    }
}
