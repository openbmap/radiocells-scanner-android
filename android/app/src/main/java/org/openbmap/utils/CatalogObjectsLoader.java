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
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.util.Log;

import org.mapsforge.core.model.LatLong;
import org.openbmap.db.CatalogDatabaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads reference wifis asynchronously.
 * Upon completion callback mListener in activity is invoked.
 */
public class CatalogObjectsLoader extends AsyncTask<Object, Void, List<LatLong>> {

    private static final String TAG = CatalogObjectsLoader.class.getSimpleName();

    /**
     * Arguments (indices) for doInBackground arguments
     */
    private static final int MIN_LAT_COL = 0;
    private static final int MAX_LAT_COL = 1;
    private static final int MIN_LON_COL = 2;
    private static final int MAX_LON_COL = 3;

    /**
     * Interface for activity.
     */
    public interface OnCatalogLoadedListener {

        void onCatalogLoaded(List<LatLong> points);
    }

    /**
     * Creating a overlay item for each wifi can cause performance issues
     * in densely mapped areas. If GROUP_WIFIS = true near wifis are merged
     * into a single overlay item
     */
    private static final boolean GROUP_WIFIS = true;

    private OnCatalogLoadedListener mListener;
    private final Context mAppContext;

    public CatalogObjectsLoader(final Context context, final OnCatalogLoadedListener listener) {
        setOnCatalogLoadedListener(listener);
        mAppContext = context.getApplicationContext();
    }

    public final void setOnCatalogLoadedListener(final OnCatalogLoadedListener listener) {
        this.mListener = listener;
    }

    // TODO change signature to left, right, top, bottom

    /**
     * Queries reference database for all wifis in specified range around map center.
     *
     * @param args
     *         Args is an object array containing
     *         args[0]: min latitude as double
     *         args[1]: max latitude as double
     *         args[2]: min longitude as double
     *         args[3]: max longitude as double
     */
    @Override
    protected final List<LatLong> doInBackground(final Object... args) {

        List<LatLong> points = new ArrayList<>();

        try {
            CatalogDatabaseHelper databaseHelper = CatalogDatabaseHelper.getInstance(mAppContext);

            // return empty result list if reference database not available
            if(databaseHelper.getFilename() == null) {
                Log.v(TAG, "Wifi catalog database not set - skipping");
                return points;
            }

            if(!GROUP_WIFIS) {
                points = databaseHelper.getPoints((Double) args[MIN_LAT_COL],
                                                  (Double) args[MAX_LAT_COL],
                                                  (Double) args[MIN_LON_COL],
                                                  (Double) args[MAX_LON_COL]);
            } else {
                // Option 2 (default): Group in 10m intervals for performance reasons
                points = databaseHelper.getPointsLazy((Double) args[MIN_LAT_COL],
                                                      (Double) args[MAX_LAT_COL],
                                                      (Double) args[MIN_LON_COL],
                                                      (Double) args[MAX_LON_COL]);
            }

        } catch(final SQLiteException e) {
            Log.e(TAG, "Sql exception occured: " + e.toString(), e);
        } catch(final Exception e) {
            Log.e(TAG, e.toString(), e);
        }

        return points;
    }

    @Override
    protected final void onPostExecute(final List<LatLong> points) {

        if(mListener != null) {
            mListener.onCatalogLoaded(points);
        }
    }
}
