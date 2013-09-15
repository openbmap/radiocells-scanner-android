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

import android.content.Context;
import android.os.AsyncTask;

/**
 * Loads session wifis asynchronously.
 */
public class GpxMapObjectsLoader extends AsyncTask<Object, Void, ArrayList<LatLong>> {

	@SuppressWarnings("unused")
	private static final String	TAG	= GpxMapObjectsLoader.class.getSimpleName();

	/**
	 * Indices for doInBackground arguments
	 */
	public enum Argument { MIN_LAT_COL, MAX_LAT_COL, MIN_LON_COL, MIN_MAX_COL }

	private static final int	MIN_LAT_COL	= 0;
	private static final int	MAX_LAT_COL	= 1;
	private static final int	MIN_LON_COL	= 2;
	private static final int	MAX_LON_COL	= 3;

	/**
	 * Interface for activity.
	 */
	public interface OnGpxLoadedListener {
		void onGpxLoaded(ArrayList<LatLong> points);
	}

	private Context	mContext;

	private OnGpxLoadedListener mListener;

	public GpxMapObjectsLoader(final Context context) {

		mContext = context;

		if (context instanceof MapViewActivity) {
			setOnGpxLoadedListener((OnGpxLoadedListener) context);
		}
	}

	public final void setOnGpxLoadedListener(final OnGpxLoadedListener listener) {
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
	 */
	@Override
	protected final ArrayList<LatLong> doInBackground(final Object... args) {         
		//Log.d(TAG, "Loading gpx points");
	
		DataHelper dbHelper = new DataHelper(mContext);
		
		ArrayList<PositionRecord> positions = dbHelper.loadPositions(String.valueOf(dbHelper.loadActiveSession().getId()),
				(Double) args[MIN_LAT_COL], (Double) args[MAX_LAT_COL], (Double) args[MIN_LON_COL], (Double) args[MAX_LON_COL]);
		
		ArrayList<LatLong> points = new ArrayList<LatLong>();
		for (int i = 0; i < positions.size(); i++) {
			points.add(new LatLong(positions.get(i).getLatitude(), positions.get(i).getLongitude()));
		}
		
		return points;
	}

	/**
	 * Informs activity on available results by calling mListener.
	 */
	@Override
	protected final void onPostExecute(final ArrayList<LatLong> points) {
		if (mListener != null) {
			mListener.onGpxLoaded(points);
		}
	}

}
