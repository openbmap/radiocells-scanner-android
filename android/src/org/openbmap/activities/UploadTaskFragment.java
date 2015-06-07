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

import java.util.Vector;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.soapclient.ServerValidation;
import org.openbmap.soapclient.ServerValidation.ServerReply;
import org.openbmap.soapclient.UploadTask;
import org.openbmap.soapclient.UploadTask.UploadTaskListener;
import org.openbmap.utils.FileUtils;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.actionbarsherlock.app.SherlockFragment;

/**
 *  Fragment manages export background tasks and retains itself across configuration changes.
 */
public class UploadTaskFragment extends SherlockFragment implements UploadTaskListener, ServerReply {

	private static final String TAG = UploadTaskFragment.class.getSimpleName();

	private enum CheckResult {UNKNOWN, BAD, GOOD};
	private CheckResult sdCardWritable = CheckResult.UNKNOWN;
	private CheckResult credentialsProvided = CheckResult.UNKNOWN;
	private CheckResult allowedVersion = CheckResult.UNKNOWN;

	private final Vector<Integer> toExport = new Vector<Integer>();
	private UploadTask mExportTask;

	private String mTitle;
	private String mMessage;
	private int	mProgress;
	private boolean	mIsExecuting = false;

	/**
	 * This method will only be called once when the retained
	 * Fragment is first created.
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Retain this fragment across configuration changes.
		setRetainInstance(true);
	}


	public int size() {
		return toExport.size();
	}

	public void add(final int id) {
		toExport.add(id);
	}

	/**
	 * Starts export after all checks have been passed
	 */
	public void execute() {
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
	 * Does the actual work
	 * @param session
	 */

	private void process(final int session) {

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		final String user = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getString(Preferences.KEY_CREDENTIALS_USER, null);
		final String password = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);
		final String targetPath = getActivity().getExternalFilesDir(null).getAbsolutePath();
		final boolean skipUpload = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getBoolean(Preferences.KEY_SKIP_UPLOAD, Preferences.VAL_SKIP_UPLOAD);
		final boolean skipDelete = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getBoolean(Preferences.KEY_SKIP_DELETE, Preferences.VAL_SKIP_DELETE);
		final boolean anonymiseSsid = prefs.getBoolean(Preferences.KEY_ANONYMISE_SSID, Preferences.VAL_ANONYMISE_SSID); 

		mExportTask = new UploadTask(getSherlockActivity(), this, session,
				targetPath, user, password, anonymiseSsid);

		mExportTask.setExportCells(true);
		mExportTask.setExportWifis(true);
		// currently deactivated to prevent crashes
		mExportTask.setUpdateWifiCatalog(false);

		// debug settings
		mExportTask.setSkipUpload(skipUpload);
		mExportTask.setSkipDelete(skipDelete);

		mExportTask.execute((Void[]) null);
	}

	/**
	 * Are openbmap credentials available
	 * @return true if credentials are available
	 */
	private boolean areCredentialsProvided() {
		// TODO verify whether credentials are valid
		// http://code.google.com/p/openbmap/issues/detail?id=40

		// checks credentials available?
		final String user = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getString(Preferences.KEY_CREDENTIALS_USER, null);
		final String password = PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);

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
			sdCardWritable = (FileUtils.isSdCardWritable() ? CheckResult.GOOD : CheckResult.BAD);
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
			final int id = toExport.size() > 0 ? toExport.get(0) : RadioBeacon.SESSION_NOT_TRACKING;
			onUploadFailed(id, getResources().getString(R.string.warning_outdated_client));
		} else if(allowedVersion == CheckResult.UNKNOWN) {
			// couldn't check version
			final int id = toExport.size() > 0 ? toExport.get(0) : RadioBeacon.SESSION_NOT_TRACKING;
			onUploadFailed(id, getResources().getString(R.string.warning_client_version_not_checked));
		} else if (credentialsProvided == CheckResult.BAD) {
			final int id = toExport.size() > 0 ? toExport.get(0) : RadioBeacon.SESSION_NOT_TRACKING;
			onUploadFailed(id, getResources().getString(R.string.user_or_password_missing));
		} else if(sdCardWritable == CheckResult.BAD) {
			final int id = toExport.size() > 0 ? toExport.get(0) : RadioBeacon.SESSION_NOT_TRACKING;
			onUploadFailed(id, getResources().getString(R.string.warning_sd_not_writable));
		} else {
			final int id = toExport.size() > 0 ? toExport.get(0) : RadioBeacon.SESSION_NOT_TRACKING;
			onUploadFailed(id, "Unknown error");
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
	public void onServerBad(final String text) {
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
	 * Saves progress dialog state for later restore (e.g. on device rotation)
	 * @param title
	 * @param message
	 * @param progress
	 */
	public void retainProgress(final String title, final String message, final int progress) {
		mTitle = title;
		mMessage = message;
		mProgress = progress;
	}

	/**
	 * Restores previously retained progress dialog state
	 * @param progressDialog
	 */
	public void restoreProgress(final ProgressDialog progressDialog) {
		progressDialog.setTitle(mTitle);
		progressDialog.setMessage(mMessage);
		progressDialog.setProgress(mProgress);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onExportCompleted(int)
	 */
	@Override
	public void onUploadCompleted(final int id) {
		// forward to activity
		((UploadTaskListener) getSherlockActivity()).onUploadCompleted(id);

		Log.i(TAG, "Export completed. Processing next");
		toExport.remove(Integer.valueOf(id));
		looper();
	}

	@Override
	public void onDryRunCompleted(final int id) {
		// forward to activity
		((UploadTaskListener) getSherlockActivity()).onDryRunCompleted(id);

		Log.i(TAG, "Export simulated. Processing next");
		toExport.remove(Integer.valueOf(id));
		looper();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onExportFailed(java.lang.String)
	 */
	@Override
	public void onUploadFailed(final int id, final String error) {
		// forward to activity
		((UploadTaskListener) getSherlockActivity()).onUploadFailed(id, error);

		Log.e(TAG, "Error exporting session " + id + ": " + error);
		toExport.remove(Integer.valueOf(id));
		looper();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onProgressUpdate(java.lang.Object[])
	 */
	@Override
	public void onUploadProgressUpdate(final Object... values) {
		// forward to activity
		((UploadTaskListener) getSherlockActivity()).onUploadProgressUpdate(values);
	}
}
