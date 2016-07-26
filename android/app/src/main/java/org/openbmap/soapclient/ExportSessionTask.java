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
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.soapclient.AsyncUploader.FileUploadListener;
import org.openbmap.utils.CatalogUpdater;
import org.openbmap.utils.MediaScanner;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Manages export and upload processes
 */
public class ExportSessionTask extends AsyncTask<Void, Object, Boolean> implements FileUploadListener {

	private static final String TAG = ExportSessionTask.class.getSimpleName();

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
	private static final String CELL_WEBSERVICE = RadioBeacon.SERVER_BASE + "/uploads/cells";

    /**
     * OpenBmap cell anonymous upload address
     */
    private static final String CELL_ANONYMOUS_WEBSERVICE = RadioBeacon.SERVER_BASE + "/uploads/share_cells";

	/**
	 * OpenBmap wifi upload address
	 */
	private static final String WIFI_WEBSERVICE = RadioBeacon.SERVER_BASE + "/uploads/wifis";

    /**
     * OpenBmap cell anonymous upload address
     */
    private static final String WIFI_ANONYMOUS_WEBSERVICE = RadioBeacon.SERVER_BASE + "/uploads/share_wifis";

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
	private final String mTempPath;

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
     * Anonymous upload using one-time token
     */
    private boolean mAnonymousUpload = Preferences.VAL_ANONYMOUS_UPLOAD;

    /**
	 * Upload md5ssid only?
	 */
	private boolean	mAnonymiseSsid = Preferences.VAL_ANONYMISE_SSID;

    /**
	 * Skip upload?
	 */
	private boolean	mSkipUpload = Preferences.VAL_SKIP_UPLOAD;

	/**
	 * Skip cleanup?
	 */
	private boolean mKeepXml = Preferences.VAL_KEEP_XML;

    /**
     * Create a GPX track
     */
    private boolean mSaveGpx = Preferences.VAL_SAVE_GPX;

	/**
	 * Update wifi catalog with new wifis?
	 */
	private boolean	mUpdateWifiCatalog = false;

	/**
	 * Number of active upload tasks
	 */
	private int	mActiveUploads;

    /**
     * One-time token for anonymous upload
     */
    private String mToken;

	/**
	 * List of all successfully uploaded files. For the moment no differentiation between cells and wifis
	 */
	private ArrayList<String> mUploadedFiles;
    private long mSpeed = -1;

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
	 * @param tempPath
	 * @param user
	 * @param password
	 */
	public ExportSessionTask(final Context context, final UploadTaskListener listener, final int session,
							 final String tempPath, final String user, final String password, final boolean anonymous_upload) {
		mAppContext = context.getApplicationContext();
		mSession = session;
		mTempPath = tempPath;
		mUser = user;
		mPassword = password;
		mListener = listener;

        mAnonymousUpload = anonymous_upload;
		mUploadedFiles = new ArrayList<>();
	}

