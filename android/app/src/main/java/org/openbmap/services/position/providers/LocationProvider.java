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
import org.openbmap.services.position.PositioningService;

import android.location.Location;

public interface LocationProvider {
	
	/**
	 * Sets the LocationService, which should be updated on location changes
	 * this is a callback by LocationService
	 * @param service update this service
	 */
	void setLocationService(LocationService service);
	
	/**
	 * Removes the LocationService, which should be updated on location changes
	 * this is a callback by LocationService
	 * @param service service to update
	 */
	void unsetLocationService(LocationService service);
	

	/**
	 * Returns the location provider's name
	 */
	 String getProviderName();
	 
	/**
	 * Gets current location
	 * @return current location
	 */
	 Location getLocation();
		
	/**
	 * stops the location provider
	 */
	void stop();
	

	/**
	 * Sets a listener , which will be informed on location change
	 * @param mListener
	 */
	void setLocationChangeListener(LocationChangeListener listener);

	/**
	 * Starts the location provider
	 * @param service
	 */
	void start(PositioningService service);
	
}
