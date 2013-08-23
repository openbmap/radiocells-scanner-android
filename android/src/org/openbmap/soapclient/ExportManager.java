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

import java.io.File;
import java.io.IOException;

import org.openbmap.R;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

/**
 * Manages export and upload process
 */
public class ExportManager extends AsyncTask<Void, Object, Boolean> {

	private static final String TAG = ExportManager.class.getSimpleName();

	private Context mContext;

	/**
	 * Session Id to export
	 */
	private int mSession;

	/**
	 * Progress dialog
	 */
	private ProgressDialog mDialog;

	/**
	 * Message in case of an error
	 */
	private String errorMsg = null;

	/**
	 * Used for callbacks.
	 */
	private ExportManagerListener mListener;

	/**
	 * Directory where xmls files are stored
	 */
	private String	mTargetPath;

	/*
	 *  Openbmap credentials : openbmap username
	 */
	private final String	mUser;
	/*
	 *  Openbmap credentials : openbmap password
	 */
	private final String	mPassword;


	private boolean	mExportCells = false;

	private boolean	mExportWifis = false;

	private boolean	mExportGpx = false;

	private String	mGpxFile;

	private String	mGpxPath;

	private Boolean	mSkipUpload;

	private Boolean	mSkipDelete;

	public interface ExportManagerListener {
		void onExportCompleted(final int id);
		void onExportFailed(final String error);
	}

	/**
	 * 
	 * @param mContext	Activities' mContext
	 * @param mListener	Listener to call upon completion
	 * @param session	Session id to export
	 * @param password 
	 * @param user 
	 */
	public ExportManager(final Context context, final ExportManagerListener listener, final int session, final String targetPath, final String user, final String password) {
		this.mContext = context;
		this.mSession = session;
		this.mTargetPath = targetPath;
		this.mUser = user;
		this.mPassword = password;
		this.mListener = listener;

		// by default: upload and delete local temp files afterward
		this.setSkipUpload(false);
		this.setSkipDelete(false);
	}

	@Override
	protected final void onPreExecute() {
		mDialog = new ProgressDialog(mContext);
		mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		//mDialog.setIndeterminate(true);
		mDialog.setTitle(mContext.getString(R.string.preparing_export));
		mDialog.setCancelable(false);
		mDialog.setIndeterminate(true);
		mDialog.show();
	}

	/**
	 * Builds cell xml files and saves/uploads them
	 */
	@Override
	protected final Boolean doInBackground(final Void... params) {
		if (mExportCells) {
			if (getSkipUpload()) {
				publishProgress(mContext.getResources().getString(R.string.exporting_cells), 0);
			} else {
				publishProgress(mContext.getResources().getString(R.string.uploading_cells), 0);
			}
			new CellExporter(mContext, mSession, mTargetPath, mUser, mPassword, getSkipUpload(), getSkipDelete()).doInBackground();
		} else {
			Log.i(TAG, "Cell export skipped");
		}

		if (mExportWifis) {
			if (getSkipUpload()) {
				publishProgress(mContext.getResources().getString(R.string.exporting_wifis), 50);
			} else {
				publishProgress(mContext.getResources().getString(R.string.uploading_wifis), 50);
			}
			new WifiExporter(mContext, mSession, mTargetPath, mUser, mPassword, getSkipUpload(), getSkipDelete()).doInBackground();
		} else {
			Log.i(TAG, "Wifi export skipped");
		}

		if (mExportGpx) {
			publishProgress(mContext.getResources().getString(R.string.exporting_gpx), 75);

			GpxExporter gpx = new GpxExporter(mContext, mSession);

			File target = new File(mGpxPath, mGpxFile);
			try {
				gpx.doExport(mGpxFile, target);
			} catch (IOException e) {
				Log.e(TAG, "Can't write gpx file " + mGpxPath + File.separator + mGpxFile);
			}
		} else {
			Log.i(TAG, "GPX export skipped");
		}

		return true;
	}

	/**
	 * Updates progress bar.
	 * @param values[0]
	 *		  		contains title as string
	 * @param values[1]
	 *				contains progress as int
	 */
	@Override
	protected final void onProgressUpdate(final Object... values) {
		mDialog.setTitle((String) values[0]);
		//mDialog.setProgress((Integer) values[1]);
	}

	@Override
	protected final void onPostExecute(final Boolean success) {
		mDialog.dismiss();
		if (success && !mSkipUpload) {
			if (mListener != null) {
				mListener.onExportCompleted(mSession);
			}
			return;
		} else if (success && mSkipUpload) {
			// do nothing if upload has been skipped
			Toast.makeText(mContext, R.string.upload_skipped, Toast.LENGTH_LONG).show();
			return;
		} else {
			if (mListener != null) {
				mListener.onExportFailed(errorMsg);
			}
			return;
		}
	}

	/**
	 * Enables or disables cells export
	 * @param exportCells
	 */
	public final void setExportCells(final boolean exportCells) {
		mExportCells = exportCells;		
	}

	/**
	 * Enables or disables wifis export
	 * @param exportWifis
	 */
	public final void setExportWifis(final boolean exportWifis) {
		mExportWifis = exportWifis;
	}

	/**
	 * Enables or disables gpx export
	 * @param exportGpx
	 */
	public final void setExportGpx(final boolean exportGpx) {
		mExportGpx = exportGpx;
	}

	public final void setGpxFilename(final String gpxFilename) {
		this.mGpxFile = gpxFilename;
	}

	public final void setGpxPath(final String gpxPath) {
		this.mGpxPath = gpxPath;
	}

	public final Boolean getSkipUpload() {
		return mSkipUpload;
	}

	public final void setSkipUpload(final Boolean skipUpload) {
		this.mSkipUpload = skipUpload;
	}

	public final Boolean getSkipDelete() {
		return mSkipDelete;
	}

	public final void setSkipDelete(final Boolean skipDelete) {
		this.mSkipDelete = skipDelete;
	}

}
