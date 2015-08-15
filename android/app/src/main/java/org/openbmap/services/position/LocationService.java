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
   Inspirations from Paul Woelfel, Email: frig@frig.at
 */
package org.openbmap.services.position;

import android.location.Location;

import org.openbmap.services.position.providers.LocationProvider;

import java.util.List;

public interface LocationService {

	/**
	 * Returns current location
	 * 
	 * @return current location
	 */
	Location getLocation();

	/**
	 * <p>
	 * Updates the location service with the current position.<br />
	 * This method checks which position is more accurate and uses the most accurate. If force is set, the location will be forced to overwrite the current location.
	 * </p>
	 * 
	 * @param pos current position
	 * @param force overwrite if set 
	 * @return 
	 */
	Location updateLocation(Location pos, boolean force);

	/**
	 * <p>
	 * Updates the location service with the current position.<br />
	 * This method checks which position is more accurate and uses the most accurate
	 * </p>
	 * @param pos current position
	 */
	void updateLocation(Location pos);
	
	/**
	 * <p>register a new LocationProvider<br />
	 * should call the LocationProvider.setLocationService</p>
	 * @param provider new LocationProvider
	 * @see LocationProvider
	 */
	void registerProvider(LocationProvider provider);
	
	/**
	 * <p>Unregisters a LocationProvider<br />
	 * should call the LocationProvider.setLocationService</p>
	 * @param provider currently registerd LocationProvider
	 * @see LocationProvider
	 */
	void unregisterProvider(LocationProvider provider);
	
	/**
	 * <p>Registers a new LocationProvider by its class name.<br />
	 * This method uses the default constructor of the object to create an instance</p>
	 * @param provider classname of the LocationProvider
	 * @throws InstantiationException if the Class could not be instanced or the class is not a subclass of LocationProvider 
	 * @throws IllegalAccessException if the Class could not be accessed
	 * @throws ClassNotFoundException if the Class could not be found
	 */
	void registerProvider(String provider) throws InstantiationException, IllegalAccessException, ClassNotFoundException;

	
	/**
	 * get a list of currently registered LocationProviders
	 * @return List of registered LocationProviders
	 */
	List<LocationProvider> getLocationProviders();

	/**
	 * @return
	 */
	float getRelativeNorth();

}
