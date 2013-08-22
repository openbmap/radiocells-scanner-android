/*
 * Created on Mar 19, 2012
 * Author: Paul Woelfel
 * Email: frig@frig.at
 */
package org.openbmap.service.position.providers;

import org.openbmap.service.position.LocationService;
import org.openbmap.service.position.LocationServiceFactory;
import org.openbmap.service.position.PositioningService;

import android.content.Context;
import android.location.Location;

/**
 * @author  Paul Woelfel (paul@woelfel.at)
 */
public abstract class LocationProviderImpl implements LocationProvider {

	private Location mLocation;
	
	protected LocationService locationService;
	
	protected PositioningService serviceContext;
	
	private Context mContext;

	private boolean mIsRunning = false;
	
	protected LocationChangeListener mListener = null;

	
	
	public LocationProviderImpl(final Context ctx) {
		this(ctx, LocationServiceFactory.getLocationService());
		this.mContext = ctx;
	}
	
	public LocationProviderImpl(final Context ctx, final LocationService locationService) {
		mLocation = new Location("dummy");
		mLocation.setProvider(getProviderName());
		mLocation.setAccuracy(-1);
		this.mContext = ctx;
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
	

	/* (non-Javadoc)
	 * @see at.fhstp.wificompass.userlocation.LocationProvider#start()
	 */
	@Override
	public void start(final PositioningService service) {
		mIsRunning = true;
		serviceContext = service;
	}


	/* (non-Javadoc)
	 * @see at.fhstp.wificompass.userlocation.LocationProvider#stop()
	 */
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


	/* (non-Javadoc)
	 * @see at.fhstp.wificompass.userlocation.LocationProvider#setLocationChangeListener(at.fhstp.wificompass.userlocation.LocationChangeListener)
	 */
	@Override
	public final void setLocationChangeListener(final LocationChangeListener listener) {
		this.mListener = listener;
	}


}
