/*
 * Created on Dec 8, 2011
 * Author: Paul Woelfel
 * Email: frig@frig.at
 */
package org.openbmap.service.position;

import java.util.Iterator;
import java.util.List;
import java.util.Vector;



import android.location.Location;
import android.util.Log;
import org.openbmap.service.position.providers.LocationProvider;
import org.openbmap.utils.GeometryToolBox;

/**
 * @author  Paul Woelfel
 */
public class LocationServiceImpl implements LocationService {

	protected static final String TAG = LocationServiceImpl.class.getSimpleName();

	protected Location mLocation;

	protected Vector<LocationProvider> mProviders;

	protected float angle=0f;

	static {
		// register as LocationService
		LocationServiceFactory.setLocationService(new LocationServiceImpl());
	}

	/**
	 * 
	 */
	public LocationServiceImpl() {
		mProviders = new Vector<LocationProvider>();
		mLocation = new Location("dummy");
		LocationServiceFactory.setLocationService(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.fhstp.aploc.interfaces.LocationService#getLocation()
	 */
	@Override
	public final Location getLocation() {
		return mLocation;
	}

	@Override
	public final Location updateLocation(final Location location, final boolean force) {
		/*
		 * updates location if force 
		 * or location information is newer
		 * or more accurate
		 * 
		 * maybe not always the newer location should be used, maybe it should sometimes use the more accurate
		 */

		if (force 
				|| location.getTime() >= mLocation.getTime() ||
				(
						mLocation.getAccuracy() == -1
						|| location.getAccuracy() >= 0 && (location.getAccuracy() < mLocation.getAccuracy()))
				) {
			mLocation = location;
		} else {
			Log.d(TAG, "Location not updated");
		}

		return mLocation;
	}

	@Override
	public final void updateLocation(final Location pos) {
		this.updateLocation(pos, false);
	}

	@Override
	public final void registerProvider(final LocationProvider provider) {
		mProviders.add(provider);
		provider.setLocationService(this);
	}

	@Override
	public final void registerProvider(final String provider) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		Object o = Class.forName(provider).newInstance();
		if (!(o instanceof LocationProvider)) {
			throw new InstantiationException("Provider " + provider + " is not a Provider");
		}
		this.registerProvider((LocationProvider) o);

	}

	@Override
	public final void unregisterProvider(final LocationProvider provider) {
		if (mProviders.contains(provider)) {
			mProviders.remove(provider);
		}
	}

	protected final void finalize() {
		for (Iterator<LocationProvider> it = mProviders.iterator(); it.hasNext();) {
			LocationProvider p = it.next();
			p.unsetLocationService(this);
		}
	}

	@Override
	public final List<LocationProvider> getLocationProviders() {
		return mProviders;
	}

	/* (non-Javadoc)
	 * @see at.fhstp.wificompass.userlocation.LocationService#setRelativeNorth(float)
	 */
	@Override
	public void setRelativeNorth(float angle) {
		this.angle =GeometryToolBox.normalizeAngle(angle);
	}

	@Override
	public float getRelativeNorth() {
		return angle;
	}
}
