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

import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.Radiobeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.PositionRecord;
import org.openbmap.events.onLocationUpdate;
import org.openbmap.events.onStartGpx;
import org.openbmap.events.onStopTracking;
import org.openbmap.services.AbstractService;

/**
 * Saves GPX track. This is mainly for debugging purposes and functionally not needed for wifi tracking.
 */
public class GpxLoggerService extends AbstractService {

	/**
	 * Minimum distance between two trackpoints in meters
	 */
	private static final int MIN_TRACKPOINT_DISTANCE = 3;

	private static final String TAG = GpxLoggerService.class.getSimpleName();

	/**
	 * Keeps the SharedPreferences
	 */
	private SharedPreferences prefs = null;

	/*
	 * last known location
	 */
	private Location mMostCurrentLocation = new Location("DUMMY");
	private String mMostCurrentLocationProvider;

	/**
	 * Are we currently tracking ?
	 */
	private boolean mIsTracking = false;

	/**
	 * Current session id
	 */
	private int mSessionId = Radiobeacon.SESSION_NOT_TRACKING;

	/*
	 * DataHelper for persisting recorded information in database
	 */
	private DataHelper mDataHelper;

	@Override
	public final void onCreate() {
		super.onCreate();
        Log.d(TAG, "GpxLoggerService created");

		// get shared preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		/*
		 * Setting up database connection
		 */
		mDataHelper = new DataHelper(this);
	}

	@Override
	public final void onDestroy() {
		if (mIsTracking) {
			// If we're currently tracking, save user data.
			stopTracking();
		}

		super.onDestroy();
	}

	/**
	 * Saves cell related information
	 * @param gpsLocation
	 */
	private void performGpsUpdate(final Location gpsLocation, final String source) {
		if (gpsLocation == null) {
			Log.e(TAG, "No GPS position available");
			return;
		}

		final PositionRecord pos = new PositionRecord(gpsLocation, mSessionId, source);

		// so far we set end position = begin position 
		mDataHelper.storePosition(pos);
	}

	@Override
	public final void onStartService() {
		EventBus.getDefault().register(this);
	}

	@Override
	public final void onStopService() {
		Log.d(TAG, "OnStopService called");
        EventBus.getDefault().unregister(this);
	}

    /**
	 * Starts gps logging .
	 * @param sessionId 
	 */
	private void startTracking(final int sessionId) {
		Log.d(TAG, "Start tracking on session " + sessionId);
		mIsTracking = true;
		mSessionId = sessionId;
		mMostCurrentLocation = new Location("dummy");
	}

	/**
	 * Stops gpx Logging
	 */
	private void stopTracking() {
		Log.d(TAG, "Stop tracking on session " + mSessionId);
		mIsTracking = false;
		mSessionId = Radiobeacon.SESSION_NOT_TRACKING;
	}

	/**
	 * Setter for mIsTracking
	 * @return true if we're currently tracking, otherwise false.
	 */
	public final boolean isTracking() {
		return mIsTracking;
	}

    @Subscribe
    public void onEvent(onStartGpx event) {
        Log.d(TAG, "ACK onStartGpx event");
        startTracking(event.session);
    }

    @Subscribe
    public void onEvent(onStopTracking event){
        Log.d(TAG, "ACK onStopTracking event");
        stopTracking();
        this.stopSelf();
    }

    @Subscribe
	public void onEvent(onLocationUpdate event){
		//Log.d(TAG, "ACK onLocationUpdate event");
        if (!mIsTracking) {
            return;
        }

        final Location location = event.location;
        final String source = event.source;

        if (location.distanceTo(mMostCurrentLocation) > MIN_TRACKPOINT_DISTANCE) {
            performGpsUpdate(location, source);
        }
        mMostCurrentLocation = location;
	}
}
