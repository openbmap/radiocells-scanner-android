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

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.db.DataHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.models.WifiChannel;
import org.openbmap.db.models.WifiRecord;
import org.openbmap.db.models.WifiRecord.CatalogStatus;

import java.io.File;
import java.util.ArrayList;

/**
 * Parent activity for hosting wifi detail fragment
 */
public class WifiDetailsActivity extends FragmentActivity {

    private static final String TAG = WifiDetailsActivity.class.getSimpleName();

    private DataHelper mDatahelper;

    private TextView tvSsid;
    private TextView tvCapabilities;
    private TextView tvFrequency;
    private TextView tvNoMeasurements;
    private TextView tvManufactor;
    private ImageView ivIsNew;

    private WifiRecord mWifi;

    /**
     * Called when the activity is first created.
     */
    @Override
    public final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wifidetails);

        initUi();

        final Bundle extras = getIntent().getExtras();
        final String bssid = extras.getString(Schema.COL_BSSID);
        Integer session = null;
        if (extras.getInt(Schema.COL_SESSION_ID) != 0) {
            session = extras.getInt(Schema.COL_SESSION_ID);
        }

        // query content provider for wifi details
        mDatahelper = new DataHelper(this);
        final ArrayList<WifiRecord> wifis = mDatahelper.loadWifisByBssid(bssid, session);
        tvNoMeasurements.setText(String.valueOf(wifis.size()));

        mWifi = wifis.get(0);
        displayRecord(mWifi);
    }

    /**
     *
     */
    private void initUi() {
        tvSsid = (TextView) findViewById(R.id.wifidetails_ssid);
        tvCapabilities = (TextView) findViewById(R.id.wifidetails_capa);
        tvFrequency = (TextView) findViewById(R.id.wifidetails_freq);
        tvNoMeasurements = (TextView) findViewById(R.id.wifidetails_no_measurements);
        tvManufactor = (TextView) findViewById(R.id.wifidetails_manufactor);
        ivIsNew = (ImageView) findViewById(R.id.wifidetails_is_new);
    }

    @Override
    protected final void onResume() {
        super.onResume();

        // get the wifi _id
        final Bundle extras = getIntent().getExtras();
        final int id = extras.getInt(Schema.COL_ID);
        // query content provider for wifi details
        mWifi = mDatahelper.loadWifiById(id);

        displayRecord(mWifi);
    }

    /**
     * @param wifi
     */
    private void displayRecord(final WifiRecord wifi) {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (wifi == null) {
            Log.e(TAG, "WifisCommunity argument was null");
            return;
        }

        if (!prefs.getString(Preferences.KEY_CATALOG_FILE, Preferences.VAL_CATALOG_NONE).equals(Preferences.VAL_CATALOG_NONE)) {
            // Open catalog database
            final String file = prefs.getString(Preferences.KEY_WIFI_CATALOG_FOLDER,
                    this.getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.CATALOG_SUBDIR)
                    + File.separator + prefs.getString(Preferences.KEY_CATALOG_FILE, Preferences.VAL_CATALOG_FILE);
            try {
                final SQLiteDatabase mCatalog = SQLiteDatabase.openDatabase(file, null, SQLiteDatabase.OPEN_READONLY);
                Cursor cur = null;
                cur = mCatalog.rawQuery("SELECT _id, manufactor FROM manufactors WHERE "
                                + "(bssid = ?) LIMIT 1",
                        new String[]{wifi.getBssid().replace(":", "").replace("-", "").substring(0, 6).toUpperCase()});

                if (cur.moveToFirst()) {
                    final String manufactor = cur.getString(cur.getColumnIndex("manufactor"));
                    tvManufactor.setText(manufactor);
                }
                cur.close();
            } catch (SQLiteCantOpenDatabaseException e1) {
                Log.e(TAG, e1.getMessage());
                Toast.makeText(this, getString(R.string.error_opening_wifi_catalog), Toast.LENGTH_LONG).show();
                return;
            } catch (SQLiteException e2) {
                Log.e(TAG, e2.getMessage());
                Toast.makeText(this, R.string.error_accessing_wifi_catalog, Toast.LENGTH_LONG).show();
                return;
            }
        }

        tvSsid.setText(wifi.getSsid() + " (" + wifi.getBssid() + ")");
        if (wifi.getCapabilities() != null)

        {
            tvCapabilities.setText(wifi.getCapabilities().replace("[", "\n["));
        } else

        {
            tvCapabilities.setText(getResources().getString(R.string.n_a));
        }

        final Integer freq = wifi.getFrequency();
        if (freq != null)

        {
            tvFrequency.setText(((WifiChannel.getChannel(freq) == null) ? getResources().getString(R.string.unknown) : WifiChannel.getChannel(freq))
                            + "  (" + freq + " MHz)");
        }

        if (wifi.getCatalogStatus().equals(CatalogStatus.NEW)) {
            ivIsNew.setImageResource(android.R.drawable.checkbox_on_background);
        } else {
            ivIsNew.setImageResource(android.R.drawable.checkbox_off_background);
        }
    }

    public final WifiRecord getWifi() {
        return mWifi;
    }

}
