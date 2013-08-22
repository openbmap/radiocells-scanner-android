/**
 * File helper methods
 */
package org.openbmap.utils;

import android.os.Environment;

public final class FileHelper {
	
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
