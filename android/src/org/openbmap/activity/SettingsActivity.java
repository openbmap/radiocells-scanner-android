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
import org.openbmap.utils.LegacyDownloader;
import org.openbmap.utils.LegacyDownloader.LegacyDownloadListener;
import org.openbmap.utils.MediaScanner;
import org.openbmap.utils.VacuumCleaner;

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

	private EditTextPreference mDataDirPref;

	private DownloadManager dm;

	private BroadcastReceiver receiver =  null; 

	@SuppressLint("NewApi")
	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		mDataDirPref = initDataDir();

		// with versions >= GINGERBREAD use download manager
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
			dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

			receiver = new BroadcastReceiver() {
				@Override
				public void onReceive(final Context context, final Intent intent) {
					String action = intent.getAction();
					if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
						long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
						Query query = new Query();
						query.setFilterById(downloadId);
						Cursor c = dm.query(query);
						if (c.moveToFirst()) {
							int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
							if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
								// we're not checking download id here, that is done in handleDownloads
								String uriString = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
								handleDownloads(uriString);
							}
						}
					}
				}
			};
		}

		initWifiCatalogDownload();
		initActiveWifiCatalog(mDataDirPref.getText());
		initMapDownload();
		initActiveMap(mDataDirPref.getText());

		initGpsSystemSettings();
		initCleanDatabase();
		initHomezoneBlocking();

		initGpsLogInterval();
	}

	@Override
	protected final void onDestroy() {
		try {
			if (receiver != null) {
				Log.i(TAG, "Unregistering broadcast receivers");
				unregisterReceiver(receiver);
			}
		} catch (IllegalArgumentException e) {
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
	private void initGpsSystemSettings() {
		Preference pref = findPreference(org.openbmap.Preferences.KEY_GPS_OSSETTINGS);
		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
				return true;
			}
		});
	}

	/**
	 * 
	 */
	private void initCleanDatabase() {
		Preference pref = findPreference(org.openbmap.Preferences.KEY_CLEAN_DATABASE);
		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				new VacuumCleaner(SettingsActivity.this).execute(new Void[]{null});
				return true;
			}
		});
	}

	/**
	 * 
	 */
	private void initHomezoneBlocking() {
		Preference pref = findPreference(org.openbmap.Preferences.KEY_BLOCK_HOMEZONE);
		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				LocationResult locationResult = new LocationResult(){
				    @Override
				    public void gotLocation(Location location){
				    	// get shared preferences
				    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);

				    	String blacklistPath = Environment.getExternalStorageDirectory().getPath()
								+ prefs.getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR) + File.separator 
								+ Preferences.BLACKLIST_SUBDIR;
				    	String filename = blacklistPath + File.separator + RadioBeacon.DEFAULT_LOCATION_BLOCK_FILE;
				    	String blocker = String.format("<ignorelist>"
				    			+ "<location comment=\"homezone\">"
				    			+ "<latitude>%s</latitude>"
				    			+ "<longitude>%s</longitude>"
				    			+ "<radius>550</radius>"
				    			+ "</location>"
				    			+ "</ignorelist>", location.getLatitude(), location.getLongitude());
				    	
				    	File folder = new File(filename.substring(1, filename.lastIndexOf(File.separator)));
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
								File file = new File(filename);
								BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
								bw.append(blocker);
								bw.close();
								Log.i(TAG, "Created default location blacklist");
								Toast.makeText(SettingsActivity.this, getResources().getString(R.string.location_blacklist_saved), Toast.LENGTH_LONG).show();
								new MediaScanner(SettingsActivity.this, folder);
							} catch (IOException e) {
								Log.e(TAG, "Error writing blacklist");
							} 
						} else {
							Log.e(TAG, "Folder not accessible: can't write blacklist");
							Toast.makeText(SettingsActivity.this, getResources().getString(R.string.error_writing_location_blacklist), Toast.LENGTH_LONG).show();
						}
				    }
				};
				
				CurrentLocationHelper myLocation = new CurrentLocationHelper();
				myLocation.getLocation(SettingsActivity.this, locationResult);
				
				return true;
			}
		});
	}

	/**
	 * Initializes gps logging interval.
	 */
	private void initGpsLogInterval() {
		// Update GPS logging interval summary to the current value
		Preference pref = findPreference(org.openbmap.Preferences.KEY_GPS_LOGGING_INTERVAL);
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
	private EditTextPreference initDataDir() {
		// External storage directory
		EditTextPreference pref = (EditTextPreference) findPreference(org.openbmap.Preferences.KEY_DATA_DIR);
		pref.setSummary(PreferenceManager.getDefaultSharedPreferences(this).getString(org.openbmap.Preferences.KEY_DATA_DIR, org.openbmap.Preferences.VAL_DATA_DIR));
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
				initActiveMap((String) pathName); 

				return true;
			}
		});
		return pref;
	}

	/**
	 * Populates the active map list preference.
	 * @param rootDir Root folder for MAPS_SUBDIR
	 */
	private void initActiveMap(final String rootDir) {
		String[] entries;
		String[] values;

		Log.d(TAG, "Scanning for map files");

		// Check for presence of maps directory
		File mapsDir = new File(Environment.getExternalStorageDirectory(),
				rootDir + File.separator
				+ org.openbmap.Preferences.MAPS_SUBDIR + File.separator);

		// List each map file
		if (mapsDir.exists() && mapsDir.canRead()) {
			String[] mapFiles = mapsDir.list(new FilenameFilter() {
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

		ListPreference lf = (ListPreference) findPreference(org.openbmap.Preferences.KEY_MAP_FILE);
		lf.setEntries(entries);
		lf.setEntryValues(values);
	}

	/**
	 * Populates the download list with links to mapsforge downloads.
	 * @param rootDir Root folder for MAPS_SUBDIR
	 */
	private void initMapDownload() {
		String[] entries;
		String[] values;

		// No map found, populate values with just the default entry.
		entries = getResources().getStringArray(R.array.listDisplayWord);
		values = getResources().getStringArray(R.array.listReturnValue);

		ListPreference lf = (ListPreference) findPreference(org.openbmap.Preferences.KEY_DOWNLOAD_MAP);
		lf.setEntries(entries);
		lf.setEntryValues(values);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			// use download manager for versions >= GINGERBREAD
			lf.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@SuppressLint("NewApi")
				public boolean onPreferenceChange(final Preference preference, final Object newValue) {

					// try to create directory
					File folder = new File(Environment.getExternalStorageDirectory().getPath()
							+ PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR)
							+ Preferences.MAPS_SUBDIR + File.separator);

					boolean folderAccessible = false;
					if (folder.exists() && folder.canWrite()) {
						folderAccessible = true;
					}

					if (!folder.exists()) {
						folderAccessible = folder.mkdirs();
					}
					if (folderAccessible) {
						final String filename = newValue.toString().substring(newValue.toString().lastIndexOf('/') + 1);

						File target = new File(folder.getAbsolutePath() + File.separator + filename);
						if (target.exists()) {
							Log.i(TAG, "Map file " + filename + " already exists. Overwriting..");
							target.delete();
						}
						
						Request request = new Request(Uri.parse(newValue.toString()));
						request.setDestinationUri(Uri.fromFile(target));
						long mapDownloadId = dm.enqueue(request);
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
						File folder = new File(Environment.getExternalStorageDirectory().getPath()
								+ PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR)
								+ Preferences.MAPS_SUBDIR + File.separator);

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
							LegacyDownloader task = (LegacyDownloader) new LegacyDownloader(preference.getContext()).execute(
									url, folder.getAbsolutePath() + File.separator + filename);

							// Callback to refresh maps preference on completion
							task.setListener((SettingsActivity) preference.getContext());

						} else {
							Toast.makeText(preference.getContext(), R.string.error_save_file_failed, Toast.LENGTH_SHORT).show();
						}	
					} catch (MalformedURLException e) {
						Log.e(TAG, "Malformed download url: " + newValue.toString());
					}
					return false;
				}
			});
		}
	}

	/**
	 * Initializes wifi catalog source preference
	 */
	@SuppressLint("NewApi")
	private void initWifiCatalogDownload() {
		Preference pref = findPreference(org.openbmap.Preferences.KEY_DOWNLOAD_WIFI_CATALOG);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			// use download manager for versions >= GINGERBREAD
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(final Preference preference) {
					// try to create directory
					File folder = new File(Environment.getExternalStorageDirectory().getPath()
							+ PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR)
							+ Preferences.WIFI_CATALOG_SUBDIR + File.separator);

					boolean folderAccessible = false;
					if (folder.exists() && folder.canWrite()) {
						folderAccessible = true;
					}

					if (!folder.exists()) {
						folderAccessible = folder.mkdirs();
					}
					if (folderAccessible) {
						File target = new File(folder.getAbsolutePath() + File.separator + Preferences.WIFI_CATALOG_FILE);
						if (target.exists()) {
							Log.i(TAG, "Catalog file already exists. Overwriting..");
							target.delete();
						}
						Request request = new Request(
								Uri.parse(Preferences.WIFI_CATALOG_DOWNLOAD_URL));
						request.setDestinationUri(Uri.fromFile(target));
						long catalogDownloadId = dm.enqueue(request);
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
						File folder = new File(Environment.getExternalStorageDirectory().getPath()
								+ PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR)
								+ Preferences.WIFI_CATALOG_SUBDIR + File.separator);

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

							LegacyDownloader task = (LegacyDownloader) new LegacyDownloader(preference.getContext()).execute(
									url, folder.getAbsolutePath() + File.separator + filename);

							// Callback to refresh maps preference on completion
							task.setListener((SettingsActivity) preference.getContext());

						} else {
							Toast.makeText(preference.getContext(), R.string.error_save_file_failed, Toast.LENGTH_SHORT).show();
						}	
					} catch (MalformedURLException e) {
						Log.e(TAG, "Malformed download url: " + Preferences.WIFI_CATALOG_DOWNLOAD_URL);
					}
					return true;
				}
			});
		}
	}

	/**
	 * Populates the wifi catalog database list preference.
	 * @param rootDir Root folder for WIFI_CATALOG_SUBDIR
	 */
	private void initActiveWifiCatalog(final String rootDir) {

		String[] entries;
		String[] values;

		// Check for presence of database directory
		File dbDir = new File(Environment.getExternalStorageDirectory(),
				rootDir + File.separator
				+ org.openbmap.Preferences.WIFI_CATALOG_SUBDIR + File.separator);
		if (dbDir.exists() && dbDir.canRead()) {
			// List each map file
			String[] dbFiles = dbDir.list(new FilenameFilter() {
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

		ListPreference lf = (ListPreference) findPreference(org.openbmap.Preferences.KEY_WIFI_CATALOG);
		lf.setEntries(entries);
		lf.setEntryValues(values);
	}

	/**
	 * Selects downloaded file either as wifi catalog / active map (based on file extension).
	 * @param file
	 */
	public final void handleDownloads(final String file) {
		initActiveMap(mDataDirPref.getText());
		initActiveWifiCatalog(mDataDirPref.getText());

		// get current file extension
		String[] filenameArray = file.split("\\.");
		String extension = "." + filenameArray[filenameArray.length - 1];

		if (extension.equals(org.openbmap.Preferences.MAP_FILE_EXTENSION)) {
			// handling map files
			activateMap(file);
		} else if (extension.equals(org.openbmap.Preferences.WIFI_CATALOG_FILE_EXTENSION)) {
			// handling wifi catalog files
			activateCatalog(file);
		}
	}

	/**
	 * Changes catalog preference item to given filename.
	 * Helper method to activate map after successful download
	 * @param absoluteFile absolute filename (including path)
	 */
	private void activateCatalog(final String absoluteFile) {
		ListPreference lf = (ListPreference) findPreference(org.openbmap.Preferences.KEY_WIFI_CATALOG);

		// get filename
		String[] filenameArray = absoluteFile.split("\\/");
		String file = filenameArray[filenameArray.length - 1];

		CharSequence[] values = lf.getEntryValues();
		for (int i = 0; i < values.length; i++) {
			if (file.equals(values[i].toString())) {
				lf.setValueIndex(i);
			}
		}
	}

	/**
	 * Changes map preference item to given filename.
	 * Helper method to activate map after successful download
	 * @param absoluteFile absolute filename (including path)
	 */
	private void activateMap(final String absoluteFile) {
		ListPreference lf = (ListPreference) findPreference(org.openbmap.Preferences.KEY_MAP_FILE);

		// get filename
		String[] filenameArray = absoluteFile.split("\\/");
		String file = filenameArray[filenameArray.length - 1];

		CharSequence[] values = lf.getEntryValues();
		for (int i = 0; i < values.length; i++) {
			if (file.equals(values[i].toString())) {
				lf.setValueIndex(i);
			}
		}
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
