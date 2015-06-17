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
import java.util.ArrayList;

import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.soapclient.FileUploader.FileUploadListener;
import org.openbmap.utils.MediaScanner;
import org.openbmap.utils.WifiCatalogUpdater;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

/**
 * Manages export and upload processes
 */
public class UploadTask extends AsyncTask<Void, Object, Boolean> implements FileUploadListener {

	private static final String TAG = UploadTask.class.getSimpleName();

	/**
	 * Max. number of parallel uploads
	 */
	private final int MAX_THREADS = 5;

	/**
	 * Wait for how many milliseconds for upload to be completed
	 * Users have reported issues with GRACE_TIME = 30000, so give it some more time
	 */
	private static final int GRACE_TIME	= 120000;

	/**
	 * OpenBmap cell upload address
	 */
	private static final String CELL_WEBSERVICE = "http://radiocells.org/uploads/cells";
	
	/**
	 * Cell target folder, always add trailing slash!
	 * Folder was used to determine, whether file upload was successful
	 */
	private static final String CELL_TARGET_FOLDER = "http://openbmap.org/upload/maps/";

	/**
	 * OpenBmap wifi upload address
	 */
	//private static final String WIFI_WEBSERVICE = "http://www.openbmap.org/upload_wifi/upl.php5";
	private static final String WIFI_WEBSERVICE = "http://radiocells.org/uploads/wifis";
	
	/**
	 * Wifi target folder, always add trailing slash!
	* Folder was used to determine, whether file upload was successful
	 */
	private static final String WIFI_TARGET_FOLDER = "http://www.openbmap.org/upload_wifi/maps/";

	/**
	 * Checks, whether the file has actually made it to the server by sending a GET request
	 */
	private static final boolean VALIDATE_UPLOAD = false;

	private Context mAppContext;

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
	private final UploadTaskListener mListener;

	/**
	 * Directory where xmls files are stored
	 */
	private final String mTargetPath;

	/*
	 *  Openbmap credentials : openbmap username
	 */
	private final String mUser;
	/*
	 *  Openbmap credentials : openbmap password
	 */
	private final String mPassword;

	/**
	 * Export and upload cells?
	 */
	private boolean	mExportCells = false;

	/**
	 * Export and upload wifis?
	 */
	private boolean	mExportWifis = false;

	/**
	 * Upload md5ssid only?
	 */
	private boolean	mAnonymiseSsid = false;

	/**
	 * Skip upload?
	 */
	private boolean	mSkipUpload;

	/**
	 * Skip cleanup?
	 */
	private boolean	mSkipDelete;

	/**
	 * Update wifi catalog with new wifis?
	 */
	private boolean	mUpdateWifiCatalog = false;

	/**
	 * Number of active upload tasks
	 */
	private int	mActiveUploads;

	/**
	 * List of all successfully uploaded files. For the moment no differentiation between cells and wifis
	 */
	private ArrayList<String>	mUploadedFiles;

	public interface UploadTaskListener {
		void onUploadProgressUpdate(Object... values);
		void onUploadCompleted(final int id);
		void onDryRunCompleted(final int id);
		void onUploadFailed(final int id, final String error);
	}

	//http://stackoverflow.com/questions/9573855/second-instance-of-activity-after-orientation-change
	/**
	 * 
	 * @param context
	 * @param listener
	 * @param session
	 * @param targetPath
	 * @param user
	 * @param password
	 */
	public UploadTask(final Context context, final UploadTaskListener listener, final int session,
			final String targetPath, final String user, final String password, final boolean anonymiseSsid) {
		this.mAppContext = context.getApplicationContext();
		this.mSession = session;
		this.mTargetPath = targetPath;
		this.mUser = user;
		this.mPassword = password;
		this.mListener = listener;

		this.mAnonymiseSsid = anonymiseSsid;

		// by default: upload and delete local temp files afterward
		this.setSkipUpload(false);
		this.setSkipDelete(false);

		this.setUpdateWifiCatalog(false);

		mUploadedFiles = new ArrayList<String>();
	}

