package org.openbmap.activity;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.utils.Downloader;

import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
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
 * Manages preferences screen.
 *
 */
public class SettingsActivity extends PreferenceActivity implements Downloader.DownloadListener {

	private static final String TAG = SettingsActivity.class.getSimpleName();
	
	private EditTextPreference mDataDirPref;

	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		// Set summary of some preferences to their actual values
		// and register a change mListener to set again the summary in case of change

		mDataDirPref = initDataDir();

		
		initWifiCatalogDownload();
		initActiveWifiCatalog(mDataDirPref.getText());
		initMapDownload();
		initActiveMap(mDataDirPref.getText());

		initGpsSystem();
		// Currently deactivated. Only GPS is used as provider.
		/*initGpsProviderPreference();*/
		 
		initGpsLogInterval();
	}

	/**
	 * Initializes wifi catalog source preference
	 */
	private void initWifiCatalogDownload() {
		Preference pref = findPreference(org.openbmap.Preferences.KEY_DOWNLOAD_WIFI_CATALOG);
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

						Downloader task = (Downloader) new Downloader(preference.getContext()).execute(
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

	/**
	 * Initializes gps system preference.
	 * OnPreferenceClick system gps settings are displayed.
	 */
	private void initGpsSystem() {
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
						Downloader task = (Downloader) new Downloader(preference.getContext()).execute(
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

	/* (non-Javadoc)
	 * @see org.openbmap.utils.Downloader.DownloadListener#onDownloadCompleted()
	 */
	@Override
	public final void onDownloadCompleted(final String file) {
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

	/* (non-Javadoc)
	 * @see org.openbmap.utils.Downloader.DownloadListener#onDownloadFailed()
	 */
	@Override
	public final void onDownloadFailed(final String file) {
		Toast.makeText(this, R.string.error_dowload_error, Toast.LENGTH_LONG).show();	
	}
}
