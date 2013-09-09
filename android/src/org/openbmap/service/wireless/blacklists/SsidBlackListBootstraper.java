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
public final class SsidBlackListBootstraper {

	private static final String	TAG	= SsidBlackListBootstraper.class.getSimpleName();
   
	/**
	 * 
	 */
	private static final String	OPEN_PREFIX	= "<prefix comment=\"default\">";


	/**
	 * 
	 */
	private static final String	CLOSE_PREFIX	= "</prefix>";

	/**
	 * 
	 */
	private static final String	OPEN_SUFFIX	= "<suffix comment=\"default\">";
	/**
	 * 
	 */
	private static final String	CLOSE_SUFFIX	= "</suffix>";

	/**
	 * 
	 */
	private static final String	START_TAG	= "<ignorelist>";

	/**
	 * 
	 */
	private static final String	END_TAG	= "</ignorelist>";
	
	private static final String[] PREFIXES = {
		"ASUS",
		"Android Barnacle Wifi Tether",
		"AndroidAP",
		"AndroidTether",
		"Clear Spot",
		"ClearSpot",
		"docomo",
		"Galaxy Note",
		"Galaxy S",
		"Galaxy Tab",
		"HelloMoto",
		"HTC ",
		"iDockUSA",
		"iHub_",
		"iPad",
		"ipad",
		"iPhone",
		"LG VS910 4G",
		"MIFI",
		"MiFi",
		"mifi",
		"MOBILE",
		"Mobile",
		"mobile",
		"myLGNet",
		"myTouch 4G Hotspot",
		"PhoneAP",
		"SAMSUNG",
		"Samsung",
		"Sprint",
		"Telekom_ICE",
		"Trimble ",
		"Verizon",
		"VirginMobile",
		"VTA Free Wi-Fi",
		"webOS Network"
	};

	private static final String[] SUFFIXES = {
		" ASUS",
		"-ASUS",
		"_ASUS",
		"MacBook",
		"MacBook Pro",
		"MiFi",
		"MyWi",
		"Tether",
		"iPad",
		"iPhone",
		"ipad",
		"iphone",
		"tether",
		"_nomap" // Google's SSID opt-out
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
			for (String prefix : PREFIXES) {
				sb.append(OPEN_PREFIX + prefix + CLOSE_PREFIX);
			}

			for (String suffix : SUFFIXES) {
				sb.append(OPEN_SUFFIX + suffix + CLOSE_SUFFIX);
			}
			sb.append(END_TAG);

			try {
				File file = new File(filename);
				BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
				bw.append(sb);
				bw.close();
				Log.i(TAG, "Created default blacklist, " + PREFIXES.length + SUFFIXES.length + " entries");
			} catch (IOException e) {
				Log.e(TAG, "Error writing blacklist");
			} 
		} else {
			Log.e(TAG, "Folder not accessible: can't write blacklist");
		}

	}
	
	private SsidBlackListBootstraper() {
	}	
}
