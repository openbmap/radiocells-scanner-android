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

import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.ViewById;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.Constants;
import org.openbmap.R;
import org.openbmap.db.DataHelper;
import org.openbmap.events.onCellChanged;
import org.openbmap.events.onCellSaved;
import org.openbmap.events.onLocationUpdated;
import org.openbmap.events.onSatInfo;
import org.openbmap.events.onWifisAdded;

import java.text.DecimalFormat;

/**
 * Activity for displaying basic session infos (# of cells, wifis, etc.)
 */
@EFragment(R.layout.statusbar_fragment)
public class HeaderFragment extends Fragment {

    private static final String TAG = HeaderFragment.class.getSimpleName();

    /**
     * Formatter for accuracy display.
     */
    private static final DecimalFormat ACCURACY_FORMAT = new DecimalFormat("0");

    /**
     * Keeps matching between satellite indicator bars to draw, and numbers
     * of satellites for each bars;
     */
    private static final int[] SAT_INDICATOR_TRESHOLD = {2, 3, 4, 6, 8};

    /**
     * Fade message after which time (in millis)
     */
    private static final long FADE_TIME = 2 * 1000;

    /**
     * Containing activity
     */
    private Context context;

    /**
     * Fades ignore messages after certain time
     */
    private Runnable flashSymbolTask;
    private final Handler flashHandler = new Handler();

    /**
     * Is GPS active ?
     */
    private final boolean gpsActive = false;

    private DataHelper mDataHelper;

    @ViewById(R.id.gpsstatus_wifi_count)
    public TextView tvWifiCount;
    @ViewById(R.id.gpsstatus_new_wifi_count)
    public TextView tvNewWifiCount;
    @ViewById(R.id.gpsstatus_cell_count)
    public TextView tvCellCount;
    @ViewById(R.id.gpsstatus_accuracy)
    public TextView tvAccuracy;
    @ViewById(R.id.cell_symbol)
    public ImageView ivCellSymbol;
    @ViewById(R.id.gpsstatus_sat_indicator)
    public ImageView ivSatIndicator;

    private Bitmap mIcon;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EventBus.getDefault().register(this);

        mDataHelper = new DataHelper(getActivity());

    }

    @AfterViews
    public void initUI() {
        tvAccuracy.setText(getResources().getString(R.string.empty));
        ivCellSymbol.setImageTintList(null);
        ivSatIndicator.setImageResource(R.drawable.sat_indicator_unknown);

        flashSymbolTask = new Runnable() {
            @Override
            public void run() {
                ivCellSymbol.clearColorFilter();
            }
        };

        tvWifiCount.setText(String.valueOf(mDataHelper.countWifis(mDataHelper.getCurrentSessionID())));
        tvNewWifiCount.setText(String.valueOf(mDataHelper.countNewWifis(mDataHelper.getCurrentSessionID())));
    }


    @Override
    public void onDestroy() {
        mDataHelper = null;

        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }

        super.onDestroy();
    }


    /**
     * Receives SAT info updates.
     */
    @Subscribe
    public void onEvent(onSatInfo event) {
        String status = event.satStatus;
        final int satCount = event.satCount;

        ivSatIndicator.setImageBitmap(null);
        if (mIcon != null && !mIcon.isRecycled()) {
            mIcon.recycle();
        }

        if ("UPDATE".equals(status)) {
            // Count how many bars should we draw
            int nbBars = 0;
            for (int i = 0; i < SAT_INDICATOR_TRESHOLD.length; i++) {
                if (satCount >= SAT_INDICATOR_TRESHOLD[i]) {
                    nbBars = i;
                }
            }
            mIcon = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier("drawable/sat_indicator_" + nbBars, null, Constants.class.getPackage().getName()));

        } else if ("OUT_OF_SERVICE".equals(status)) {
            mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.sat_indicator_off);
            tvAccuracy.setText(getResources().getString(R.string.no_sat_signal));
        } else if ("TEMPORARILY_UNAVAILABLE".equals(status)) {
            mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.sat_indicator_off);
            tvAccuracy.setText(getResources().getString(R.string.no_sat_signal));
        }
        ivSatIndicator.setImageBitmap(mIcon);
    }


    @Subscribe
    public void onEvent(onLocationUpdated event) {
        if (event.location == null) {
            return;
        }

        if (event.location.hasAccuracy()) {
            //Log.v(TAG, "GPS Accuracy " + event.location.getAccuracy());
            tvAccuracy.setText(ACCURACY_FORMAT.format(event.location.getAccuracy()) + getResources().getString(R.string.meter));
        } else {
            tvAccuracy.setText(getResources().getString(R.string.empty));
        }
    }

    @Subscribe
    public void onEvent(onCellSaved event) {
        if (mDataHelper != null) {
            tvCellCount.setText(String.valueOf(mDataHelper.countCells(mDataHelper.getCurrentSessionID())));
        }
    }

    @Subscribe
    public void onEvent(onCellChanged event) {
        if (flashHandler != null) {
            flashHandler.removeCallbacks(flashSymbolTask);
            flashHandler.postDelayed(flashSymbolTask, FADE_TIME);
            ivCellSymbol.setColorFilter(Color.argb(255, 255, 0, 0));
        }
    }

    @Subscribe
    public void onEvent(onWifisAdded event) {
        if (mDataHelper != null) {
            tvWifiCount.setText(String.valueOf(mDataHelper.countWifis(mDataHelper.getCurrentSessionID())));
            tvNewWifiCount.setText(String.valueOf(mDataHelper.countNewWifis(mDataHelper.getCurrentSessionID())));
        }
    }

}