	/**
	 * Builds cell xml files and saves/uploads them
	 */
	@SuppressLint("NewApi")
	@Override
	protected final Boolean doInBackground(final Void... params) {
		ArrayList<String> wifiFiles = new ArrayList<>();
		ArrayList<String> cellFiles = new ArrayList<>();
		Boolean success = true;

        if (!mSkipUpload && mAnonymousUpload && (mExportCells || mExportWifis)) {
            mToken = getToken();
            Log.i(TAG, "Token " + mToken);
        }

		if (mExportCells) {
			Log.i(TAG, "Exporting cells");
			// export cells
			publishProgress(mAppContext.getResources().getString(R.string.please_stay_patient), mAppContext.getResources().getString(R.string.exporting_cells), 0);
			cellFiles = new CellSerializer(mAppContext, mSession, mTempPath, RadioBeacon.SW_VERSION).export();

			// upload
			if (!mSkipUpload) {
				uploadAllCells(cellFiles);
			}
		} else {
			Log.i(TAG, "Cell export skipped");
		}

		if (mExportWifis) {
			Log.i(TAG, "Exporting wifis");
			// export wifis
			publishProgress(mAppContext.getResources().getString(R.string.please_stay_patient), mAppContext.getResources().getString(R.string.exporting_wifis), 50);
			wifiFiles = new WifiSerializer(mAppContext, mSession, mTempPath, RadioBeacon.SW_VERSION, mAnonymiseSsid).export();

			// upload
			if (!mSkipUpload) {
				uploadAllWifis(wifiFiles);
			}
		} else {
			Log.i(TAG, "Wifi export skipped");
		}

		if (!mSkipUpload) {
			// wait max 30s for all upload tasks to finish
			final long startGrace = System.currentTimeMillis();
			sleepTillCompleted(startGrace);

			// check, whether all files are uploaded
			if (mUploadedFiles.size() != (wifiFiles.size() + cellFiles.size())) {
				Log.e(TAG, "Not all files have been uploaded!");
				// set state to failed on upload problems
				success = false;
			} else {
				Log.i(TAG, "All files uploaded");
			}

			// and cleanup
			if (!mKeepXml) {
				// delete only successfully uploaded files
				Log.i(TAG, "Deleting uploaded files");
				deleteXmlFiles(mUploadedFiles);
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
			new CatalogUpdater(mAppContext).execute((Void[]) null);
		}

        if (mSaveGpx) {
            Log.i(TAG, "Exporting gpx");
            publishProgress(mAppContext.getResources().getString(R.string.please_stay_patient), mAppContext.getResources().getString(R.string.exporting_gpx), 75);

            final String filename = GpxSerializer.suggestGpxFilename(mSession);
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mAppContext);
            final int verbosity = Integer.parseInt(prefs.getString(Preferences.KEY_GPX_VERBOSITY, Preferences.VAL_GPX_VERBOSITY));

            final SaveGpxTask task = new SaveGpxTask(mAppContext, null, mSession, mTempPath, filename, verbosity);
            task.execute();
        } else {
            Log.i(TAG, "GPX export skipped");
        }

		return success;
	}

	private void deleteXmlFiles(ArrayList<String> files) {
		for (int i = 0; i < files.size(); i++) {
            final File temp = new File(files.get(i));
            if (!temp.delete()) {
				Log.w(TAG, "Couldn't delete " + temp.getAbsolutePath());
            }
        }
	}

	private void sleepTillCompleted(long startGrace) {
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
	}

