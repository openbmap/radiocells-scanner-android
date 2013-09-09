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


package org.openbmap.service.wireless.blacklists;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.util.Log;

/**
 * Creates initial wifi blacklist with some default entries
 */
public final class BssidBlackListBootstraper {

	private static final String	TAG	= BssidBlackListBootstraper.class.getSimpleName();

	/**
	 * 
	 */
	private static final String	OPEN_PREFIX	= "<bssid comment=\"default\">";

	/**
	 * 
	 */
	private static final String	CLOSE_PREFIX	= "</bssid>";

	/**
	 * 
	 */
	private static final String	START_TAG	= "<ignorelist>";

	/**
	 * 
	 */
	private static final String	END_TAG	= "</ignorelist>";

	private static final String[] ADDRESSES = {
		"000000000000" // most obvious
		// updates will follow:
		// check openbmap database for wifis mac which have more than one measurements
		// if measurements are too far way, it's either a mobile wifi or otherwise unreliable
	};


	public static void run(final String filename) {
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
			StringBuilder sb = new StringBuilder();
			sb.append(START_TAG);
			for (String address : ADDRESSES) {
				sb.append(OPEN_PREFIX + address + CLOSE_PREFIX);
			}

			sb.append(END_TAG);

			try {
				File file = new File(filename);
				BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
				bw.append(sb);
				bw.close();
				Log.i(TAG, "Created default blacklist, " + ADDRESSES.length + " entries");
			} catch (IOException e) {
				Log.e(TAG, "Error writing blacklist");
			} 
		} else {
			Log.e(TAG, "Folder not accessible: can't write blacklist");
		}

	}

	private BssidBlackListBootstraper() {
		
	}
}
