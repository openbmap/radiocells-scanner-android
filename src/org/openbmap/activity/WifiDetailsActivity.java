package org.openbmap.activity;


import org.openbmap.R;
import org.openbmap.db.DataHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.model.WifiRecord;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.TextView;

public class WifiDetailsActivity  extends FragmentActivity implements WifiDetailsFragment.OnWifiSelectedListener {

	private static final String TAG = WifiDetailsActivity.class.getSimpleName();
	
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
		tvSsid = (TextView) findViewById(R.id.wifidetails_ssid);
		tvCapabilities = (TextView) findViewById(R.id.wifidetails_capa);
		tvFrequency = (TextView) findViewById(R.id.wifidetails_freq);
		WifiDetailsFragment detailsFragment = (WifiDetailsFragment) getSupportFragmentManager().findFragmentById(R.id.wifiDetailsFragment);	
		detailsFragment.setOnWifiSelectedListener(this);

		mDatahelper = new DataHelper(this);
		
		Bundle extras = getIntent().getExtras();
		String bssid = extras.getString(Schema.COL_BSSID);
		// query content provider for wifi details
		mWifi = mDatahelper.loadWifisByBssid(bssid).get(0);
		
		displayRecord(mWifi);
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
			tvCapabilities.setText("Encryption :" + wifi.getCapabilities());
			// TODO: replace by channel
			tvFrequency.setText("Frequency :" + String.valueOf(wifi.getFrequency()));
		}
	}

	@Override
	public final void onStart() {
		super.onStart();
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
