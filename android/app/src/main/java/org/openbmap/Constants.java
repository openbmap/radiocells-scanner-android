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

/**
 * Application wide settings and constants
 */
package org.openbmap;

/**
 * Constants & app-wide variables.
 */
public final class Constants {

	/**
     * The full Package name of Constants returned by calling
     * Constants.class.getPackage().getName()
     */
    public static final String PACKAGE_NAME = Constants.class.getPackage().getName();

	/**
	 * Intent signaling new session has been saved
	 */
    public static final String INTENT_NEW_SESSION = Constants.PACKAGE_NAME + ".intent.SESSION_SAVED";

	/**
	 * Intent signalling sessions have been changed (e.g. deletes)
	 */
    public static final String INTENT_SESSION_UPDATE = Constants.PACKAGE_NAME + ".intent.SESSION_UPDATE";

	/**
	 * Session Id, when not currently tracking
	 */
	public static final int SESSION_NOT_TRACKING = 0;

	/**
	 * Default provider name
	 */
	public static final String PROVIDER_NONE = "NONE";

	/**
	 * Provider used for manual gps positioning
	 */
	public static final String PROVIDER_USER_DEFINED = "WAYPOINT";

	/**
	 * Default ssid blacklist's filename
	 */
	public static final String DEFAULT_SSID_BLOCK_FILE	= "default_ssid.xml";

	/**
	 * User-defined ssid blacklist's filename
	 */
	public static final String CUSTOM_SSID_BLOCK_FILE	= "custom_ssid.xml";

	/**
	 * Location blacklist's filename
	 */
	public static final String DEFAULT_LOCATION_BLOCK_FILE	= "custom_location.xml";


	/**
	 * SWID string for XML files
	 */
	public static final String SWID = "Radiobeacon";

	/**
	 * SW Version string for XML files and credit screens
	 * Caution:
     * 			- If you change client version, check whether you need to adjust current_version.xml on server and VERSION_COMPATIBILITY
     * 			- Also consider updating android:versionCode="x+1" android:versionName="XYZ" in AndroidManifest.xml (e.g. for automatic F-Droid updates)
	 */
	public static final String SW_VERSION = "0.8.18";

	/**
	 * ServerValidation compares server version against VERSION_COMPATIBILITY to check whether client is outdated.
	 * If server's current_version.xml version differs from VERSION_COMPATIBILITY upload is denied.
	 * VERSION_COMPATIBILITY is typically set to the last major release.
	 *
	 * If you just have minor improvements on the client side, increase SW_VERSION, e.g. 00.6.01 and leave VERSION_COMPATIBILITY
	 * at the server's current_version.xml version, e.g. 00.6.00. Thus the ServerValidation will still allow uploads.
	 *
	 * On major changes adjust server's current_version.xml version, SW_VERSION and VERSION_COMPATIBILITY
	 */
	public static final String VERSION_COMPATIBILITY = "00.8.00";

	/**
	 * Database scheme version, increment to trigger database update
	 */
	public static final int DATABASE_VERSION = 14;

    /**
     * Server host name excluding final slash
     */
	public static final String SERVER_BASE = "https://radiocells.org";

	public static final int PERM_REQUEST_LOCATION_NOTIFICATION = 555;

	/**
	 * Private dummy constructor
	 */
    private Constants() {

	}
}
