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

package org.openbmap.services.blacklists;

import android.annotation.SuppressLint;
import android.location.Location;
import android.util.Log;

import org.openbmap.utils.GeometryUtils;
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
 * Validates ssid against xml file of black listed locations
 * Create a file name custom_location.xml in your org.openbmap/blacklists folder
 * Example content (lat, lon, radius in meters):
 * <ignorelist><location comment="test area"><latitude>49.55306</latitude><longitude>9.0057</longitude><radius>550</radius></location></ignorelist>
 */

public class LocationBlackList {

	private static final String TAG = LocationBlackList.class.getSimpleName();

	/**
	 * XML tag prefix location
	 */
	private static final String LOCATION_TAG = "location";

	/**
	 * XML tag prefix latitude
	 */
	private static final String LATITUDE_TAG = "latitude";

	/**
	 * XML tag prefix longitude
	 */
	private static final String LONGITUDE_TAG = "longitude";

	/**
	 * XML tag prefix radius
	 */
	private static final String RADIUS_TAG = "radius";

	/**
	 * Default: block within 500 m
	 */
	private static final int DEFAULT_RADIUS = 500;

	/**
	 * List of blocked locations
	 */
	private final ArrayList<ForbiddenArea> mBlockList;

	/**
	 * Dead zone is defined by center point and radius
	 */
	private class ForbiddenArea {
		protected Location location;
		protected long radius;

		/**
		 * @param loc
		 * @param rad
		 */
		public ForbiddenArea(final Location loc, final long rad) {
			location = loc;
			radius = rad;
		}
	}

	public LocationBlackList() {
		mBlockList = new ArrayList<>();
	}

	/**
	 * Loads blacklist from xml file
	 * @param filename - user-provided list (e.g. own homezone)
	 * @throws XmlPullParserException
	 */
	public final void openFile(final String filename) {
		if (filename != null) {
			try {
				final File file = new File(filename);
				final FileInputStream stream = new FileInputStream(file);
				parseBlocklist(stream);
			} catch (final FileNotFoundException e) {
				Log.w(TAG, "User-defined blacklist " + filename + " not found. Skipping");
			} 
		} else {
			Log.i(TAG, "No user-defined blacklist provided");
		}
	}

	/**
	 * Load location blacklist
	 * @param file
	 */
	private void parseBlocklist(final FileInputStream file) {
		try {
			final XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(true);
			final XmlPullParser xpp = factory.newPullParser();

			if (file != null) {
				xpp.setInput(new InputStreamReader(file));

				int eventType = xpp.getEventType();
				String currentTag = null;

				Location loc = null;
				long radius = DEFAULT_RADIUS;

				while (eventType != XmlPullParser.END_DOCUMENT) {
					if (eventType == XmlPullParser.START_TAG) {
						currentTag = xpp.getName();
						if (currentTag.equals(LOCATION_TAG)) {
							loc = new Location("DUMMY");
						}
					} else if (eventType == XmlPullParser.TEXT) {
						if (LATITUDE_TAG.equals(currentTag)) {
							try {
								loc.setLatitude(Double.valueOf(xpp.getText()));
							} catch (final NumberFormatException e) {
								Log.e(TAG, "Error getting latitude");
								loc = null;
							}
						}
						if (LONGITUDE_TAG.equals(currentTag)) {
							try {
								loc.setLongitude(Double.valueOf(xpp.getText()));
							} catch (final NumberFormatException e) {
								Log.e(TAG, "Error getting longitude");
								loc = null;
							}
						}
						if (RADIUS_TAG.equals(currentTag)) {
							try {
								radius = Long.valueOf(xpp.getText());
							} catch (final NumberFormatException e) {
								Log.e(TAG, "Error getting longitude");
								radius = DEFAULT_RADIUS;
							}
						}
					} else if (eventType == XmlPullParser.END_TAG) {
						if (LOCATION_TAG.equals(xpp.getName())) {
							if (GeometryUtils.isValidLocation(loc, false)) {
								mBlockList.add(new ForbiddenArea(loc, radius));
							} else {
								Log.e(TAG, "Invalid location");
							}
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
		Log.i(TAG, "Loaded " + mBlockList.size() + " location blacklist entries");
	}

	/**
	 * Checks whether given location is in ignore list
	 * @param location location
	 * @return true, if in ignore list
	 */
	@SuppressLint("DefaultLocale")
	public final boolean contains(final Location location) {
		boolean match = false;
		for (final ForbiddenArea dead : mBlockList) {
			if (location.distanceTo(dead.location) < dead.radius) {
				match = true; 
				break;
			}
		}
		return match; 
	}
}
