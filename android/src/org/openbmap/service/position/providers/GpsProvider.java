/**
 * 
 */
package org.openbmap.service.position.providers;

import org.openbmap.Preferences;
import org.openbmap.RadioBeacon;
import org.openbmap.service.position.LocationService;
import org.openbmap.service.position.LocationServiceFactory;
import org.openbmap.service.position.PositioningService;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;

/**
 * @author power
 *
 */
public class GpsProvider extends LocationProviderImpl implements LocationListener {

	private static final String	TAG	= GpsProvider.class.getSimpleName();

	private Context	mContext;

	/**
	 * LocationManager
	 */
	private LocationManager lmgr;

	private boolean	isGpsEnabled;

	private long gpsLoggingInterval;

	private long mLastGPSTimestamp;
	private Location mLastLocation = new Location("dummy");

	// TODO: use barometer if available
	public GpsProvider(final Context ctx) {
		this(ctx, LocationServiceFactory.getLocationService());
	}

	public GpsProvider(final Context ctx, final LocationService locationService) {
		super(ctx, locationService);
		mContext = ctx;
	}

	@Override
	public final void start(final PositioningService positioningService) {
		super.start(positioningService);
		
		//read the logging interval from preferences
		gpsLoggingInterval = Long.parseLong(PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext()).getString(
				Preferences.KEY_GPS_LOGGING_INTERVAL, Preferences.VAL_GPS_LOGGING_INTERVAL)) * RadioBeacon.MILLIS_IN_SECOND;

		// Register ourselves for location updates
		lmgr = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
		lmgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, gpsLoggingInterval, 0, this);
	}

	@Override
	public final void stop() {
		super.stop();
		lmgr.removeUpdates(this);
	}


	/* (non-Javadoc)
	 * @see android.location.LocationListener#onProviderDisabled(java.lang.String)
	 */
	@Override
	public final void onProviderDisabled(final String provider) {
		isGpsEnabled = false;
	}

	/* (non-Javadoc)
	 * @see android.location.LocationListener#onProviderEnabled(java.lang.String)
	 */
	@Override
	public final void onProviderEnabled(final String provider) {
		isGpsEnabled = true;
	}

	/* (non-Javadoc)
	 * @see android.location.LocationListener#onStatusChanged(java.lang.String, int, android.os.Bundle)
	 */
	@Override
	public void onStatusChanged(final String provider, final int status, final Bundle extras) {
		// Not interested in provider status			
	}

	/* (non-Javadoc)
	 * @see android.location.LocationListener#onLocationChanged(android.location.Location)
	 */
	@Override
	public final void onLocationChanged(final Location location) {		
		// We're receiving location, so GPS is enabled
		isGpsEnabled = true;
		
		// first of all we check if the time from the last used fix to the current fix is greater than the logging interval
		if ((mLastGPSTimestamp + gpsLoggingInterval) < System.currentTimeMillis()) {
			mLastGPSTimestamp = System.currentTimeMillis(); // save the time of this fix			
			mLastLocation = location;
			
			locationService.updateLocation(location);

			if (mListener != null) {
				mListener.onLocationChange(location);
			}
		}
	}
}
