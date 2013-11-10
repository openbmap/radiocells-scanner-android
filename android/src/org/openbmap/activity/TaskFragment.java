/**
 * 
 */
package org.openbmap.activity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.openbmap.Preferences;
import org.openbmap.activity.SessionActivity.ExportTasks;
import org.openbmap.soapclient.ExportManager;
import org.openbmap.soapclient.ExportManager.ExportManagerListener;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;

/**
 * This Fragment manages a single background task and retains
 * itself across configuration changes.
 */
public class TaskFragment extends Fragment {

	/**
	 * Callback interface through which the fragment will report the
	 * task's progress and results back to the Activity.
	 */
	public static interface TaskCallbacks {
		void onPreExecute();
		void onProgressUpdate(Object[] values);
		void onCancelled();
		void onPostExecute();
	}

	private ExportManager mExportTask;

	private String mTitle;
	private String mMessage;
	private int	mProgress;

	private boolean	mIsExecuting = false;

	/**
	 * Hold a reference to the parent Activity so we can report the
	 * task's current progress and results. The Android framework
	 * will pass us a reference to the newly created Activity after
	 * each configuration change.
	 */
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
	}

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

	/**
	 * Set the callback to null so we don't accidentally leak the
	 * Activity instance.
	 */
	@Override
	public void onDetach() {
		super.onDetach();
	}

	/**
	 * Configures task, but doesn't yet start it
	 * @param session
	 * @param targetPath
	 * @param user
	 * @param password
	 * @param exportGpx
	 * @param skipUpload
	 * @param skipDelete
	 */

	public void setTask(ExportTasks tasks, int session,
			String targetPath, String user, String password, boolean exportGpx, boolean skipUpload, boolean skipDelete) {

		mIsExecuting = true;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		boolean anonymiseSsid = prefs.getBoolean(Preferences.KEY_ANONYMISE_SSID, Preferences.VAL_ANONYMISE_SSID); 
		mExportTask = new ExportManager(getActivity(), (ExportManagerListener) tasks, (TaskCallbacks) getActivity(), session, targetPath, user, password, anonymiseSsid);
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
	}

	/**
	 * Executes task
	 */
	public void execute() {
		if (mExportTask != null) {
			mExportTask.execute((Void[]) null);
		}
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

	/**
	 * @return
	 */
	public boolean isExecuting() {
		return mIsExecuting;
	}

	public void resetExecuting() {
		mIsExecuting = false;
	}
}
