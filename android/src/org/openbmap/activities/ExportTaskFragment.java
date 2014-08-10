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

package org.openbmap.activities;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Vector;

import org.openbmap.Preferences;
import org.openbmap.RadioBeacon;
import org.openbmap.soapclient.ExportSessionTask;
import org.openbmap.soapclient.ExportSessionTask.ExportTaskListener;
import org.openbmap.soapclient.ServerValidation;
import org.openbmap.soapclient.ServerValidation.ServerReply;
import org.openbmap.utils.FileHelper;

import org.openbmap.R;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.actionbarsherlock.app.SherlockFragment;

/**
 *  Fragment manages export background tasks and retains itself across configuration changes.
 */
public class ExportTaskFragment extends SherlockFragment implements ExportTaskListener, ServerReply {

	private static final String TAG = ExportTaskFragment.class.getSimpleName();

	private enum CheckResult {UNKNOWN, BAD, GOOD};
	private CheckResult sdCardWritable = CheckResult.UNKNOWN;
	private CheckResult credentialsProvided = CheckResult.UNKNOWN;
	private CheckResult allowedVersion = CheckResult.UNKNOWN;

	private Vector<Integer> toExport = new Vector<Integer>();
	private ExportSessionTask mExportTask;

	private String mTitle;
	private String mMessage;
	private int	mProgress;
	private boolean	mIsExecuting = false;

	/**
	 * This method will only be called once when the retained
	 * Fragment is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Retain this fragment across configuration changes.
		setRetainInstance(true);
	}


	public int size() {
		return toExport.size();
	}

	public void add(int id) {
		toExport.add(id);
	}

	/**
	 * Starts export after all checks have been passed
	 */
	public void prepareStart() {
		mIsExecuting = true;
		stageVersionCheck();
	}

	/**
	 * Exports while sessions on the list
	 */
	private void looper() {
		Log.i(TAG, "Looping over pending exports");
		if (toExport.size() > 0) {
			Log.i(TAG, "Will process export " + toExport.get(0));
			process(toExport.get(0));
		} else {
			Log.i(TAG, "No more pending exports left. Finishing");
			mIsExecuting = false;
		}
	}

	public boolean isExecuting() {
		return this.mIsExecuting;
	}

	/**
	 * @param session
	 * @param targetPath
	 */

	private void process(int session) {

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		String user = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getString(Preferences.KEY_CREDENTIALS_USER, null);
		String password = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);
		String targetPath = Environment.getExternalStorageDirectory().getPath()
				+ PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getString(Preferences.KEY_DATA_FOLDER, Preferences.VAL_DATA_FOLDER) + File.separator;
		boolean exportGpx = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getBoolean(Preferences.KEY_GPS_SAVE_COMPLETE_TRACK, false);
		boolean skipUpload = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getBoolean(Preferences.KEY_SKIP_UPLOAD, Preferences.VAL_SKIP_UPLOAD);
		boolean skipDelete = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getBoolean(Preferences.KEY_SKIP_DELETE, Preferences.VAL_SKIP_DELETE);
		boolean anonymiseSsid = prefs.getBoolean(Preferences.KEY_ANONYMISE_SSID, Preferences.VAL_ANONYMISE_SSID); 

		mExportTask = new ExportSessionTask(getSherlockActivity(), this, session,
				targetPath, user, password, anonymiseSsid);

		mExportTask.setExportCells(true);
		mExportTask.setExportWifis(true);
		mExportTask.setExportGpx(exportGpx);
		// currently deactivated to prevent crashes
		mExportTask.setUpdateWifiCatalog(false);

		// debug settings
		mExportTask.setSkipUpload(skipUpload);
		mExportTask.setSkipDelete(skipDelete);

