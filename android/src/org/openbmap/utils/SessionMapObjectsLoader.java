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

import java.util.ArrayList;

import org.mapsforge.core.model.LatLong;
import org.openbmap.activity.MapViewActivity;
import org.openbmap.db.DataHelper;
import org.openbmap.db.DatabaseHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.model.PositionRecord;
import org.openbmap.db.model.WifiRecord;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Loads session wifis asynchronously.
 */
public class SessionMapObjectsLoader extends AsyncTask<Object, Void, ArrayList<LatLong>> {

	private static final String	TAG	= SessionMapObjectsLoader.class.getSimpleName();

	/**
	 * Indices for doInBackground arguments
	 */
	public enum Arguments { SESSION_ID, MIN_LAT_COL, MAX_LAT_COL, MIN_LON_COL, MIN_MAX_COL }

	private static final int 	SESSION_ID = 0;
	private static final int	MIN_LAT_COL	= 1;
	private static final int	MAX_LAT_COL	= 2;
	private static final int	MIN_LON_COL	= 3;
	private static final int	MAX_LON_COL	= 4;
	private static final int	HIGHLIGHT_WIFI_COL	= 5;

	/**
	 * Interface for activity.
	 */
	public interface OnSessionLoadedListener {
		void onSessionLoaded(ArrayList<LatLong> points);
	}

	private Context	mContext;

	private OnSessionLoadedListener mListener;

	public SessionMapObjectsLoader(final Context context) {

		mContext = context;

		if (context instanceof MapViewActivity) {
			setOnSessionLoadedListener((OnSessionLoadedListener) context);
		}
	}

	public final void setOnSessionLoadedListener(final OnSessionLoadedListener listener) {
		this.mListener = listener;
	}

	/**
	 * Queries reference database for all wifis in specified range around map centre.
	 * @param args
	 * 			Args is an object array containing
	 * 			SESSION_ID, MIN_LAT_COL, MAX_LAT_COL, MIN_LON_COL, MIN_MAX_COL
	 */
	@Override
	protected final ArrayList<LatLong> doInBackground(final Object... args) {         
		Log.d(TAG, "Loading session wifis");
		ArrayList<LatLong> points = new ArrayList<LatLong>(0);

		DataHelper dbHelper = new DataHelper(mContext);

		if (args[HIGHLIGHT_WIFI_COL] == null) {
			// Draw either all session wifis ...
			
			//long start = System.currentTimeMillis();
			DatabaseHelper mDbHelper = new DatabaseHelper(mContext);
			
			// use raw query for performance reasons
			String query = "SELECT w.rowid as " + Schema.COL_ID + ", MAX(" + Schema.COL_LEVEL + "), b." + Schema.COL_LATITUDE
					+ ", b." + Schema.COL_LONGITUDE + " FROM " + Schema.TBL_WIFIS + " as w "
					+ "JOIN " + Schema.TBL_POSITIONS + " as b ON " + Schema.COL_BEGIN_POSITION_ID + " = b." + Schema.COL_ID 
					+ "  WHERE w." + Schema.COL_SESSION_ID + " = " + (Integer) args[SESSION_ID] + " AND " 
					+ " b.longitude >= " + (Double) args[MIN_LON_COL] + " AND "
					+ " b.longitude <= " + (Double) args[MAX_LON_COL] + " AND " 
					+ " b.latitude >= " + (Double) args[MIN_LAT_COL] + " AND "
					+ " b.latitude <= " + (Double) args[MAX_LAT_COL] + " GROUP BY w." + Schema.COL_BSSID + ", w." + Schema.COL_MD5_SSID;
			
			Cursor ca = mDbHelper.getReadableDatabase().rawQuery(query, null);
			final int columnIndex7 = ca.getColumnIndex(Schema.COL_LATITUDE);
			final int columnIndex8 = ca.getColumnIndex(Schema.COL_LONGITUDE);

			while (ca.moveToNext()) {
				points.add(new LatLong(ca.getDouble(columnIndex7), ca.getDouble(columnIndex8)));
			}
			ca.close();
			
			//Log.d(TAG, "loaded wifi overlay in (" + (System.currentTimeMillis() - start) + " ms)");
		} else {
			// ... or only selected	
			ArrayList<WifiRecord> candidates = dbHelper.loadWifisByBssid((String) args[HIGHLIGHT_WIFI_COL]);
			if (candidates.size() > 0) {
				points.add(new LatLong((candidates.get(0)).getBeginPosition().getLatitude(),
						(candidates.get(0)).getBeginPosition().getLongitude()));
			}
		}

		return points;
	}

	/**
	 * Informs activity on available results by calling mListener.
	 */
	@Override
	protected final void onPostExecute(final ArrayList<LatLong> points) {
		if (mListener != null) {
			mListener.onSessionLoaded(points);
		}
	}

	/**
	 * Checks whether location is within (visible) bounding box.
	 * The bounding box, whose dimension are determined in activity, is described by min & max parameters
	 * @param mLocation Location to test
	 * @param minLat minimum latitude
	 * @param maxLat maximum latitude
	 * @param minLon minimum longitude
	 * @param maxLon maximum longitude
	 * @return true if location is on screen
	 */
	private boolean isLocationVisible(final PositionRecord loc, final double minLat, final double maxLat, final double minLon, final double maxLon) {
		return (loc.getLatitude() > minLat && loc.getLatitude() < maxLat
				&& loc.getLongitude() > minLon && loc.getLongitude() < maxLon);

	}
}
