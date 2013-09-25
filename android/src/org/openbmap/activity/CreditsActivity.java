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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.openbmap.R;
import org.openbmap.RadioBeacon;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Credit activity
 */
public class CreditsActivity extends Activity {

	@SuppressWarnings("unused")
	private static final String TAG = CreditsActivity.class.getSimpleName();

	/** Called when the activity is first created. */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.credits);
		final TextView tvClientVersion = (TextView) findViewById(R.id.credits_client_version);
		tvClientVersion.setText(RadioBeacon.SW_VERSION);
		final TextView tvBuild = (TextView) findViewById(R.id.credits_build);
		tvBuild.setText("(" + readBuildInfo() + ")");
	}

	public final String readBuildInfo() {
		InputStream buildInStream = getResources().openRawResource(R.raw.build);
	    ByteArrayOutputStream buildOutStream = new ByteArrayOutputStream();
	
	    int i;
	 
	    try { 
	        i = buildInStream.read();
	        while (i != -1) {
	            buildOutStream.write(i);
	            i = buildInStream.read();
	        }
	 
	        buildInStream.close();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	 
	    return buildOutStream.toString();
	    // use buildOutStream.toString() to get the data 

	}

}
