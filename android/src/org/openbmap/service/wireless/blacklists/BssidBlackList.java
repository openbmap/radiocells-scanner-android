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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.annotation.SuppressLint;
import android.util.Log;

/**
 * Validates bssid against xml file of black listed wifis (e.g. mobile wlans on buses, trains, etc)
 * Check http://anonsvn.wireshark.org/wireshark/trunk/manuf for a (GPL licenced) list  manufactures
 */
public class BssidBlackList {

	private static final String	TAG	= BssidBlackList.class.getSimpleName();

	/**
	 * Debug setting: re-create XML file on each run (ergo: refreshing the list)
	 * This is helpful while adding new mac addresses to BssidBlackListBootstraper
	 * When no mac addresses are added to BssidBlackListBootstraper anymore,
	 * ALWAYS_RECREATE_BSSID_BLACKLIST can be set to false
	 */
	private static final boolean ALWAYS_RECREATE_BSSID_BLACKLIST	= true;
	
	/**
	 * XML tag prefix mac address
	 */
	private static final String	PREFIX_TAG	= "prefix";

	/**
	 * XML tag full mac address
	 */
	private static final String	ADDRESS_TAG	= "bssid";

	/**
	 * List of ignored bssid manufactorer codes
	 */
	private ArrayList<String>	mPrefixes;

	/**
	 * List of blocked addresses (bssids)
	 */
	private ArrayList<String>	mAddresses;

	public BssidBlackList() {
		mPrefixes = new ArrayList<String>();
		mAddresses = new ArrayList<String>();
	}

	/**
	 * Loads blacklist from xml files
	 * @param defaultList - generic list (well-known bad wifis, e.g. trains, buses, mobile hotspots)
	 * @param extraUserList - user-provided list (e.g. own wifi)
	 * @throws XmlPullParserException
	 */
	public final void openFile(final String defaultList, final String extraUserList) {
		
		if (ALWAYS_RECREATE_BSSID_BLACKLIST) {
			BssidBlackListBootstraper.run(defaultList);
		}
		
		if (defaultList != null) {
			try {				
				File file = new File(defaultList);
				FileInputStream defaultStream = new FileInputStream(file);
				add(defaultStream);
			} catch (FileNotFoundException e) {
				Log.i(TAG, "Default blacklist " + defaultList + " not found. Setting up..");
				BssidBlackListBootstraper.run(defaultList);
			} 
		}

		if (extraUserList != null) {
			try {
				File file = new File(extraUserList);
				FileInputStream userStream = new FileInputStream(file);
				add(userStream);
			} catch (FileNotFoundException e) {
				Log.w(TAG, "User-defined blacklist " + extraUserList + " not found. Skipping");
			} 
		} else {
			Log.i(TAG, "No user-defined blacklist provided");
		}
	}

	/**
	 * @param file
	 */
	private void add(final FileInputStream file) {
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(true);
			XmlPullParser xpp = factory.newPullParser();

			if (file != null) {
				xpp.setInput(new InputStreamReader(file));

				int eventType = xpp.getEventType();
				String currentTag = null;
				String value = null;

				while (eventType != XmlPullParser.END_DOCUMENT) {
					if (eventType == XmlPullParser.START_TAG) {
						currentTag = xpp.getName();
					} else if (eventType == XmlPullParser.TEXT) {
						if (PREFIX_TAG.equals(currentTag) || ADDRESS_TAG.equals(currentTag)) {
							value = xpp.getText();
						}
					} else if (eventType == XmlPullParser.END_TAG) {
						if (PREFIX_TAG.equals(xpp.getName())) {
							mPrefixes.add(value);
						}
						if (ADDRESS_TAG.equals(xpp.getName())) {
							mAddresses.add(value);
						}
					}
					eventType = xpp.next();
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "I/O exception reading blacklist");
		} catch (XmlPullParserException e) {
			Log.e(TAG, "Error parsing blacklist");
		}
		Log.i(TAG, "Loaded " + (mPrefixes.size() + mAddresses.size()) + " BSSID blacklist entries");
	}

	/**
	 * Checks whether given ssid is in ignore list
	 * @param bssid SSID to check
	 * @return true, if in ignore list
	 */
	@SuppressLint("DefaultLocale")
	public final boolean contains(final String bssid) {
		boolean match = false;
		for (String prefix : mPrefixes) {
			if (bssid.toLowerCase().startsWith(prefix.toLowerCase())) {
				match = true; 
				break;
			}
		}
		
		// don't look anyfurther
		if (match) {
			return match;
		}

		for (String address : mAddresses) {
			if (bssid.equalsIgnoreCase(address)) {
				match = true;
				break;
			}
		}

		return match; 
	}
}
