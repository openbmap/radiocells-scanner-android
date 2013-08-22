/**
 * 
 */
package org.openbmap.activity;

import org.openbmap.R;
import org.openbmap.RadioBeacon;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;


public class CreditsActivity extends Activity {

	@SuppressWarnings("unused")
	private static final String TAG = CreditsActivity.class.getSimpleName();

	/** Called when the activity is first created. */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.credits);
		final TextView clientVersion = (TextView) findViewById(R.id.credits_client_version);
		clientVersion.setText(RadioBeacon.SW_VERSION);
	}


}
