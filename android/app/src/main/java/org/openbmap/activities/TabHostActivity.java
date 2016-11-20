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

package org.openbmap.activities;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.events.onStopTracking;
import org.openbmap.services.ManagerService;
import org.openbmap.utils.ActivityUtils;

/**
 * TabHostActivity for "tracking" mode. It hosts the tabs "Stats", "WifisRadiocells Overview", "Cell Overview" and "Map".
 * TabHostActivity is also in charge of service communication.
 */
public class TabHostActivity extends AppCompatActivity {
	private static final String	TAG	= TabHostActivity.class.getSimpleName();

	/**
	 * Tab pager
	 */
	private SelectiveScrollViewPager mPager;

	/**
	 * Keeps the SharedPreferences.
	 */
	private SharedPreferences mPrefs = null;

	private android.support.v7.app.ActionBar mActionBar;

    /** Messenger for communicating with service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean mIsBound;

    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RadioBeacon.MSG_SERVICE_SHUTDOWN:
                    int reason = msg.arg1;

                    if (reason == RadioBeacon.SHUTDOWN_REASON_LOW_POWER) {
                        Toast.makeText(TabHostActivity.this, getString(R.string.battery_warning), Toast.LENGTH_LONG).show();
                    }

                    doUnbindService();
                    TabHostActivity.this.finish();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);
            //mCallbackText.setText("Attached.");

            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                Message msg = Message.obtain(null, RadioBeacon.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);

                // Give it some value as an example.
                //msg = Message.obtain(null, ManagerService.MSG_SET_VALUE, this.hashCode(), 0);
                //mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }

            // As part of the sample, tell the user what happened.
            //Toast.makeText(HostActivity.this, "Service connected", Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            // mCallbackText.setText("Disconnected.");

            // As part of the sample, tell the user what happened.
            // Toast.makeText(HostActivity.this, "Service disconnected", Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * Establish a connection with the service.  We use an explicit
     * class name because there is no reason to be able to let other
     * applications replace our component.
     */
    void doBindService() {
        bindService(new Intent(TabHostActivity.this, ManagerService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, RadioBeacon.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }

            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
            //mCallbackText.setText("Unbinding.");
        }
    }

	/** Called when the activity is first created. */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// get shared preferences
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		initUi();

		// service related stuff
		verifyGPSProvider();

		// TODO: show warning if wifi is not enabled
		// TODO: show warning if GSM is not enabled
	}

	@Override
	protected final void onResume() {
		super.onResume();
        doBindService();
	}

	@Override
	protected final void onPause() {
        doUnbindService();
        // ?? stopTracking();
		super.onPause();
	}

	@Override
	protected final void onStop() {
        doUnbindService();
        // ?? stopTracking();
		super.onStop();
	}

	@Override
	protected final void onDestroy() {
        Log.d(TAG, "Destroying TabHost");
    //    EventBus.getDefault().unregister(this);
        doUnbindService();
        // ?? stopTracking();
		super.onDestroy();
	}

	/**
	 * Create context menu with start and stop buttons for "tracking" mode.
	 * @param menu Menu to inflate
	 * @return always true
	 */
	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.control_menu, menu);
		return true;
	}

	/**
	 * Context menu, while in "tracking" mode
	 */
	@Override
	public final boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_stoptracking:
                stopTracking();
				break;
			default:
				break;
		}
		return super.onOptionsItemSelected(item);
	}

    private void stopTracking() {
        Log.d(TAG, "REQ StopTrackingEvent");
                EventBus.getDefault().post(new onStopTracking());

        final Intent intent = new Intent(this, StartscreenActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        this.finish();
    }

    /**
	 * Checks whether GPS is enabled.
	 * If not, user is asked whether to activate GPS.
	 */
	private void verifyGPSProvider() {
		final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
		if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			// GPS isn't enabled. Offer user to go enable it
			new AlertDialog.Builder(this)
			.setTitle(R.string.no_gps)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setMessage(
					R.string.turn_on_gps_question)
					.setCancelable(true)
					.setPositiveButton(android.R.string.yes,
							new DialogInterface.OnClickListener() {
						@Override
						public void onClick(final DialogInterface dialog,
								final int which) {
							startActivity(new Intent(
									Settings.ACTION_LOCATION_SOURCE_SETTINGS));
						}
					})
					.setNegativeButton(android.R.string.no,
							new DialogInterface.OnClickListener() {
						@Override
						public void onClick(final DialogInterface dialog,
								final int which) {
							dialog.cancel();
						}
					}).create().show();
		}
	}

	/**
	 * Configures UI elements.
	 */
	private void initUi() {
        setContentView(R.layout.host_activity);
        final boolean keepScreenOn = mPrefs.getBoolean(Preferences.KEY_KEEP_SCREEN_ON, false);
        ActivityUtils.setKeepScreenOn(this, keepScreenOn);

        // Activate Fragment Manager
        final FragmentManager fm = getFragmentManager();

        mActionBar = getSupportActionBar();
		getSupportActionBar().setDisplayShowHomeEnabled(true);
		getSupportActionBar().setDisplayShowTitleEnabled(true);
		//getSupportActionBar().setLogo(R.drawable.ic_action_logo);
		//getSupportActionBar().setDisplayUseLogoEnabled(true);
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Highlight helper for selected tab in actionbar
        mPager = (SelectiveScrollViewPager) findViewById(R.id.pager);
        mPager.setOffscreenPageLimit(0);

        // add tabs to actionbar
        final CustomViewPagerAdapter mTabsAdapter = new CustomViewPagerAdapter(this, mPager);

        mTabsAdapter.addTab(mActionBar.newTab().setText(getResources().getString(R.string.overview)), StatsActivity.class, null);
        mTabsAdapter.addTab(mActionBar.newTab().setText(getResources().getString(R.string.wifis)), WifiListContainer.class, null);
        mTabsAdapter.addTab(mActionBar.newTab().setText(getResources().getString(R.string.cells)), CellsListContainer.class, null);
        //mTabsAdapter.addTab(mActionBar.newTab().setText(getResources().getString(R.string.map)), MapViewActivity.class, null);
		mTabsAdapter.addTab(mActionBar.newTab().setIcon(android.R.drawable.ic_dialog_map), MapViewActivity.class, null);
	}
}
