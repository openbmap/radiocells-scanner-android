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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.openbmap.R;
import org.openbmap.utils.MediaScanner;

import java.io.File;
import java.io.IOException;

/**
 * Manages export gpx process
 */
public class SaveGpxTask extends AsyncTask<Void, Object, Boolean> {

	private static final String TAG = SaveGpxTask.class.getSimpleName();

	/**
	 * Session Id to export
	 */
	private final int mSession;

	/**
	 * Message in case of an error
	 */
	private final String errorMsg = null;

	/**
	 * Used for callbacks.
	 */
	private final SaveGpxTaskListener mListener;

    private Context mAppContext;

	/**
	 * Folder where GPX file is created
	 */
	private final String mPath;

	/**
	 * GPX filename
	 */
	private final String mFilename;

    private final int mVerbosity;
	
	public interface SaveGpxTaskListener {
		void onSaveGpxProgressUpdate(Object[] values);
		void onSaveGpxCompleted(final String filename);
		void onSaveGpxFailed(final int id, final String error);
	}

	//http://stackoverflow.com/questions/9573855/second-instance-of-activity-after-orientation-change
	/**
	 *  @param context
	 * @param listener
	 * @param session
	 * @param path
	 * @param filename
	 * @param verbosity
	 */
	public SaveGpxTask(final Context context, final SaveGpxTaskListener listener, final int session,
					   final String path, final String filename, int verbosity) {
		mAppContext = context.getApplicationContext();
		mSession = session;
		mPath = path;
		mFilename = filename;
		mListener = listener;
		mVerbosity = verbosity;
	}

	/**
	 * Builds GPX file (not uploaded in any case)
	 */
	@Override
	protected final Boolean doInBackground(final Void... params) {
		Boolean success = false;
		
		publishProgress(mAppContext.getResources().getString(R.string.please_stay_patient), mAppContext.getResources().getString(R.string.exporting_gpx), 0);
		final GpxSerializer gpx = new GpxSerializer(mAppContext, mSession);
		final File target = new File(mPath, mFilename);
		try {
			gpx.doExport(mFilename, target, mVerbosity);
			success = true;
		} catch (final IOException e) {
			Log.e(TAG, "Can't write gpx file " + mPath + File.separator + mFilename);
		}
		
		return success;
	}

	/**
	 * Updates progress bar.
	 * @param values[0] contains title (as string)
	 * @param values[1] contains message (as string)
	 * @param values[2] contains progress (as int)
	 */
	@Override
	protected final void onProgressUpdate(final Object... values) {
		if (mListener != null) {
			mListener.onSaveGpxProgressUpdate(values);
		}
	}

	@SuppressLint("NewApi")
	@Override
	protected final void onPostExecute(final Boolean success) {

		// rescan SD card, otherwise files may not be visible when connected to desktop pc
		// (MTP cache problem)
		Log.i(TAG, "Re-indexing SD card folder " + mPath);
		new MediaScanner(mAppContext, new File(mPath));


		if (success) {
			if (mListener != null) {
				mListener.onSaveGpxCompleted(mPath + "/" + mFilename);
			}
			return;
		} else {
			if (mListener != null) {
				mListener.onSaveGpxFailed(mSession, errorMsg);
			}
			return;
		}
	}

	/**
	 * @param context
	 */
	public void setContext(final Context context) {
		mAppContext = context;
	}

}