	/**
	 * Builds cell xml files and saves/uploads them
	 */
	@SuppressLint("NewApi")
	@Override
	protected final Boolean doInBackground(final Void... params) {
		ArrayList<String> wifiFiles = new ArrayList<String>();
		ArrayList<String> cellFiles = new ArrayList<String>();
		Boolean success = true;

		if (mExportCells) {
			Log.i(TAG, "Exporting cells");
			// export cells
			publishProgress(mAppContext.getResources().getString(R.string.please_stay_patient), mAppContext.getResources().getString(R.string.exporting_cells), 0);
			cellFiles = new CellExporter(mAppContext, mSession, mTargetPath, RadioBeacon.SW_VERSION).export();

			// upload
			if (!getSkipUpload()) {
				for (int i = 0; i < cellFiles.size(); i++) {
					// thread control for the poor: spawn only MAX_THREADS tasks
					while (mActiveUploads > MAX_THREADS) {
						Log.i(TAG, "Maximum number of upload threads reached. Waiting..");
						try {
							Thread.sleep(50);
						} catch (final InterruptedException e) {
						}
					}
					publishProgress(mAppContext.getResources().getString(R.string.please_stay_patient), mAppContext.getResources().getString(R.string.uploading_cells) + "(" + "Files" + ": " + String.valueOf(cellFiles.size() -i) +")" , 0);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
						// enforce parallel execution on HONEYCOMB
						new FileUploader(this, mUser, mPassword, CELL_WEBSERVICE, VALIDATE_UPLOAD, CELL_TARGET_FOLDER).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, cellFiles.get(i));
						mActiveUploads += 1;
					} else {
						new FileUploader(this, mUser, mPassword, CELL_WEBSERVICE, VALIDATE_UPLOAD, CELL_TARGET_FOLDER).execute(cellFiles.get(i));
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
			publishProgress(mAppContext.getResources().getString(R.string.please_stay_patient), mAppContext.getResources().getString(R.string.exporting_wifis), 50);
			wifiFiles = new WifiExporter(mAppContext, mSession, mTargetPath, RadioBeacon.SW_VERSION, mAnonymiseSsid).export();

			// upload
			if (!getSkipUpload()) {
				for (int i = 0; i < wifiFiles.size(); i++) {
					while (mActiveUploads > MAX_THREADS) {
						Log.i(TAG, "Maximum number of upload threads reached. Waiting..");
						try {
							Thread.sleep(50);
						} catch (final InterruptedException e) {
						}
					}
					publishProgress(mAppContext.getResources().getString(R.string.please_stay_patient), mAppContext.getResources().getString(R.string.uploading_wifis) + "(Files: " + String.valueOf(wifiFiles.size() -i ) + ")", 50);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
						// enforce parallel execution on HONEYCOMB
						new FileUploader(this, mUser, mPassword, WIFI_WEBSERVICE, VALIDATE_UPLOAD, WIFI_TARGET_FOLDER).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, wifiFiles.get(i));
						mActiveUploads += 1;
					} else {
						new FileUploader(this, mUser, mPassword, WIFI_WEBSERVICE, VALIDATE_UPLOAD, WIFI_TARGET_FOLDER).execute(wifiFiles.get(i));
						mActiveUploads += 1;
					}	
				}
			}
		} else {
			Log.i(TAG, "Wifi export skipped");
		}

		if (!getSkipUpload()) {
			// wait max 30s for all upload tasks to finish
			final long startGrace = System.currentTimeMillis(); 
			while (mActiveUploads > 0 ) {
				Log.i(TAG, "Waiting for uploads to complete. (Active " + String.valueOf(mActiveUploads) + ")");
				try {
					Thread.sleep(50);
				} catch (final InterruptedException e) {
				}
				if (System.currentTimeMillis() - startGrace > GRACE_TIME) {
					Log.i(TAG, "Timeout reached");
					break;
				}
			}

			// check, whether all files are uploaded
			if (mUploadedFiles.size() != (wifiFiles.size() + cellFiles.size())) {
				Log.w(TAG, "Problem: Not all files have been uploaded!");
				// set state to failed on upload problems
				success = false;
			} else {
				Log.i(TAG, "All files uploaded");
			}

			// and cleanup
			if (!getSkipDelete()) {
				// delete only successfully uploaded files
				Log.i(TAG, "Deleting uploaded files");
				for (int i = 0; i < mUploadedFiles.size(); i++) {
					final File temp = new File(mUploadedFiles.get(i));
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

		if (mUpdateWifiCatalog) {
			Log.i(TAG, "Updating wifi catalog");
			new WifiCatalogUpdater(mAppContext).execute((Void[]) null);			
		}

		return success;
	}

	/**
	 * Updates progress bar.
	 * @param values[0] contains title (as string)
	 * @param values[1] contains message (as string)
	 * @param values[1] contains progress (as int)
	 */
	@Override
	protected final void onProgressUpdate(final Object... values) {
		if (mListener != null) {
			mListener.onUploadProgressUpdate(values);
		}
	}

	@SuppressLint("NewApi")
	@Override
	protected final void onPostExecute(final Boolean success) {

		// rescan SD card on honeycomb devices
		// Otherwise files may not be visible when connected to desktop pc (MTP cache problem)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			Log.i(TAG, "Re-indexing SD card temp folder");
			new MediaScanner(mAppContext, new File(mTargetPath));
		}

		if (mSkipUpload) {
			// upload simulated only
			Toast.makeText(mAppContext, R.string.upload_skipped, Toast.LENGTH_LONG).show();
			if (mListener != null) {
				mListener.onDryRunCompleted(mSession);
			}
			return;
		}

		if (success && !mSkipUpload) {
			if (mListener != null) {
				mListener.onUploadCompleted(mSession);
			}
			return;
		} else {
			if (mListener != null) {
				mListener.onUploadFailed(mSession, errorMsg);
			}
			return;
		}
	}

	/*
	@Override 
	protected final void onCanceled() {
		if (mCallbacks != null) {
			mCallbacks.onCancelled();
		}
	}*/

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
		Log.i(TAG, "Finished upload, pending uploads " + mActiveUploads);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.FileUploader.UploadTaskListener#onUploadFailed(java.lang.String)
	 */
	@Override
	public final void onUploadFailed(final String file, final String error) {
		Log.e(TAG, "Upload failed:" + file + " " + error);
		mActiveUploads -= 1;
	}

	/**
	 * @param sessionActivity
	 */
	public void setContext(final Context context) {
		mAppContext = context;
	}

	/**
	 * @param b
	 */
	public void setUpdateWifiCatalog(final boolean updateCatalog) {
		mUpdateWifiCatalog = updateCatalog;
	}

}
