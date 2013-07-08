/* 
 * This example demonstrates a good way to communicate between Activity and Service.
 * 
 * 1. Implement a service by inheriting from AbstractService
 * 2. Add a ServiceManager to your activity
 *   - Control the service with ServiceManager.start() and .stop()
 *   - Send messages to the service via ServiceManager.send() 
 *   - Receive messages with by passing a Handler in the constructor
 * 3. Send and receive messages on the service-side using send() and onReceiveMessage()
 * 
 * Author: Philipp C. Heckel; based on code by Lance Lefebure from
 *         http://stackoverflow.com/questions/4300291/example-communication-between-activity-and-service-using-messaging
 * Source: https://code.launchpad.net/~binwiederhier/+junk/android-service-example
 * Date:   6 Jun 2012
 */
package org.openbmap.service;

import java.lang.ref.WeakReference;

import org.openbmap.RadioBeacon;


import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class ServiceManager {

	private static final String TAG = ServiceManager.class.getSimpleName();

	private Class<? extends AbstractService> mServiceClass;
	private Context mActivity;
	/*
	 * Signals whether service is bound.
	 * Be careful: This doesn't mean that service already has been created yet.
	 * @see https://groups.google.com/forum/?fromgroups=#!topic/android-developers/aQHDzzQMjQs
	 */
	private boolean mIsBound = false;
	private boolean mIsServiceReady = false;

	private Messenger mService = null;
	private Handler mActivityHandler = null;
	private final Messenger mActivityMessenger = new Messenger(new ActivityHandler(this));

	/**
	 * Handler used to pass messages to activity.
	 */
	private static class ActivityHandler extends Handler {
		private final WeakReference<ServiceManager> mWeakRef;

		public ActivityHandler(final ServiceManager activity) {
			mWeakRef = new WeakReference<ServiceManager>(activity);
		}

		/**
		 * Forwards message to activity
		 */
		@Override
		public void handleMessage(final Message msg) {
			Log.d(TAG, "Service Manager received message " + msg.toString());
			ServiceManager manager = mWeakRef.get();
			if (manager.mActivityHandler != null) {
				Log.i(TAG, "Incoming message from service. Passing to handler: " + msg);
				manager.mActivityHandler.handleMessage(msg);
			}
		}
	}

	private ServiceConnection mConnection = new ServiceConnection() {

		/*
		 * (non-Javadoc)
		 * @see android.content.ServiceConnection#onServiceConnected(android.content.ComponentName, android.os.IBinder)
		 */
		public void onServiceConnected(final ComponentName className, final IBinder service) {
			mService = new Messenger(service);
			Log.i(TAG, "Attached " + className.toShortString());
			try {
				// register at service
				Message serviceMsg = Message.obtain(null, AbstractService.MSG_REGISTER_CLIENT);
				serviceMsg.replyTo = mActivityMessenger;
				mService.send(serviceMsg);
				
				// inform activity that service is ready
				Message activityMsg = new Message(); 
				activityMsg.what = RadioBeacon.MSG_SERVICE_READY;		
				mActivityMessenger.send(activityMsg);
				
				mIsServiceReady = true;
			} catch (RemoteException e) {
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

	public ServiceManager(final Context context, final Class<? extends AbstractService> serviceClass, final Handler activityHandler) {
		this.mActivity = context;
		this.mServiceClass = serviceClass;
		mActivityHandler = activityHandler;

		bindAndStart();

	}

	public final void bindAndStart() {
		doBindService();
		doStartService();
	}

	public final void unbindAndStop() {
		doUnbindService();
		doStopService();	
	}

	/**
	 * Use with caution (only in Activity.onDestroy())! 
	 */
	public final void unbind() {
		doUnbindService();
	}

	public final boolean isRunning() {
		ActivityManager manager = (ActivityManager) mActivity.getSystemService(Context.ACTIVITY_SERVICE);

		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
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
		mActivity.startService(new Intent(mActivity, mServiceClass));    	
	}

	private void doStopService() {
		mActivity.stopService(new Intent(mActivity, mServiceClass));
	}

	private void doBindService() {
		mActivity.bindService(new Intent(mActivity, mServiceClass), mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	private void doUnbindService() {

		// If we have received the service, and hence registered with it, then now is the time to unregister.
		if (mService != null) {
			try {
				Message msg = Message.obtain(null, AbstractService.MSG_UNREGISTER_CLIENT);
				msg.replyTo = mActivityMessenger;
				mService.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

		if (mConnection != null) {
			// Detach our existing connection.
			mActivity.unbindService(mConnection);
		}
		mIsBound = false;
		Log.i(TAG, "Unbinding completed.");

	}
}
