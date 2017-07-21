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

package org.openbmap.activities.settings;

import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.openbmap.Constants;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.utils.CatalogUpdater;
import org.openbmap.utils.CurrentLocationHelper;
import org.openbmap.utils.CurrentLocationHelper.LocationResult;
import org.openbmap.utils.DirectoryChooserDialog;
import org.openbmap.utils.FileUtils;
import org.openbmap.utils.MediaScanner;
import org.openbmap.utils.VacuumCleaner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Preferences activity.
 */
public class AdvancedSettingsActivity extends PreferenceActivity {

	private static final String TAG = AdvancedSettingsActivity.class.getSimpleName();

	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.advanced_preferences);

		initMapFolderControl();
		initWifiCatalogFolderControl();

		initCleanDatabaseButton();

		initHomezoneBlockingButton();

		initLocalSyncButton();
	}

	/**
	 * Blocks wifi and cell recording around current position
	 */
	private void initHomezoneBlockingButton() {
		final Preference pref = findPreference(Preferences.KEY_BLOCK_HOMEZONE);
		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				final LocationResult locationResult = new LocationResult() {
					@Override
					public void gotLocation(final Location location) {
						final String blacklistPath = AdvancedSettingsActivity.this.getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.BLACKLIST_SUBDIR;
						final String filename = blacklistPath + File.separator + Constants.DEFAULT_LOCATION_BLOCK_FILE;
						final String blocker = String.format("<ignorelist>"
								+ "<location comment=\"homezone\">"
								+ "<latitude>%s</latitude>"
								+ "<longitude>%s</longitude>"
								+ "<radius>550</radius>"
								+ "</location>"
								+ "</ignorelist>", location.getLatitude(), location.getLongitude());

						final File folder = new File(filename.substring(1, filename.lastIndexOf(File.separator)));
						boolean folderAccessible = false;
						if (folder.exists() && folder.canWrite()) {
							folderAccessible = true;
						}

						if (!folder.exists()) {
							Log.i(TAG, "Folder missing: create " + folder.getAbsolutePath());
							folderAccessible = folder.mkdirs();
						}

						if (folderAccessible) {
							try {
								final File file = new File(filename);
								final BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
								bw.append(blocker);
								bw.close();
								Log.i(TAG, "Created default location blacklist");
                                AdvancedSettingsActivity.this.runOnUiThread(new Runnable() {
                                    public void run() {
                                        Toast.makeText(AdvancedSettingsActivity.this, getResources().getString(R.string.location_blacklist_saved), Toast.LENGTH_LONG).show();
                                    }
                                });
								new MediaScanner(AdvancedSettingsActivity.this, folder);
							} catch (final IOException e) {
								Log.e(TAG, "Error writing blacklist");
							}
						} else {
							Log.e(TAG, "Folder not accessible: can't write blacklist");
                            AdvancedSettingsActivity.this.runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(AdvancedSettingsActivity.this, getResources().getString(R.string.error_writing_location_blacklist), Toast.LENGTH_LONG).show();
                                }
                            });
						}
					}
				};

				final CurrentLocationHelper myLocation = new CurrentLocationHelper();
				myLocation.getLocation(AdvancedSettingsActivity.this, locationResult);

				return true;
			}
		});
	}

	/**
	 * Initializes data directory preference.
	 */
	private void initMapFolderControl() {
		final Preference button = findPreference(Preferences.KEY_MAP_FOLDER);
		button.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			private String mChosenDir = FileUtils.getMapFolder(AdvancedSettingsActivity.this).getAbsolutePath();
			private boolean mNewFolderEnabled = false;

			@Override
			public boolean onPreferenceClick(final Preference arg0) {

				final DirectoryChooserDialog directoryChooserDialog =
						new DirectoryChooserDialog(AdvancedSettingsActivity.this, new DirectoryChooserDialog.ChosenDirectoryListener() {
							@Override
							public void onChosenDir(final String chosenDir) {
								mChosenDir = chosenDir;
								final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(AdvancedSettingsActivity.this);
								settings.edit().putString(Preferences.KEY_MAP_FOLDER, chosenDir).apply();
								//Toast.makeText(SettingsActivity.this, chosenDir, Toast.LENGTH_LONG).show();
							}
						});

				directoryChooserDialog.setNewFolderEnabled(mNewFolderEnabled);
				directoryChooserDialog.chooseDirectory(mChosenDir);
				mNewFolderEnabled = !mNewFolderEnabled;

				return true;
			}
		});
	}

	/**
	 * Updates wifi catalog with new local wifis
	 */
	private void initLocalSyncButton() {
		final Preference pref = findPreference(Preferences.KEY_UPDATE_CATALOG);
		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				Toast.makeText(AdvancedSettingsActivity.this, R.string.synchronizing, Toast.LENGTH_LONG).show();
				new CatalogUpdater(AdvancedSettingsActivity.this).execute(new Void[]{null});
				return true;
			}
		});
	}

	/**
	 * Initializes wifi catalog folder preference.
	 */
	private void initWifiCatalogFolderControl() {
		final Preference button = findPreference(Preferences.KEY_WIFI_CATALOG_FOLDER);
		button.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			private String mChosenDir = FileUtils.getCatalogFolder(AdvancedSettingsActivity.this).getAbsolutePath();

			private boolean mNewFolderEnabled = false;

			@Override
			public boolean onPreferenceClick(final Preference arg0) {

				// Create DirectoryChooserDialog and register a callback
				final DirectoryChooserDialog directoryChooserDialog =
						new DirectoryChooserDialog(AdvancedSettingsActivity.this, new DirectoryChooserDialog.ChosenDirectoryListener() {
							@Override
							public void onChosenDir(final String chosenDir) {
								mChosenDir = chosenDir;
								final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(AdvancedSettingsActivity.this);
								settings.edit().putString(Preferences.KEY_WIFI_CATALOG_FOLDER, chosenDir).apply();
								//Toast.makeText(SettingsActivity.this, chosenDir, Toast.LENGTH_LONG).show();
							}
						});

				directoryChooserDialog.setNewFolderEnabled(mNewFolderEnabled);
				directoryChooserDialog.chooseDirectory(mChosenDir);
				mNewFolderEnabled = !mNewFolderEnabled;

				return true;
			}
		});
	}


	/**
	 * Performs VACCUM ANALYZE on database
	 */
	private void initCleanDatabaseButton() {
		final Preference pref = findPreference(Preferences.KEY_CLEAN_DATABASE);
		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				Toast.makeText(AdvancedSettingsActivity.this, getString(R.string.vacuuming), Toast.LENGTH_LONG).show();
				new VacuumCleaner(AdvancedSettingsActivity.this).execute(new Void[]{null});
				return true;
			}
		});
	}


}
