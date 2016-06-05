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

package org.openbmap.utils;

import android.content.Context;
import android.util.Log;

import org.openbmap.Preferences;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Deletes all (temp) log files in data dir
 */
public final class TempFileUtils {

	private static final String TAG = TempFileUtils.class.getSimpleName();

	/**
	 * Deletes all temp files (i.e. xml files) in applications root dir.
	 * @param context
	 */
	public static void cleanTempFiles(final Context context) {
		// List each map file
		final File logDir = new File(context.getExternalFilesDir(null).getAbsolutePath());

		if (logDir.exists() && logDir.canWrite()) {
			final String[] mapFiles = logDir.list(new FilenameFilter() {
				@Override
				public boolean accept(final File dir, final String filename) {
					return filename.endsWith(Preferences.LOG_FILE_EXTENSION);
				}
			});

			int count = 0;
			for (int i = 0; i < mapFiles.length; i++) {
				final File file = new File(logDir.toString(), mapFiles[i]);
				count +=  file.delete() ? 1 : 0;
			}
			Log.i(TAG, "Deleted " + count + " temp files in " + logDir.toString());
		}
	}

	/**
	 * Private dummy constructor
	 */
	private TempFileUtils() {

	}

}