	private void uploadAllCells(ArrayList<String> cellFiles) {
		for (int i = 0; i < cellFiles.size(); i++) {
            // thread control for the poor: spawn only MAX_THREADS tasks
            while (mActiveUploads >= allowedThreads()) {
                Log.i(TAG, "Number of upload threads exceeds max parallel threads (" + mActiveUploads + "/" + allowedThreads() + "). Waiting..");
                try {
                    Thread.sleep(100);
                } catch (final InterruptedException e) {
                }
            }
            publishProgress(mAppContext.getResources().getString(R.string.please_stay_patient), mAppContext.getResources().getString(R.string.uploading_cells) + "(" + "Files" + ": " + String.valueOf(cellFiles.size() -i) +")" , 0);

            // enforce parallel execution on HONEYCOMB
            if (!mAnonymousUpload) {
                new AsyncUploader(this, mUser, mPassword, CELL_WEBSERVICE).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, cellFiles.get(i));
                mActiveUploads += 1;
            } else if (mAnonymousUpload && (mToken != null)){
                new AsyncUploader(this, mToken, CELL_ANONYMOUS_WEBSERVICE).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, cellFiles.get(i));
                mActiveUploads += 1;
            } else {
                Log.e(TAG, "Neither user name nor token was available");
            }
        }
	}

	private void uploadAllWifis(ArrayList<String> wifiFiles) {
		for (int i = 0; i < wifiFiles.size(); i++) {
            while (mActiveUploads >= allowedThreads()) {
                Log.i(TAG, "Maximum number of upload threads reached. Waiting..");
                try {
                    Thread.sleep(50);
                } catch (final InterruptedException e) {
                }
            }
            publishProgress(mAppContext.getResources().getString(R.string.please_stay_patient), mAppContext.getResources().getString(R.string.uploading_wifis) + "(" + mAppContext.getString(R.string.files) + ": " + String.valueOf(wifiFiles.size() -i ) + ")", 50);
            // enforce parallel execution on HONEYCOMB
            if (!mAnonymousUpload) {
                new AsyncUploader(this, mUser, mPassword, WIFI_WEBSERVICE).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, wifiFiles.get(i));
                mActiveUploads += 1;
            } else if (mAnonymousUpload && (mToken != null)) {
                new AsyncUploader(this, mToken, WIFI_ANONYMOUS_WEBSERVICE).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, wifiFiles.get(i));
                mActiveUploads += 1;
            } else {
                Log.e(TAG, "Neither user name nor token was available");
            }
        }
	}

	/**
     * Returns number of parallel uploads threads
     * @return 1 if no speed measurement available yet
     */
	private int allowedThreads() {
        if (mSpeed == -1) {
            Log.d(TAG, "No speed measurements. Only 1 thread allowed");
            return 1;
        } else if (mSpeed < 10) {
            Log.d(TAG, "Speed below 10kb. Only 1 thread allowed");
            return 1;
        } else {
            Log.d(TAG, "No thread restriction. " + MAX_THREADS + " allowed");
            return MAX_THREADS;
        }
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

	@Override
	protected final void onPostExecute(final Boolean success) {

		// rescan SD card on honeycomb devices
		// Otherwise files may not be visible when connected to desktop pc (MTP cache problem)
		Log.i(TAG, "Re-indexing SD card temp folder");
		new MediaScanner(mAppContext, new File(mTempPath));

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
     * Enables or disables SSID anonymisation
     * @param anonymiseSsid
     */
    public final void setAnonymiseSsid(final boolean anonymiseSsid) {
        this.mAnonymiseSsid = anonymiseSsid;
    }

    /**
     * Disables actual upload ('dry run')
     * @param skipUpload
     */
	public final void setSkipUpload(final boolean skipUpload) {
		this.mSkipUpload = skipUpload;
	}

    /**
     * Enables or disables GPX creation
     * @param saveGpx
     */
    public void setSaveGpx(boolean saveGpx) {
        this.mSaveGpx = saveGpx;
    }

    /**
     * If activated a local copy of uploaded XML files is kept
     * @param keepXml
     */
	public final void setKeepXml(final boolean keepXml) {
		this.mKeepXml = keepXml;
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.FileUploader.UploadTaskListener#onUploadCompleted(java.util.ArrayList)
	 */
	@Override
	public final void onUploadCompleted(final String file, final long size, final long speed) {
		mUploadedFiles.add(file);
		mActiveUploads -= 1;
        mSpeed = speed;
		Log.i(TAG, "Finished upload (size " + size + " bytes, speed" + speed + "kb), pending uploads " + mActiveUploads);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.FileUploader.UploadTaskListener#onUploadFailed(java.lang.String)
	 */
	@Override
	public final void onUploadFailed(final String file, final String error) {
		Log.e(TAG, "Upload failed:" + file + " " + error);
		mActiveUploads -= 1;
        mSpeed = -1;
	}

	/**
	 * @param updateCatalog
	 */
	public void setUpdateWifiCatalog(final boolean updateCatalog) {
		mUpdateWifiCatalog = updateCatalog;
	}

    private String getToken() {
        final DefaultHttpClient httpclient = new DefaultHttpClient(new BasicHttpParams());
        final HttpPost httppost = new HttpPost("https://radiocells.org/openbmap/uploads/generate_api_key");
        final StringBuilder sb = new StringBuilder();

        InputStream inputStream = null;
        try {
            final HttpResponse response = httpclient.execute(httppost);
            final HttpEntity entity = response.getEntity();

            inputStream = entity.getContent();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);

            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting token:" + e.getMessage());
        } finally {
            try{
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception squish) {
                return null;
            }
        }
        return sb.toString();
    }

}
