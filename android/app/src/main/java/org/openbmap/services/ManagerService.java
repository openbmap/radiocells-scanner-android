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
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.activities.tabs.TabHostActivity;
import org.openbmap.db.DataHelper;
import org.openbmap.db.models.Session;
import org.openbmap.events.onServiceShutdown;
import org.openbmap.events.onStartGpx;
import org.openbmap.events.onStartLocation;
import org.openbmap.events.onStartTracking;
import org.openbmap.events.onStartWireless;
import org.openbmap.events.onStopTracking;
import org.openbmap.services.positioning.GpxLoggerService;
import org.openbmap.services.positioning.PositioningService;
import org.openbmap.services.positioning.PositioningService.ProviderType;
import org.openbmap.services.wireless.ScannerService;

import java.util.ArrayList;

/**
 * ManagerService is the service coordinator, starting other sub-services as required
 * It's started as soon as Radiobeacon app starts and runs in the application context
 * It listens to StartTrackingEvent and StopTrackingEvent on the message bus as well as system's
 * low battery events
 */
public class ManagerService extends Service {
    private static final String TAG = ManagerService.class.getSimpleName();

    /** For showing and hiding our notification. */
    NotificationManager notificationManager;

    /**
     * System notification id.
     */
    private static final int NOTIFICATION_ID = 1235;

    /** Keeps track of all current registered clients. */
    ArrayList<Messenger> clients = new ArrayList<>();

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
    private SharedPreferences prefs = null;

    private DataHelper dataHelper;

    private PositioningService positioningService;
    private ScannerService wirelessService;
    private GpxLoggerService gpxTrackerService;
    private CatalogService catalogService;

    private boolean positioningBound;
    private boolean wirelessBound;
    private boolean gpxBound;
    private boolean poiBound;

    private PowerManager.WakeLock mWakeLock;

    /**
     * Current session
     */
    private int currentSession = RadioBeacon.SESSION_NOT_TRACKING;
    private int mShutdownReason;

    /**
     * Handler of incoming messages from clients.
     */
    static class UpstreamHandler extends Handler {
    }

