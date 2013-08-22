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
 * @Author: Philipp C. Heckel; based on code by Lance Lefebure 
 * @see: http://stackoverflow.com/questions/4300291/example-communication-between-activity-and-service-using-messaging         
 * @see: https://code.launchpad.net/~binwiederhier/+junk/android-service-example
 * 
 */
package org.openbmap.service;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public abstract class AbstractService extends Service {

	private static final String TAG = AbstractService.class.getSimpleName();

	static final int MSG_REGISTER_CLIENT = 9991;
	static final int MSG_UNREGISTER_CLIENT = 9992;

	private ArrayList<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all current registered clients.
	private final Messenger mMessenger = new Messenger(new ServiceManagerHandler(this)); // Target we publish for clients to send messages to ServiceManagerHandler.

	/**
	 * Handles incoming messages from service manager.
	 * @author power
	 *
	 */
	private static class ServiceManagerHandler extends Handler {
		private final WeakReference<AbstractService> mWeakRef;

		public ServiceManagerHandler(final AbstractService activity) {
			mWeakRef = new WeakReference<AbstractService>(activity);
		}

		@Override
		public void handleMessage(final Message msg) {
			AbstractService service = mWeakRef.get();
			if (service != null) {

				switch (msg.what) {
					case MSG_REGISTER_CLIENT:
						Log.i(TAG, "Client registered: " + msg.replyTo);
						service.mClients.add(msg.replyTo);
						break;
					case MSG_UNREGISTER_CLIENT:
						Log.i(TAG, "Client un-registered: " + msg.replyTo);
						service.mClients.remove(msg.replyTo);
						break;            
					default:
						//super.handleMessage(msg);
						service.onReceiveMessage(msg);
				}
			}
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		onStartService();
	}

	@Override
	public final int onStartCommand(final Intent intent, final int flags, final int startId) {
		return START_STICKY; // run until explicitly stopped.
	}

	@Override
	public final IBinder onBind(final Intent intent) {
		return mMessenger.getBinder();
	}

	@Override
	public void onDestroy() {
		onStopService();
		super.onDestroy();
		Log.i(TAG, "Service Stopped.");
	}    

	protected final void send(final Message msg) {
		Log.d(TAG, "Broadcasting message " + msg.toString() + " to " + mClients.size() + " clients");
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				Log.i(TAG, "Sending message to clients: " + msg);
				mClients.get(i).send(msg);
			} catch (RemoteException e) {
				// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
				Log.e(TAG , "Client is dead. Removing from list: " + i);
				mClients.remove(i);
			}
		}    	
	}

	public abstract void onStartService();
	public abstract void onStopService();
	public abstract void onReceiveMessage(Message msg);

}
