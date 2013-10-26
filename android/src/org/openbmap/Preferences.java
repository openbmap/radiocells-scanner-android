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
package org.openbmap;

import java.io.File;

/**
 * Stores settings keys and default values.
 * See preferences.xml for layout, strings-preferences.xml for text.
 */
public final class Preferences {
	// Property names
	public static final String KEY_GPS_OSSETTINGS = "gps.ossettings";
	public static final String KEY_GPS_CHECKSTARTUP = "gps.checkstartup";
	public static final String KEY_GPS_LOGGING_INTERVAL = "gps.interval";
	// TODO add support for external (e.g. bluetooth gps provider)
	public static final String KEY_GPS_PROVIDER = "gps.provider";
	public static final String KEY_GPS_SAVE_COMPLETE_TRACK = "gps.save_track";
	public static final String KEY_DATA_FOLDER = "data.dir";
	public static final String KEY_MAP_FOLDER = "data.folder_map";
	public static final String KEY_WIFI_CATALOG_FOLDER = "data.folder_catalog";
	
	/**
	 * Selected map file
	 */
	public static final String KEY_MAP_FILE = "data.map";

	/**
	 * Map download button
	 */
	public static final String KEY_DOWNLOAD_MAP = "data.download";	
	
	/**
	 * Wifi catalog download button
	 */
	public static final String KEY_DOWNLOAD_WIFI_CATALOG = "data.download_wifi_catalog";
	
	/**
	 * Selected wifi catalog file
	 */
	public static final String KEY_WIFI_CATALOG_FILE = "data.ref_database";
	
	/**
	 * Keeps screen on during logging?
	 */
	public static final String KEY_KEEP_SCREEN_ON = "ui.keep_screen_on";
	
	/**
	 * Openbmap user name
	 */
	public static final String KEY_CREDENTIALS_USER = "credentials.user";
	
	/**
	 * Openbmap password
	 */
	public static final String KEY_CREDENTIALS_PASSWORD = "credentials.password";
	
	/**
	 * Shall cells be saved?
	 */
	public static final String KEY_SAVE_CELLS = "save_cells";
	
	/**
	 * Shall wifis be saved?
	 */
	public static final String KEY_SAVE_WIFIS = "save_wifis";
	
	/**
	 * Minimum distance between cells logged.
	 */
	public static final String KEY_MIN_CELL_DISTANCE = "logging.cell_distance";
	
	/**
	 * Minimum distance between wifis logged.
	 */
	public static final String KEY_MIN_WIFI_DISTANCE = "logging.wifi_distance";
	
	/**
	 * Required GPS accuracy
	 */
	public static final String KEY_REQ_GPS_ACCURACY = "logging.gps_accuracy";
	
	/**
	 * Simulate upload only?
	 */
	public static final String	KEY_SKIP_UPLOAD = "debug.simulate_upload";
	
	/**
	 * Clean database button
	 */
	public static final String	KEY_CLEAN_DATABASE = "debug.clean_database";
	
	/**
	 * Update wifi catalog button
	 */
	public static final String KEY_UPDATE_CATALOG = "debug.update_catalog";
	
	/**
	 * Keep local temp files after upload?
	 */
	public static final String	KEY_SKIP_DELETE = "debug.keep_export_files";
	
	/**
	 * Blocks wifi and cell scan around current location
	 */
	public static final String KEY_BLOCK_HOMEZONE = "privacy.block_homezone";
	
	/**
	 * Replace SSIDS by md5 hash on upload
	 */
	public static final String KEY_ANONYMISE_SSID = "privacy.anonymise_ssid";
	/*
	 * Default values following ..
	 */
	
	/**
	 * Root folder for all additional data
	 */
	public static final String VAL_DATA_FOLDER = "/org.openbmap";
	
	/**
	 * Default map file name
	 */
	public static final String VAL_MAP_FILE = "germany.map";
	
	/**
	 * No map set
	 */
	public static final String VAL_MAP_NONE = "none";
	
