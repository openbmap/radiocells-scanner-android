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

package org.openbmap.activities.tabs;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.activities.SelectiveScrollViewPager;
import org.openbmap.activities.StartscreenActivity_;
import org.openbmap.events.onServiceShutdown;
import org.openbmap.events.onSessionStop;
import org.openbmap.events.onStopRequested;
import org.openbmap.services.ManagerService;
import org.openbmap.utils.ActivityUtils;

/**
 * Invisible host activity for "tracking" mode.
 * It hosts the tabs "Stats", "Wifis", "Cells" and "Map".
 * TabHostActivity is also in charge of service communication.
 */

@EActivity(R.layout.tabhost_activity)
public class TabHostActivity extends AppCompatActivity {
	private static final String	TAG	= TabHostActivity.class.getSimpleName();

	/**
	 * Tab pager
	 */
    @ViewById(R.id.pager)
    SelectiveScrollViewPager mPager;

	/**
	 * Keeps the SharedPreferences.
	 */
	private SharedPreferences mPrefs = null;

	private ActionBar mActionBar;

    /** Messenger for communicating with service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean mIsBound;

    @Subscribe
    public void onEvent(onServiceShutdown event) {
        /*
        Since ManagerService runs permanently, following lines shouldn't be needed.
        Additionally power events are handled by the service itself

        if (event.reason == Constants.SHUTDOWN_REASON_LOW_POWER) {
            Toast.makeText(TabHostActivity.this, getString(R.string.battery_warning), Toast.LENGTH_LONG).show();
        }

        stopManagerService();
        */

        TabHostActivity.this.finish();
    }

    /**
     * Establish a connection with the service.
     */
    void startManagerService() {
        Log.i(TAG, "Starting ManagerService");
        Intent intent = new Intent(this, ManagerService.class);
        startService(intent);

        mIsBound = true;
    }

    void stopManagerService() {
        if (mIsBound) {
            Log.i(TAG, "Stopping ManagerService");
            Intent intent = new Intent(this, ManagerService.class);
            stopService(intent);
            mIsBound = false;
        }
    }

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		// service related stuff
		verifyGpsEnabled();
        verifyWifiEnabled();
        verifyNotAirplaneMode();
        // TODO: show warning if GSM is not enabled

        if (!EventBus.getDefault().isRegistered(this)) {
            Log.v(TAG, "Registering eventbus receiver for ManagerService");
            EventBus.getDefault().register(this);}
        else {
            Log.w(TAG, "Event bus receiver already registered");
        }
	}

    @Override
    public void onResume() {
        super.onResume();
        startManagerService();
    }

    @Subscribe
    public void onEvent(onStopRequested event){
        stopTracking();
    }

	@Override
	protected void onDestroy() {
        Log.d(TAG, "TabHostActivity#onDestroy");
        EventBus.getDefault().unregister(this);
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
        EventBus.getDefault().post(new onSessionStop());

        final Intent intent = new Intent(this, StartscreenActivity_.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        this.finish();
    }

    /**
	 * Checks whether GPS is enabled.
	 * If not, user is asked whether to activate GPS.
	 */
	private void verifyGpsEnabled() {
        final LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.i(TAG, "GPS is disabled - warning");
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
                                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
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
        } else {
            Log.i(TAG, "GPS is enabled - good");
        }
    }

    /**
     * Checks whether wifi is enabled. If not dialog shown to enable
     */
    private void verifyWifiEnabled() {
		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		if (!wifi.isWifiEnabled()){
            Log.i(TAG, "Wifi is disabled - warning");
            new AlertDialog.Builder(this)
                    .setTitle(R.string.no_wifi)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(
                            R.string.turn_on_wifi_question)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.yes,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialog,
                                                    final int which) {
                                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
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
		} else {
            Log.i(TAG, "Wifi is enabled - good");
        }
	}

    private void verifyNotAirplaneMode() {
        boolean airplane_on = Settings.Global.getInt(this.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        if (airplane_on) {
            Log.i(TAG, "In Airplane mode - warning");
            new AlertDialog.Builder(this)
                    .setTitle(R.string.airplane_mode_on_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.airplane_mode_on_message)
                    .setCancelable(true)
                    .setNeutralButton(R.string.understood,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialog,
                                                    final int which) {
                                    Log.i(TAG, "User must disable airplane mode manually..");
                                }
                            }
                    ).create().show();
        } else {
            Log.i(TAG, "Not in airplane mode - good");
        }
    }

    /**
     * Configures UI elements.
	 */
    @AfterViews
	public void initUi() {
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
        mPager.setOffscreenPageLimit(0);

        // add tabs to actionbar
        final CustomViewPagerAdapter mTabsAdapter = new CustomViewPagerAdapter(this, mPager);

        mTabsAdapter.addTab(mActionBar.newTab().setIcon(R.drawable.ic_overview)/*.setText(getResources().getString(R.string.overview))*/, OverviewFragment_.class, null);
        mTabsAdapter.addTab(mActionBar.newTab().setIcon(R.drawable.ic_wifi),/*.setText(getResources().getString(R.string.wifis)*/ WifisListFragment_.class, null);
        mTabsAdapter.addTab(mActionBar.newTab().setIcon(R.drawable.ic_cell)/*setText(getResources().getString(R.string.cells))*/, CellListFragment_.class, null);
        //mTabsAdapter.addTab(mActionBar.newTab().setText(getResources().getString(R.string.map)), MapFragment.class, null);
		mTabsAdapter.addTab(mActionBar.newTab().setIcon(android.R.drawable.ic_dialog_map), MapFragment_.class, null);
	}
}
