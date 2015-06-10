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

package org.openbmap.services.position.providers;

import org.openbmap.services.position.LocationService;
import org.openbmap.services.position.LocationServiceFactory;
import org.openbmap.services.position.PositioningService;

import android.content.Context;
import android.location.Location;

/**
Inspirations from Paul Woelfel, Email: frig@frig.at
*/

public abstract class LocationProviderImpl implements LocationProvider {

	private Location mLocation;
	
	protected LocationService locationService;
	
	protected PositioningService serviceContext;

	private boolean mIsRunning = false;
	
	protected LocationChangeListener mListener = null;
	
	public LocationProviderImpl(final Context ctx) {
		this(ctx, LocationServiceFactory.getLocationService());
	}
	
	public LocationProviderImpl(final Context ctx, final LocationService locationService) {
		mLocation = new Location("dummy");
		mLocation.setProvider(getProviderName());
		mLocation.setAccuracy(-1);
		locationService.registerProvider(this);
	}
	
	@Override
	public final void setLocationService(final LocationService service) {
		locationService = service;
	}

	@Override
	public final void unsetLocationService(final LocationService service) {
		locationService = null;
	}

	@Override
	public final String getProviderName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public final Location getLocation() {
		return mLocation;
	}
	
	@Override
	public void start(final PositioningService service) {
		mIsRunning = true;
		serviceContext = service;
	}

	@Override
	public void stop() {
		mIsRunning = false;
		serviceContext = null;
	}

	/**
	 * @return  the mIsRunning
	 */
	public final boolean isRunning() {
		return mIsRunning;
	}

	@Override
	public final void setLocationChangeListener(final LocationChangeListener listener) {
		this.mListener = listener;
	}


}
