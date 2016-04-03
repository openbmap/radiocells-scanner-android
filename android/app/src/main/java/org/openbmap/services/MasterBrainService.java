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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.Radiobeacon;
import org.openbmap.activities.TabHostActivity;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.Session;
import org.openbmap.events.onStartGpx;
import org.openbmap.events.onStartLocation;
import org.openbmap.events.onStartTracking;
import org.openbmap.events.onStartWireless;
import org.openbmap.events.onStopTracking;
import org.openbmap.services.positioning.GpxLoggerService;
import org.openbmap.services.positioning.PositioningService;
import org.openbmap.services.positioning.PositioningService.ProviderType;
import org.openbmap.services.wireless.WirelessLoggerService;

import java.util.ArrayList;

/**
 * MasterBrainService is the service coordinator, starting other sub-services as required
 * It's started as soon as Radiobeacon app starts and runs in the application context
 * It listens to StartTrackingEvent and StopTrackingEvent on the message bus as well as system's
 * low battery events
 */
public class MasterBrainService extends Service {
    private static final String TAG = MasterBrainService.class.getSimpleName();

    /** For showing and hiding our notification. */
    NotificationManager mNotificationManager;
    /** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();

    /**
     * System notification id.
     */
    private static final int NOTIFICATION_ID = 1235;

    /**
     * Selected navigation provider, default GPS
     */
    private ProviderType mSelectedProvider = ProviderType.GPS;

    /**
     * Unique powerlock id
     */
    private static final String WAKELOCK_NAME = "org.openbmap.wakelock";

    /**
     * Keeps the SharedPreferences.
     */
    private SharedPreferences mPrefs = null;

    private DataHelper mDataHelper;

    private PositioningService mPositioningService;
    private WirelessLoggerService mWirelessService;
    private GpxLoggerService mGpxService;
    private boolean mPositioningBound;
    private boolean mWirelessBound;
    private boolean mGpxBound;

    private PowerManager.WakeLock mWakeLock;

    /**
     * Current session
     */
    private int mSession = Radiobeacon.SESSION_NOT_TRACKING;
    private int mShutdownReason;

