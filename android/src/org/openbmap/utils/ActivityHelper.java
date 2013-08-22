/**
 * Helper class for setting display on
 */
package org.openbmap.utils;

import android.app.Activity;
import android.view.WindowManager;

/**
 * @author power
 *
 */
public final class ActivityHelper {

	public static void setKeepScreenOn(final Activity activity, final boolean keepScreenOn) {
	    // WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
		if (keepScreenOn) {
	      activity.getWindow().
	        addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	    } else {
	      activity.getWindow().
	        clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	    }
	  }
	
	/**
	 * Private dummy constructor
	 */
	private ActivityHelper() {
	
	}
	
}
