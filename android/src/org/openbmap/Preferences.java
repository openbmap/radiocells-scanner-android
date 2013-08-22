/**
 * 
 */
package org.openbmap;

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
	public static final String KEY_DATA_DIR = "data.dir";
	public static final String KEY_MAP_FILE = "data.map";

	public static final String KEY_DOWNLOAD_MAP = "data.download";	
	public static final String KEY_DOWNLOAD_WIFI_CATALOG = "data.download_wifi_catalog";
	public static final String KEY_WIFI_CATALOG = "data.ref_database";
	public static final String KEY_KEEP_SCREEN_ON = "ui.keep_screen_on";
	public static final String KEY_CREDENTIALS_USER = "credentials.user";
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
	 * Upload files or simulate only
	 */
	public static final String	KEY_SKIP_UPLOAD	= "debug.simulate_upload";
	
	/**
	 * Keep local temp files after upload?
	 */
	public static final String	KEY_SKIP_DELETE	= "debug.keep_export_files";
	
	/*
	 * Default values following ..
	 */
	
	/**
	 * Root folder for all additional data
	 */
	public static final String VAL_DATA_DIR = "/org.openbmap";
	
	/**
	 * Default map file name
	 */
	public static final String VAL_MAP_FILE = "nordrhein-westfalen.map";
	
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
	public static final boolean	VAL_SKIP_UPLOAD	= false;
	
	/**
	 * By default delete local temp files after upload
	 */
	public static final boolean	VAL_SKIP_DELETE	= false;
	
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
	public static final boolean	VAL_GPS_SAVE_COMPLETE_TRACK	= false;
	
	/**
	 * Check whether GPS is enabled by default
	 */
	public static final boolean VAL_GPS_CHECKSTARTUP = true;
	
	/**
	 * Get GPS position as often as possible by default
	 */
	public static final String VAL_GPS_LOGGING_INTERVAL = "0";
	
	/**
	 * Directory containing maps, relative to application root dir.
	 */
	public static final String MAPS_SUBDIR = "/maps";
	
	/**
	 * Directory containing ref database, relative to application root dir.
	 */
	public static final String WIFI_CATALOG_SUBDIR = "/databases";
	
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
	public static final String	WIFI_CATALOG_DOWNLOAD_URL = "https://dl.dropbox.com/s/8xp83mklfd6gt0k/openbmap.sqlite";
	
	/**
	 * URL, which is called to check whether this client version is out-of-date
	 */
	public static final String	VERSION_CHECK_URL = "http://www.openbmap.org/current_version.xml";
	
	/**
	 * Filename catalog database
	 */
	public static final String	WIFI_CATALOG_FILE	= "openbmap.sqlite";
	
	/**
	 * Private dummy constructor
	 */
	private Preferences() {
	
	}
}