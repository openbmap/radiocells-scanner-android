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

import android.os.Environment;

/**
 * File helper methods
 */
public final class FileHelper {
	
	@SuppressWarnings("unused")
	private static final String TAG = FileHelper.class.getSimpleName();

	/**
	 * Checks whether SD card is currently
	 * @return true if SD card is mounted
	 */
	public static boolean isSdCardMounted() {
		boolean externalStorageAvailable = false;
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			externalStorageAvailable = true;
		} else {
			externalStorageAvailable = false;
		}
		return externalStorageAvailable;
	}
	
	public static boolean isSdCardMountedWritable() {
		@SuppressWarnings("unused")
		boolean externalStorageAvailable = false;
		boolean externalStorageWritable = false;
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			externalStorageAvailable = externalStorageWritable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			externalStorageAvailable = true;
			externalStorageWritable = false;
		} else {
			externalStorageAvailable = externalStorageWritable = false;
		}
			 
		return externalStorageWritable;
	}

	/**
	 * Private dummy constructor
	 */
	private FileHelper() {

	}

}
