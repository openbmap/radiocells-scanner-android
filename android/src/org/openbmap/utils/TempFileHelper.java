/**
 * Deletes all (temp) log files in data dir
 */
package org.openbmap.utils;

import java.io.File;
import java.io.FilenameFilter;

import org.openbmap.Preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * @author power
 *
 */
public final class TempFileHelper {
	
	private static final String TAG = TempFileHelper.class.getSimpleName();

	/**
	 * Deletes all temp files (i.e. xml files) in applications root dir.
	 * @param mContext
	 */
	public static void cleanTempFiles(final Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		// List each map file
		File logDir = new File(
				Environment.getExternalStorageDirectory().getPath()
				+ prefs.getString(Preferences.KEY_DATA_DIR, Preferences.VAL_DATA_DIR) + File.separator
				);

		if (logDir.exists() && logDir.canWrite()) {
			String[] mapFiles = logDir.list(new FilenameFilter() {
				@Override
				public boolean accept(final File dir, final String filename) {
					return filename.endsWith(Preferences.LOG_FILE_EXTENSION);
				}
			});

			int count = 0;
			for (int i = 0; i < mapFiles.length; i++) {
				File file = new File(logDir.toString(), mapFiles[i]);
				count +=  file.delete() ? 1 : 0;
			}
			Log.i(TAG, "Deleted " + count + " temp files in " + logDir.toString());
		}
	}

	/**
	 * Private dummy constructor
	 */
	private TempFileHelper() {

	}

}
