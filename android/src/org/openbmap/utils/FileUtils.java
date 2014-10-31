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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import android.os.Environment;

/**
 * File helper methods
 */
public final class FileUtils {

	@SuppressWarnings("unused")
	private static final String TAG = FileUtils.class.getSimpleName();

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

	public static void moveFile(File src, File dst) throws IOException
	{
		copyFile(src, dst);
		src.delete();
	}
	
	/**
	 * Copies file to destination.
	 * This was needed to copy file from temp folder to SD card. A simple renameTo fails..
	 * see http://stackoverflow.com/questions/4770004/how-to-move-rename-file-from-internal-app-storage-to-external-storage-on-android
	 * @param src
	 * @param dst
	 * @throws IOException
	 */
	public static void copyFile(File src, File dst) throws IOException {
		FileChannel inChannel = new FileInputStream(src).getChannel();
		FileChannel outChannel = new FileOutputStream(dst).getChannel();
		try {
			inChannel.transferTo(0, inChannel.size(), outChannel);
		} finally {
			if (inChannel != null) {
				inChannel.close();
			}
			
			if (outChannel != null) {
				outChannel.close();
			}
		}
	}

	/**
	 * Private dummy constructor
	 */
	private FileUtils() {

	}

}
