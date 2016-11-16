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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.RadioBeacon;
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

	private static final String TAG = GpxLoggerService.class.getSimpleName();

	/**
	 * Minimum distance between two trackpoints in meters
	 */
	private static final int MIN_TRACKPOINT_DISTANCE = 3;

	/*
	 * last known location
	 */
	private Location lastLocation = new Location("DUMMY");
	private String mMostCurrentLocationProvider;

	/**
	 * Are we currently tracking ?
	 */
	private boolean mIsTracking = false;

	/**
	 * Current session id
	 */
	private int session = RadioBeacon.SESSION_NOT_TRACKING;

	/*
	 * DataHelper for persisting recorded information in database
	 */
	private DataHelper dataHelper;

	@Override
	public final void onCreate() {
		super.onCreate();
        Log.d(TAG, "GpxLoggerService created");

		/*
		 * Setting up database connection
		 */
		dataHelper = new DataHelper(this);
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
	 * @param location
	 */
	private void performGpsUpdate(final Location location, final String source) {
		if (location == null) {
			Log.e(TAG, "No GPS position available");
			return;
		}

		final PositionRecord pos = new PositionRecord(location, session, source);

		// so far we set end position = begin position
		dataHelper.storePosition(pos);
	}

	@Override
	public final void onStartService() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);}
        else {
            Log.w(TAG, "Event bus receiver already registered");
        }
	}

	@Override
	public final void onStopService() {
		Log.d(TAG, "OnStopService called");
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
	}

    /**
	 * Starts gps logging .
	 * @param sessionId
	 */
	private void startTracking(final int sessionId) {
		Log.d(TAG, "Start tracking on session " + sessionId);
		mIsTracking = true;
		session = sessionId;
		lastLocation = new Location("dummy");
	}

	/**
	 * Stops gpx Logging
	 */
	private void stopTracking() {
		Log.d(TAG, "Stop tracking on session " + session);
		mIsTracking = false;
		session = RadioBeacon.SESSION_NOT_TRACKING;
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

        if (location.distanceTo(lastLocation) > MIN_TRACKPOINT_DISTANCE) {
            performGpsUpdate(location, source);
        }
        lastLocation = location;
	}
}
