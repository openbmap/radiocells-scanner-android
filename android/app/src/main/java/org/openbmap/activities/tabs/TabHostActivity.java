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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
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
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.activities.SelectiveScrollViewPager;
import org.openbmap.activities.StartscreenActivity;
import org.openbmap.events.onServiceShutdown;
import org.openbmap.events.onStopTracking;
import org.openbmap.services.ManagerService;
import org.openbmap.utils.ActivityUtils;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * TabHostActivity for "tracking" mode. It hosts the tabs "Stats", "WifisRadiocells Overview", "Cell Overview" and "Map".
 * TabHostActivity is also in charge of service communication.
 */
public class TabHostActivity extends AppCompatActivity {
	private static final String	TAG	= TabHostActivity.class.getSimpleName();

	/**
	 * Tab pager
	 */
    @BindView(R.id.pager)
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
        if (event.reason == RadioBeacon.SHUTDOWN_REASON_LOW_POWER) {
            Toast.makeText(TabHostActivity.this, getString(R.string.battery_warning), Toast.LENGTH_LONG).show();
        }

        stopManagerService();
        TabHostActivity.this.finish();
    }

    /**
     * Establish a connection with the service.  We use an explicit
     * class name because there is no reason to be able to let other
     * applications replace our component.
     */
    void startManagerService() {
        Intent intent = new Intent(this, ManagerService.class);
        startService(intent);

        mIsBound = true;
    }

    void stopManagerService() {
        if (mIsBound) {
            Intent intent = new Intent(this, ManagerService.class);
            stopService(intent);
            mIsBound = false;
        }
    }

	/** Called when the activity is first created. */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.tabhost_activity);

		ButterKnife.bind(this);

		// get shared preferences
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		initUi();

		// service related stuff
		isGpsAvailable();
		// TODO: show warning if wifi is not enabled
		// TODO: show warning if GSM is not enabled
	}

	@Override
	public final void onResume() {
        super.onResume();
        startManagerService();
    }

	@Override
	protected final void onDestroy() {
        Log.d(TAG, "Destroying TabHost");
    //    EventBus.getDefault().unregister(this);
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
	private void isGpsAvailable() {
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
		}
	}

	/**
	 * Configures UI elements.
	 */
	private void initUi() {
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

        mTabsAdapter.addTab(mActionBar.newTab().setIcon(R.drawable.ic_overview)/*.setText(getResources().getString(R.string.overview))*/, OverviewFragment.class, null);
        mTabsAdapter.addTab(mActionBar.newTab().setIcon(R.drawable.ic_wifi),/*.setText(getResources().getString(R.string.wifis)*/ WifisListFragment.class, null);
        mTabsAdapter.addTab(mActionBar.newTab().setIcon(R.drawable.ic_cell)/*setText(getResources().getString(R.string.cells))*/, CellListFragment.class, null);
        //mTabsAdapter.addTab(mActionBar.newTab().setText(getResources().getString(R.string.map)), MapFragment.class, null);
		mTabsAdapter.addTab(mActionBar.newTab().setIcon(android.R.drawable.ic_dialog_map), MapFragment.class, null);
	}
}