	/**
	 * Default reference database filename
	 */
	public static final String VAL_REF_DATABASE = "openbmap.sqlite";
	
	/**
	 * Reference database not set 
	 */
	public static final String VAL_WIFI_CATALOG_NONE = "none";
	
	/**
	 * Default minimum distance cells
	 */
	public static final String VAL_MIN_CELL_DISTANCE = "35";
	
	/**
	 * Default minimum distance wifis
	 */
	public static final String VAL_MIN_WIFI_DISTANCE = "5";
	
	/**
	 * Default GPS accuracy
	 */
	public static final String VAL_REQ_GPS_ACCURACY = "25";
	
	/**
	 * Default screen lock settings
	 */
	public static final boolean VAL_KEY_KEEP_SCREEN_ON = true;
	
	/**
	 * By default upload session
	 */
	public static final boolean	VAL_SKIP_UPLOAD = false;
	
	/**
	 * By default delete local temp files after upload
	 */
	public static final boolean	VAL_SKIP_DELETE = false;
	
	/**
	 * Save cells by default
	 */
	public static final boolean VAL_SAVE_CELLS = true;
	
	/**
	 * Save wifis by default
	 */
	public static final boolean VAL_SAVE_WIFIS = true;
	
	/**
	 * Save complete gps track is deactivated by default.
	 * If activated not only wifi and cell positions are saved,
	 * but the complete track. Useful for debugging purposes
	 */
	public static final boolean	VAL_GPS_SAVE_COMPLETE_TRACK = false;
	
	/**
	 * Check whether GPS is enabled by default
	 */
	public static final boolean VAL_GPS_CHECKSTARTUP = true;
	
	/**
	 * Get GPS position as often as possible by default
	 */
	public static final String VAL_GPS_LOGGING_INTERVAL = "0";
	
	/**
	 * Don't anonymise SSIDS by default
	 */
	public static final boolean VAL_ANONYMISE_SSID = false;
	
	/**
	 * Default maps folder name, relative to application root dir.
	 * Can be overwritten in settings by specifying KEY_MAP_FOLDER
	 */
	private static final String MAPS_SUBDIR = "maps";
	
	public static final String VAL_MAP_FOLDER = VAL_DATA_FOLDER  + File.separator + MAPS_SUBDIR;
	
	/**
	 * Default wifi catalog folder name, relative to application root dir.
	 * Can be overwritten in settings by specifying KEY_WIFI_CATALOG_FOLDER
	 */
	private static final String WIFI_CATALOG_SUBDIR = "databases";
	
	public static final String VAL_WIFI_CATALOG_FOLDER = VAL_DATA_FOLDER + File.separator + WIFI_CATALOG_SUBDIR;
	
	/**
	 * Directory containing wifi blacklists, relative to application root dir.
	 */
	public static final String BLACKLIST_SUBDIR = "blacklists";
	/**
	 * File extension for maps
	 */
	public static final String MAP_FILE_EXTENSION = ".map";
	
	/**
	 * File extension for wifi catalog
	 */
	public static final String WIFI_CATALOG_FILE_EXTENSION = ".sqlite";
	
	/**
	 * File extension for wifi and cell log files
	 */
	public static final String LOG_FILE_EXTENSION = ".xml";
	
	/**
	 * URL, where wifi catalog with openbmap's preprocessed wifi positions can be downloaded
	 */
	public static final String	WIFI_CATALOG_DOWNLOAD_URL = "http://googledrive.com/host/0B97gHr4MqjHpM2h0QVR5SWJOcGs/openbmap.sqlite";
	
	/**
	 * Filename catalog database
	 */
	public static final String	WIFI_CATALOG_FILE = "openbmap.sqlite";
	
	/**
	 * URL, which is called to check whether this client version is up-to-date
	 */
	public static final String	VERSION_CHECK_URL = "http://www.openbmap.org/current_version.xml";
	
	/**
	 * Private dummy constructor
	 */
	private Preferences() {
	
	}
}