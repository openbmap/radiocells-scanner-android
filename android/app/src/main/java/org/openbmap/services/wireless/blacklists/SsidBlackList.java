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

package org.openbmap.services.wireless.blacklists;

import android.annotation.SuppressLint;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Validates ssid against xml file of black listed wifis (e.g. mobile wlans on buses, trains, etc)
 */
public class SsidBlackList {

	private static final String	TAG	= SsidBlackList.class.getSimpleName();
	
	/**
	 * Debug setting: re-create XML file on each run (ergo: refreshing the list)
	 * This is helpful while adding new mac addresses to BssidBlackListBootstraper
	 * When no mac addresses are added to BssidBlackListBootstraper anymore,
	 * ALWAYS_RECREATE_BSSID_BLACKLIST can be set to false
	 */
	private static final boolean ALWAYS_RECREATE_SSID_BLACKLIST	= true;
	
	/**
	 * XML tag prefixes
	 */
	private static final String	PREFIX_TAG	= "prefix";

	/**
	 * XML tag suffixes
	 */
	private static final String	SUFFIX_TAG	= "suffix";
	
	/**
	 * List of ignored ssid prefixes
	 */
	private final ArrayList<String>	mPrefixes;
	
	/**
	 * List of ignored ssid suffixes
	 */
	private final ArrayList<String>	mSuffixes;

	public SsidBlackList() {
		mPrefixes = new ArrayList<>();
		mSuffixes = new ArrayList<>();
	}

	/**
	 * Loads blacklist from xml files
	 * @param defaultList - generic list (well-known bad wifis, e.g. trains, buses, mobile hotspots)
	 * @param extraUserList - user-provided list (e.g. own wifi)
	 * @throws XmlPullParserException
	 */
	public final void openFile(final String defaultList, final String extraUserList) {

		if (ALWAYS_RECREATE_SSID_BLACKLIST) {
			SsidBlackListBootstraper.run(defaultList);
		}
		
		if (defaultList != null) {
			try {				
				final File file = new File(defaultList);
				final FileInputStream defaultStream = new FileInputStream(file);
				add(defaultStream);
			} catch (final FileNotFoundException e) {
				Log.i(TAG, "Default blacklist " + defaultList + " not found. Setting up..");
				SsidBlackListBootstraper.run(defaultList);
			} 
		}

		if (extraUserList != null) {
			try {
				final File file = new File(extraUserList);
				final FileInputStream userStream = new FileInputStream(file);
				add(userStream);
			} catch (final FileNotFoundException e) {
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
			final XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(true);
			final XmlPullParser xpp = factory.newPullParser();

			if (file != null) {
				xpp.setInput(new InputStreamReader(file));

				int eventType = xpp.getEventType();
				String currentTag = null;
				String value = null;

				while (eventType != XmlPullParser.END_DOCUMENT) {
					if (eventType == XmlPullParser.START_TAG) {
						currentTag = xpp.getName();
					} else if (eventType == XmlPullParser.TEXT) {
						if (PREFIX_TAG.equals(currentTag) || SUFFIX_TAG.equals(currentTag)) {
							value = xpp.getText();
						}
					} else if (eventType == XmlPullParser.END_TAG) {
						if (PREFIX_TAG.equals(xpp.getName())) {
							mPrefixes.add(value);
						}
						if (SUFFIX_TAG.equals(xpp.getName())) {
							mSuffixes.add(value);
						}
					}
					eventType = xpp.next();
				}
			}
		} catch (final IOException e) {
			Log.e(TAG, "I/O exception reading blacklist");
		} catch (final XmlPullParserException e) {
			Log.e(TAG, "Error parsing blacklist");
		}
		Log.i(TAG, "Loaded " + (mPrefixes.size() + mSuffixes.size()) + " SSID blacklist entries");
	}

	/**
	 * Checks whether given ssid is in ignore list
	 * @param ssid SSID to check
	 * @return true, if in ignore list
	 */
	@SuppressLint("DefaultLocale")
	public final boolean contains(final String ssid) {
		boolean match = false;
		for (final String prefix : mPrefixes) {
			if (ssid.toLowerCase().startsWith(prefix.toLowerCase())) {
				match = true; 
				break;
			}
		}

		// don't look any further
		if (match) {
			return match;
		}
		
		for (final String suffix : mSuffixes) {
			if (ssid.toLowerCase().endsWith(suffix.toLowerCase())) {
				match = true;
				break;
			}
		}

		return match; // OK
	}
}
