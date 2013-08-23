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

import org.mapsforge.core.model.GeoPoint;
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
public class SessionMapObjectsLoader extends AsyncTask<Object, Void, ArrayList<GeoPoint>> {

	private static final String	TAG	= SessionMapObjectsLoader.class.getSimpleName();

	/**
	 * Indices for doInBackground arguments
	 */
	public enum Argument { MIN_LAT_COL, MAX_LAT_COL, MIN_LON_COL, MIN_MAX_COL }
	
	private static final int	MIN_LAT_COL	= 0;
	private static final int	MAX_LAT_COL	= 1;
	private static final int	MIN_LON_COL	= 2;
	private static final int	MAX_LON_COL	= 3;
	private static final int	HIGHLIGHT_WIFI_COL	= 4;

	/**
	 * Interface for activity.
	 */
	public interface OnSessionLoadedListener {
		void onSessionLoaded(ArrayList<GeoPoint> points);
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
	 * 			args[0]: min latitude as double
	 * 			args[1]: max latitude as double
	 * 			args[2]: min longitude as double
	 *			args[3]: max longitude as double
	 *			args[4]: single wifi to highlight. If parameter is null, all session wifis are returned
	 */
	@Override
	protected final ArrayList<GeoPoint> doInBackground(final Object... args) {         
		Log.d(TAG, "Loading session wifis");
		ArrayList<GeoPoint> points = new ArrayList<GeoPoint>();

		if (args[HIGHLIGHT_WIFI_COL] == null) {
			// Draw either all session wifis ...
			// In this case, get all wifis from active session
			DataHelper dbHelper = new DataHelper(mContext);
			//TODO: instead of loading all session wifis and filter then pass filter values directly to database
			ArrayList<WifiRecord> sessionWifis = dbHelper.loadWifisOverview(dbHelper.loadActiveSession().getId());
			if (sessionWifis == null) {
				return points;
			}

			for (WifiRecord wifi : sessionWifis) {
				if (isLocationVisible(wifi.getBeginPosition(), (Double) args[MIN_LAT_COL], (Double) args[MAX_LAT_COL], (Double) args[MIN_LON_COL], (Double) args[MAX_LON_COL])) {
					//Log.w(TAG, "Add wifi " + wifi.getBssid() + " @ " + wifi.getRequestPosition().toString());
					points.add(new GeoPoint(wifi.getBeginPosition().getLatitude(), wifi.getBeginPosition().getLongitude()));
				} 
			}
		} else {
			// ... or only selected	
			// Log.d(TAG, "Single draw mode");
			points.add(new GeoPoint(((WifiRecord) args[HIGHLIGHT_WIFI_COL]).getBeginPosition().getLatitude(),
					((WifiRecord) args[HIGHLIGHT_WIFI_COL]).getBeginPosition().getLongitude()));
		}

		return points;
	}

	/**
	 * Informs activity on available results by calling mListener.
	 */
	@Override
	protected final void onPostExecute(final ArrayList<GeoPoint> points) {
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
