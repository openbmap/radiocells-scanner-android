/*
 * Created on Dec 8, 2011
 * Author: Paul Woelfel
 * Email: frig@frig.at
 */
package org.openbmap.service.position.providers;

import org.openbmap.service.position.LocationService;
import org.openbmap.service.position.PositioningService;

import android.location.Location;

/**
 * @author Paul Woelfel (paul@woelfel.at)
 */
public interface LocationProvider {
	
	/**
	 * set the LocationService, which should be updated on location changes
	 * this is a callback by LocationService
	 * @param service update this service
	 */
	void setLocationService(LocationService service);
	
	/**
	 * remove the LocationService, which should be updated on location changes
	 * this is a callback by LocationService
	 * @param service service to update
	 */
	void unsetLocationService(LocationService service);
	

	/**
	 * return the Name of the Location Provider
	 */
	 String getProviderName();
	 
	/**
	 * get current Location of the user
	 * @return current location
	 */
	 Location getLocation();
		
	/**
	 * stops the location provider
	 */
	void stop();
	

	/**
	 * set a mListener, which will be informed, if the location has changed
	 * @param mListener
	 */
	void setLocationChangeListener(LocationChangeListener listener);

	/**
	 * starts the location provider
	 * @param service
	 */
	void start(PositioningService service);
	
}