    private ServiceConnection positioningConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            AbstractService.LocalBinder binder = (AbstractService.LocalBinder) service;
            positioningService = (PositioningService) binder.getService();
            positioningBound = true;
            Log.i(TAG, "PositioningService bound");
            EventBus.getDefault().post(new onStartLocation());
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.i(TAG, "PositioningService disconnected");
            positioningBound = false;
        }
    };

    private ServiceConnection wirelessConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            AbstractService.LocalBinder binder = (AbstractService.LocalBinder) service;
            wirelessService = (ScannerService) binder.getService();
            wirelessBound = true;
            Log.i(TAG, "WirelessService bound");
            EventBus.getDefault().post(new onStartWireless(currentSession));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            wirelessBound = false;
            Log.i(TAG, "WirelessService disconnected");
        }
    };

    private ServiceConnection gpxConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            AbstractService.LocalBinder binder = (AbstractService.LocalBinder) service;
            gpxTrackerService = (GpxLoggerService) binder.getService();
            gpxBound = true;
            Log.i(TAG, "GpxService bound");
            EventBus.getDefault().post(new onStartGpx(currentSession));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            gpxBound = false;
            Log.i(TAG, "GpxService disconnected");
        }
    };

    private ServiceConnection catalogConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            AbstractService.LocalBinder binder = (AbstractService.LocalBinder) service;
            catalogService = (CatalogService) binder.getService();
            poiBound = true;
            Log.i(TAG, "CatalogService bound");
            //EventBus.getDefault().post(new onStartPoi(currentSession));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            poiBound = false;
            Log.i(TAG, "CatalogService disconnected");
        }
    };

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger upstreamMessenger = new Messenger(new UpstreamHandler());

    @Override
    public void onCreate() {
        Log.d(TAG, "ManagerService created");

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);}
        else {
            Log.w(TAG, "Event bus receiver already registered");
        }

        dataHelper = new DataHelper(this);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
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
        cancelNotification();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
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
        }
    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return upstreamMessenger.getBinder();
    }

    /**
     * Called when start tracking is requested on the message bus
     * @param event
     */
    @Subscribe
    public void onEvent(onStartTracking event){
        Log.d(TAG, "Received StartTracking event");
        Log.i(TAG, "=============================================================================");
        Log.i(TAG, "Configuration");
        Log.i(TAG, "Ignore low battery: " + prefs.getBoolean(Preferences.KEY_IGNORE_BATTERY, Preferences.DEFAULT_IGNORE_BATTERY));
        Log.i(TAG, "External power source avail.: " + isExternalPowerAvailable(this));
        Log.i(TAG, "Min GPS accuracy: " + prefs.getString(Preferences.KEY_REQ_GPS_ACCURACY, Preferences.DEFAULT_REQ_GPS_ACCURACY));
        Log.i(TAG, "Scan mode: " + prefs.getString(Preferences.KEY_WIFI_SCAN_MODE, Preferences.DEFAULT_WIFI_SCAN_MODE));
        Log.i(TAG, "Map: " + prefs.getString(Preferences.KEY_MAP_FILE, Preferences.DEFAULT_MAP_FILE));
        Log.i(TAG, "Catalog: " + prefs.getString(Preferences.KEY_CATALOG_FILE, Preferences.DEFAULT_CATALOG_FILE));
        Log.i(TAG, "=============================================================================");
        startTracking(currentSession);
    }

    /**
     * Called when stop tracking is requested on the message bus
     * @param event
     */
    @Subscribe
    public void onEvent(onStopTracking event){
        Log.d(TAG, "Received StopTrackingEvent event");
        stopTracking(RadioBeacon.SHUTDOWN_REASON_NORMAL);
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
            Log.e(TAG, "Error acquiring wakelock " + WAKELOCK_NAME + e.toString(), e);
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

    /**
     * Receiver for system's low battery events
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())) {
                Log.d(TAG, "ACTION_BATTERY_LOW received");
                final boolean ignoreBattery = prefs.getBoolean(Preferences.KEY_IGNORE_BATTERY, Preferences.DEFAULT_IGNORE_BATTERY);
                if (!ignoreBattery) {
                    Toast.makeText(context, getString(R.string.battery_warning), Toast.LENGTH_LONG).show();
                    stopTracking(RadioBeacon.SHUTDOWN_REASON_LOW_POWER);
                } else {
                  Log.i(TAG, "Battery low but ignoring due to settings");
                }
            } else {
                Log.d(TAG, "Received intent " + intent.getAction() + " but ignored");
            }
        }
    };

    /**
     * Prepares database and sub-services to start tracking
     * @param session
     */
    private void startTracking(final int session) {
        currentSession = session;
        requirePowerLock();

        if (session != RadioBeacon.SESSION_NOT_TRACKING) {
            Log.d(TAG, "Preparing session " + session);
            currentSession = session;
            resumeSession(session);
        } else {
            Log.d(TAG, "Preparing new session");
            currentSession = newSession();
        }
        bindAll();
        showNotification();
    }

    /**
     * Updates database and stops sub-services after stop request
     * @param reason
     */
    private void stopTracking(int reason) {
        closeSession();
        currentSession = RadioBeacon.SESSION_NOT_TRACKING;

        unbindAll();
        EventBus.getDefault().post(new onServiceShutdown(reason));

        releasePowerLock();
        cancelNotification();

        stopSelf();
    }

    /**
     * Binds all sub-services
     */
    private void bindAll() {
        Log.d(TAG, "Binding services");
        Intent i1 = new Intent(this, PositioningService.class);
        bindService(i1, positioningConnection, Context.BIND_AUTO_CREATE);

        Intent i2 = new Intent(this, ScannerService.class);
        bindService(i2, wirelessConnection, Context.BIND_AUTO_CREATE);

        Intent i3 = new Intent(this, GpxLoggerService.class);
        bindService(i3, gpxConnection, Context.BIND_AUTO_CREATE);

        Intent i4 = new Intent(this, CatalogService.class);
        bindService(i4, catalogConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Unbinds all sub-services
     */
    private void unbindAll() {
        Log.d(TAG, "Unbinding services");

        if (positioningBound) {
            unbindService(positioningConnection);
            positioningBound = false;
        }

        if (wirelessBound) {
            unbindService(wirelessConnection);
            wirelessBound = false;
        }

        if (gpxBound) {
            unbindService(gpxConnection);
            gpxBound = false;
        }

        if (poiBound) {
            unbindService(catalogConnection);
            poiBound = false;
        }
    }

    /**
     * Creates a new sessions and adds session record to the database
     * Invalidates any other active sessions
     */
    private int newSession() {
        dataHelper.invalidateCurrentSessions();
        final Session active = new Session();
        active.setCreatedAt(System.currentTimeMillis());
        active.setLastUpdated(System.currentTimeMillis());
        active.setDescription("No description yet");
        active.isActive(true);

        // id can only be set after session has been stored to database.
        final Uri result = dataHelper.insertSession(active);
        final int id = Integer.valueOf(result.getLastPathSegment());
        active.setId(id);
        return id;
    }

    /**
     * Resumes specific session and updates database session record
     * @param id
     */
    private void resumeSession(final int id) {
        final Session resume = dataHelper.getSessionById(id);

        if (resume == null) {
            Log.e(TAG, "Error loading session " + id);
            return;
        }

        resume.isActive(true);
        dataHelper.insertSession(resume, true);
    }

    /**
     * Updates cell and wifi count for active session and closes active session
     */
    private void closeSession() {
        final Session active = dataHelper.getCurrentSession();
        if (active != null) {
            active.setWifisCount(dataHelper.countWifis(active.getId()));
            active.setCellsCount(dataHelper.countCells(active.getId()));
            active.setWaypointsCount(dataHelper.countWaypoints(active.getId()));
            dataHelper.insertSession(active, false);
        }
        dataHelper.invalidateCurrentSessions();
    }

    /**
     * Shows Android notification while this service is running.
     */
    private void showNotification() {
        PendingIntent intent = PendingIntent.getActivity(this, 0, new Intent(this, TabHostActivity.class), 0);
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext());
        builder.setAutoCancel(false);
        builder.setContentTitle(getString(R.string.app_name));
        builder.setContentText(getString(R.string.notification_caption));
        builder.setSmallIcon(R.drawable.icon_greyed_25x25);
        builder.setContentIntent(intent);
        builder.setOngoing(true);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Hides Android notification
     */
    private void cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    /**
     * Checks if device is connected to AC or external battery
     * @param context
     * @return true if external power source available
     */
    public static boolean isExternalPowerAvailable(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }
}
