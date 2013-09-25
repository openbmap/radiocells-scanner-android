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
	 * XML opening tag prefix
	 */
	private static final String	PREFIX_OPEN	= "<prefix comment=\"";

	/**
	 * XML middle tag prefix
	 */
	private static final String	PREFIX_MIDDLE = "\">";

	/**
	 * XML closing tag prefix
	 */
	private static final String	PREFIX_CLOSE = "</prefix>";

	/**
	 * XML opening tag full mac address
	 */
	private static final String	ADDRESS_OPEN	= "<bssid comment=\"default\">";

	/**
	 * XML middle tag prefix
	 */
	private static final String	ADDRESS_MIDDLE = "\">";
	
	/**
	 * XML closing tag full mac address
	 */
	private static final String	ADDRESS_CLOSE	= "</bssid>";

	/**
	 * XML opening tag file
	 */
	private static final String	FILE_OPEN	= "<ignorelist>";

	/**
	 * XML closing tag file
	 */
	private static final String	FILE_CLOSE	= "</ignorelist>";

	private static final String[][] ADDRESSES = {
		{"Invalid mac", "00:00:00:00:00:00"} 
		// updates will follow:
		// check openbmap database for wifis mac which have more than one measurements
		// if measurements are too far way, it's either a mobile wifi or otherwise unreliable
	};

	private static final String[][] PREFIXES =  {
		// automotive manufacturers
		{"Harman/Becker Automotive Systems, used by Audi", "00:1C:D7"},
		{"Harman/Becker Automotive Systems GmbH", "9C:DF:03"},
		{"Continental Automotive Systems", "00:1E:AE"},
		{"Continental Automotive Systems", "00:54:AF"},
		{"Bosch Automotive Aftermarket", "70:C6:AC"},
		{"Continental Automotive Czech Republic s.r.o.", "9C:28:BF"},
		{"Robert Bosch LLC Automotive Electronics", "D0:B4:98"},
		{"Panasonic Automotive Systems Company of America", "E0:EE:1B"},
		// mobile devices
		{"Murata Manufacturing Co., Ltd., used on some LG devices", "44:A7:CF"},
		{"Murata Manufacturing Co., Ltd., used in some mobile devices", "40:F3:08"},
		{"LG Electronics, used in Nexus 4",	"10:68:3F"},
		{"Apple, used in iphone", "00:26:08"},
		{"Apple, used in iphone", "7C:C5:37"}
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
			sb.append(FILE_OPEN);
			for (String[] prefix : PREFIXES) {
				sb.append(PREFIX_OPEN + prefix[0] + PREFIX_MIDDLE + prefix[1] + PREFIX_CLOSE);
			}

			for (String address[] : ADDRESSES) {
				sb.append(ADDRESS_OPEN + address[0] + ADDRESS_MIDDLE + address[1] + ADDRESS_CLOSE);
			}

			sb.append(FILE_CLOSE);

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
