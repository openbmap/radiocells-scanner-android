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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.utils.CurrentLocationHelper;
import org.openbmap.utils.CurrentLocationHelper.LocationResult;
import org.openbmap.utils.DirectoryChooserDialog;
import org.openbmap.utils.FileUtils;
import org.openbmap.utils.LegacyDownloader;
import org.openbmap.utils.LegacyDownloader.LegacyDownloadListener;
import org.openbmap.utils.MediaScanner;
import org.openbmap.utils.VacuumCleaner;
import org.openbmap.utils.WifiCatalogUpdater;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/**
 * Preferences activity.
 */
public class SettingsActivity extends PreferenceActivity implements LegacyDownloadListener {

	private static final String TAG = SettingsActivity.class.getSimpleName();

	private DownloadManager mDownloadManager;

	private BroadcastReceiver mReceiver = null; 

	@SuppressLint("NewApi")
	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preferences);

		initMapFolderControl();
		initWifiCatalogFolderControl();

		// with versions >= GINGERBREAD use download manager
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			initDownloadManager();
		}

		initWifiCatalogDownloadControl();
		initActiveWifiCatalogControl(PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_WIFI_CATALOG_FOLDER,
				this.getExternalFilesDir(null).getAbsolutePath() + Preferences.WIFI_CATALOG_SUBDIR));

		initMapDownloadControl();
		initActiveMapControl(PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_MAP_FOLDER,
				this.getExternalFilesDir(null).getAbsolutePath() + Preferences.MAPS_SUBDIR));

		initGpsSystemSettingsControl();
		initCleanDatabaseControl();

		initUpdateWifiCatalogControl();
		initHomezoneBlockingControl();

		initGpsLogIntervalControl();
	}

	/**
	 * Initialises download manager for GINGERBREAD and newer
	 */
	@SuppressLint("NewApi")
	private void initDownloadManager() {

		mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

		mReceiver = new BroadcastReceiver() {
			@SuppressLint("NewApi")
			@Override
			public void onReceive(final Context context, final Intent intent) {
				final String action = intent.getAction();
				if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
					final long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
					final Query query = new Query();
					query.setFilterById(downloadId);
					final Cursor c = mDownloadManager.query(query);
					if (c.moveToFirst()) {
						final int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
						if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
							// we're not checking download id here, that is done in handleDownloads
							final String uriString = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
							handleDownloads(uriString);
						}
					}
				}
			}
		};

		registerReceiver(mReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
	}

	@Override
	protected final void onDestroy() {
		try {
			if (mReceiver != null) {
				Log.i(TAG, "Unregistering broadcast receivers");
				unregisterReceiver(mReceiver);
			}
		} catch (final IllegalArgumentException e) {
			// do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android}
			super.onDestroy();
			return;
		}

		super.onDestroy();
	}

	/**
	 * Initializes gps system preference.
	 * OnPreferenceClick system gps settings are displayed.
	 */
	private void initGpsSystemSettingsControl() {
		final Preference pref = findPreference(Preferences.KEY_GPS_OSSETTINGS);
		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
				return true;
			}
		});
	}

	/**
	 * Blocks wifi and cell recording around current position
	 */
	private void initHomezoneBlockingControl() {
		final Preference pref = findPreference(org.openbmap.Preferences.KEY_BLOCK_HOMEZONE);
		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				final LocationResult locationResult = new LocationResult(){
					@Override
					public void gotLocation(final Location location){
						final String blacklistPath = SettingsActivity.this.getExternalFilesDir(null).getAbsolutePath() + Preferences.BLACKLIST_SUBDIR;
						final String filename = blacklistPath + File.separator + RadioBeacon.DEFAULT_LOCATION_BLOCK_FILE;
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
								Toast.makeText(SettingsActivity.this, getResources().getString(R.string.location_blacklist_saved), Toast.LENGTH_LONG).show();
								new MediaScanner(SettingsActivity.this, folder);
							} catch (final IOException e) {
								Log.e(TAG, "Error writing blacklist");
							} 
						} else {
							Log.e(TAG, "Folder not accessible: can't write blacklist");
							Toast.makeText(SettingsActivity.this, getResources().getString(R.string.error_writing_location_blacklist), Toast.LENGTH_LONG).show();
						}
					}
				};

				final CurrentLocationHelper myLocation = new CurrentLocationHelper();
				myLocation.getLocation(SettingsActivity.this, locationResult);

				return true;
			}
		});
	}

	/**
	 * Initializes gps logging interval.
	 */
	private void initGpsLogIntervalControl() {
		// Update GPS logging interval summary to the current value
		final Preference pref = findPreference(org.openbmap.Preferences.KEY_GPS_LOGGING_INTERVAL);
		pref.setSummary(
				PreferenceManager.getDefaultSharedPreferences(this).getString(org.openbmap.Preferences.KEY_GPS_LOGGING_INTERVAL, org.openbmap.Preferences.VAL_GPS_LOGGING_INTERVAL)
				+ " " + getResources().getString(R.string.prefs_gps_logging_interval_seconds)
				+ ". " + getResources().getString(R.string.prefs_gps_logging_interval_summary));
		pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(final Preference preference, final Object newValue) {
				// Set summary with the interval and "seconds"
				preference.setSummary(newValue
						+ " " + getResources().getString(R.string.prefs_gps_logging_interval_seconds)
						+ ". " + getResources().getString(R.string.prefs_gps_logging_interval_summary));
				return true;
			}
		});
	}

	/**
	 * Initializes data directory preference.
	 * @return EditTextPreference with data directory.
	 */
	/*
	private EditTextPreference initDataFolderControl() {
		// External storage directory
		EditTextPreference pref = (EditTextPreference) findPreference(Preferences.KEY_DATA_FOLDER);
		pref.setSummary(PreferenceManager.getDefaultSharedPreferences(this).getString(org.openbmap.Preferences.KEY_DATA_FOLDER, org.openbmap.Preferences.VAL_DATA_FOLDER));
		pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(final Preference preference, final Object newValue) {
				String pathName = "";

				// Ensure there is always a leading slash
				if (!((String) newValue).startsWith(File.separator)) {
					pathName = File.separator + (String) newValue;
				} else {
					pathName = (String) newValue;
				}

				// try to create directory
				File folder = new File(Environment.getExternalStorageDirectory() + pathName);
				boolean success = true;
				if (!folder.exists()) {
					success = folder.mkdirs();
				}
				if (!success) {
					Toast.makeText(getBaseContext(), R.string.error_create_directory_failed + pathName, Toast.LENGTH_LONG).show();
					return false;
				}

				// Set summary with the directory value
				preference.setSummary((String) pathName);

				// Re-populate available maps
				//initActiveMap((String) pathName); 

				return true;
			}
		});
		return pref;
	}*/

	/**
	 * Initializes data directory preference.
	 *
	 * @return EditTextPreference with data directory.
	 */
	/* NEW VERSION
    private void initDataFolderControl() {
        Preference button = (Preference) findPreference(Preferences.KEY_DATA_FOLDER);
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            private String mChosenDir = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getString(
            		Preferences.KEY_DATA_FOLDER,
                    SettingsActivity.this.getExternalFilesDir(null).getAbsolutePath());
            private boolean mNewFolderEnabled = true;

            @Override
            public boolean onPreferenceClick(Preference preference) {

                // Create DirectoryChooserDialog and register a callback
                DirectoryChooserDialog directoryChooserDialog =
                        new DirectoryChooserDialog(SettingsActivity.this, new DirectoryChooserDialog.ChosenDirectoryListener() {
                                    @Override
                                    public void onChosenDir(String chosenDir) {
                                        mChosenDir = chosenDir;

                                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
                                        settings.edit().putString(Preferences.KEY_DATA_FOLDER, chosenDir).commit();

                                        Toast.makeText(SettingsActivity.this, chosenDir, Toast.LENGTH_LONG).show();
                                    }
                                });
                // Toggle new folder button enabling
                directoryChooserDialog.setNewFolderEnabled(mNewFolderEnabled);
                // Load directory chooser dialog for initial 'mChosenDir' directory.
                // The registered callback will be called upon final directory selection.
                directoryChooserDialog.chooseDirectory(mChosenDir);
                mNewFolderEnabled = !mNewFolderEnabled;

                // Set summary with the directory value
				preference.setSummary((String) mChosenDir);
                return true;
            }
        });
    }
	 */

	/**
	 * Initializes data directory preference.
	 * @return EditTextPreference with data directory.
	 */
	private void initMapFolderControl() {
		final Preference button = (Preference) findPreference(Preferences.KEY_MAP_FOLDER);
		button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			private String mChosenDir = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getString(Preferences.KEY_MAP_FOLDER,
					SettingsActivity.this.getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.MAPS_SUBDIR);

			private boolean mNewFolderEnabled = false;

			@Override
			public boolean onPreferenceClick(final Preference arg0) {

				final DirectoryChooserDialog directoryChooserDialog =
						new DirectoryChooserDialog(SettingsActivity.this, new DirectoryChooserDialog.ChosenDirectoryListener() {
							@Override
							public void onChosenDir(final String chosenDir) {
								mChosenDir = chosenDir;
								final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
								settings.edit().putString(Preferences.KEY_MAP_FOLDER, chosenDir).commit();
								initActiveMapControl(chosenDir);
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
	 * Populates the download list with links to mapsforge downloads.
	 */
	private void initMapDownloadControl() {
		String[] entries;
		String[] values;

		// No map found, populate values with just the default entry.
		entries = getResources().getStringArray(R.array.listDisplayWord);
		values = getResources().getStringArray(R.array.listReturnValue);

		final ListPreference lf = (ListPreference) findPreference(Preferences.KEY_DOWNLOAD_MAP);
		lf.setEntries(entries);
		lf.setEntryValues(values);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			// use download manager for versions >= GINGERBREAD
			lf.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@SuppressLint("NewApi")
				public boolean onPreferenceChange(final Preference preference, final Object newValue) {

					// try to create directory
					final File folder = new File(PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getString(
							Preferences.KEY_MAP_FOLDER,
							SettingsActivity.this.getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.MAPS_SUBDIR));

					boolean folderAccessible = false;
					if (folder.exists() && folder.canWrite()) {
						folderAccessible = true;
					}

					if (!folder.exists()) {
						folderAccessible = folder.mkdirs();
					}
					if (folderAccessible) {
						final String filename = newValue.toString().substring(newValue.toString().lastIndexOf('/') + 1);

						final File target = new File(folder.getAbsolutePath() + File.separator + filename);
						if (target.exists()) {
							Log.i(TAG, "Map file " + filename + " already exists. Overwriting..");
							target.delete();
						}

						try {
							// try to download to target. If target isn't below Environment.getExternalStorageDirectory(),
							// e.g. on second SD card a security exception is thrown
							final Request request = new Request(Uri.parse(newValue.toString()));
							request.setDestinationUri(Uri.fromFile(target));
							final long mapDownloadId = mDownloadManager.enqueue(request);
						} catch (final SecurityException sec) {
							// download to temp dir and try to move to target later
							Log.w(TAG, "Security exception, can't write to " + target + ", using " + SettingsActivity.this.getExternalCacheDir());
							final File tempFile = new File(SettingsActivity.this.getExternalCacheDir() + File.separator + filename);

							final Request request = new Request(Uri.parse(newValue.toString()));
							request.setDestinationUri(Uri.fromFile(tempFile));
							mDownloadManager.enqueue(request);
						}
					} else {
						Toast.makeText(preference.getContext(), R.string.error_save_file_failed, Toast.LENGTH_SHORT).show();
					}

					return false;
				}
			});
		} else {
			// use home-brew download manager for version < GINGERBREAD
			lf.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				public boolean onPreferenceChange(final Preference preference, final Object newValue) {

					try {
						// try to create directory
						final File folder = new File(PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getString(
								Preferences.KEY_MAP_FOLDER,
								SettingsActivity.this.getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.MAPS_SUBDIR)
								);

						boolean folderAccessible = false;
						if (folder.exists() && folder.canWrite()) {
							folderAccessible = true;
						}

						if (!folder.exists()) {
							folderAccessible = folder.mkdirs();
						}
						if (folderAccessible) {
							final URL url = new URL(newValue.toString());
							final String filename = newValue.toString().substring(newValue.toString().lastIndexOf('/') + 1);

							Log.d(TAG, "Saving " + url + " at " + folder.getAbsolutePath() + '/' + filename);
							final LegacyDownloader task = (LegacyDownloader) new LegacyDownloader(preference.getContext()).execute(
									url, folder.getAbsolutePath() + File.separator + filename);

							// Callback to refresh maps preference on completion
							task.setListener((SettingsActivity) preference.getContext());

						} else {
							Toast.makeText(preference.getContext(), R.string.error_save_file_failed, Toast.LENGTH_SHORT).show();
						}	
					} catch (final MalformedURLException e) {
						Log.e(TAG, "Malformed download url: " + newValue.toString());
					}
					return false;
				}
			});
		}
	}

	/**
	 * Populates the active map list preference by scanning available map files.
	 * @param mapFolder Folder for maps
	 */
	private void initActiveMapControl(final String mapFolder) {
		String[] entries;
		String[] values;

		Log.d(TAG, "Scanning for map files");

		// Check for presence of maps directory
		final File mapsDir = new File(mapFolder);

		// List each map file
		if (mapsDir.exists() && mapsDir.canRead()) {
			final String[] mapFiles = mapsDir.list(new FilenameFilter() {
				@Override
				public boolean accept(final File dir, final String filename) {
					return filename.endsWith(org.openbmap.Preferences.MAP_FILE_EXTENSION);
				}
			});

			// Create array of values for each map file + one for not selected
			entries = new String[mapFiles.length + 1];
			values = new String[mapFiles.length + 1];

			// Create default / none entry
			entries[0] = getResources().getString(R.string.prefs_map_none);
			values[0] = org.openbmap.Preferences.VAL_MAP_NONE;

			for (int i = 0; i < mapFiles.length; i++) {
				entries[i + 1] = mapFiles[i].substring(0, mapFiles[i].length() - org.openbmap.Preferences.MAP_FILE_EXTENSION.length());
				values[i + 1] = mapFiles[i];
			}
		} else {
			// No map found, populate values with just the default entry.
			entries = new String[] {getResources().getString(R.string.prefs_map_none)};
			values = new String[] {org.openbmap.Preferences.VAL_MAP_NONE};
		}

		final ListPreference lf = (ListPreference) findPreference(Preferences.KEY_MAP_FILE);
		lf.setEntries(entries);
		lf.setEntryValues(values);
	}

	/**
	 * Changes map preference item to given filename.
	 * Helper method to activate map after successful download
	 * @param absoluteFile absolute filename (including path)
	 */
	private void activateMap(final String absoluteFile) {
		final ListPreference lf = (ListPreference) findPreference(org.openbmap.Preferences.KEY_MAP_FILE);

		// get filename
		final String[] filenameArray = absoluteFile.split("\\/");
		final String file = filenameArray[filenameArray.length - 1];

		final CharSequence[] values = lf.getEntryValues();
		for (int i = 0; i < values.length; i++) {
			if (file.equals(values[i].toString())) {
				lf.setValueIndex(i);
			}
		}
	}

	/**
	 * Initializes wifi catalog folder preference.
	 * @return EditTextPreference with data directory.
	 */
	private void initWifiCatalogFolderControl() {
		final Preference button = (Preference) findPreference(Preferences.KEY_WIFI_CATALOG_FOLDER);
		button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			private String mChosenDir = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getString(Preferences.KEY_WIFI_CATALOG_FOLDER,
					SettingsActivity.this.getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.WIFI_CATALOG_SUBDIR);

			private boolean mNewFolderEnabled = false;

			@Override
			public boolean onPreferenceClick(final Preference arg0) {

				// Create DirectoryChooserDialog and register a callback
				final DirectoryChooserDialog directoryChooserDialog =
						new DirectoryChooserDialog(SettingsActivity.this, new DirectoryChooserDialog.ChosenDirectoryListener() {
							@Override
							public void onChosenDir(final String chosenDir) {
								mChosenDir = chosenDir;
								final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
								settings.edit().putString(Preferences.KEY_WIFI_CATALOG_FOLDER, chosenDir).commit();
								initActiveWifiCatalogControl(chosenDir);
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
	 * Initializes wifi catalog source preference
	 */
	@SuppressLint("NewApi")
	private void initWifiCatalogDownloadControl() {
		final Preference pref = findPreference(Preferences.KEY_DOWNLOAD_WIFI_CATALOG);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			// use download manager for versions >= GINGERBREAD
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(final Preference preference) {
					// try to create directory		
					final File folder = new File(PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getString(
							Preferences.KEY_WIFI_CATALOG_FOLDER,
							SettingsActivity.this.getExternalFilesDir(null).getAbsolutePath() + Preferences.WIFI_CATALOG_SUBDIR));

					boolean folderAccessible = false;
					if (folder.exists() && folder.canWrite()) {
						folderAccessible = true;
					}

					if (!folder.exists()) {
						folderAccessible = folder.mkdirs();
					}
					if (folderAccessible) {
						final File target = new File(folder.getAbsolutePath() + File.separator + Preferences.WIFI_CATALOG_FILE);
						if (target.exists()) {
							Log.i(TAG, "Catalog file already exists. Overwriting..");
							target.delete();
						}

						try {
							// try to download to target. If target isn't below Environment.getExternalStorageDirectory(),
							// e.g. on second SD card a security exception is thrown
							final Request request = new Request(
									Uri.parse(Preferences.WIFI_CATALOG_DOWNLOAD_URL));
							request.setDestinationUri(Uri.fromFile(target));
							final long catalogDownloadId = mDownloadManager.enqueue(request);
						} catch (final SecurityException sec) {
							// download to temp dir and try to move to target later
							Log.w(TAG, "Security exception, can't write to " + target + ", using " + SettingsActivity.this.getExternalCacheDir() 
									+ File.separator + Preferences.WIFI_CATALOG_FILE);
							final File tempFile = new File(SettingsActivity.this.getExternalCacheDir() + File.separator + Preferences.WIFI_CATALOG_FILE);
							final Request request = new Request(
									Uri.parse(Preferences.WIFI_CATALOG_DOWNLOAD_URL));
							request.setDestinationUri(Uri.fromFile(tempFile));
							mDownloadManager.enqueue(request);
						}
					} else {
						Toast.makeText(preference.getContext(), R.string.error_save_file_failed, Toast.LENGTH_SHORT).show();
					}
					return true;
				}
			});
		} else {
			// use home-brew download manager for version < GINGERBREAD
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(final Preference preference) {
					try {
						// try to create directory
						final File folder = new File(
								PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(
										Preferences.KEY_WIFI_CATALOG_FOLDER,
										SettingsActivity.this.getExternalFilesDir(null).getAbsolutePath() + Preferences.WIFI_CATALOG_SUBDIR
										));

						boolean folderAccessible = false;
						if (folder.exists() && folder.canWrite()) {
							folderAccessible = true;
						}

						if (!folder.exists()) {
							folderAccessible = folder.mkdirs();
						}
						if (folderAccessible) {
							final URL url = new URL(Preferences.WIFI_CATALOG_DOWNLOAD_URL);
							final String filename = Preferences.WIFI_CATALOG_FILE;

							final LegacyDownloader task = (LegacyDownloader) new LegacyDownloader(preference.getContext()).execute(
									url, folder.getAbsolutePath() + File.separator + filename);

							// Callback to refresh maps preference on completion
							task.setListener((SettingsActivity) preference.getContext());

						} else {
							Toast.makeText(preference.getContext(), R.string.error_save_file_failed, Toast.LENGTH_SHORT).show();
						}	
					} catch (final MalformedURLException e) {
						Log.e(TAG, "Malformed download url: " + Preferences.WIFI_CATALOG_DOWNLOAD_URL);
					}
					return true;
				}
			});
		}
	}

	/**
	 * Populates the wifi catalog list preference by scanning catalog folder.
	 * @param catalogFolder Root folder for WIFI_CATALOG_SUBDIR
	 */
	private void initActiveWifiCatalogControl(final String catalogFolder) {

		String[] entries;
		String[] values;

		// Check for presence of database directory
		final File folder = new File(catalogFolder);

		if (folder.exists() && folder.canRead()) {
			// List each map file
			final String[] dbFiles = folder.list(new FilenameFilter() {
				@Override
				public boolean accept(final File dir, final String filename) {
					return filename.endsWith(
							org.openbmap.Preferences.WIFI_CATALOG_FILE_EXTENSION);
				}
			});

			// Create array of values for each map file + one for not selected
			entries = new String[dbFiles.length + 1];
			values = new String[dbFiles.length + 1];

			// Create default / none entry
			entries[0] = getResources().getString(R.string.prefs_map_none);
			values[0] = org.openbmap.Preferences.VAL_WIFI_CATALOG_NONE;

			for (int i = 0; i < dbFiles.length; i++) {
				entries[i + 1] = dbFiles[i].substring(0, dbFiles[i].length() - org.openbmap.Preferences.WIFI_CATALOG_FILE_EXTENSION.length());
				values[i + 1] = dbFiles[i];
			}
		} else {
			// No wifi catalog found, populate values with just the default entry.
			entries = new String[] {getResources().getString(R.string.prefs_map_none)};
			values = new String[] {org.openbmap.Preferences.VAL_WIFI_CATALOG_NONE};
		}

		final ListPreference lf = (ListPreference) findPreference(org.openbmap.Preferences.KEY_WIFI_CATALOG_FILE);
		lf.setEntries(entries);
		lf.setEntryValues(values);
	}

	/**
	 * Changes catalog preference item to given filename.
	 * Helper method to activate wifi catalog following successful download
	 * @param absoluteFile absolute filename (including path)
	 */
	private void activateWifiCatalog(final String absoluteFile) {
		final ListPreference lf = (ListPreference) findPreference(org.openbmap.Preferences.KEY_WIFI_CATALOG_FILE);

		// get filename
		final String[] filenameArray = absoluteFile.split("\\/");
		final String file = filenameArray[filenameArray.length - 1];

		final CharSequence[] values = lf.getEntryValues();
		for (int i = 0; i < values.length; i++) {
			if (file.equals(values[i].toString())) {
				lf.setValueIndex(i);
			}
		}
	}

	/**
	 * Performs VACCUM ANALYZE on database
	 */
	private void initCleanDatabaseControl() {
		final Preference pref = findPreference(Preferences.KEY_CLEAN_DATABASE);
		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				new VacuumCleaner(SettingsActivity.this).execute(new Void[]{null});
				return true;
			}
		});
	}

	/**
	 * Updates wifi catalog with new local wifis
	 */
	private void initUpdateWifiCatalogControl() {
		final Preference pref = findPreference(Preferences.KEY_UPDATE_CATALOG);
		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				Toast.makeText(SettingsActivity.this, R.string.synchronizing, Toast.LENGTH_LONG).show();
				new WifiCatalogUpdater(SettingsActivity.this).execute(new Void[]{null});
				return true;
			}
		});
	}

	/**
	 * Selects downloaded file either as wifi catalog / active map (based on file extension).
	 * @param file
	 */
	public final void handleDownloads(String file) {
		// get current file extension
		final String[] filenameArray = file.split("\\.");
		final String extension = "." + filenameArray[filenameArray.length - 1];

		// TODO verify on newer Android versions (>4.2)
		// replace prefix file:// in filename string
		file = file.replace("file://", "");

		if (extension.equals(org.openbmap.Preferences.MAP_FILE_EXTENSION)) {
			final String mapFolder = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_MAP_FOLDER,
					SettingsActivity.this.getExternalFilesDir(null).getAbsolutePath() + Preferences.MAPS_SUBDIR);
			if (file.indexOf(SettingsActivity.this.getExternalCacheDir().getPath()) > - 1 ) {
				// file has been downloaded to cache folder, so move..
				file = moveToFolder(file, mapFolder); 
			}

			initActiveMapControl(mapFolder);
			// handling map files
			activateMap(file);
		} else if (extension.equals(org.openbmap.Preferences.WIFI_CATALOG_FILE_EXTENSION)) {
			final String catalogFolder = PreferenceManager.getDefaultSharedPreferences(this).getString(
					Preferences.KEY_WIFI_CATALOG_FOLDER,
					SettingsActivity.this.getExternalFilesDir(null).getAbsolutePath() + Preferences.WIFI_CATALOG_SUBDIR);
			if (file.indexOf(SettingsActivity.this.getExternalCacheDir().getPath()) > - 1 ) {
				// file has been downloaded to cache folder, so move..
				file = moveToFolder(file, catalogFolder); 
			}

			initActiveWifiCatalogControl(catalogFolder);
			// handling wifi catalog files
			activateWifiCatalog(file);
		}
	}

	/**
	 * Moves file to specified folder
	 * @param file
	 * @param folder
	 * @return new file name
	 */
	private String moveToFolder(final String file, final String folder) {
		// file path contains external cache dir, so we have to move..
		final File source = new File(file);
		final File destination = new File(folder + File.separator + source.getName());
		Log.i(TAG, file + " stored in temp folder. Moving to " + destination.getAbsolutePath());

		try {
			FileUtils.moveFile(source, destination);
		}
		catch (final IOException e) {
			Log.e(TAG, "I/O error while moving file");
		}
		return  destination.getAbsolutePath();
	}

	// [start] Android 2.2 compatiblity: work-around for the missing download manager

	/* (non-Javadoc)
	 * @see org.openbmap.utils.LegacyDownloader.LegacyDownloadListener#onDownloadCompleted(java.lang.String)
	 */
	@Override
	public void onDownloadCompleted(final String filename) {
		handleDownloads(filename);
	}

	/* (non-Javadoc)
	 * @see org.openbmap.utils.LegacyDownloader.LegacyDownloadListener#onDownloadFailed(java.lang.String)
	 */
	@Override
	public void onDownloadFailed(final String filename) {
		Log.e(TAG, "Download (compatiblity mode) failed " + filename);
		Toast.makeText(this, getResources().getString(R.string.download_failed) + " " + filename , Toast.LENGTH_LONG).show();

	}
	// [end]

}