    /**
     * Handler of incoming messages from clients.
     */
    class UpstreamHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Radiobeacon.MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case Radiobeacon.MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private ServiceConnection mPositioningConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            AbstractService.LocalBinder binder = (AbstractService.LocalBinder) service;
            mPositioningService = (PositioningService) binder.getService();
            mPositioningBound = true;
            Log.d(TAG, "REQ StartPositioningEvent");
            EventBus.getDefault().post(new onStartLocation());
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mPositioningBound = false;
        }
    };

    private ServiceConnection mWirelessConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            AbstractService.LocalBinder binder = (AbstractService.LocalBinder) service;
            mWirelessService = (WirelessLoggerService) binder.getService();
            mWirelessBound = true;
            Log.d(TAG, "REQ StartWirelessEvent");
            EventBus.getDefault().post(new onStartWireless(mSession));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mWirelessBound = false;
        }
    };

    private ServiceConnection mGpxConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            AbstractService.LocalBinder binder = (AbstractService.LocalBinder) service;
            mGpxService = (GpxLoggerService) binder.getService();
            mGpxBound = true;
            Log.d(TAG, "REQ StartGpxEvent");
            EventBus.getDefault().post(new onStartGpx(mSession));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mGpxBound = false;
        }
    };

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mUpstreamMessenger = new Messenger(new UpstreamHandler());

    @Override
    public void onCreate() {
        Log.d(TAG, "MasterBrainService created");

        EventBus.getDefault().register(this);

        mDataHelper = new DataHelper(this);

        // get shared preferences
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        registerReceiver();
        // We want this service to continue running until it is explicitly stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver();
        hideNotification();
        EventBus.getDefault().unregister(this);

        unbindAll();
    }

    /**
     * Registers broadcast receiver
     */
    private void registerReceiver() {
        Log.i(TAG, "Registering broadcast receivers");
        final IntentFilter filter = new IntentFilter();
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
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mUpstreamMessenger.getBinder();
    }

    /**
     * Called when start tracking is requested on the message bus
     * @param event
     */
    @Subscribe
    public void onEvent(onStartTracking event){
        Log.d(TAG, "Received StartTracking event");
        mSession = event.session;
        requirePowerLock();
        startTracking(mSession);
    }

    /**
     * Called when stop tracking is requested on the message bus
     * @param event
     */
    @Subscribe
    public void onEvent(onStopTracking event){
        Log.d(TAG, "Received StopTrackingEvent event");
        stopTracking(Radiobeacon.SHUTDOWN_REASON_NORMAL);
        mSession = Radiobeacon.SESSION_NOT_TRACKING;
        releasePowerLock();
    }

    /**
     * Acquires wakelock to prevent CPU falling asleep
     */
    private void requirePowerLock() {
        final PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
        try {
            Log.i(TAG, "Acquiring wakelock " + WAKELOCK_NAME);
            mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_NAME);
            mWakeLock.setReferenceCounted(true);
        } catch (final Exception e) {
            Log.e(TAG, "Error acquiring wakelock " + WAKELOCK_NAME);
            e.printStackTrace();
        }
    }

    /**
     * Releases wakelock, if held
     */
    private void releasePowerLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            Log.i(TAG, "Releasing wakelock " + WAKELOCK_NAME);
            mWakeLock.release();
        }
        mWakeLock = null;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        /**
         * Handles start and stop service requests.
         */
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())) {
                Log.d(TAG, "ACTION_BATTERY_LOW received");
                final boolean ignoreBattery = mPrefs.getBoolean(Preferences.KEY_IGNORE_BATTERY, Preferences.VAL_IGNORE_BATTERY);
                if (!ignoreBattery) {
                    Toast.makeText(context, getString(R.string.battery_warning), Toast.LENGTH_LONG).show();
                    stopTracking(Radiobeacon.SHUTDOWN_REASON_LOW_POWER);
                } else {
                  Log.i(TAG, "Battery low but ignoring due to settings");
                }
            } else {
                Log.d(TAG, "Received intent " + intent.getAction().toString() + " but ignored");
            }
        }
    };

    /**
     * Prepares database and sub-services to start tracking
     * @param session
     */
    private void startTracking(final int session) {
        if (session != Radiobeacon.SESSION_NOT_TRACKING) {
            Log.d(TAG, "Preparing session " + session);
            mSession = session;
            resumeSession(session);
        } else {
            Log.d(TAG, "Preparing new session");
            mSession = setupNewSession();
        }
        bindAll();
        showNotification();
    }

    /**
     * Updates database and stops sub-services after stop request
     * @param reason
     */
    private void stopTracking(int reason) {
        unbindAll();

        updateDatabase();

        for (int i = mClients.size() - 1; i >= 0; i--) {
            try {
                mClients.get(i).send(Message.obtain(null, Radiobeacon.MSG_SERVICE_SHUTDOWN, reason, 0));
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }

        hideNotification();
    }

    /**
     * Binds all sub-services
     */
    private void bindAll() {
        Log.d(TAG, "Binding services");
        Intent i1 = new Intent(this, PositioningService.class);
        bindService(i1, mPositioningConnection, Context.BIND_AUTO_CREATE);

        Intent i2 = new Intent(this, WirelessLoggerService.class);
        bindService(i2, mWirelessConnection, Context.BIND_AUTO_CREATE);

        Intent i3 = new Intent(this, GpxLoggerService.class);
        bindService(i3, mGpxConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Unbinds all sub-services
     */
    private void unbindAll() {
        // Unbind from the service
        if (mPositioningBound) {
            unbindService(mPositioningConnection);
            mPositioningBound = false;
        }

        if (mWirelessBound) {
            unbindService(mWirelessConnection);
            mWirelessBound = false;
        }

        if (mGpxBound) {
            unbindService(mGpxConnection);
            mGpxBound = false;
        }
    }

    /**
     * Opens new session
     */
    private int setupNewSession() {
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
    private void resumeSession(final int id) {
        final Session resume = mDataHelper.loadSession(id);

        if (resume == null) {
            Log.e(TAG, "Error loading session " + id);
            return;
        }

        resume.isActive(true);
        mDataHelper.storeSession(resume, true);
    }

    /**
     * Updates number of cells and wifis and closes active session
     */
    private void updateDatabase() {
        final Session active = mDataHelper.loadActiveSession();
        if (active != null) {
            active.setWifisCount(mDataHelper.countWifis(active.getId()));
            active.setCellsCount(mDataHelper.countCells(active.getId()));
            active.setWaypointsCount(mDataHelper.countWaypoints(active.getId()));
            mDataHelper.storeSession(active, false);
        }
        mDataHelper.invalidateActiveSessions();
    }

    /**
     * Shows Android notification while this service is running.
     */
    private void showNotification() {
        // Set the icon, scrolling text and timestamp
        final Notification notification = new Notification(R.drawable.icon_greyed_25x25, getString(R.string.notification_caption),
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, TabHostActivity.class), 0);
        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getString(R.string.app_name), getString(R.string.notification_caption), contentIntent);

        /**
         * TODO display additional infos / actions
         */
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    /**
     * Hides Android notification
     */
    private void hideNotification() {
        mNotificationManager.cancel(NOTIFICATION_ID);
    }

}
