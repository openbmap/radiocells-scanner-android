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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.model.Session;
import org.openbmap.soapclient.ExportManager;
import org.openbmap.soapclient.ExportManager.ExportManagerListener;
import org.openbmap.soapclient.ServerValidation;
import org.openbmap.soapclient.ServerValidation.ServerReply;
import org.openbmap.utils.FileHelper;
import org.openbmap.utils.TempFileHelper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * Parent screen for hosting main screen
 */
public class SessionActivity extends FragmentActivity implements SessionListFragment.SessionFragementListener, ExportManagerListener, ServerReply
{
	// TODO: clarify whether we need two different export task listeners, one for cells + one for wifis
	private static final String TAG = SessionActivity.class.getSimpleName();

	private DataHelper mDataHelper;

	private WifiLock wifiLock;

	private int	mPendingExport;

	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// setup data connections
		mDataHelper = new DataHelper(this);

		initUi();
	}

	@Override
	public final void onResume() {
		super.onResume();

		// create wifi lock (will be acquired for version check/upload)
		WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "UploadLock");

		// force an fragment refresh
		reloadFragment();
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

		mPendingExport = id;

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

			new AlertDialog.Builder(this)
			.setTitle(R.string.confirmation)
			.setMessage(R.string.question_delete_session)
			.setCancelable(true)
			.setIcon(android.R.drawable.ic_dialog_info)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int which) {
					deleteCommand(id);
					dialog.dismiss();
					return;
				}
			})
			.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int which) {
					dialog.cancel();
					return;
				}
			}).create().show();
		} else {
			Log.i(TAG, "Good: Session hasn't yet been uploaded");
		}

		// checks credentials available?
		String user = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_CREDENTIALS_USER, null);
		String password = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);

		if (!isValidLogin(user, password)) {
			new AlertDialog.Builder(this)
			.setTitle(R.string.user_or_password_missing)
			.setMessage(R.string.question_enter_user)
			.setCancelable(true)
			.setIcon(android.R.drawable.ic_dialog_info)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int which) {
					startActivity(new Intent(getBaseContext(), SettingsActivity.class));
					dialog.dismiss();
					return;
				}
			})
			.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int which) {
					dialog.cancel();
					return;
				}
			}).create().show();
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

		// invalidate all active session
		mDataHelper.invalidateActiveSessions();
		// Create a new session and activate it
		// Then start HostActivity. HostActivity onStart() and onResume() check active session
		// and starts services for active session
		Session active = new Session();
		active.setCreatedAt(System.currentTimeMillis());
		active.setLastUpdated(System.currentTimeMillis());
		active.setDescription("No description yet");
		active.isActive(true);
		// id can only be set after session has been stored to database.
		Uri result = mDataHelper.storeSession(active);
		active.setId(result);

		startActivity(new Intent(this, HostActivity.class));
	}

	/* (non-Javadoc)
	 * @see org.openbmap.activity.SessionListFragment.SessionFragementListener#resumeSession(long)
	 */
	@Override
	public final void resumeCommand(final int id) {
		// TODO: check whether we need a INTENT_START_SERVICE here
		Session resume = mDataHelper.loadSession(id);

		if (resume == null) {
			Log.e(TAG, "Couldn't load session " + id);
			return;
		}

		resume.isActive(true);
		mDataHelper.storeSession(resume, true);

		startActivity(new Intent(this, HostActivity.class));
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

		new AlertDialog.Builder(this)
		.setTitle(R.string.confirmation)
		.setMessage(R.string.question_delete_all_sessions)
		.setCancelable(true)
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				dialog.dismiss();
			}
		})
		.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				dialog.cancel();
				return;
			}
		}).create().show();

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
	public final void reloadFragment() {
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

		// as we have the session now as xml files, we can delete it from database
		new AlertDialog.Builder(this)
		.setTitle(R.string.session_uploaded)
		.setMessage(R.string.do_you_want_to_delete_this_session)
		.setCancelable(true)
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				deleteCommand(id);
				dialog.dismiss();
			}
		})
		.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				dialog.cancel();
			}
		}).create().show();
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

		new AlertDialog.Builder(this)
		.setTitle(android.R.string.dialog_alert_title)
		.setMessage("Export error!" + error)
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setNeutralButton(android.R.string.ok, new OnClickListener() {
			@Override
			public void onClick(final DialogInterface alert, final int which) {
				alert.dismiss();						
			}
		})
		.show();
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
				+ PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR) + File.separator;
		boolean exportGpx = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_GPS_SAVE_COMPLETE_TRACK, false);

		boolean skipUpload = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_SKIP_UPLOAD, Preferences.VAL_SKIP_UPLOAD);
		boolean skipDelete = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_SKIP_DELETE, Preferences.VAL_SKIP_DELETE);

		// and start export and upload..
		ExportManager e = new ExportManager(this, this, mPendingExport, targetPath, user, password);
		e.setExportCells(true);
		e.setExportWifis(true);
		e.setExportGpx(exportGpx);

		// debug settings
		e.setSkipUpload(skipUpload);
		e.setSkipDelete(skipDelete);

		SimpleDateFormat date = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
		e.setGpxPath(targetPath);
		e.setGpxFilename(date.format(new Date(System.currentTimeMillis())) + ".gpx");
		e.execute((Void[]) null);

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
		new AlertDialog.Builder(this)
		.setTitle(getResources().getString(R.string.error_version_check_title))
		.setMessage(getResources().getString(R.string.error_version_check_body))
		.setCancelable(false)
		.setIcon(android.R.drawable.ic_dialog_info)
		.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				dialog.dismiss();
				repairWifiConnection();
				proceedAfterServerValidation();
			}

		})
		.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				dialog.cancel();
				Toast.makeText(SessionActivity.this, "Upload aborted..", Toast.LENGTH_LONG).show();
				return;
			}
		}).create().show();

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
}
