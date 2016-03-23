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

import android.app.ProgressDialog;
import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.openbmap.R;
import org.openbmap.db.DatabaseHelper;

/**
 * Reorganizes database (vacuum)
 */
public class VacuumCleaner extends AsyncTask<Void, Void, Boolean> {

	private static final String TAG = VacuumCleaner.class.getSimpleName();
	private final Context	mContext;
	private ProgressDialog	mDialog;

	public VacuumCleaner(final Context context) {
		mContext = context;
	}
	
	@Override
	protected final void onPreExecute() {
		mDialog = new ProgressDialog(mContext);
		mDialog.setTitle(mContext.getString(R.string.cleaning_database));
		mDialog.setMessage(mContext.getString(R.string.please_stay_patient));
		mDialog.setCancelable(false);
		mDialog.setIndeterminate(true);
		mDialog.show();
	}

	@Override
	protected final Boolean doInBackground(final Void... params) {

		Log.i(TAG, "Cleaning database");
		try {
			final SQLiteDatabase db = new DatabaseHelper(mContext).getWritableDatabase();
			db.execSQL("VACUUM");
			Log.i(TAG, "Finished cleaning");
			db.close();
		} catch (final SQLiteDatabaseLockedException e){
			// possibly a database upgrade is currently taking place
			Log.e(TAG, "Error locking database");
			return false;
		} catch (final SQLiteException e) {
			Log.e(TAG, "Database error: " + e.getMessage());
			return false;
		} catch (final SQLException e) {
            Log.e(TAG, "Generic database expection: " + e.getMessage());
            return false;
        }
		return true;
	}

	@Override
	protected final void onPostExecute(final Boolean success) {
		if (mDialog != null) {
			mDialog.dismiss();
		}
		
		if (!success) {
			Toast.makeText(mContext,  mContext.getResources().getString(R.string.database_lock_error), Toast.LENGTH_LONG).show();
		}
	}
}
