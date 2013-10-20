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

	private static final String TAG= SsidBlackListBootstraper.class.getSimpleName();

	/**
	 * XML opening tag prefix
	 */
	private static final String PREFIX_OPEN= "<prefix comment=\"";

	/**
	 * XML middle tag prefix
	 */
	private static final String PREFIX_MIDDLE = "\">";

	/**
	 * XML closing tag prefix
	 */
	private static final String PREFIX_CLOSE = "</prefix>";

	/**
	 * XML opening tag prefix
	 */
	private static final String SUFFIX_OPEN= "<suffix comment=\"";

	/**
	 * XML middle tag prefix
	 */
	private static final String SUFFIX_MIDDLE = "\">";

	/**
	 * XML closing tag prefix
	 */
	private static final String SUFFIX_CLOSE = "</suffix>";

	/**
	 * 
	 */
	private static final String START_TAG= "<ignorelist>";

	/**
	 * 
	 */
	private static final String END_TAG= "</ignorelist>";

	private static final String[][] PREFIXES = {
		{"default", "ASUS"},
		{"default", "Android Barnacle Wifi Tether"},
		{"default", "AndroidAP"},
		{"default", "AndroidTether"},
		{"default", "Clear Spot"},
		{"default", "ClearSpot"},
		{"default", "docomo"},
		{"default", "Galaxy Note"},
		{"default", "Galaxy S"},
		{"default", "Galaxy Tab"},
		{"default", "HelloMoto"},
		{"default", "HTC "},
		{"default", "iDockUSA"},
		{"default", "iHub_"},
		{"default", "iPad"},
		{"default", "ipad"},
		{"default", "iPhone"},
		{"default", "LG VS910 4G"},
		{"default", "MIFI"},
		{"default", "MiFi"},
		{"default", "mifi"},
		{"default", "MOBILE"},
		{"default", "Mobile"},
		{"default", "mobile"},
		{"default", "myLGNet"},
		{"default", "myTouch 4G Hotspot"},
		{"default", "PhoneAP"},
		{"default", "SAMSUNG"},
		{"default", "Samsung"},
		{"default", "Sprint"},
		{"German long haul buses", "DeinBus"},
		{"German long haul buses", "MeinFernbus"},
		{"Long haul buses", "eurolines"},	
		{"Long haul buses", "ecolines"},
		{"Hurtigen lines", "guest@MS "},
		{"Hurtigen lines", "admin@MS "},
		{"German fast trains", "Telekom_ICE"},
		{"default", "Trimble "},
		{"default", "Verizon"},
		{"default", "VirginMobile"},
		{"default", "VTA Free Wi-Fi"},
		{"default", "webOS Network"},
		{"empty ssid (not really hidden, just not broadcasting..)", ""}
	};

	private static final String[][] SUFFIXES = {
		{"default", " ASUS"},
		{"default", "-ASUS"},
		{"default", "_ASUS"},
		{"default", "MacBook"},
		{"default", "MacBook Pro"},
		{"default", "MiFi"},
		{"default", "MyWi"},
		{"default", "Tether"},
		{"default", "iPad"},
		{"default", "iPhone"},
		{"default", "ipad"},
		{"default", "iphone"},
		{"default", "tether"},
		{"Google's SSID opt-out", "_nomap"}
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
			for (String[] prefix : PREFIXES) {
				sb.append(PREFIX_OPEN + prefix[0] + PREFIX_MIDDLE + prefix[1] + PREFIX_CLOSE);
			}

			for (String[] suffix : SUFFIXES) {
				sb.append(SUFFIX_OPEN + suffix[0] + SUFFIX_MIDDLE + suffix[1] + SUFFIX_CLOSE);
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
