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

package org.openbmap.activity;

import org.openbmap.R;
import org.openbmap.db.DataHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.model.WifiChannel;
import org.openbmap.db.model.WifiRecord;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.TextView;

/**
 * Parent activity for hosting wifi detail fragment
 */
public class WifiDetailsActivity  extends FragmentActivity {

	private DataHelper mDatahelper;

	private TextView tvSsid;
	private TextView tvCapabilities;
	private TextView tvFrequency;

	private WifiRecord	mWifi;

	/** Called when the activity is first created. */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);	
		setContentView(R.layout.wifidetails);

		initUi();

		mDatahelper = new DataHelper(this);

		Bundle extras = getIntent().getExtras();
		String bssid = extras.getString(Schema.COL_BSSID);
		// query content provider for wifi details
		mWifi = mDatahelper.loadWifisByBssid(bssid).get(0);

		displayRecord(mWifi);
	}

	/**
	 * 
	 */
	private void initUi() {
		tvSsid = (TextView) findViewById(R.id.wifidetails_ssid);
		tvCapabilities = (TextView) findViewById(R.id.wifidetails_capa);
		tvFrequency = (TextView) findViewById(R.id.wifidetails_freq);

		//WifiDetailsMap detailsFragment = (WifiDetailsMap) getSupportFragmentManager().findFragmentById(R.id.wifiDetailsMap);	
	}

	@Override
	protected final void onResume() {
		super.onResume();
		// get the cell _id
		// get the wifi _id
		Bundle extras = getIntent().getExtras();
		int id = extras.getInt(Schema.COL_ID);
		// query content provider for wifi details
		mWifi = mDatahelper.loadWifiById(id);

		displayRecord(mWifi);

	}

	/**
	 * @param wifi 
	 * 
	 */
	private void displayRecord(final WifiRecord wifi) {
		if (wifi != null) {
			tvSsid.setText(wifi.getSsid() + " (" + wifi.getBssid() + ")");
			if (wifi.getCapabilities() != null) {
				tvCapabilities.setText(getResources().getString(R.string.encryption) + ":" + wifi.getCapabilities().replace("[", "\n["));
			} else {
				tvCapabilities.setText(getResources().getString(R.string.encryption) + ":" + R.string.n_a);
			}

			// TODO: replace by channel
			Integer freq = wifi.getFrequency();
			if (freq != null) {
				tvFrequency.setText(
						getResources().getString(R.string.channel) + ":"
						+ ((WifiChannel.getChannel(freq) == null) ? getResources().getString(R.string.unknown) : WifiChannel.getChannel(freq))
						+ "  (" + freq + " MHz)");
			}
		}
	}

	public final WifiRecord getWifi() {
		return mWifi;
	}

	/**
	 * highlights selected wifi on MapView
	 * @param id
	 */
	public final void onWifiSelected(final long id) {
		Intent intent = new Intent(this, MapViewActivity.class);
		intent.putExtra(Schema.COL_ID, (int) id);
		startActivity(intent);
	}
}
