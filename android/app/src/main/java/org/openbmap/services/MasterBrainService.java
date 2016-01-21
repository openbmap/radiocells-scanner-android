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
 * Idea to communicate between Activity and Service comes from:
 * @Author: Philipp C. Heckel; based on code by Lance Lefebure 
 * @see: http://stackoverflow.com/questions/4300291/example-communication-between-activity-and-service-using-messaging         
 * @see: https://code.launchpad.net/~binwiederhier/+junk/android-service-example
 *
 * 1. Implement a service by inheriting from AbstractService
 * 2. Add a ServiceManager to your activity
 *   - Control the service with ServiceManager.start() and .stop()
 *   - Send messages to the service via ServiceManager.send() 
 *   - Receive messages with by passing a Handler in the constructor
 * 3. Send and receive messages on the service-side using send() and onReceiveMessage()
 */
package org.openbmap.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.activities.HostActivity;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.Session;
import org.openbmap.services.position.GpxLoggerService;
import org.openbmap.services.position.PositioningService;
import org.openbmap.services.position.PositioningService.State;
import org.openbmap.services.wireless.WirelessLoggerService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MasterBrainService extends Service {
    private static final String TAG = MasterBrainService.class.getSimpleName();

    /** For showing and hiding our notification. */
    NotificationManager mNM;
    /** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    /** Holds last value set by a client. */
    int mValue = 0;

    /**
     * Command to service to set a new value.  This can be sent to the
     * service to supply a new value, and will be sent by the service to
     * any registered clients with the new value.
     */
    public static final int MSG_SET_VALUE = 3;

    /**
     * System notification id.
     */
    private static final int NOTIFICATION_ID = 1235;

    /**
     * Selected navigation provider, default GPS
     */
    private PositioningService.State mSelectedProvider = PositioningService.State.GPS;

    /**
     * Keeps the SharedPreferences.
     */
    private SharedPreferences mPrefs = null;

    private DataHelper mDataHelper;

    /** Messenger for communicating with service. */
    Messenger mPositioningService = null;

    /** Messenger for communicating with service. */
    Messenger mGpxService = null;

    /** Messenger for communicating with service. */
    Messenger mWirelessService = null;

    /** Messenger for communicating with service. */
    Messenger mService = null;
    private boolean mIsPositioningBound;
    private boolean mIsGpxBound;
    private boolean mIsWirelessBound;

    /**
     * Background service collecting cell and wifi infos.
     */
    private DownstreamConnection mWirelessServiceManager;

    /**
     * Background gps location service.
     */
    private DownstreamConnection mPositioningServiceManager;

    /**
     * Background gpx logger server
     */
    private DownstreamConnection mGpxLoggerServiceManager;
    private DownstreamHandler mDownstreamHandler;

    /**
     * Handler of incoming messages from clients.
     */
    class UpstreamHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RadioBeacon.MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case RadioBeacon.MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                /*
                case RadioBeacon.MSG_SET_VALUE:
                    mValue = msg.arg1;
                    for (int i=mClients.size()-1; i>=0; i--) {
                        try {
                            mClients.get(i).send(Message.obtain(null,
                                    MSG_SET_VALUE, mValue, 0));
                        } catch (RemoteException e) {
                            // The client is dead.  Remove it from the list;
                            // we are going through the list from back to front
                            // so this is safe to do inside the loop.
                            mClients.remove(i);
                        }
                    }
                    break;*/
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Reacts on messages from gps location service
     */
    private static class DownstreamHandler extends Handler {
        private final WeakReference<MasterBrainService> mService;

        DownstreamHandler(final MasterBrainService activity) {
            mService = new WeakReference<MasterBrainService>(activity);
        }

        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case RadioBeacon.MSG_SERVICE_READY:
                    // start tracking immediately after service is ready

                    if (mService != null) {
                        final MasterBrainService master =  mService.get();
                        if (msg.getData() != null) {
                            // start updates as soon as services are ready
                            final String service = msg.getData().getString("service");
                            if (service.equals(".services.position.PositioningService")) {
                                Log.d(TAG, "Positioning service ready. Requesting position updates");
                                master.requestPositionUpdates(State.GPS);
                            } else if (service.equals(".services.wireless.WirelessLoggerService")) {
                                Log.d(TAG, "Wireless logger service ready. Requesting wireless updates");
                                master.requestWirelessUpdates();
                            } else if (service.equals(".services.position.GpxLoggerService")) {
                                Log.d(TAG, "GPX logger service ready. Requesting gpx tracking");
                                master.requestGpxTracking();
                            } else {
                                Log.w(TAG, "Unknown service " + service);
                            }
                        }
                    }
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }


    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mUpstreamMessenger = new Messenger(new UpstreamHandler());

    @Override
    public void onCreate() {
        Log.d(TAG, "MasterBrainService created");

        mDataHelper = new DataHelper(this);
        mDownstreamHandler = new DownstreamHandler(this);
        
        // get shared preferences
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        registerReceiver();
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION_ID);
        unregisterReceiver();

        stopServices();
    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mUpstreamMessenger.getBinder();
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.icon_greyed_25x25, getString(R.string.notification_caption),
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, HostActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getString(R.string.app_name), getString(R.string.notification_caption), contentIntent);

        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNM.notify(NOTIFICATION_ID, notification);
    }

    /**
     * Receives GPS location updates
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        /**
         * Handles start and stop service requests.
         */
        @Override
        public void onReceive(final Context context, final Intent intent) {
			if (RadioBeacon.INTENT_START_TRACKING.equals(intent.getAction())) {
                Log.d(TAG, "INTENT_START_TRACKING received");
				startServices();
                int current_id = RadioBeacon.SESSION_NOT_TRACKING;
                if (intent.hasExtra("_id")) {
                    current_id = intent.getIntExtra("_id", RadioBeacon.SESSION_NOT_TRACKING);
                    resumeSessionCommand(current_id);
                } else {
                    current_id = newSessionCommand();
                }
                requestPositionUpdates(State.GPS);
                requestWirelessUpdates();
                requestGpxTracking();
                showNotification();
            } else if (RadioBeacon.INTENT_STOP_TRACKING.equals(intent.getAction())) {
                Log.d(TAG, "INTENT_STOP_TRACKING received");

                updateSessionStats();
                closeActiveSession();
                // stops background services
                stopServices();

                mValue = RadioBeacon.SHUTDOWN_REASON_NORMAL;
                for (int i = mClients.size() - 1; i >= 0; i--) {
                    try {
                        mClients.get(i).send(Message.obtain(null,
                                RadioBeacon.MSG_SERVICE_SHUTDOWN, mValue, 0));
                    } catch (RemoteException e) {
                        // The client is dead.  Remove it from the list;
                        // we are going through the list from back to front
                        // so this is safe to do inside the loop.
                        mClients.remove(i);
                    }
                }

                mNM.cancel(NOTIFICATION_ID);

            } else if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())) {
                Log.d(TAG, "ACTION_BATTERY_LOW received");
                final boolean ignoreBattery = mPrefs.getBoolean(Preferences.KEY_IGNORE_BATTERY, Preferences.VAL_IGNORE_BATTERY);
                if (!ignoreBattery) {
                    Toast.makeText(context, getString(R.string.battery_warning), Toast.LENGTH_LONG).show();
                    updateSessionStats();
                    // invalidates active track
                    closeActiveSession();
                    // stops background services
                    stopServices();

                    mValue = RadioBeacon.SHUTDOWN_REASON_LOW_POWER;
                    for (int i = mClients.size() - 1; i >= 0; i--) {
                        try {
                            mClients.get(i).send(Message.obtain(null,
                                    RadioBeacon.MSG_SERVICE_SHUTDOWN, mValue, 0));
                        } catch (RemoteException e) {
                            // The client is dead.  Remove it from the list;
                            // we are going through the list from back to front
                            // so this is safe to do inside the loop.
                            mClients.remove(i);
                        }
                    }
                } else {
                  Log.i(TAG, "Battery low but ignoring due to settings");
                }
            } else {
                Log.d(TAG, "Received intent " + intent.getAction().toString() + " but ignored");
            }

        }
    };

    /**
     * Setups services. Any running services will be restart.
     */
    private void startServices() {
        stopServices();

        Log.d(TAG, "Starting Services");
        if (mPositioningServiceManager == null) {
            mPositioningServiceManager = new DownstreamConnection(this, PositioningService.class, mDownstreamHandler);
        }
        mPositioningServiceManager.bindAndStart();

        if (mWirelessServiceManager == null) {
            mWirelessServiceManager = new DownstreamConnection(this, WirelessLoggerService.class, mDownstreamHandler);
        }
        mWirelessServiceManager.bindAndStart();

        if (mGpxLoggerServiceManager == null) {
            mGpxLoggerServiceManager = new DownstreamConnection(this, GpxLoggerService.class, mDownstreamHandler);
        }
        mGpxLoggerServiceManager.bindAndStart();
    }

    /**
     * Stops GPS und wireless logger services.
     */
    private void stopServices() {
        Log.d(TAG, "Stopping Services");
        // Brute force: specific ServiceManager can be null, if service hasn't been started
        // Service status is ignored, stop message is send regardless of whether started or not
        try {
            mPositioningServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));
            // deactivated: let's call this from the service itself
            // positionServiceManager.unbindAndStop();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to stop gpsPositionServiceManager. Is service runnign?" /*+ e.getMessage()*/);
            //e.printStackTrace();
        }

        try {
            mWirelessServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));
            // deactivated: let's call this from the service itself
            // wirelessServiceManager.unbindAndStop();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to stop wirelessServiceManager. Is service running?" /*+ e.getMessage()*/);
            //e.printStackTrace();
        }

        try {
            mGpxLoggerServiceManager.sendAsync(Message.obtain(null, RadioBeacon.MSG_STOP_TRACKING));
            // deactivated: let's call this from the service itself
            // gpxLoggerServiceManager.unbindAndStop();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to stop gpxLoggerServiceManager. Is service running?" /*+ e.getMessage()*/);
            //e.printStackTrace();
        }
    }


    /**
     * Registers broadcast receiver
     */
    private void registerReceiver() {
        Log.i(TAG, "Registering broadcast receivers");
        final IntentFilter filter = new IntentFilter();
        filter.addAction(RadioBeacon.INTENT_START_TRACKING);
        filter.addAction(RadioBeacon.INTENT_STOP_TRACKING);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        registerReceiver(mReceiver, filter);
    }

    /**
     * Unregisters broadcast receiver
     */
    private void unregisterReceiver() {
        try {
            Log.i(TAG, "Unregistering broadcast receivers");
            unregisterReceiver(mReceiver);
        } catch (final IllegalArgumentException e) {
            // do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android}
            return;
        }
    }

    /**
     * Opens new session
     */
    private int newSessionCommand() {
        // invalidate all active session
        mDataHelper.invalidateActiveSessions();
        // Create a new session and activate it
        // Then start HostActivity. HostActivity onStart() and onResume() check active session
        // and starts services for active session
        final Session active = new Session();
        active.setCreatedAt(System.currentTimeMillis());
        active.setLastUpdated(System.currentTimeMillis());
        active.setDescription("No description yet");
        active.isActive(true);
        // id can only be set after session has been stored to database.
        final Uri result = mDataHelper.storeSession(active);
        final int id = Integer.valueOf(result.getLastPathSegment());
        active.setId(id);
        return id;
    }

    /**
     * Resumes specific session
     * @param id
     */
    private void resumeSessionCommand(final int id) {
        // TODO: check whether we need a INTENT_START_SERVICE here
        final Session resume = mDataHelper.loadSession(id);

        if (resume == null) {
            Log.e(TAG, "Error loading session " + id);
            return;
        }

        resume.isActive(true);
        mDataHelper.storeSession(resume, true);
    }

    /**
     * Updates number of cells and wifis.
     */
    private void updateSessionStats() {
        final Session active = mDataHelper.loadActiveSession();
        if (active != null) {
            active.setWifisCount(mDataHelper.countWifis(active.getId()));
            active.setCellsCount(mDataHelper.countCells(active.getId()));
            active.setWaypointsCount(mDataHelper.countWaypoints(active.getId()));
            mDataHelper.storeSession(active, false);
        }
    }

    /**
     * Closes active session
     */
    private void closeActiveSession() {
        mDataHelper.invalidateActiveSessions();
    }

    /**
     * Starts broadcasting GPS position.
     * @param provider
     * @return false on error, otherwise true
     */
    public final boolean requestPositionUpdates(final PositioningService.State provider) {
        // TODO check whether services have already been connected (i.e. received MSG_SERVICE_READY signal)
        Log.d(TAG, "Requesting position updates");
        final Bundle bundle = new Bundle();
        bundle.putString("provider", provider.toString());
        return requestUpdates(mPositioningServiceManager, bundle);
    }

    /**
     * Starts GPX tracking.
     * @return false on error, otherwise true
     */
    public final boolean requestGpxTracking() {
        // TODO check whether services have already been connected (i.e. received MSG_SERVICE_READY signal)
        Log.d(TAG, "Requesting gpx tracking");
        return requestUpdates(mPositioningServiceManager, null);
    }

    /**
     * Starts wireless tracking.
     * @return false on error, otherwise true
     */
    public final boolean requestWirelessUpdates() {
        // TODO check whether services have already been connected (i.e. received MSG_SERVICE_READY signal)
        Log.d(TAG, "Requesting wireless updates");
        return requestUpdates(mWirelessServiceManager, null);
    }

    public final boolean requestUpdates(DownstreamConnection service, Bundle bundle){
        try {
            if (service == null) {
                Log.w(TAG, "Service is null. No message will be sent");
                return false;
            }

            final int session = mDataHelper.getActiveSessionId();

            if (session == RadioBeacon.SESSION_NOT_TRACKING) {
                Log.e(TAG, "Couldn't start tracking, no active session");
                return false;
            }

            if (bundle == null) {
                bundle = new Bundle();
            }
            bundle.putInt(RadioBeacon.MSG_KEY, session);

            final Message msg = new Message();
            msg.what = RadioBeacon.MSG_START_TRACKING;
            msg.setData(bundle);

            service.sendAsync(msg);

            updateUI();
            return true;
        } catch (final RemoteException e) {
            // service communication failed
            e.printStackTrace();
            return false;
        } catch (final NumberFormatException e) {
            e.printStackTrace();
            return false;
        } catch (final Exception e) {
            e.printStackTrace();
            return false;
        }
    }



    /**
     * Broadcasts requests for UI refresh on wifi and cell info.
     */
    private void updateUI() {
        final Intent intent1 = new Intent(RadioBeacon.INTENT_WIFI_UPDATE);
        sendBroadcast(intent1);
        final Intent intent2 = new Intent(RadioBeacon.INTENT_CELL_UPDATE);
        sendBroadcast(intent2);
    }
}
