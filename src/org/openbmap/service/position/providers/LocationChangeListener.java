/*
 * Created on Mar 31, 2012
 * Author: Paul Woelfel
 * Email: frig@frig.at
 */
package org.openbmap.service.position.providers;

import android.location.Location;

public interface LocationChangeListener {
	void onLocationChange(Location loc);
}
