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
import java.util.ArrayList;

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

/*
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
 */














import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
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
public class SessionActivity extends FragmentActivity
implements SessionListFragment.SessionFragementListener, TaskCallbacks 
{
	private static final String TAG = SessionActivity.class.getSimpleName();

	private DataHelper mDataHelper;

	private WifiLock wifiLock;

	private TaskFragment mTaskFragment;

	private ProgressDialog exportProgress;

	private ExportTasks mExportTasks = new ExportTasks();

	private FragmentManager	fm;

	class ExportTasks implements ServerReply, OnAlertClickInterface, ExportManagerListener {
		// alert builder ids
		private static final int ID_DELETE_UPLOADED_SESSION = 1;
		private static final int ID_REPAIR_WIFI	= 2;
		private static final int ID_MISSING_CREDENTIALS	= 3;
		private static final int ID_EXPORT_FAILED = 4;

		private class ExportTask {
			int id;
			boolean noDialogs = false;
		}

		private ArrayList<ExportTask> ids = new ArrayList<ExportTask>();
		private boolean isProcessing = false;
		private ExportTask currentTask;

		public void add(int me) {
			ExportTask newTask = new ExportTask();
			newTask.id = me;
			newTask.noDialogs = false;

			ids.add(newTask);
		};

		public void add(int me, boolean autoDelete) {
			ExportTask newTask = new ExportTask();
			newTask.id = me;
			newTask.noDialogs = autoDelete;

			ids.add(newTask);
		};

		public void remove(int me) {
			for (int i = 0; i < ids.size(); i++) {
				if (ids.get(i).id == me) {
					ids.remove(i);
				}
			}

			if (isProcessing) {
				// continue on next
				start();
			}
		}

		public void start() {

			if (isProcessing) {
				return;
			}

			if (ids.size() == 0) {
				isProcessing = false;
				return;
			}

			isProcessing = true;
			currentTask = ids.get(0);

			proceedAfterLocalCheck();
		}

		/**
		 * Performs some local checks:
		 * <ul>
		 * 	<li> session hasn't yet been uploaded</li> 
		 *  <li> SD card writable</li> 
		 *  <li> User name and password set in preferences</li> 
		 * </ul>
		 */
		private void proceedAfterLocalCheck() {

			// Has session been uploaded?
			if (hasBeenUploaded(currentTask.id)) {
				Log.i(TAG, SessionActivity.this.getString(R.string.warning_already_uploaded));
				showAlertDialog(ID_DELETE_UPLOADED_SESSION, R.string.session_already_uploaded, R.string.question_delete_session, false);

				remove(currentTask.id);
				return;
			} else {
				Log.i(TAG, "Good: Session hasn't yet been uploaded");
			}

			// Is SD card writeable?
			if (!FileHelper.isSdCardMountedWritable()) {
				Log.e(TAG, "SD card not writable");
				Toast.makeText(SessionActivity.this, R.string.warning_sd_not_writable, Toast.LENGTH_SHORT).show();

				isProcessing = false;

				return;
			} else {
				Log.i(TAG, "Good: SD card writable");
			}

			// Skips the server checks, if we're just exporting (and not uploading)
			boolean skipUpload = PreferenceManager.getDefaultSharedPreferences(SessionActivity.this).getBoolean(Preferences.KEY_SKIP_UPLOAD, Preferences.VAL_SKIP_UPLOAD);
			if (skipUpload) {
				onServerGood();
				return;
			}

			// checks credentials available?
			String user = PreferenceManager.getDefaultSharedPreferences(SessionActivity.this).getString(Preferences.KEY_CREDENTIALS_USER, null);
			String password = PreferenceManager.getDefaultSharedPreferences(SessionActivity.this).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);

			if (!isValidLogin(user, password)) {
				showAlertDialog(ID_MISSING_CREDENTIALS, R.string.user_or_password_missing, R.string.question_enter_user, false);

				isProcessing = false;

				return;
			} else {
				Log.i(TAG, "Good: User and password provided");
			}

			// Now let's check server
			proceedAfterServerValidation();
		}

		/**
		 * Checks whether openbmap.org is accessible and whether client version is up-to-date.
		 * Based on result methods from interface ServerReply are called
		 */
		private void proceedAfterServerValidation() {
			// Acquires wifi lock for export process
			if (wifiLock != null && !wifiLock.isHeld()) {
				Log.d(TAG, "Acquiring wifi lock");
				wifiLock.acquire();
			}

			new ServerValidation(SessionActivity.this, this).execute(RadioBeacon.VERSION_COMPATIBILITY);
		}

		/* (non-Javadoc)
		 * @see org.openbmap.soapclient.ServerValidation.ServerReply#onVersionGood()
		 */
		@Override
		public final void onServerGood() {
			Log.i(TAG, "Server good, online and valid version");
			String user = PreferenceManager.getDefaultSharedPreferences(SessionActivity.this).getString(Preferences.KEY_CREDENTIALS_USER, null);
			String password = PreferenceManager.getDefaultSharedPreferences(SessionActivity.this).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);

			String targetPath = Environment.getExternalStorageDirectory().getPath()
					+ PreferenceManager.getDefaultSharedPreferences(SessionActivity.this).getString(Preferences.KEY_DATA_FOLDER, Preferences.VAL_DATA_FOLDER) + File.separator;
			boolean exportGpx = PreferenceManager.getDefaultSharedPreferences(SessionActivity.this).getBoolean(Preferences.KEY_GPS_SAVE_COMPLETE_TRACK, false);

			boolean skipUpload = PreferenceManager.getDefaultSharedPreferences(SessionActivity.this).getBoolean(Preferences.KEY_SKIP_UPLOAD, Preferences.VAL_SKIP_UPLOAD);
			boolean skipDelete = PreferenceManager.getDefaultSharedPreferences(SessionActivity.this).getBoolean(Preferences.KEY_SKIP_DELETE, Preferences.VAL_SKIP_DELETE);

			mTaskFragment.setTask(this, currentTask.id, targetPath, user, password, exportGpx, skipUpload, skipDelete);
			mTaskFragment.execute();
		}

		/* (non-Javadoc)
		 * @see org.openbmap.soapclient.ServerValidation.ServerReply#onVersionBad()
		 */
		@Override
		public void onServerBad(final String text) {
			Log.e(TAG, "Out-dated version");
			Toast.makeText(SessionActivity.this, SessionActivity.this.getString(R.string.warning_outdated_client) + "\n" + text, Toast.LENGTH_LONG).show();
			isProcessing = false;
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

			remove(currentTask.id);
			isProcessing = false;

			if (!currentTask.noDialogs) {
				showAlertDialog(ID_DELETE_UPLOADED_SESSION, R.string.session_uploaded, R.string.do_you_want_to_delete_this_session, false);
			}

			if (ids.size() > 0) {
				// process next
				start();
			}
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
			isProcessing = false;
		}

		/**
		 * Shows a alert dialog
		 * @param dialogId
		 * @param titleId
		 * @param messageId
		 * @param onlyNeutral
		 */
		private void showAlertDialog(int dialogId, int titleId, int messageId, boolean onlyNeutral) {
			Log.d(TAG, "Creating dialog " + dialogId);
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
		 * @see org.openbmap.utils.OnAlertClickInterface#doPositiveClick()
		 */
		@Override
		public void onAlertPositiveClick(int alertId) {
			if (alertId == ID_DELETE_UPLOADED_SESSION) {
				deleteCommand(currentTask.id);
				return;
			} else if (alertId == ID_REPAIR_WIFI) {
				repairWifiConnection();
				proceedAfterServerValidation();
				// don't reset processing state here, just retry
				return;
			} else if ( alertId == ID_MISSING_CREDENTIALS) {
				startActivity(new Intent(getBaseContext(), SettingsActivity.class));
				isProcessing = false;
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
				isProcessing = false;
				return;
			} else if (alertId == ID_MISSING_CREDENTIALS) {
				isProcessing = false;
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
	}

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

		Bundle b = getIntent().getExtras();
		if (b != null) {
			if (b.getString("command") != null && b.getString("command").equals("upload_all")) {
				exportAllCommand();
			}
		}

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

	/**
	 * Exports session to xml with AsyncTasks.
	 * Once exported, {@link onExportCompleted} is called
	 * @param id
	 * 		session id
	 */
	public final void exportCommand(final int id) {
		mExportTasks.add(id);
		mExportTasks.start();
		updateUI();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.activity.SessionListFragment.SessionFragementListener#exportAll()
	 */
	@Override
	public void exportAllCommand() {
		ArrayList<Integer> sessions = mDataHelper.getSessionList();
		for (int id : sessions) {
			if (!hasBeenUploaded(id)) {
				mExportTasks.add(id, true);
			}
		}
		mExportTasks.start();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.activity.SessionListFragment.SessionFragementListener#stopSession(int)
	 */
	@Override
	public final void stopCommand(final int id) {
		mDataHelper.invalidateActiveSessions();
		// Signalling host activity to stop services
		Intent intent = new Intent(RadioBeacon.INTENT_STOP_TRACKING);
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
		Intent intent = new Intent(RadioBeacon.INTENT_STOP_TRACKING);
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
		Intent intent = new Intent(RadioBeacon.INTENT_STOP_TRACKING);
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
		MenuInflater inflater = getMenuInflater(); /*Sherlock style getSupportMenuInflater();*/
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
	 * @param id Session id
	 * @return true if session has been uploaded or doesn't exist
	 */
	private boolean hasBeenUploaded(final int Id) {
		DataHelper dataHelper = new DataHelper(this);
		Session session = dataHelper.loadSession(Id);

		if (session != null) {
			return session.hasBeenExported();
		} else {
			return true;
		}
	}

	/**
	 * Toggles wifi enabled (to trigger reconnect)
	 */
	private void repairWifiConnection() {
		Log.i(TAG, "Reparing wifi connection");
		WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		wifiManager.setWifiEnabled(false);
		wifiManager.setWifiEnabled(true);
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


	/**
	 * Inits Ui controls
	 */
	private void initUi() {
		setContentView(R.layout.session);
		SessionListFragment detailsFragment = (SessionListFragment) getSupportFragmentManager().findFragmentById(R.id.sessionListFragment);	
		detailsFragment.setOnSessionSelectedListener(this);
	}

}
