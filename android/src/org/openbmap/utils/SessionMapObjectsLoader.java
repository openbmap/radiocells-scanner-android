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
import org.openbmap.db.model.PositionRecord;
import org.openbmap.db.model.WifiRecord;

import android.content.Context;
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
	 * 			args[1]: min latitude as double
	 * 			args[2]: max latitude as double
	 * 			args[3]: min longitude as double
	 *			args[4]: max longitude as double
	 *			args[5]: single wifi to highlight. If parameter is null, all session wifis are returned
	 */
	@Override
	protected final ArrayList<LatLong> doInBackground(final Object... args) {         
		Log.d(TAG, "Loading session wifis");
		ArrayList<LatLong> points = new ArrayList<LatLong>(0);

		DataHelper dbHelper = new DataHelper(mContext);

		if (args[HIGHLIGHT_WIFI_COL] == null) {
			// Draw either all session wifis ...
			// In this case, get all wifis from active session
			//TODO: instead of loading all session wifis and filter then pass filter values directly to database
			ArrayList<WifiRecord> sessionWifis = dbHelper.loadWifisOverviewWithin((Integer) args[SESSION_ID],
					(Double) args[MIN_LON_COL],
					(Double) args[MAX_LON_COL],
					(Double) args[MIN_LAT_COL],
					(Double) args[MAX_LAT_COL]);

			if (sessionWifis == null) {
				return points;
			}

			for (WifiRecord wifi : sessionWifis) {
				points.add(new LatLong(wifi.getBeginPosition().getLatitude(), wifi.getBeginPosition().getLongitude()));

			}
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
