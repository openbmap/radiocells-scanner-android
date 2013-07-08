package org.openbmap;

/**
 * Constants & app-wide variables.
 */
public final class RadioBeacon {

	/**
	 * The full Package name of RadioBeacon returned by calling
	 * RadioBeacon.class.getPackage().getName()
	 */
	public static final String PACKAGE_NAME = RadioBeacon.class.getPackage().getName();

	/**
	 * Intent to start services
	 */
	public static final String INTENT_START_SERVICES = RadioBeacon.PACKAGE_NAME + ".intent.START_SERVICES";

	/**
	 * Intent to stop services.
	 */
	public static final String INTENT_STOP_SERVICES = RadioBeacon.PACKAGE_NAME + ".intent.STOP_SERVICES";

	/**
	 * Intent to broadcast new GPS position
	 */
	public static final String INTENT_BROADCAST_POSITION = RadioBeacon.PACKAGE_NAME + ".intent.BROADCAST_GPS";
	
	/**
	 * Intent signalling new session has been saved
	 */
	public static final String INTENT_NEW_SESSION = RadioBeacon.PACKAGE_NAME + ".intent.SESSION_SAVED";
	
	/**
	 * Intent signalling new cell has been saved
	 */
	public static final String INTENT_NEW_CELL = RadioBeacon.PACKAGE_NAME + ".intent.CELL_SAVED";

	/**
	 * Intent signalling new wifi has been saved
	 */
	public static final String INTENT_NEW_WIFI = RadioBeacon.PACKAGE_NAME + ".intent.WIFI_SAVED";

	/**
	 * Intent signalling cells have been changed (e.g. deletes)
	 */
	public static final String INTENT_CELL_UPDATE = RadioBeacon.PACKAGE_NAME + ".intent.CELL_UPDATE";
	
	/**
	 * Intent signalling wifis have been changed (e.g. deletes)
	 */
	public static final String INTENT_WIFI_UPDATE = RadioBeacon.PACKAGE_NAME + ".intent.WIFI_UPDATE";
	
	/**
	 * Intent signalling sessions have been changed (e.g. deletes)
	 */
	public static final String	INTENT_SESSION_UPDATE = RadioBeacon.PACKAGE_NAME + ".intent.SESSION_UPDATE";
	
	/**
	 * Key for extra data "location" in Intent
	 */
	public static final String INTENT_KEY_LOCATION = "location";

	/**
	 * Key for extra data "uuid" in Intent
	 */
	public static final String INTENT_KEY_UUID = "uuid";
	
	/**
	 * time (in ms) we use to handle a key press as a long press
	 */
	public static final long LONG_PRESS_TIME = 1000;

	// Messages
	public static final int MSG_REGISTER_CLIENT = 1;

	public static final int MSG_REQUEST_STATUS = 2;

	public static final int MSG_UNREGISTER_CLIENT = 3;

	public static final int MSG_START_TRACKING = 4;
	
	public static final int MSG_STOP_TRACKING = 5;
	
	public static final int MSG_SERVICE_READY = 5;
	
	public static final String MSG_KEY = "msg";
	
	/**
	 * Session Id, when not currently tracking
	 */
	public static final int SESSION_NOT_TRACKING = 0;
	
	/**
	 * conversion factor for milliseconds
	 * 1 second equals 1000 milli-seconds
	 */
	public static final int MILLIS_IN_SECOND = 1000;
	
	/**
	 * SWID string for XML files
	 */
	public static final String SWID = "Radiobeacon";
	
	/**
	 * SW Version string for XML files
	 * Caution: If you change client version, always rember to change current_version.xml on server at the same time!
	 * Otherwise {@link StaleVersionChecker} will deny upload
	 */
	public static final String SW_VERSION = "00.6.00";

	/**
	 * Private dummy constructor
	 */
	private RadioBeacon() {
		
	}
}