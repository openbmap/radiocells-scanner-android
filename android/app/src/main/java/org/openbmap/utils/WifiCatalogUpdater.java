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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import org.openbmap.Preferences;
import org.openbmap.db.DatabaseHelper;
import org.openbmap.db.RadioBeaconContentProvider;
import org.openbmap.db.Schema;

import java.io.File;
import java.util.ArrayList;

/**
 * Adds new wifis to wifi catalog
 */
public class WifiCatalogUpdater extends AsyncTask<Void, Void, Void> {

	private static final String	TAG	= WifiCatalogUpdater.class.getSimpleName();

	/**
	 * Keeps the SharedPreferences.
	 */
	private SharedPreferences prefs = null;

	private DatabaseHelper	mDbHelper;

	private Context	mContext;

	public WifiCatalogUpdater(final Context context) {
		mContext = context;
		// dialog = new ProgressDialog(mContext);
		// get shared preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		mDbHelper = new DatabaseHelper(context);
	}

	@Override
	protected final Void doInBackground(final Void... args) {         

		try {
			// skipping if no reference database set 
			if (prefs.getString(Preferences.KEY_WIFI_CATALOG_FILE, Preferences.VAL_WIFI_CATALOG_NONE).equals(Preferences.VAL_WIFI_CATALOG_NONE)) {
				Log.w(TAG, "Nothing to update: no wifi catalog set");
				return null;
			}

			SQLiteDatabase localDb = mDbHelper.getReadableDatabase();
			Cursor cursorWifis = localDb.rawQuery(
					"SELECT w." + Schema.COL_BSSID + ", avg(p."+ Schema.COL_LATITUDE +") as latitude, avg(p."+ Schema.COL_LONGITUDE +") as longitude FROM "+ Schema.TBL_WIFIS +" w"
					+ " JOIN "+ Schema.TBL_POSITIONS + " p ON (w."+ Schema.COL_BEGIN_POSITION_ID +" = p."+ Schema.COL_ID +") WHERE " + Schema.COL_KNOWN_WIFI + " = 0 GROUP BY w."+ Schema.COL_BSSID + "",
					null);

			// Open catalog database
			String file = prefs.getString(Preferences.KEY_WIFI_CATALOG_FOLDER, 
					mContext.getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.WIFI_CATALOG_SUBDIR)
					+ File.separator + prefs.getString(Preferences.KEY_WIFI_CATALOG_FILE, Preferences.VAL_WIFI_CATALOG_FILE);

			SQLiteDatabase catalogDb = null;
			try {
				catalogDb = SQLiteDatabase.openDatabase(file, null, SQLiteDatabase.OPEN_READWRITE);
			}
			catch (SQLiteException e) {
				e.printStackTrace();
				Log.e(TAG, "Can't open wifi catalog database");
				return null;
			}

			catalogDb.rawQuery("PRAGMA journal_mode=DELETE", null);

			ArrayList<String> updateLater = new ArrayList<String>();
			ArrayList<ContentValues> newWifis = new ArrayList<ContentValues>();
			while (cursorWifis.moveToNext()) {
				//Log.d(TAG, "Inserting " + cursorWifis.getString(0).replace(":", "") );

				ContentValues newWifi = new ContentValues();
				newWifi.put("bssid", cursorWifis.getString(0).replace(":", ""));
				newWifi.put("latitude", cursorWifis.getDouble(1));
				newWifi.put("longitude", cursorWifis.getDouble(2));
				newWifi.put("source", 99);
				newWifis.add(newWifi);
				
				updateLater.add(cursorWifis.getString(0));
			}
			
			Log.i(TAG, "Pending inserts " + cursorWifis.getCount());
			cursorWifis.close();
			localDb.close();
			
			catalogDb.beginTransaction();
			try {
				for (ContentValues add : newWifis) {
					catalogDb.insertWithOnConflict("wifi_zone", null, add, SQLiteDatabase.CONFLICT_IGNORE);
				}
				catalogDb.setTransactionSuccessful();
			} finally {         
				catalogDb.endTransaction();
		    }
			
			Log.i(TAG, "Added wifis to catalog, updating known wifi tag for all sessions");
			ContentResolver contentResolver = mContext.getContentResolver();
			ContentValues updateExisting = new ContentValues();
			updateExisting.put(Schema.COL_KNOWN_WIFI, 2);
			
			for (String bssid : updateLater) {
				// reset is_new_wifi status
				contentResolver.update(RadioBeaconContentProvider.CONTENT_URI_WIFI, updateExisting,
						Schema.COL_BSSID + " = ?", new String[]{bssid});
			}
			// DON'T DO THIS!
			//catalogDb.execSQL("VACUUM");
			//catalogDb.execSQL("COMMIT");
			catalogDb.close();
			Log.i(TAG, "Catalog update finished ");
		} catch (SQLiteException e) {
			Log.e(TAG, "SQL exception occured: " + e.toString());
		} catch (Exception e) {
			e.printStackTrace();
		} 

		return null;
	}
}
