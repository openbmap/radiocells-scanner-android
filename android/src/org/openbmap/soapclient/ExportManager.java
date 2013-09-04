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
import java.util.ArrayList;

import org.openbmap.R;
import org.openbmap.soapclient.FileUploader.UploadTaskListener;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

/**
 * Manages export and upload process
 */
@SuppressLint("NewApi")
public class ExportManager extends AsyncTask<Void, Object, Boolean> implements UploadTaskListener {

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

	private boolean	mSkipUpload;

	private boolean	mSkipDelete;

	/**
	 * Number of active upload tasks
	 */
	private int	mActiveUploads;

	/**
	 * OpenBmap cell upload address
	 */
	private static final String CELL_WEBSERVICE = "http://openBmap.org/upload/upl.php5";

	/**
	 * OpenBmap wifi upload address
	 */
	private static final String WIFI_WEBSERVICE = "http://www.openbmap.org/upload_wifi/upl.php5";

	/**
	 * List of all successfully uploaded files. For the moment no differentiation between cells and wifis
	 */
	private ArrayList<String>	mUploadedFiles;

	/**
	 * Max. number of parallel uploads
	 */
	private int	MAX_THREADS = 7;

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

		mUploadedFiles = new ArrayList<String>();
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
		ArrayList<String> wifiFiles = new ArrayList<String>();
		ArrayList<String> cellFiles = new ArrayList<String>();

		if (mExportCells) {
			Log.i(TAG, "Exporting cells");
			// export cells
			publishProgress(mContext.getResources().getString(R.string.exporting_cells), 0);
			cellFiles = new CellExporter(mContext, mSession, mTargetPath, mUser).export();

			// upload
			if (!getSkipUpload()) {
				for (int i = 0; i < cellFiles.size(); i++) {
					// thread control for the poor: spawn only MAX_THREADS tasks
					while (mActiveUploads > MAX_THREADS) {
						Log.i(TAG, "Maximum number of upload threads reached. Waiting..");
						try {
							Thread.sleep(50);
						} catch (InterruptedException e) {
						}
					}
					publishProgress(mContext.getResources().getString(R.string.uploading_cells) + "(Files: " + String.valueOf(cellFiles.size() -i) +")" , 0);

					if (Build.VERSION.SDK_INT >= 11 /*Build.VERSION_CODES.HONEYCOMB*/) {
						// enforce parallel execution on HONEYCOMB
						new FileUploader(this, mUser, mPassword, CELL_WEBSERVICE).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, cellFiles.get(i));
						mActiveUploads += 1;
					} else {
						new FileUploader(this, mUser, mPassword, CELL_WEBSERVICE).execute(cellFiles.get(i));
						mActiveUploads += 1;
					}
				}		
			}
		} else {
			Log.i(TAG, "Cell export skipped");
		}

		if (mExportWifis) {
			Log.i(TAG, "Exporting wifis");
			// export wifis
			publishProgress(mContext.getResources().getString(R.string.exporting_wifis), 50);
			wifiFiles = new WifiExporter(mContext, mSession, mTargetPath, mUser).export();

			// upload
			if (!getSkipUpload()) {
				for (int i = 0; i < wifiFiles.size(); i++) {
					while (mActiveUploads > MAX_THREADS) {
						Log.i(TAG, "Maximum number of upload threads reached. Waiting..");
						try {
							Thread.sleep(50);
						} catch (InterruptedException e) {
						}
					}
					publishProgress(mContext.getResources().getString(R.string.uploading_wifis) + "(Files: " + String.valueOf(wifiFiles.size() -i ) + ")", 50);
					
					if (Build.VERSION.SDK_INT >= 11 /*Build.VERSION_CODES.HONEYCOMB*/) {
						// enforce parallel execution on HONEYCOMB
						new FileUploader(this, mUser, mPassword, WIFI_WEBSERVICE).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, wifiFiles.get(i));
						mActiveUploads += 1;
					} else {
						new FileUploader(this, mUser, mPassword, WIFI_WEBSERVICE).execute(wifiFiles.get(i));
						mActiveUploads += 1;
					}	
				}
			}
		} else {
			Log.i(TAG, "Wifi export skipped");
		}

		if (!getSkipUpload()) {
			// wait max 30s for all upload tasks to finish
			long startGrace = System.currentTimeMillis(); 
			while (mActiveUploads > 0 ) {
				Log.i(TAG, "Waiting for uploads to complete. (Active " + String.valueOf(mActiveUploads) + ")");
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}
				if (System.currentTimeMillis() - startGrace > 30000) {
					Log.i(TAG, "Timeout reached");
					break;
				}
			}

			// check, whether all files are uploaded
			if (mUploadedFiles.size() != (wifiFiles.size() + cellFiles.size())) {
				Log.w(TAG, "Problem: Not all files have been uploaded!");
			} else {
				Log.i(TAG, "All files uploaded");
			}

			// and cleanup
			if (!getSkipDelete()) {
				// delete only successfully uploaded files
				Log.i(TAG, "Deleting uploaded files");
				for (int i = 0; i < mUploadedFiles.size(); i++) {
					File temp = new File(mUploadedFiles.get(i));
					if (!temp.delete()) {
						Log.e(TAG, "Error deleting " + mUploadedFiles.get(i));
					}	
				}
			} else {
				Log.i(TAG, "Deleting files skipped");
			}
		}
		// clean up a bit
		wifiFiles = null;
		cellFiles = null;
		mUploadedFiles = null;
		System.gc();
		
		if (mExportGpx) {
			Log.i(TAG, "Exporting gpx");
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

	public final void setSkipUpload(final boolean skipUpload) {
		this.mSkipUpload = skipUpload;
	}

	public final boolean getSkipDelete() {
		return mSkipDelete;
	}

	public final void setSkipDelete(final boolean skipDelete) {
		this.mSkipDelete = skipDelete;
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.FileUploader.UploadTaskListener#onUploadCompleted(java.util.ArrayList)
	 */
	@Override
	public final void onUploadCompleted(final String file) {
		mUploadedFiles.add(file);
		mActiveUploads -= 1;
		Log.i(TAG, "Finished upload, open uploads" + mActiveUploads);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.FileUploader.UploadTaskListener#onUploadFailed(java.lang.String)
	 */
	@Override
	public final void onUploadFailed(final String file, final String error) {
		Log.e(TAG, "Upload failed:" + file + " " + error);
		mActiveUploads -= 1;
	}

}
