/*
 * Created on Feb 22, 2012
 * Author: Paul Woelfel
 * Email: frig@frig.at
 */
package org.openbmap.service.position;


/**
 * @author  Paul Woelfel (paul@woelfel.at)
 */
public class LocationServiceFactory {
	
	protected static LocationService ls = null;
	
	static void setLocationService(final LocationService ls){
		LocationServiceFactory.ls = ls;
	}
	
	public static LocationService getLocationService() {
		if (ls == null) {
//			throw new LocationServiceException("no location service defined!");
			ls = new LocationServiceImpl();
		}
		return ls;
	}
}
