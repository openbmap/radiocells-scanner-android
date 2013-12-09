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

import java.util.ArrayList;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.Session;
import org.openbmap.soapclient.ExportSessionTask.ExportTaskListener;
import org.openbmap.utils.AlertDialogHelper;
import org.openbmap.utils.OnAlertClickInterface;
import org.openbmap.utils.TabManager;
import org.openbmap.utils.TempFileHelper;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.widget.TabHost;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Parent screen for hosting main screen
 */
public class StartscreenActivity extends SherlockFragmentActivity
implements SessionListFragment.SessionFragementListener, OnAlertClickInterface, ExportTaskListener
{

	private static final String TAG = StartscreenActivity.class.getSimpleName();

	private static final String	WIFILOCK_NAME	= "UploadLock";

	// alert builder ids
	private static final int ID_REPAIR_WIFI	= 2;
	private static final int ID_MISSING_CREDENTIALS	= 3;
	private static final int ID_EXPORT_FAILED = 4;

	private static final int ID_DELETE_SESSION	= 5;
	private static final int ID_DELETE_PROCESSED = 6;
	private static final int ID_DELETE_ALL = 7;

	/**
	 * Tab host control
	 */
	private TabHost mTabHost;

	/**
	 * Tab host helper class
	 */
	private TabManager mTabManager;

	/**
	 * Data helper
	 */
	private DataHelper mDataHelper;

	/**
	 * Wifi lock, used while exporting
	 */
	private WifiLock wifiLock;

	/**
	 * Persistent fragment saving export task across activity re-creation
	 */
	private ExportTaskFragment mTaskFragment;

	private FragmentManager	fm;

	/**
	 * Dialog indicating export progress
	 */
	private ProgressDialog exportProgress;

	/**
	 * List of all pending exports
	 */
	private ArrayList<Integer> pendingExports = new ArrayList<Integer>();

	/**
	 * Counts successfully exported sessions
	 */
	private int	completedExports;

	/**
	 * Counts failed exports
	 */
	private int failedExports;

	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// setup data connections
		mDataHelper = new DataHelper(this);

		Bundle b = getIntent().getExtras();
		if (b != null) {
			if (b.getString("command") != null && b.getString("command").equals("upload_all")) {
				exportAllCommand();
			}
		}

		initUi(savedInstanceState);

		initPersistantFragment();
	}

	/**
	 * Creates a persistent fragment for keeping export status.
	 * If fragment has been created before, no new fragment is created
	 */
	private void initPersistantFragment() {
		fm = getSupportFragmentManager();
		mTaskFragment = (ExportTaskFragment) fm.findFragmentByTag("task");

		// If the Fragment is non-null, then it is currently being
		// retained across a configuration change.
		if (mTaskFragment == null) {
			Log.i(TAG, "Task fragment not found. Creating..");
			mTaskFragment = new ExportTaskFragment();
			fm.beginTransaction().add(mTaskFragment, "task").commit();

			initExportDialog(true);
		} else {
			Log.i(TAG, "Recycling task fragment");
			initExportDialog(false);
			showExportDialog();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("tab", mTabHost.getCurrentTabTag());
	}

	@Override
	public final void onResume() {
		super.onResume();

		createWifiLock();

		// force an fragment refresh
		reloadListFragment();
	}

	@Override
	public final void onPause() {
		releaseWifiLock();

		super.onPause();
	}

	@Override
	public final void onDestroy() {
		releaseWifiLock();

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


	/* 
	 * Resumes existing session
	 */
	@Override
	public final void resumeCommand(final int id) {
		final Intent resumeSession = new Intent(this, HostActivity.class);
		resumeSession.putExtra("_id", id);

		startActivity(resumeSession);
	}

	/**
	 * Exports session
	 * Once exported, {@link onExportCompleted} is called
	 * @param id
	 * 		session id
	 */
	public final void exportCommand(final int id) {
		acquireWifiLock();
		pendingExports.clear();
		completedExports = 0;
		failedExports = 0;

		pendingExports.add(id);
		mTaskFragment.add(id);
		mTaskFragment.prepareStart();

		showExportDialog();

		updateUI();
	}

	/* 
	 * Exports all sessions, which haven't been uploaded yet
	 */
	@Override
	public void exportAllCommand() {
		acquireWifiLock();
		pendingExports.clear();
		completedExports = 0;
		failedExports = 0;

		ArrayList<Integer> sessions = mDataHelper.getSessionList();
		for (int id : sessions) {
			if (!hasBeenUploaded(id)) {
				Log.i(TAG, "Adding " + id + " to export task list");
				mTaskFragment.add(id);
				pendingExports.add(id);
			}
		}
		mTaskFragment.prepareStart();

		showExportDialog();
	}

	/* 
	 * Stops all active session
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
		AlertDialogHelper.newInstance(ID_DELETE_SESSION, R.string.delete, R.string.do_you_want_to_delete_this_session, String.valueOf(id), false).show(getSupportFragmentManager(), "delete");
	}

	/**
	 * Deletes all session from pending list
	 */
	public final void deleteBatchCommand(ArrayList<Integer> ids) {
		for (int id : ids) {
			deleteConfirmed(id);
		}
	}

	/**
	 * User has confirmed delete
	 * @param id
	 */
	public final void deleteConfirmed(final int id) {
		if (id == RadioBeacon.SESSION_NOT_TRACKING) {
			return;
		}

		Log.i(TAG, "Deleting session " + id);

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
	 * @see org.openbmap.activities.SessionListFragment.SessionFragementListener#deleteAllSessions(long)
	 */
	@Override
	public final void deleteAllCommand() {
		AlertDialogHelper.newInstance(ID_DELETE_ALL, R.string.dialog_delete_all_sessions_title, R.string.dialog_delete_all_sessions_message, null, false).show(getSupportFragmentManager(), "delete_all");
	}

	public final void deleteAllConfirmed() {
		// Signalling service stop request
		Intent intent = new Intent(RadioBeacon.INTENT_STOP_TRACKING);
		sendBroadcast(intent);

		mDataHelper.deleteAllSession();

		updateUI();
	}

	/**
	 * Updates session list fragment and informs
	 * Sends broadcast to update UI.
	 */
	private void updateUI() {
		reloadListFragment();

		// Force update on list fragements' adapters.
		// TODO check if we really need this
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
		MenuInflater inflater = getSupportMenuInflater();

		/**
    	yet missing
        menu.add("Upload all")
         	.setIcon(R.drawable.ic_action_upload)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
		 */

		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public final boolean onOptionsItemSelected(final MenuItem item) {
		//Log.d(TAG, "OptionItemSelected, handled by StartscreenActivity");
		switch (item.getItemId()) {
			case R.id.menu_create_new_session:
				// starts a new session
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
	 * @see org.openbmap.activities.SessionListFragment.SessionFragementListener#reload()
	 */
	@Override
	public final void reloadListFragment() {
		Log.i(TAG, "Refreshing session list fragment");
		SessionListFragment sessionFrag = (SessionListFragment) getSupportFragmentManager().findFragmentByTag("session");

		if (sessionFrag != null) {
			// TODO check if this is really necessary. Adapter should be able to handle updates automatically
			sessionFrag.refreshAdapter();
		} 
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

	/**
	 * Inits Ui controls
	 * @param savedInstanceState 
	 */
	private void initUi(Bundle savedInstanceState) {
		setContentView(R.layout.startscreen);

		getSupportActionBar().setTitle(R.string.title);
		getSupportActionBar().setSubtitle(R.string.subtitle);

		mTabHost = (TabHost) findViewById(android.R.id.tabhost);
		mTabHost.setup();

		mTabManager = new TabManager(this, mTabHost, R.id.realtabcontent);

		mTabManager.addTab(mTabHost.newTabSpec("sessions").setIndicator(getResources().getString(R.string.sessions)),
				SessionListFragment.class, null);

		//mTabManager.addTab(mTabHost.newTabSpec("overview").setIndicator("Overview"),
		//		OverviewFragment.class, null);

		if (savedInstanceState != null) {
			mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
		}

		//SessionListFragment detailsFragment = (SessionListFragment) getSupportFragmentManager().findFragmentById(R.id.sessionListFragment);	
		//detailsFragment.setOnSessionSelectedListener(this);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.utils.OnAlertClickInterface#onAlertPositiveClick(int)
	 */
	@Override
	public void onAlertPositiveClick(int alertId, String args) {
		if (alertId == ID_DELETE_ALL) {
			int id = (args != null ? Integer.valueOf(args) : RadioBeacon.SESSION_NOT_TRACKING);
			stopCommand(id);
			deleteAllConfirmed();
		} else if (alertId == ID_DELETE_SESSION) {
			int id = (args != null ? Integer.valueOf(args) : RadioBeacon.SESSION_NOT_TRACKING);
			stopCommand(id);
			deleteConfirmed(id);
		} else if (alertId == ID_DELETE_PROCESSED) {
			String candidates = (args != null ? String.valueOf(args) : "");

			ArrayList<Integer> list = new ArrayList<Integer>();
			for (String s : candidates.split("\\s*;\\s*")) {
				list.add(Integer.valueOf(s));
			}

			deleteBatchCommand(list);
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.utils.OnAlertClickInterface#onAlertNegativeClick(int)
	 */
	@Override
	public void onAlertNegativeClick(int alertId, String args) {
		if (alertId == ID_DELETE_ALL) {
			return;
		} else if (alertId == ID_DELETE_SESSION) {
			return;
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.utils.OnAlertClickInterface#onAlertNeutralClick(int, java.lang.String)
	 */
	@Override
	public void onAlertNeutralClick(int alertId, String args) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onProgressUpdate(java.lang.Object[])
	 */
	@Override
	public void onProgressUpdate(Object... values) {
		//Log.v(TAG, "Updating dialog");
		if (exportProgress != null) {
			exportProgress.setTitle((CharSequence) values[0]);
			exportProgress.setMessage((CharSequence) values[1]);	
			exportProgress.setProgress((Integer) values[2]);
		}
		mTaskFragment.retainProgress((String) values[0], (String) values[1], (Integer) values[2]);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onExportCompleted(int)
	 */
	@Override
	public void onExportCompleted(int id) {
		// mark as exported
		Session session = mDataHelper.loadSession(id);
		session.hasBeenExported(true);
		session.isActive(false);
		mDataHelper.storeSession(session, false);

		completedExports += 1;
		Log.i(TAG, "Session " + id + " exported");
		Log.i(TAG, "Exported " + completedExports + "/" + pendingExports.size() + " sessions");

		if (pendingExports.size() == 1) {
			Log.i(TAG, "Export finished");

			pendingExports.clear();
			completedExports = 0;

			hideExportDialog();
			releaseWifiLock();

			deleteCommand(id);
		} else if (pendingExports.size() == completedExports + failedExports) {
			Log.i(TAG, "All exports finished");

			if (failedExports > 0) {
				// at least one export failed
				AlertDialogHelper.newInstance(ID_EXPORT_FAILED, R.string.export_error_title, R.string.export_error, String.valueOf(id), true).show(getSupportFragmentManager(), "failed");
			} else {
				// if everything is ok, offer to delete
				String candidates = "";
				for (int one : pendingExports){
					candidates += one + ";";
				}
				AlertDialogHelper.newInstance(ID_DELETE_PROCESSED, R.string.delete, R.string.do_you_want_to_delete_processed_sessions, candidates, false).show(getSupportFragmentManager(), "failed");
			}

			pendingExports.clear();
			completedExports = 0;
			failedExports = 0;

			hideExportDialog();
			releaseWifiLock();

			// TODO move to onAlertNegative with ID_DELETE_PROCESSED
			reloadListFragment();
		} else {
			// we've got more exports
			showExportDialog();
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onExportFailed(java.lang.String)
	 */
	@Override
	public void onExportFailed(int id, String error) {
		Log.e(TAG, "Export session " + id + " failed");

		failedExports += 1;
		releaseWifiLock();
		hideExportDialog();

		AlertDialogHelper.newInstance(ID_EXPORT_FAILED, R.string.export_error_title, R.string.export_error, String.valueOf(id), true).show(getSupportFragmentManager(), "failed");
	}

	/**
	 * Creates or restores a progress dialogs
	 * This dialog is not shown until calling showExportDialog() explicitly
	 * @param new 
	 * 
	 */
	private void initExportDialog(boolean newDialog) {
		if (newDialog) {
			exportProgress = new ProgressDialog(this);
			exportProgress.setCancelable(false);
			exportProgress.setIndeterminate(true);

			String defaultTitle = getResources().getString(R.string.preparing_export);
			String defaultMessage = getResources().getString(R.string.please_stay_patient);
			exportProgress.setTitle(defaultTitle);
			exportProgress.setMessage(defaultMessage);

			mTaskFragment.retainProgress(defaultTitle, defaultMessage, (int) exportProgress.getProgress());	
		} else {
			exportProgress = new ProgressDialog(this);
			exportProgress.setCancelable(false);
			exportProgress.setIndeterminate(true);

			mTaskFragment.restoreProgress(exportProgress);
		}
	}

	/**
	 * Opens existing export dialog, if any
	 */
	private void showExportDialog() {
		if (exportProgress == null) {
			throw new IllegalArgumentException("Export progress dialog must not be null");
		}

		if (mTaskFragment.isExecuting()) {
			mTaskFragment.restoreProgress(exportProgress);

			if (!exportProgress.isShowing()) {
				exportProgress.show();
			}
		}
	}

	/**
	 * Closes export dialog
	 */
	private void hideExportDialog() {
		if (exportProgress != null) {
			exportProgress.cancel();
		}
	}

	/**
	 * Creates a new wifi lock, which isn't yet acquired
	 */
	private void createWifiLock() {
		// create wifi lock (will be acquired for version check/upload)
		WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		if (wifiManager != null) {
			wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, WIFILOCK_NAME);
		} else {
			Log.e(TAG, "Error acquiring wifi lock");
		}
	}

	/**
	 * Acquires wifi lock
	 */
	private void acquireWifiLock() {
		if (wifiLock == null) {
			Log.w(TAG, "Wifilock not found. Skipping acquisition..");
			return;
		}
		if (!wifiLock.isHeld()) {
			wifiLock.acquire();
		} else {
			Log.i(TAG, "Wifilock is hold already. Skipping acquisition..");
		}
	}

	/**
	 * Releases previously acquired wifi lock
	 */
	private void releaseWifiLock() {
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}
	}
}
