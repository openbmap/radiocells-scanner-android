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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.Toast;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.Session;
import org.openbmap.soapclient.ExportGpxTask.ExportGpxTaskListener;
import org.openbmap.soapclient.ExportDataTask.UploadTaskListener;
import org.openbmap.utils.AlertDialogUtils;
import org.openbmap.utils.OnAlertClickInterface;
import org.openbmap.utils.TabManager;
import org.openbmap.utils.TempFileUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Parent screen for hosting main screen
 */
public class StartscreenActivity extends AppCompatActivity
implements SessionListFragment.SessionFragementListener, OnAlertClickInterface, UploadTaskListener, ExportGpxTaskListener {

	private static final String TAG = StartscreenActivity.class.getSimpleName();

	/**
	 *
	 */
	private static final String UPLOAD_TASK = "upload_task";
	private static final String EXPORT_GPX_TASK = "export_gpx_task";

	private static final String	WIFILOCK_NAME = "UploadLock";

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
	private WifiLock mWifiLock;

	/**
	 * Persistent fragment saving upload task across activity re-creation
	 */
	private UploadTaskFragment mUploadTaskFragment;

	/**
	 * Persistent fragment saving export gpx task across activity re-creation
	 */
	private ExportGpxTaskFragment mExportGpxTaskFragment;

	private FragmentManager	fm;

	/**
	 * Dialog indicating upload progress
	 */
	private ProgressDialog mUploadProgress;

	/**
	 * Dialog indicating export gpx progress
	 */
	private ProgressDialog mExportGpxProgress;

	/**
	 * List of all pending exports
	 */
	private final ArrayList<Integer> pendingExports = new ArrayList<Integer>();

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

        initPersistantFragments();

		// setup data connections
		mDataHelper = new DataHelper(this);

		final Bundle b = getIntent().getExtras();
		if (b != null) {
			if (b.getString("command") != null && b.getString("command").equals("upload_all")) {
				uploadAllCommand();
            }
        }

        initUi(savedInstanceState);
	}

	/**
	 * Creates a persistent fragment for keeping export status.
	 * If fragment has been created before, no new fragment is created
	 * i.e. the Fragment is non-null, then it is currently being
	 * retained across a configuration change.
	 */
	private void initPersistantFragments() {
		fm = getSupportFragmentManager();
		mUploadTaskFragment = (UploadTaskFragment) fm.findFragmentByTag(UPLOAD_TASK);
		mExportGpxTaskFragment = (ExportGpxTaskFragment) fm.findFragmentByTag(EXPORT_GPX_TASK);

		if (mUploadTaskFragment == null) {
			Log.d(TAG, "Task fragment not found. Creating..");
			mUploadTaskFragment = new UploadTaskFragment();
            // was commitAllowingStateLoss()
			fm.beginTransaction().add(mUploadTaskFragment, UPLOAD_TASK).commit();
            // https://stackoverflow.com/questions/7469082/getting-exception-illegalstateexception-can-not-perform-this-action-after-onsa
            fm.executePendingTransactions();

			initUploadTaskDialog(true);
		} else {
			Log.d(TAG, "Showing existing upload task fragment");
			initUploadTaskDialog(false);
			showUploadTaskDialog();
		}


		if (mExportGpxTaskFragment == null) {
			Log.d(TAG, "Task fragment not found. Creating..");
			mExportGpxTaskFragment = new ExportGpxTaskFragment();
            // was commitAllowingStateLoss()
            fm.beginTransaction().add(mExportGpxTaskFragment, EXPORT_GPX_TASK).commit();
            // https://stackoverflow.com/questions/7469082/getting-exception-illegalstateexception-can-not-perform-this-action-after-onsa
			fm.executePendingTransactions();

			initExportGpxTaskDialog(true);
		} else {
			Log.d(TAG, "Showing existings export gpx task fragment");
			initExportGpxTaskDialog(false);
			showExportGpxTaskDialog();
		}
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
        outState.putString("tab", mTabHost.getCurrentTabTag());
		super.onSaveInstanceState(outState);
	}

	@Override
	public final void onResume() {
		super.onResume();

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
        // bring up service
        final Intent intent = new Intent(RadioBeacon.INTENT_START_TRACKING);
        sendBroadcast(intent);
        // bring up UI
		final Intent activity = new Intent(this, HostActivity.class);
		activity.putExtra("new_session", true);
		startActivity(activity);
	}

	/*
	 * Resumes existing session
	 * @param id session id to resume
	 */
	@Override
	public final void resumeCommand(final int id) {
        // bring up service
        final Intent intent = new Intent(RadioBeacon.INTENT_START_TRACKING);
        intent.putExtra("_id", id);
        sendBroadcast(intent);

		final Intent activity = new Intent(this, HostActivity.class);
		activity.putExtra("_id", id);
		startActivity(activity);
	}

	/**
	 * Uploads session
	 * Once exported, {@link onExportCompleted} is called
	 * @param session
	 * 		session id
	 */
	public final void uploadCommand(final int session) {
		acquireWifiLock();
		pendingExports.clear();
		completedExports = 0;
		failedExports = 0;

		pendingExports.add(session);
		mUploadTaskFragment.add(session);
		mUploadTaskFragment.execute();

		showUploadTaskDialog();

		updateUI();
	}

	/*
	 * Exports all sessions, which haven't been uploaded yet
	 */
	@Override
	public void uploadAllCommand() {
		acquireWifiLock();
		pendingExports.clear();
		completedExports = 0;
		failedExports = 0;

		final ArrayList<Integer> sessions = mDataHelper.getSessionList();
		for (final int id : sessions) {
			if (!hasBeenUploaded(id)) {
				Log.i(TAG, "Adding " + id + " to export task list");
				mUploadTaskFragment.add(id);
				pendingExports.add(id);
			}
		}
		mUploadTaskFragment.execute();

		showUploadTaskDialog();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.activities.SessionListFragment.SessionFragementListener#exportGpxCommand(int)
	 */
	@Override
	public void exportGpxCommand(final int id) {
		Log.i(TAG, "Exporting gpx");

		final String path = this.getExternalFilesDir(null).getAbsolutePath();

		final SimpleDateFormat date = new SimpleDateFormat("yyyyMMddhhmmss", Locale.US);
		final String filename = date.format(new Date(System.currentTimeMillis())) + "(" + String.valueOf(id) + ")" + ".gpx";

		showExportGpxTaskDialog();
		mExportGpxTaskFragment.execute(id, path, filename);
	}

	/*
	 * Stops all active session
	 */
	@Override
	public final void stopCommand(final int id) {
		mDataHelper.invalidateActiveSessions();
		// Signalling host activity to stop services
		final Intent intent = new Intent(RadioBeacon.INTENT_STOP_TRACKING);
		sendBroadcast(intent);

		updateUI();
	}

	/**
	 * Deletes session.
	 * @param id
	 * 		session id
	 */
	public final void deleteCommand(final int id) {
        try {
            AlertDialogUtils.newInstance(ID_DELETE_SESSION,
                    getResources().getString(R.string.delete), getResources().getString(R.string.do_you_want_to_delete_this_session),
                    String.valueOf(id), false).show(getSupportFragmentManager(), "delete");
        } catch (IllegalStateException e) {
            // Crashes when app has been sent to background
            // quick fix don' display dialog when in background
        }
	}

	/**
	 * Deletes all session from pending list
	 */
	public final void deleteBatchCommand(final ArrayList<Integer> ids) {
		for (final int id : ids) {
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
		final Intent intent = new Intent(RadioBeacon.INTENT_STOP_TRACKING);
		sendBroadcast(intent);

		mDataHelper.deleteSession(id);

		final boolean skipDelete = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_KEEP_XML, Preferences.VAL_KEEP_XML);

		if (!skipDelete) {
			// delete all temp files (.xml)
			TempFileUtils.cleanTempFiles(this);
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
		AlertDialogUtils.newInstance(ID_DELETE_ALL,
                getResources().getString(R.string.dialog_delete_all_sessions_title), getResources().getString(R.string.dialog_delete_all_sessions_message),
                null, false).show(getSupportFragmentManager(), "delete_all");
	}

	public final void deleteAllConfirmed() {
		// Signalling service stop request
		final Intent intent = new Intent(RadioBeacon.INTENT_STOP_TRACKING);
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
		final Intent intent1 = new Intent(RadioBeacon.INTENT_WIFI_UPDATE);
		sendBroadcast(intent1);
		final Intent intent2 = new Intent(RadioBeacon.INTENT_CELL_UPDATE);
		sendBroadcast(intent2);
	}

	/**
	 * Create options menu.
	 * @param menu Menu to inflate
	 * @return always true
	 */
	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		final MenuInflater inflater = getMenuInflater();
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
			case R.id.menu_upload_all:
				uploadAllCommand();
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
		final SessionListFragment sessionFrag = (SessionListFragment) getSupportFragmentManager().findFragmentByTag("session");

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
	private boolean hasBeenUploaded(final int id) {
		final DataHelper dataHelper = new DataHelper(this);
		final Session session = dataHelper.loadSession(id);

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
		Log.i(TAG, "Repairing wifi connection");
		final WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		wifiManager.setWifiEnabled(false);
		wifiManager.setWifiEnabled(true);
	}

	/**
	 * Inits Ui controls
	 * @param savedInstanceState
	 */
	private void initUi(final Bundle savedInstanceState) {
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
	}

	/* (non-Javadoc)
	 * @see org.openbmap.utils.OnAlertClickInterface#onAlertPositiveClick(int)
	 */
	@Override
	public void onAlertPositiveClick(final int alertId, final String args) {
		if (alertId == ID_DELETE_ALL) {
			final int id = (args != null ? Integer.valueOf(args) : RadioBeacon.SESSION_NOT_TRACKING);
			stopCommand(id);
			deleteAllConfirmed();
		} else if (alertId == ID_DELETE_SESSION) {
			final int id = (args != null ? Integer.valueOf(args) : RadioBeacon.SESSION_NOT_TRACKING);
			stopCommand(id);
			deleteConfirmed(id);
		} else if (alertId == ID_DELETE_PROCESSED) {
			final String candidates = (args != null ? String.valueOf(args) : "");

			final ArrayList<Integer> list = new ArrayList<Integer>();
			for (final String s : candidates.split("\\s*;\\s*")) {
				list.add(Integer.valueOf(s));
			}

			deleteBatchCommand(list);
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.utils.OnAlertClickInterface#onAlertNegativeClick(int)
	 */
	@Override
	public void onAlertNegativeClick(final int alertId, final String args) {
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
	public void onAlertNeutralClick(final int alertId, final String args) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onProgressUpdate(java.lang.Object[])
	 */
	@Override
	public void onUploadProgressUpdate(final Object... values) {
		if (mUploadProgress != null) {
			mUploadProgress.setTitle((CharSequence) values[0]);
			mUploadProgress.setMessage((CharSequence) values[1]);
			mUploadProgress.setProgress((Integer) values[2]);
		}
		mUploadTaskFragment.retainProgress((String) values[0], (String) values[1], (Integer) values[2]);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onExportCompleted(int)
	 */
	@Override
	public void onUploadCompleted(final int id) {
		// mark as exported
		final Session session = mDataHelper.loadSession(id);
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

			hideUploadTaskDialog();
			releaseWifiLock();

            if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_DELETE_SESSIONS, Preferences.VAL_DELETE_SESSIONS)) {
                deleteConfirmed(id);
            } else {
                deleteCommand(id);
            }
		} else if (pendingExports.size() == completedExports + failedExports) {
			Log.i(TAG, "All exports finished");

			if (failedExports > 0) {
				// at least one export failed
                try {
                    AlertDialogUtils.newInstance(ID_EXPORT_FAILED, getResources().getString(R.string.export_error_title), getResources().getString(R.string.export_error),
                            String.valueOf(id), true).show(getSupportFragmentManager(), "failed");
                } catch (IllegalStateException e) {
                    // Crashes when app has been sent to background
                    // quick fix don' display dialog when in background
                }
			} else {
				// if everything is ok, offer to delete
                if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_DELETE_SESSIONS, Preferences.VAL_DELETE_SESSIONS)) {
                    deleteBatchCommand(pendingExports);
                } else {
                    String candidates = "";
                    for (final int one : pendingExports){
                        candidates += one + ";";
                    }
                    try {
                        AlertDialogUtils.newInstance(ID_DELETE_PROCESSED, getResources().getString(R.string.delete), getResources().getString(R.string.do_you_want_to_delete_processed_sessions),
                                candidates, false).show(getSupportFragmentManager(), "failed");
                    } catch (IllegalStateException e ) {
                        // Crashes when app has been sent to background
                        // quick fix don' display dialog when in background
                    }
                }
			}

			pendingExports.clear();
			completedExports = 0;
			failedExports = 0;

			hideUploadTaskDialog();
			releaseWifiLock();

			// TODO move to onAlertNegative with ID_DELETE_PROCESSED
			reloadListFragment();
		} else {
			// we've got more exports
			showUploadTaskDialog();
		}
	}

	/**
	 *   Called when upload is only simulated
	 **/
	@Override
	public void onDryRunCompleted(final int id) {

		completedExports += 1;
		Log.i(TAG, "Session " + id + " exported");
        Log.i(TAG, "Exported " + completedExports + "/" + pendingExports.size() + " sessions");

		if (pendingExports.size() == 1) {
			Log.i(TAG, "Export simulated");

			pendingExports.clear();
			completedExports = 0;

			hideUploadTaskDialog();
			releaseWifiLock();

		} else if (pendingExports.size() == completedExports + failedExports) {
			Log.i(TAG, "All exports simulated");

			if (failedExports > 0) {
				// at least one export failed
                try {
                    AlertDialogUtils.newInstance(ID_EXPORT_FAILED,
                            getResources().getString(R.string.export_error_title), getResources().getString(R.string.export_error),
                            String.valueOf(id), true).show(getSupportFragmentManager(), "failed");
                } catch (IllegalStateException e) {
                    // Crashes when app has been sent to background
                    // quick fix don' display dialog when in background
                }
			}

			pendingExports.clear();
			completedExports = 0;
			failedExports = 0;

			hideUploadTaskDialog();
			releaseWifiLock();

			reloadListFragment();
		} else {
			// we've got more exports
			showUploadTaskDialog();
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onExportFailed(java.lang.String)
	 */
	@Override
	public void onUploadFailed(final int id, final String error) {
		Log.e(TAG, "Export session " + id + " failed");

		failedExports += 1;
		releaseWifiLock();
		hideUploadTaskDialog();

		final String errorText = (error == null || error.length() == 0) ? getResources().getString(R.string.export_error) : error;
		try {
            AlertDialogUtils.newInstance(ID_EXPORT_FAILED,
                    getResources().getString(R.string.export_error_title), errorText,
                    String.valueOf(id), true).show(getSupportFragmentManager(), "failed");
        } catch (IllegalStateException e) {
            // Crashes when app has been sent to background
            // quick fix don' display dialog when in background
        }
	}

	/**
	 * Creates or restores a progress dialogs
	 * This dialog is not shown until calling showExportDialog() explicitly
	 * @param newDialog
	 */
	private void initExportGpxTaskDialog(final boolean newDialog) {
		if (newDialog) {
			mExportGpxProgress = new ProgressDialog(this);
			mExportGpxProgress.setCancelable(false);
			mExportGpxProgress.setIndeterminate(true);

			final String defaultTitle = getResources().getString(R.string.exporting_gpx);
			final String defaultMessage = getResources().getString(R.string.please_stay_patient);
			mExportGpxProgress.setTitle(defaultTitle);
			mExportGpxProgress.setMessage(defaultMessage);

			mUploadTaskFragment.retainProgress(defaultTitle, defaultMessage, (int) mExportGpxProgress.getProgress());
		} else {
			mExportGpxProgress = new ProgressDialog(this);
			mExportGpxProgress.setCancelable(false);
			mExportGpxProgress.setIndeterminate(true);

			mExportGpxTaskFragment.restoreProgress(mExportGpxProgress);
		}
	}

	/**
	 * Opens export gpx dialog, if any
	 */
	private void showExportGpxTaskDialog() {
		if (mExportGpxProgress == null) {
			throw new IllegalArgumentException("Export progress dialog must not be null");
		}

		if (mExportGpxTaskFragment.isExecuting()) {
			mExportGpxTaskFragment.restoreProgress(mExportGpxProgress);

			if (!mExportGpxProgress.isShowing()) {
				mExportGpxProgress.show();
			}
		}
	}

	/**
	 * Creates or restores a progress dialogs
	 * This dialog is not shown until calling showExportDialog() explicitly
	 * @param newDialog
	 *
	 */
	private void initUploadTaskDialog(final boolean newDialog) {
		if (newDialog) {
			mUploadProgress = new ProgressDialog(this);
			mUploadProgress.setCancelable(false);
			mUploadProgress.setIndeterminate(true);

			final String defaultTitle = getResources().getString(R.string.preparing_export);
			final String defaultMessage = getResources().getString(R.string.please_stay_patient);
			mUploadProgress.setTitle(defaultTitle);
			mUploadProgress.setMessage(defaultMessage);

			mUploadTaskFragment.retainProgress(defaultTitle, defaultMessage, (int) mUploadProgress.getProgress());
		} else {
			mUploadProgress = new ProgressDialog(this);
			mUploadProgress.setCancelable(false);
			mUploadProgress.setIndeterminate(true);

			mUploadTaskFragment.restoreProgress(mUploadProgress);
		}
	}

	/**
	 * Opens upload dialog, if any
	 */
	private void showUploadTaskDialog() {
		if (mUploadProgress == null) {
			throw new IllegalArgumentException("Export progress dialog must not be null");
		}

		if (mUploadTaskFragment.isExecuting()) {
			mUploadTaskFragment.restoreProgress(mUploadProgress);

			if (!mUploadProgress.isShowing()) {
				mUploadProgress.show();
			}
		}
	}

	/**
	 * Closes upload dialog
	 */
	private void hideUploadTaskDialog() {
		if (mUploadProgress != null) {
			mUploadProgress.dismiss();
            }
	}

    /**
     * Closes upload dialog
     */
    private void hideGpxTaskDialog() {
        if (mExportGpxProgress != null) {
            mExportGpxProgress.dismiss();
        }
    }
	/**
	 * Prevent Wi-Fi sleep by acquiring a wifi lock
	 */
	private void acquireWifiLock() {
		if (mWifiLock == null) {
			final WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
			if (wifiManager != null) {
				mWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, WIFILOCK_NAME);
			} else {
				Log.e(TAG, "Error acquiring wifi lock");
			}
		}

		if (mWifiLock == null) {
			Log.w(TAG, "WifiLock not found. Skipping acquisition..");
			return;
		}
		if (!mWifiLock.isHeld()) {
			mWifiLock.acquire();
		} else {
			Log.i(TAG, "WifiLock is hold already. Skipping acquisition..");
		}
	}

	/**
	 * Releases previously acquired wifi lock
	 */
	private void releaseWifiLock() {
		if (mWifiLock != null && mWifiLock.isHeld()) {
			mWifiLock.release();
			mWifiLock = null;
		}
	}

	/*
	 * Fired once gpx export task has finished
	 */
	@Override
	public void onExportGpxCompleted(final int id) {
		Log.i(TAG, "GPX export completed");
        hideGpxTaskDialog();
	}

	/*
	 * Fired once gpx export task has failed
	 */
	@Override
	public void onExportGpxFailed(final int id, final String error) {
		Log.e(TAG, "GPX export failed: " + error);
        hideGpxTaskDialog();
		Toast.makeText(this, R.string.gpx_export_failed, Toast.LENGTH_LONG).show();
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.ExportGpxTask.ExportGpxTaskListener#onExportGpxProgressUpdate(java.lang.Object[])
	 */
	@Override
	public void onExportGpxProgressUpdate(final Object[] values) {
		if (mExportGpxProgress != null) {
			mExportGpxProgress.setTitle((CharSequence) values[0]);
			mExportGpxProgress.setMessage((CharSequence) values[1]);
			mExportGpxProgress.setProgress((Integer) values[2]);
		}
		mExportGpxTaskFragment.retainProgress((String) values[0], (String) values[1], (Integer) values[2]);

	}
}
