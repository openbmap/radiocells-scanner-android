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

package org.openbmap.services;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.Constants;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.PositionRecord;
import org.openbmap.events.onGpxStart;
import org.openbmap.events.onGpxStop;
import org.openbmap.events.onLocationUpdated;

/**
 * Saves GPX track. This is mainly for debugging purposes and functionally not needed for wifi tracking.
 */
public class GpxLoggerService extends Service {

	private static final String TAG = GpxLoggerService.class.getSimpleName();

	/**
	 * Minimum distance between two trackpoints in meters
	 */
	private static final int MIN_TRACKPOINT_DISTANCE = 3;

	/*
	 * last known location
	 */
	private Location lastLocation = new Location("DUMMY");

	/**
	 * Are we currently tracking ?
	 */
    private boolean isTracking = false;

	/**
	 * Current session id
	 */
    private long session = Constants.SESSION_NOT_TRACKING;

	/*
	 * DataHelper for persisting recorded information in database
	 */
	private DataHelper dataHelper;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

	@Override
	public final void onCreate() {
		super.onCreate();
        Log.d(TAG, "GpxLoggerService created");
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        } else {
            Log.w(TAG, "Event bus receiver already registered");
        }

		/*
		 * Setting up database connection
		 */
		dataHelper = new DataHelper(this);
	}

	@Override
	public final void onDestroy() {
        if (isTracking) {
            // If we're currently tracking, save user data.
			stopTracking();
		}

        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        super.onDestroy();
        Log.i(TAG, "GpxLoggerService stopped");
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger upstreamMessenger = new Messenger(new ManagerService.UpstreamHandler());

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return upstreamMessenger.getBinder();
    }

    @Subscribe
    public void onEvent(onGpxStart event) {
        Log.d(TAG, "ACK onGpxStart event");
        startTracking(event.session);
    }

    @Subscribe
    public void onEvent(onGpxStop event) {
        Log.d(TAG, "ACK onGpxStop event");
        stopTracking();
        //this.stopSelf();
    }

	/**
	 * Saves cell related information
	 * @param location
	 */
    private void savePosition(final Location location, final String source) {
        if (location == null) {
			Log.e(TAG, "No GPS position available");
			return;
		}

		final PositionRecord pos = new PositionRecord(location, session, source);
		// so far we set end position = begin position
		dataHelper.savePosition(pos);
	}

    /**
	 * Starts gps logging .
	 * @param sessionId
	 */
	private void startTracking(final long sessionId) {
		Log.d(TAG, "Start tracking on session " + sessionId);
        isTracking = true;
        session = sessionId;
		lastLocation = new Location("dummy");
	}

	/**
	 * Stops gpx Logging
	 */
	private void stopTracking() {
		Log.d(TAG, "Stop tracking on session " + session);
        isTracking = false;
        session = Constants.SESSION_NOT_TRACKING;
    }

	/**
     * Setter for isTracking
     * @return true if we're currently tracking, otherwise false.
	 */
	public final boolean isTracking() {
        return isTracking;
    }

    @Subscribe
    public void onEvent(onLocationUpdated event) {
        //Log.d(TAG, "ACK onLocationUpdated event");
        if (!isTracking) {
            return;
        }

        final Location location = event.location;
        if (location == null) {
            return;
        }

        if (location.distanceTo(lastLocation) > MIN_TRACKPOINT_DISTANCE) {
            savePosition(location, "trackpoint");
        }
        lastLocation = location;
	}
}
