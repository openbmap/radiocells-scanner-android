package org.openbmap.activity;

import org.openbmap.R;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class WifiListActivity  extends FragmentActivity {
	
	@SuppressWarnings("unused")
	private static final String TAG = WifiListActivity.class.getSimpleName();

	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);	
		setContentView(R.layout.wifilist);
	}

}
