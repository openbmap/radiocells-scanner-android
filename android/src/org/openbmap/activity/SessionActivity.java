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

package org.openbmap.activity;

import java.io.File;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.activity.TaskFragment.TaskCallbacks;
import org.openbmap.db.DataHelper;
import org.openbmap.db.model.Session;
import org.openbmap.soapclient.ExportManager.ExportManagerListener;
import org.openbmap.soapclient.ServerValidation;
import org.openbmap.soapclient.ServerValidation.ServerReply;
import org.openbmap.utils.FileHelper;
import org.openbmap.utils.MyAlertDialogFragment;
import org.openbmap.utils.OnAlertClickInterface;
import org.openbmap.utils.TempFileHelper;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * Parent screen for hosting main screen
 */
public class SessionActivity
extends FragmentActivity
implements SessionListFragment.SessionFragementListener, ExportManagerListener, ServerReply, OnAlertClickInterface, TaskCallbacks 
{
	private static final String TAG = SessionActivity.class.getSimpleName();

	private DataHelper mDataHelper;

	private WifiLock wifiLock;

	/**
	 * 
	 */
	private int	mPendingSession;

	private TaskFragment mTaskFragment;

	private ProgressDialog exportProgress;

	// alert builder ids
	private static final int ID_DELETE_UPLOADED_SESSION = 1;
	private static final int ID_REPAIR_WIFI	= 2;
	private static final int ID_MISSING_CREDENTIALS	= 3;
	private static final int ID_EXPORT_FAILED = 4;

	private FragmentManager	fm;

	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// setup data connections
		mDataHelper = new DataHelper(this);

		fm = getSupportFragmentManager();
		mTaskFragment = (TaskFragment) fm.findFragmentByTag("task");

		// If the Fragment is non-null, then it is currently being
		// retained across a configuration change.
		if (mTaskFragment == null) {
			Log.i(TAG, "Task fragment not found. Creating..");
			mTaskFragment = new TaskFragment();
			fm.beginTransaction().add(mTaskFragment, "task").commit();
		} else {
			Log.i(TAG, "Recycling task fragment");

			if (exportProgress == null) {
				exportProgress = new ProgressDialog(this);
				exportProgress.setCancelable(false);
			}

			if (mTaskFragment.isExecuting()) {
				mTaskFragment.restoreProgress(exportProgress);
				exportProgress.show();
			}
		}

		initUi();
	}


	@Override
	public final void onResume() {
		super.onResume();

		// create wifi lock (will be acquired for version check/upload)
		WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "UploadLock");

		// force an fragment refresh
		reloadListFragment();
	}

	@Override
	public final void onPause() {
		// TODO: check, whether we release wifi lock (for upload) onPause and onDestroy or only onDestroy
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}

		super.onPause();
	}

	@Override
	public final void onDestroy() {
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}

		if (exportProgress != null) {
			// clean up dialogs
			exportProgress.dismiss();
		}

		super.onDestroy();
	}

	/**
	 * Inits Ui controls
	 */
	private void initUi() {
		setContentView(R.layout.session);
		SessionListFragment detailsFragment = (SessionListFragment) getSupportFragmentManager().findFragmentById(R.id.sessionListFragment);	
		detailsFragment.setOnSessionSelectedListener(this);
	}

	/**
	 * Exports session to xml with AsyncTasks.
	 * Once exported, {@link onExportCompleted } is called
	 * @param id
	 * 		session id
	 */
	public final void exportCommand(final int id) {

		mPendingSession = id;

		// checks SD card writeable?
		if (!FileHelper.isSdCardMountedWritable()) {
			Log.e(TAG, "SD card not writable");
			Toast.makeText(this.getBaseContext(), R.string.warning_sd_not_writable, Toast.LENGTH_SHORT).show();
			return;
		} else {
			Log.i(TAG, "Good: SD card writable");
		}

		// skip the other checks, if we're just exporting (and not uploading)
		boolean skipUpload = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_SKIP_UPLOAD, Preferences.VAL_SKIP_UPLOAD);
		if (skipUpload) {
			onServerGood();
			return;
		}

		// check whether session has been uploaded
		if (hasBeenUploaded(id)) {
			Log.i(TAG, this.getString(R.string.warning_already_uploaded));
			showAlertDialog(ID_DELETE_UPLOADED_SESSION, R.string.session_already_uploaded, R.string.question_delete_session, false);

			return;

		} else {
			Log.i(TAG, "Good: Session hasn't yet been uploaded");
		}

		// checks credentials available?
		String user = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_CREDENTIALS_USER, null);
		String password = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);

		if (!isValidLogin(user, password)) {
			showAlertDialog(ID_MISSING_CREDENTIALS,R.string.user_or_password_missing, R.string.question_enter_user, false);

			return;
		} else {
			Log.i(TAG, "Good: User and password provided");
		}

		// acquire wifi lock for export process
		if (wifiLock != null && !wifiLock.isHeld()) {
			Log.d(TAG, "Acquiring wifi lock");
			wifiLock.acquire();
		}

		proceedAfterServerValidation();

		updateUI();
	}

	/**
	 * Checks whether user name and password has been set
	 * @param password2 
	 * @param user2 
	 * @return
	 */
	private boolean isValidLogin(final String user, final String password) {
		return (user != null && password != null);
	}

	/**
	 * Checks if session has been exported
	 */
	private boolean hasBeenUploaded(final int sessionId) {
		DataHelper dataHelper = new DataHelper(this);
		Session session = dataHelper.loadSession(sessionId);
		return session.hasBeenExported();
	}

	/**
	 * Checks whether openbmap.org is accessible and whether client version is up-to-date.
	 * Based on result methods from interface ServerReply are called
	 */
	private void proceedAfterServerValidation() {
		new ServerValidation(this, this).execute(RadioBeacon.VERSION_COMPATIBILITY);
	}

	/* 
	 * Creates a new session record and starts HostActivity ("tracking" mode)
	 */
	@Override
	public final void startCommand() {
		final Intent newSession = new Intent(this, HostActivity.class);
		newSession.putExtra("new_session", true);
		startActivity(newSession);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.activity.SessionListFragment.SessionFragementListener#resumeSession(long)
	 */
	@Override
	public final void resumeCommand(final int id) {
		final Intent resumeSession = new Intent(this, HostActivity.class);
		resumeSession.putExtra("new_session", false);
		resumeSession.putExtra("id", id);

		startActivity(resumeSession);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.activity.SessionListFragment.SessionFragementListener#stopSession(int)
	 */
	@Override
	public final void stopCommand(final int id) {
		mDataHelper.invalidateActiveSessions();
		// Signalling host activity to stop services
		Intent intent = new Intent(RadioBeacon.INTENT_STOP_SERVICES);
		sendBroadcast(intent);

		updateUI();
	}

	/**
	 * Deletes session.
	 * @param id
	 * 		session id
	 */		
	public final void deleteCommand(final int id) {
		// Signalling service stop request
		Intent intent = new Intent(RadioBeacon.INTENT_STOP_SERVICES);
		sendBroadcast(intent);

		mDataHelper.deleteSession(id);

		boolean skipDelete = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_SKIP_DELETE, Preferences.VAL_SKIP_DELETE);

		if (!skipDelete) {
			// delete all temp files (.xml)
			TempFileHelper.cleanTempFiles(this);
		}

		// force UI list update
		updateUI();
		Toast.makeText(getBaseContext(), R.string.deleted, Toast.LENGTH_SHORT).show();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.activity.SessionListFragment.SessionFragementListener#deleteAllSessions(long)
	 */
	@Override
	public final void deleteAllCommand() {
		// Signalling service stop request
		Intent intent = new Intent(RadioBeacon.INTENT_STOP_SERVICES);
		sendBroadcast(intent);

		mDataHelper.deleteAllSession();

		updateUI();
	}

	/**
	 * Sends broadcast to update UI.
	 */
	private void updateUI() {
		// Force update on list fragements' adapters.
		Intent intent1 = new Intent(RadioBeacon.INTENT_WIFI_UPDATE);
		sendBroadcast(intent1);
		Intent intent2 = new Intent(RadioBeacon.INTENT_CELL_UPDATE);
		sendBroadcast(intent2);
	}

	/**
	 * Create options menu.
	 * @param menu Menu to inflate
	 * @return always true
	 */
	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public final boolean onOptionsItemSelected(final MenuItem item) {
		//Log.d(TAG, "OptionItemSelected, handled by SessionActivity");
		switch (item.getItemId()) {
			case R.id.menu_create_new_session:
				startCommand();
				break;
			case R.id.menu_settings:
				// Start settings activity
				startActivity(new Intent(this, SettingsActivity.class));
				break;
			case R.id.menu_credits:
				// Start settings activity
				startActivity(new Intent(this, CreditsActivity.class));
				break;
			default:
				break; 
		}
		return super.onOptionsItemSelected(item);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.activity.SessionListFragment.SessionFragementListener#reload()
	 */
	@Override
	public final void reloadListFragment() {
		SessionListFragment sessionFrag = (SessionListFragment) getSupportFragmentManager().findFragmentById(R.id.sessionListFragment);

		if (sessionFrag != null) {
			// TODO check if this is really necessary. Adapter should be able to handle updates automatically
			sessionFrag.refreshAdapter();
		} 

	}

	/**
	 * When export is finished, all xml files from data dir are uploaded to openbmap
	 * Please note, that there might been xml files from other session in that folder.
	 * These will also be uploaded. On success xml files will be deleted
	 */
	@Override
	public final void onExportCompleted(final int id) {

		// release wifi lock
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}

		// mark as has been exported
		Session session = mDataHelper.loadSession(id);
		session.hasBeenExported(true);
		session.isActive(false);
		mDataHelper.storeSession(session, false);

		showAlertDialog(ID_DELETE_UPLOADED_SESSION, R.string.session_uploaded, R.string.do_you_want_to_delete_this_session, false);
	}

	/**
	 * Shows a alert dialog
	 * @param dialogId
	 * @param titleId
	 * @param messageId
	 * @param onlyNeutral
	 */
	private void showAlertDialog(int dialogId, int titleId, int messageId, boolean onlyNeutral) {
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		DialogFragment alert = MyAlertDialogFragment.newInstance(this, dialogId, 
				titleId, messageId, onlyNeutral);
		// TODO throws IllegalStateException: Cannot perform this action after onSaveInstance
		// see http://www.androiddesignpatterns.com/2013/08/fragment-transaction-commit-state-loss.html
		// see http://stackoverflow.com/questions/7992496/how-to-handle-asynctask-onpostexecute-when-paused-to-avoid-illegalstateexception
		// see http://stackoverflow.com/questions/8040280/how-to-handle-handler-messages-when-activity-fragment-is-paused/8122789#8122789
		alert.show(fm /*getSupportFragmentManager()*/, "dialog");
		transaction.commitAllowingStateLoss();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.WifiExporter.ExportTaskListener#onExportFailed(java.lang.String)
	 */
	@Override
	public final void onExportFailed(final String error) {
		// release wifi lock
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}

		showAlertDialog(ID_EXPORT_FAILED, android.R.string.dialog_alert_title, R.string.export_error, true);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ServerValidation.ServerReply#onVersionGood()
	 */
	@Override
	public final void onServerGood() {
		Log.i(TAG, "Server good, online and valid version");
		String user = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_CREDENTIALS_USER, null);
		String password = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);

		String targetPath = Environment.getExternalStorageDirectory().getPath()
				+ PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_DATA_FOLDER, Preferences.VAL_DATA_FOLDER) + File.separator;
		boolean exportGpx = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_GPS_SAVE_COMPLETE_TRACK, false);

		boolean skipUpload = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_SKIP_UPLOAD, Preferences.VAL_SKIP_UPLOAD);
		boolean skipDelete = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_SKIP_DELETE, Preferences.VAL_SKIP_DELETE);

		mTaskFragment.setTask(mPendingSession, targetPath, user, password, exportGpx, skipUpload, skipDelete);
		mTaskFragment.execute();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ServerValidation.ServerReply#onVersionBad()
	 */
	@Override
	public void onServerBad(final String text) {
		Log.e(TAG, "Out-dated version");
		Toast.makeText(this, this.getString(R.string.warning_outdated_client) + "\n" + text, Toast.LENGTH_LONG).show();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ServerValidation.ServerReply#onCheckFailed()
	 */
	@Override
	public final void onServerCheckFailed() {
		Log.i(TAG, "Server check failed");
		// if we still have no connection, offer to toggle wifi state
		showAlertDialog(ID_REPAIR_WIFI, R.string.error_version_check_title, R.string.error_version_check_body, false);
	}

	/**
	 * Toggles wifi enabled (to trigger reconnect) and checks whether client is online afterwards
	 * @return true if client is online
	 */
	private void repairWifiConnection() {
		Log.i(TAG, "Reparing wifi connection");
		WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		wifiManager.setWifiEnabled(false);
		wifiManager.setWifiEnabled(true);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.utils.OnAlertClickInterface#doPositiveClick()
	 */
	@Override
	public void onAlertPositiveClick(int alertId) {
		if (alertId == ID_DELETE_UPLOADED_SESSION) {
			deleteCommand(mPendingSession);
			return;
		} else if (alertId == ID_REPAIR_WIFI) {
			repairWifiConnection();
			proceedAfterServerValidation();
			return;
		} else if ( alertId == ID_MISSING_CREDENTIALS) {
			startActivity(new Intent(getBaseContext(), SettingsActivity.class));
			return;
		}
	}


	/* (non-Javadoc)
	 * @see org.openbmap.utils.OnAlertClickInterface#doNegativeClick()
	 */
	@Override
	public void onAlertNegativeClick(int alertId) {
		if (alertId == ID_DELETE_UPLOADED_SESSION) {
			return;
		} else if (alertId == ID_REPAIR_WIFI) {
			Toast.makeText(SessionActivity.this, getResources().getString(R.string.upload_aborted), Toast.LENGTH_LONG).show();
			return;
		} else if (alertId == ID_MISSING_CREDENTIALS) {
			return;
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.utils.OnAlertClickInterface#doNeutralClick(int)
	 */
	@Override
	public void onAlertNeutralClick(int alertId) {
		if (alertId == ID_EXPORT_FAILED) {
			return;
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.activity.TaskFragment.TaskCallbacks#onPreExecute()
	 */
	@Override
	public void onPreExecute() {
		String defaultTitle = getResources().getString(R.string.preparing_export);
		String defaultMessage = getResources().getString(R.string.please_stay_patient);
		exportProgress = new ProgressDialog(this);

		exportProgress.setTitle(defaultTitle);
		exportProgress.setMessage(defaultMessage);

		exportProgress.setCancelable(false);
		exportProgress.setIndeterminate(true);
		exportProgress.show();
		mTaskFragment.retainProgress(defaultTitle, defaultMessage, (int) exportProgress.getProgress());
	}

	/* (non-Javadoc)
	 * @see org.openbmap.activity.TaskFragment.TaskCallbacks#onCancelled()
	 */
	@Override
	public void onCancelled() {
		if (exportProgress != null) {
			exportProgress.cancel();
		}
		mTaskFragment.resetExecuting();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.activity.TaskFragment.TaskCallbacks#onPostExecute()
	 */
	@Override
	public void onPostExecute() {
		if (exportProgress != null) {
			exportProgress.cancel();
		}
		mTaskFragment.resetExecuting();
	}


	/* (non-Javadoc)
	 * @see org.openbmap.activity.TaskFragment.TaskCallbacks#onProgressUpdate(java.lang.Object[])
	 */
	@Override
	public void onProgressUpdate(Object[] values) {
		if (exportProgress != null) {
			exportProgress.setTitle((CharSequence) values[0]);
			exportProgress.setMessage((CharSequence) values[1]);	
			exportProgress.setProgress((Integer) values[2]);
		}
		mTaskFragment.retainProgress((String) values[0], (String) values[1], (Integer) values[2]);
	}

}
