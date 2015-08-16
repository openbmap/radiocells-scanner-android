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

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import org.openbmap.RadioBeacon;

import java.lang.ref.WeakReference;

public class DownstreamConnection {

    private static final String TAG = DownstreamConnection.class.getSimpleName();

    private final Class<? extends AbstractService> mServiceClass;
    private final Context mManager;
    /*
     * Signals whether service is bound.
     * Be careful: This doesn't mean that service already has been created yet.
     * @see https://groups.google.com/forum/?fromgroups=#!topic/android-developers/aQHDzzQMjQs
     */
    private boolean mIsBound = false;
    private boolean mIsServiceReady = false;

    private Messenger mService = null;
    private Handler mUpstreamHandler = null;
    private final Messenger mUpstreamMessenger = new Messenger(new ForwardingHandler(this));

    /**
     * Handler used to pass messages to activity.
     */
    private static class ForwardingHandler extends Handler {
        private final WeakReference<DownstreamConnection> mWeakRef;

        public ForwardingHandler(final DownstreamConnection con) {
            mWeakRef = new WeakReference<DownstreamConnection>(con);
        }

        /**
         * Forwards message upstream
         */
        @Override
        public void handleMessage(final Message msg) {
            Log.d(TAG, "Service Manager received message " + msg.toString());
            final DownstreamConnection con = mWeakRef.get();
            if (con.mUpstreamHandler != null) {
                Log.i(TAG, "Incoming message from service. Passing to handler: " + msg);
                con.mUpstreamHandler.handleMessage(msg);
            }
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        /*
         * (non-Javadoc)
         * @see android.content.ServiceConnection#onServiceConnected(android.content.ComponentName, android.os.IBinder)
         */
        public void onServiceConnected(final ComponentName className, final IBinder service) {
            mService = new Messenger(service);
            Log.i(TAG, "Attached " + className.toShortString());
            try {
                // register at service
                final Message serviceMsg = Message.obtain(null, RadioBeacon.MSG_REGISTER_CLIENT);
                serviceMsg.replyTo = mUpstreamMessenger;
                mService.send(serviceMsg);

                // inform activity that service is ready
                final Message msg = new Message();
                msg.what = RadioBeacon.MSG_SERVICE_READY;
                Bundle info = new Bundle();
                info.putString("service", className.getShortClassName());
                msg.setData(info);
                mUpstreamMessenger.send(msg);

                mIsServiceReady = true;
            } catch (final RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
                mIsServiceReady = false;
                e.printStackTrace();
            }
        }

        /*
         * (non-Javadoc)
         * @see android.content.ServiceConnection#onServiceDisconnected(android.content.ComponentName)
         */
        public void onServiceDisconnected(final ComponentName className) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
            mService = null;
            mIsServiceReady = false;
            Log.i(TAG, "Disconnected " + className.toShortString());
        }
    };

    public DownstreamConnection(final Context context, final Class<? extends AbstractService> serviceClass, final Handler handler) {
        this.mManager = context;
        this.mServiceClass = serviceClass;
        this.mUpstreamHandler = handler;

        bindAndStart();
    }

    public final void bindAndStart() {
        doBindService();
        doStartService();
    }

    public final void unbindAndStop() {
        doStopService();
        doUnbindService();
    }

    /**
     * Use with caution (only in Activity.onDestroy())!
     */
    public final void unbind() {
        doUnbindService();
    }

    public final boolean isRunning() {
        final ActivityManager manager = (ActivityManager) mManager.getSystemService(Context.ACTIVITY_SERVICE);

        for (final RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (mServiceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sends message to service.
     * @param msg
     * @throws RemoteException
     */
    public final void sendAsync(final Message msg) throws RemoteException {
        if (mService != null) {
            mService.send(msg);
        }
    }

    private void doStartService() {
        mManager.startService(new Intent(mManager, mServiceClass));
    }

    private void doStopService() {
        mManager.stopService(new Intent(mManager, mServiceClass));
    }

    private void doBindService() {
        mManager.bindService(new Intent(mManager, mServiceClass), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    private void doUnbindService() {
        // If we have received the service, and hence registered with it, then now is the time to unregister.
        if (mService != null) {
            try {
                final Message msg = Message.obtain(null, RadioBeacon.MSG_UNREGISTER_CLIENT);
                msg.replyTo = mUpstreamMessenger;
                mService.send(msg);
            } catch (final RemoteException e) {
                e.printStackTrace();
            }
        }

        if (mConnection != null) {
            try {
                Log.i(TAG, "Unbindung service connection");
                mManager.unbindService(mConnection);
            } catch (final IllegalArgumentException e) {
                Log.w(TAG, "Service wasn't registered, thus unbinding failed");
            }

        }
        mIsBound = false;
        Log.i(TAG, "Unbinding completed.");
    }
}