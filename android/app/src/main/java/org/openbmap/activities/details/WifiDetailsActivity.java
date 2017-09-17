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

package org.openbmap.activities.details;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
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
 * Display details for specific wifi. WifiDetailsActivity also takes care of
 * loading the records from the database. It also hosts the heatmap fragment.
 */
@EActivity(R.layout.wifidetails)
public class WifiDetailsActivity extends FragmentActivity {

    private static final String TAG = WifiDetailsActivity.class.getSimpleName();

    @ViewById(R.id.wifidetails_ssid) TextView tvSsid;
    @ViewById(R.id.wifidetails_capa) TextView tvEncryption;
    @ViewById(R.id.wifidetails_freq) TextView tvFrequency;
    @ViewById(R.id.wifidetails_no_measurements) TextView tvNoMeasurements;
    @ViewById(R.id.wifidetails_manufactor) TextView tvManufactor;
    @ViewById(R.id.wifidetails_is_new) ImageView ivIsNew;

    private ArrayList<WifiRecord> mMeasurements;
    private Integer mSession;
    private String mBssid;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle extras = getIntent().getExtras();
        mBssid = extras.getString(Schema.COL_BSSID);
        mSession = null;
        if (extras.getInt(Schema.COL_SESSION_ID) != 0) {
            mSession = extras.getInt(Schema.COL_SESSION_ID);
        }
    }

    @AfterViews
    public void initUi() {
        // query content provider for wifi details
        DataHelper datahelper = new DataHelper(this);
        mMeasurements = datahelper.getAllMeasurementsForBssid(this.mBssid, this.mSession);
        tvNoMeasurements.setText(String.valueOf(mMeasurements.size()));
        displayRecord(mMeasurements.get(0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayRecord(mMeasurements.get(0));

    }

    /**
     * @param wifi
     */
    private void displayRecord(@NonNull final WifiRecord wifi) {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (wifi == null) {
            Log.e(TAG, "Wifi argument was null");
            return;
        }

        if (!prefs.getString(Preferences.KEY_CATALOG_FILE, Preferences.VAL_CATALOG_NONE).equals(Preferences.VAL_CATALOG_NONE)) {
            final String search = wifi.getBssid().replace(":", "").replace("-", "").substring(0, 6).toUpperCase();
            String manufactor = getManufactorName(search);

            if (manufactor != null) {
                tvManufactor.setText(manufactor);
            } else {
                tvManufactor.setText(R.string.n_a);
            }
        }

        tvSsid.setText(wifi.getSsid() + " (" + wifi.getBssid() + ")");
        if (wifi.getCapabilities() != null) {
            tvEncryption.setText(wifi.getCapabilities().replace("[", "").replace("]","\n"));
        } else {
            tvEncryption.setText(getResources().getString(R.string.n_a));
        }

        final Integer freq = wifi.getFrequency();
        if (freq != null) {
            tvFrequency.setText(
                    ((WifiChannel.getChannel(freq) == null) ? getResources().getString(R.string.unknown) : getString(R.string.frequency) + " " + WifiChannel.getChannel(freq))
                            + " (" + freq + " MHz)");
        }

        if (wifi.getCatalogStatus().equals(CatalogStatus.NEW)) {
            ivIsNew.setVisibility(View.VISIBLE);
        } else {
            ivIsNew.setVisibility(View.INVISIBLE);
        }
    }

    private String getManufactorName(String search) {
        String result = null;
        // Open catalog database
        final String file = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_CATALOG_FOLDER,
                this.getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.CATALOG_SUBDIR)
                + File.separator + PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_CATALOG_FILE, Preferences.DEFAULT_CATALOG_FILE);
        try {

            Log.v(TAG, "Looking up manufactor " + search);
            final SQLiteDatabase mCatalog = SQLiteDatabase.openDatabase(file, null, SQLiteDatabase.OPEN_READONLY);
            Cursor cursor = null;
            cursor = mCatalog.rawQuery("SELECT _id, manufactor FROM manufactors WHERE "
                            + "(bssid = ?) LIMIT 1",
                    new String[]{search});

            if (cursor.moveToFirst()) {
                result = cursor.getString(cursor.getColumnIndex("manufactor"));
            }
            cursor.close();
        } catch (SQLiteCantOpenDatabaseException e1) {
            Log.e(TAG, "Error opening catalog: " + e1.getMessage());
            result = null;
            // Toast.makeText(this, getString(R.string.error_opening_wifi_catalog), Toast.LENGTH_LONG).show();
        } catch (SQLiteException e2) {
            Log.e(TAG, "SQL exception on catalog: "  + e2.getMessage());
            result = null;
            //Toast.makeText(this, R.string.error_accessing_wifi_catalog, Toast.LENGTH_LONG).show();
        }
        return result;
    }

    public ArrayList<WifiRecord> getMeasurements() {
        return mMeasurements;
    }
}
