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

package org.openbmap.services.positioning;

import android.location.Location;
import android.util.Log;

import org.openbmap.services.positioning.providers.LocationProvider;
import org.openbmap.utils.GeometryUtils;

import java.util.List;
import java.util.Vector;

/**
 * Inspirations from Paul Woelfel, Email: frig@frig.at
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
		mProviders = new Vector<>();
		mLocation = new Location("dummy");
		LocationServiceFactory.setLocationService(this);
	}

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
				|| location.getTime() >= mLocation.getTime()
				|| (mLocation.getAccuracy() == -1
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

    @Override
	protected void finalize() throws Throwable {
        for (LocationProvider lp : mProviders ) {
            lp.unsetLocationService(this);
		}

		super.finalize();
	}

	@Override
	public final List<LocationProvider> getLocationProviders() {
		return mProviders;
	}

	public final void setRelativeNorth(final float angle) {
		this.angle = GeometryUtils.normalizeAngle(angle);
	}

	public final float getRelativeNorth() {
		return angle;
	}

}