		SimpleDateFormat date = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
		mExportTask.setGpxPath(targetPath);
		mExportTask.setGpxFilename(date.format(new Date(System.currentTimeMillis())) + ".gpx");
		mExportTask.execute((Void[]) null);
	}

	/**
	 * Checks whether SD card is writable
	 * @return true if writable
	 */
	private boolean isSdCardWritable() {
		// Is SD card writeable?
		if (!FileHelper.isSdCardMountedWritable()) {
			Log.e(TAG, "SD card not writable");
			return false;
		} else {
			Log.i(TAG, "Good: SD card writable");
			return true;
		}
	}

	/**
	 * Are openbmap credentials available
	 * @return true if credentials are available
	 */
	private boolean areCredentialsProvided() {
		// TODO verify whether credentials are valid
		// http://code.google.com/p/openbmap/issues/detail?id=40

		// checks credentials available?
		String user = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getString(Preferences.KEY_CREDENTIALS_USER, null);
		String password = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);

		if (!isValidLogin(user, password)) {
			Log.e(TAG, "User credentials missing");
			return false;
		} else {
			Log.i(TAG, "Good: User and password provided");
			return true;
		}
	}

	/**
	 * First round: validate local version vs. server
	 */
	private void stageVersionCheck() {
		if (allowedVersion == CheckResult.UNKNOWN) {
			// will call onServerCheckGood()/onServerCheckBad() upon completion
			new ServerValidation(getSherlockActivity(), this).execute(RadioBeacon.VERSION_COMPATIBILITY);
		} else if (allowedVersion == CheckResult.GOOD) {
			stageLocalChecks();
		}
	}

	/**
	 * Second round: check whether local device is ready
	 */
	private void stageLocalChecks() {
		if (sdCardWritable == CheckResult.UNKNOWN) {
			sdCardWritable = (isSdCardWritable() ? CheckResult.GOOD : CheckResult.BAD);
		}

		if (credentialsProvided == CheckResult.UNKNOWN) {
			credentialsProvided =  (areCredentialsProvided() ? CheckResult.GOOD : CheckResult.BAD);
		}
		stageFinalCheck();
	}

	/**
	 * Validate everything (first + second round) is good, before starting export
	 */
	private void stageFinalCheck() {
		// so now it's time to decide whether we start or not..
		if (allowedVersion == CheckResult.GOOD && sdCardWritable == CheckResult.GOOD && credentialsProvided == CheckResult.GOOD) {
			looper();
		}
		else if(allowedVersion == CheckResult.BAD) {
			// version is outdated
			int id = toExport.size() > 0 ? toExport.get(0) : RadioBeacon.SESSION_NOT_TRACKING;
			onExportFailed(id, getResources().getString(R.string.warning_outdated_client));
		} else if(allowedVersion == CheckResult.UNKNOWN) {
			// couldn't check version
			int id = toExport.size() > 0 ? toExport.get(0) : RadioBeacon.SESSION_NOT_TRACKING;
			onExportFailed(id, getResources().getString(R.string.warning_client_version_not_checked));
		} else if (credentialsProvided == CheckResult.BAD) {
			int id = toExport.size() > 0 ? toExport.get(0) : RadioBeacon.SESSION_NOT_TRACKING;
			onExportFailed(id, getResources().getString(R.string.user_or_password_missing));
		} else if(sdCardWritable == CheckResult.BAD) {
			int id = toExport.size() > 0 ? toExport.get(0) : RadioBeacon.SESSION_NOT_TRACKING;
			onExportFailed(id, getResources().getString(R.string.warning_sd_not_writable));
		} else {
			int id = toExport.size() > 0 ? toExport.get(0) : RadioBeacon.SESSION_NOT_TRACKING;
			onExportFailed(id, "Unknown error");
		}

	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ServerValidation.ServerReply#onServerGood()
	 */
	@Override
	public void onServerGood() {
		allowedVersion = CheckResult.GOOD;
		stageLocalChecks();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ServerValidation.ServerReply#onServerBad(java.lang.String)
	 */
	@Override
	public void onServerBad(String text) {
		Log.e(TAG, text);
		allowedVersion = CheckResult.BAD;
		// skip local checks, server can't be reached anyways
		stageFinalCheck();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ServerValidation.ServerReply#onServerCheckFailed()
	 */
	@Override
	public void onServerCheckFailed() {
		allowedVersion = CheckResult.UNKNOWN;
		//stageLocalChecks();
		// skip local checks, server can't be reached anyways
		stageFinalCheck();
	}

	/**
	 * Checks whether user name and password has been set
	 * @param user
	 * @param password 
	 * @return true if user as well as password have been provided
	 */
	private boolean isValidLogin(final String user, final String password) {
		return (user != null && password != null);
	}

	/**
	 * Retains progress dialog state for later restore (e.g. on device rotation)
	 * @param title
	 * @param message
	 * @param progress
	 */
	public void retainProgress(String title, String message, int progress) {
		mTitle = title;
		mMessage = message;
		mProgress = progress;
	}

	/**
	 * Restores previously retain progress dialog state
	 * @param progressDialog
	 */
	public void restoreProgress(ProgressDialog progressDialog) {
		progressDialog.setTitle(mTitle);
		progressDialog.setMessage(mMessage);
		progressDialog.setProgress(mProgress);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onExportCompleted(int)
	 */
	@Override
	public void onExportCompleted(final int id) {
		// forward to activity
		((ExportTaskListener) getSherlockActivity()).onExportCompleted(id);

		Log.i(TAG, "Export completed. Processing next");
		toExport.remove(Integer.valueOf(id));
		looper();
	}

	@Override
	public void onDryRunCompleted(final int id) {
		// forward to activity
		((ExportTaskListener) getSherlockActivity()).onDryRunCompleted(id);

		Log.i(TAG, "Export simulated. Processing next");
		toExport.remove(Integer.valueOf(id));
		looper();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onExportFailed(java.lang.String)
	 */
	@Override
	public void onExportFailed(int id, String error) {
		// forward to activity
		((ExportTaskListener) getSherlockActivity()).onExportFailed(id, error);

		Log.e(TAG, "Error exporting session " + id + ": " + error);
		toExport.remove(Integer.valueOf(id));
		looper();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onProgressUpdate(java.lang.Object[])
	 */
	@Override
	public void onProgressUpdate(Object... values) {
		// forward to activity
		((ExportTaskListener) getSherlockActivity()).onProgressUpdate(values);
	}
}
