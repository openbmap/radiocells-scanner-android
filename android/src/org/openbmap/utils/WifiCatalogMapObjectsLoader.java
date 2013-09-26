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
import org.openbmap.Preferences;
import org.openbmap.activity.MapViewActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Loads reference wifis asynchronously.
 * Upon completion callback mListener in activity is invoked.
 */
public class WifiCatalogMapObjectsLoader extends AsyncTask<Object, Void, ArrayList<LatLong>> {

	private static final String	TAG	= WifiCatalogMapObjectsLoader.class.getSimpleName();

	/**
	 * Interface for activity.
	 */
	public interface OnCatalogLoadedListener {
		void onCatalogLoaded(ArrayList<LatLong> points);
	}

	/**
	 * Indices for doInBackground arguments
	 */
	private static final int	MIN_LAT_COL	= 0;
	private static final int	MAX_LAT_COL	= 1;
	private static final int	MIN_LON_COL	= 2;
	private static final int	MAX_LON_COL	= 3;

	/**
	 * Maximum overlay items diplayed
	 * Prevents out of memory/performance issues
	 */
	private static final int MAX_REFS = 5000;
	
	/**
	 * Creating a overlay item for each wifi can cause performance issues
	 * in densely mapped areas. If GROUP_WIFIS = true near wifis are merged
	 * into a single overlay item
	 */
	private static final boolean GROUP_WIFIS	= true;

	/**
	 * Keeps the SharedPreferences.
	 */
	private SharedPreferences prefs = null;

	/**
	 * Database containing well-known wifis from openbmap.org.
	 */
	private SQLiteDatabase mRefdb;

	private OnCatalogLoadedListener mListener;

	public WifiCatalogMapObjectsLoader(final Context context) {

		// dialog = new ProgressDialog(mContext);
		// get shared preferences
		if (context instanceof MapViewActivity) {
			setOnCatalogLoadedListener((OnCatalogLoadedListener) context);
		}
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
	}

	public final void setOnCatalogLoadedListener(final OnCatalogLoadedListener listener) {
		this.mListener = listener;
	}

	@Override
	protected final void onPreExecute() {

	}

	// TODO change signature to left, right, top, bottom
	/**
	 * Queries reference database for all wifis in specified range around map center.
	 * @param args
	 * 			Args is an object array containing
	 * 			args[0]: min latitude as double
	 * 			args[1]: max latitude as double
	 * 			args[2]: min longitude as double
	 *			args[3]: max longitude as double
	 */
	@Override
	protected final ArrayList<LatLong> doInBackground(final Object... args) {         

		ArrayList<LatLong> points = new ArrayList<LatLong>();

		try {
			// skipping if no reference database set 
			if (prefs.getString(Preferences.KEY_WIFI_CATALOG, Preferences.VAL_WIFI_CATALOG_NONE).equals(Preferences.VAL_WIFI_CATALOG_NONE)) {
				return points;
			}

			// Open catalog database
			String path = Environment.getExternalStorageDirectory().getPath()
					+ prefs.getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR)
					+ Preferences.WIFI_CATALOG_SUBDIR + "/" + prefs.getString(Preferences.KEY_WIFI_CATALOG, Preferences.VAL_REF_DATABASE);
			mRefdb = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);

			Cursor refs = null;
			if (!GROUP_WIFIS) {
				// Option 1: Full precision 
				refs = mRefdb.rawQuery("SELECT _id, latitude as grouped_lat, longitude as grouped_lon FROM wifi_zone WHERE "
						+ "(latitude > ? AND latitude < ? AND longitude > ? AND longitude < ?)", 
						// TODO this probably fails around 0 meridian
						new String[] {
								String.valueOf((Double) args[MIN_LAT_COL]),
								String.valueOf((Double) args[MAX_LAT_COL]),
								String.valueOf((Double) args[MIN_LON_COL]),
								String.valueOf((Double) args[MAX_LON_COL])}
						);
			} else {
				// Option 2 (default): Group in 10m intervals for performance reasons
				refs = mRefdb.rawQuery("SELECT round(latitude,4) as grouped_lat, round(longitude,4) as grouped_lon FROM wifi_zone WHERE "
						+ "(latitude > ? AND latitude < ? AND longitude > ? AND longitude < ?) GROUP BY grouped_lat, grouped_lon", 
						// TODO this probably fails around 0 meridian
						new String[] {
								String.valueOf((Double) args[MIN_LAT_COL]),
								String.valueOf((Double) args[MAX_LAT_COL]),
								String.valueOf((Double) args[MIN_LON_COL]),
								String.valueOf((Double) args[MAX_LON_COL])
						}
						);
			}

			int i = 0;
			int latCol = refs.getColumnIndex("grouped_lat");
			int lonCol = refs.getColumnIndex("grouped_lon");
			while (refs.moveToNext() && i < MAX_REFS) {
				points.add(new LatLong(refs.getDouble(latCol), refs.getDouble(lonCol)));
				i++;
			}
			Log.i(TAG, i + " reference wifis received in bounding box" 
					+ "[lat min " + (Double) args[MIN_LAT_COL] + " lat max " + (Double) args[MAX_LAT_COL] + " , lon min " + (Double) args[MIN_LON_COL] + " lon max " + (Double) args[MAX_LON_COL] +"]");
		} catch (SQLiteException e) {
			Log.e(TAG, "Sql exception occured: " + e.toString());
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (mRefdb != null) {
				mRefdb.close();	
			}
		}

		return points;
	}

	@Override
	protected final void onPostExecute(final ArrayList<LatLong> points) {

		if (mListener != null) {
			mListener.onCatalogLoaded(points);
		}
	}
}
