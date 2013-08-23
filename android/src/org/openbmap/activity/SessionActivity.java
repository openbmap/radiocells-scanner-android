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
import org.openbmap.utils.FileHelper;
import org.openbmap.utils.TempFileHelper;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
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
public class SessionActivity extends FragmentActivity implements SessionListFragment.SessionFragementListener, ExportManagerListener
{
	// TODO: clarify whether we need two different export task listeners, one for cells + one for wifis
	private static final String TAG = SessionActivity.class.getSimpleName();

	private DataHelper mDataHelper;

	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// setup data connections
		mDataHelper = new DataHelper(this);

		initControls();
	}

	/**
	 * 
	 */
	private void initControls() {
		setContentView(R.layout.session);
		SessionListFragment detailsFragment = (SessionListFragment) getSupportFragmentManager().findFragmentById(R.id.sessionListFragment);	
		detailsFragment.setOnSessionSelectedListener(this);
	}

	@Override
	public final void onResume() {
		super.onResume();
		// force an fragment refresh
		reloadFragment();
	}

	/**
	 * Exports session to xml with AsyncTasks.
	 * Once exported, {@link onExportCompleted } is called
	 * @param id
	 * 		session id
	 */
	public final void uploadSession(final int id) {

		String user = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_CREDENTIALS_USER, null);
		String password = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);

		if (user == null || password == null) {
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
				}
			})
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int which) {
					dialog.cancel();
				}
			}).create().show();

			return;
		}

		if (!FileHelper.isSdCardMountedWritable()) {
			Log.e(TAG, "SD card not writable");
			Toast.makeText(this.getBaseContext(), R.string.warning_sd_not_writable, Toast.LENGTH_SHORT).show();
			return;
		}

		String targetPath = Environment.getExternalStorageDirectory().getPath()
				+ PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR) + File.separator;
		boolean exportGpx = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_GPS_SAVE_COMPLETE_TRACK, false);
		
		boolean skipUpload = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_SKIP_UPLOAD, Preferences.VAL_SKIP_UPLOAD);
		boolean skipDelete = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_SKIP_DELETE, Preferences.VAL_SKIP_DELETE);
		
		ExportManager e = new ExportManager(this, this, id, targetPath, user, password);
		e.setExportCells(true);
		e.setExportWifis(true);
		e.setExportGpx(exportGpx);
		
		// debug settings
		e.setSkipUpload(skipUpload);
		e.setSkipDelete(skipDelete);

		SimpleDateFormat date = new SimpleDateFormat("yyyyMMddhhmmss", Locale.US);
		e.setGpxPath(targetPath);
		e.setGpxFilename(date.format(new Date(System.currentTimeMillis())) + ".gpx");
		e.execute((Void[]) null);

		updateUI();
	}

	/* 
	 * Creates a new session record and starts HostActivity ("tracking" mode)
	 */
	@Override
	public final void startSession() {

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
	public final void resumeSession(final int id) {
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
	public final void stopSession(final int id) {
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
	public final void deleteSession(final int id) {
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
	public final void deleteAllSessions() {

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
		Log.d(TAG, "OptionItemSelected, handled by SessionActivity");
		switch (item.getItemId()) {
			case R.id.menu_create_new_session:
				startSession();
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
		// mark as has been exported
		Session session = mDataHelper.loadSession(id);
		session.hasBeenExported(true);
		session.isActive(false);
		mDataHelper.storeSession(session, false);

		// as we have the session now as xml files, we can delete it from database
		new AlertDialog.Builder(this)
		.setTitle("Session has been uploaded")
		.setMessage("Do you want to delete current session?")
		.setCancelable(true)
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				deleteSession(id);
				dialog.dismiss();
			}
		})
		.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
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

}
