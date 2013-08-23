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
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.model.Session;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/**
 * Activity for displaying basic session infos (# of cells, wifis, etc.)
 */
public class StatsActivity extends Activity {
	private static final String TAG = StatsActivity.class.getSimpleName();

	/**
	 * UI controls
	 */
	private TextView tvLastCell;
	private TextView tvLastWifi;
	private TextView tvSession;
	private TextView tvCountCells;
	private TextView tvCountWifis;

	private DataHelper	dbHelper;

	/**
	 * Receives cell / wifi news
	 */
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			Log.d(TAG, "Received intent " + intent.getAction());

			// handling cell and wifi broadcasts
			if (RadioBeacon.INTENT_NEW_CELL.equals(intent.getAction())) {
				String lastCell = intent.getStringExtra(RadioBeacon.MSG_KEY);
				tvLastCell.setText(lastCell);

				Session active = dbHelper.loadActiveSession();
				refreshSessionStats(active);

			} else if (RadioBeacon.INTENT_NEW_WIFI.equals(intent.getAction())) {
				String lastWifi = intent.getStringExtra(RadioBeacon.MSG_KEY);
				tvLastWifi.setText(lastWifi);

				Session active = dbHelper.loadActiveSession();
				refreshSessionStats(active);
				
			} else if (RadioBeacon.INTENT_NEW_SESSION.equals(intent.getAction())) {
				String id = intent.getStringExtra(RadioBeacon.MSG_KEY);
				Session session = dbHelper.loadSession(Integer.valueOf(id));
				refreshSessionStats(session);
			}
		}
	};

	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// setup UI controls
		initUi();

		// setup broadcast filters
		registerReceiver();	
		dbHelper = new DataHelper(this);
	}

	/**
	 * 
	 */
	private void initUi() {
		setContentView(R.layout.stats);
		tvLastCell = (TextView) findViewById(R.id.stats_cell_description);
		tvLastWifi = (TextView) findViewById(R.id.stats_wifi_description);
		tvSession = (TextView) findViewById(R.id.stats_session_description);
		tvCountCells = (TextView) findViewById(R.id.stats_cell_total);
		tvCountWifis = (TextView) findViewById(R.id.stats_wifi_total);
	}

	@Override
	protected final void onPause() {
		unregisterReceiver();
		super.onPause();
	}
	/**
	 * Registers broadcast receivers.
	 */
	private void registerReceiver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(RadioBeacon.INTENT_NEW_WIFI);
		filter.addAction(RadioBeacon.INTENT_NEW_CELL);
		filter.addAction(RadioBeacon.INTENT_SESSION_UPDATE);
		registerReceiver(mReceiver, filter);
	}
	
	/**
	 * Unregisters receivers for GPS and wifi scan results.
	 */
	private void unregisterReceiver() {
		try {
			unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android}
		}
	}
	

	@Override
	protected final void onResume() {
		super.onResume();
		
		registerReceiver();	
		
		if (dbHelper != null) {
			Session active = dbHelper.loadActiveSession();
			refreshSessionStats(active);
		}
	}
	
	/**
	 * Refreshes session id, number of cells and wifis.
	 */
	private void refreshSessionStats(final Session session) {
			
		if (session != null) {
			tvSession.setText(String.valueOf(session.getId()));
			tvCountCells.setText(
					"("
							+ String.valueOf(dbHelper.countCells(session.getId()))
							+ ")"
					);	
			tvCountWifis.setText(
					"("
							+ String.valueOf(dbHelper.countWifis(session.getId()))
							+ ")"
					);
		} else {
			tvSession.setText("-");
			tvCountCells.setText("(--)");
			tvCountWifis.setText("(--)");
		}
	}
}
