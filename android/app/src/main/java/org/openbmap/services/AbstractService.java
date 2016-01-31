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

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public abstract class AbstractService extends Service {

	private static final String TAG = AbstractService.class.getSimpleName();

	public class LocalBinder extends Binder {
        AbstractService getService() {
			return AbstractService.this;
		}
	}

    private final IBinder mBinder = new LocalBinder();

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
		return mBinder;
	}

	@Override
	public void onDestroy() {
        onStopService();
        super.onDestroy();
		Log.i(TAG, "Service stopped");
	}

	public abstract void onStartService();
	public abstract void onStopService();
	public abstract void onReceiveMessage(Message msg);

}
