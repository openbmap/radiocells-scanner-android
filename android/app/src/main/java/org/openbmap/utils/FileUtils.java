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
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Environment;
import android.util.Log;

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
		final String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			externalStorageAvailable = true;
		} else {
			externalStorageAvailable = false;
		}
		return externalStorageAvailable;
	}

	public static boolean isSdCardWritable() {
		@SuppressWarnings("unused")
		boolean externalStorageAvailable = false;
		boolean externalStorageWritable = false;
		final String state = Environment.getExternalStorageState();
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
	 * Moves file from source to destination
	 * @param src
	 * @param dst
	 * @throws IOException
	 */
	public static void moveFile(final File src, final File dst) throws IOException
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
	public static void copyFile(final File src, final File dst) throws IOException {
		final FileChannel inChannel = new FileInputStream(src).getChannel();
		final FileChannel outChannel = new FileOutputStream(dst).getChannel();
		try {
			inChannel.transferTo(0, inChannel.size(), outChannel);
		} 
		finally {
			if (inChannel != null) {
				inChannel.close();
			}

			if (outChannel != null) {
				outChannel.close();
			}
		}
	}
	
	public static void copyFdToFile(FileDescriptor src, File dst) throws IOException {
	    FileChannel inChannel = new FileInputStream(src).getChannel();
	    FileChannel outChannel = new FileOutputStream(dst).getChannel();
	    try {
	        inChannel.transferTo(0, inChannel.size(), outChannel);
	    } finally {
	        if (inChannel != null)
	            inChannel.close();
	        if (outChannel != null)
	            outChannel.close();
	    }
	}

	/**
	 * Moves folder from on location to another
	 * @param sourceLocation
	 * @param targetLocation
	 * @throws IOException
	 */
	public static void moveFolder(final File sourceLocation , final File targetLocation) throws IOException {
		Log.i(TAG, "Moving folder content " + sourceLocation + " to " + targetLocation);
		if (sourceLocation.isDirectory()) {
			if (!targetLocation.exists() && !targetLocation.mkdirs()) {
				throw new IOException("Cannot create dir " + targetLocation.getAbsolutePath());
			}

			final String[] children = sourceLocation.list();
			for (int i=0; i<children.length; i++) {
				moveFolder(new File(sourceLocation, children[i]), new File(targetLocation, children[i]));
			}
		} else {
			// make sure the directory we plan to store the recording in exists
			final File directory = targetLocation.getParentFile();
			if (directory != null && !directory.exists() && !directory.mkdirs()) {
				throw new IOException("Cannot create dir " + directory.getAbsolutePath());
			}

			final boolean good = sourceLocation.renameTo(targetLocation);
			if (!good) {
				Log.e(TAG, "Error moving " + sourceLocation + " to " + targetLocation);
			}
			/* Copy
	        InputStream in = new FileInputStream(sourceLocation);
	        OutputStream out = new FileOutputStream(targetLocation);

	        // Copy the bits from instream to outstream
	        byte[] buf = new byte[1024];
	        int len;
	        while ((len = in.read(buf)) > 0) {
	            out.write(buf, 0, len);
	        }
	        in.close();
	        out.close();
			 */
		}
	}
	
	/**
	 * Private dummy constructor
	 */
	private FileUtils() {

	}

	/**
	 * @param from
	 * @param to
	 */
	public static void copyFromAssets(Context context, String from, File to) {
		AssetManager am = context.getAssets();
		AssetFileDescriptor afd = null;
		try {
		    afd = am.openFd(from);
		    copyFdToFile(afd.getFileDescriptor(), to);
		} catch (IOException e) {
		    e.printStackTrace();
		}

	}

}
