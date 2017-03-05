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

package org.openbmap.commands;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.activities.StartscreenActivity_;
import org.openbmap.activities.tabs.TabHostActivity_;
import org.openbmap.events.onStartTracking;
import org.openbmap.events.onStopRequested;

public class CommandReceiver extends BroadcastReceiver {

    private static final String TAG = CommandReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("org.openbmap.command.START")) {
            Log.i(TAG, "Received start command");

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.getBoolean(Preferences.KEY_ALLOW_AUTOMATION, Preferences.VAL_ALLOW_AUTOMATION)) {
                Log.w(TAG, "Missing Automation permission, ignore intent");
                Toast.makeText(context, R.string.warning_automation_disabled, Toast.LENGTH_LONG).show();
                return;
            }

            //final Intent activity = new Intent(context, TabHostActivity_.class);
            // TODO: do we really need this flag?
            // see discussion at http://stackoverflow.com/questions/3918517/calling-startactivity-from-outside-of-an-activity-context
            //activity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //context.startActivity(activity);

            EventBus.getDefault().post(new onStartTracking());
            // bring up UI
            final Intent hostActivity = new Intent(context, TabHostActivity_.class);
            // TODO: do we really need this flag? see discussion at http://stackoverflow.com/questions/3918517/calling-startactivity-from-outside-of-an-activity-context
            hostActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            hostActivity.putExtra("new_session", true);
            context.startActivity(hostActivity);
        } else if (intent.getAction().equals("org.openbmap.command.STOP")) {
            Log.i(TAG, "Received stop command");

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.getBoolean(Preferences.KEY_ALLOW_AUTOMATION, Preferences.VAL_ALLOW_AUTOMATION)) {
                Log.w(TAG, "Missing Automation permission, ignore intent");
                Toast.makeText(context, R.string.warning_automation_disabled, Toast.LENGTH_LONG).show();
                return;
            }

            // let TabHostActivity handle shutdown so don't use EventBus.getDefault().post(new onStopTracking());
            EventBus.getDefault().post(new onStopRequested());
            Toast.makeText(context, R.string.stopped_tracking, Toast.LENGTH_SHORT).show();

        } else if (intent.getAction().equals("org.openbmap.command.UPLOAD")) {
            Log.i(TAG, "Received upload command");

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.getBoolean(Preferences.KEY_ALLOW_AUTOMATION, Preferences.VAL_ALLOW_AUTOMATION)) {
                Log.w(TAG, "Missing Automation permission, ignore intent");
                Toast.makeText(context, R.string.warning_automation_disabled, Toast.LENGTH_LONG).show();
                return;
            }

            final Intent activity = new Intent(context, StartscreenActivity_.class);
            final Bundle b = new Bundle();
            b.putString("command", "upload_all");
            intent.putExtras(b);

            context.startActivity(activity);
        } else {
            Log.i(TAG, "Received unknown command " + intent.getAction());
        }

    }
}
