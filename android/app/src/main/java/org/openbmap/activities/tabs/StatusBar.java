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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.Constants;
import org.openbmap.R;
import org.openbmap.db.DataHelper;
import org.openbmap.events.onCellChanged;
import org.openbmap.events.onCellSaved;
import org.openbmap.events.onLocationUpdated;
import org.openbmap.events.onWifisAdded;

import java.text.DecimalFormat;

/**
 * Layout for the GPS status bar
 */
public class StatusBar extends LinearLayout {

	private static final String TAG = StatusBar.class.getSimpleName();

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

	private String mProvider;

	private DataHelper mDataHelper;

	private final TextView tvWifiCount;
	private final TextView tvNewWifiCount;
	private final TextView tvCellCount;
	private final TextView tvAccuracy;

    private final ImageView ivCellSymbol;

    private final ImageView ivSatIndicator;

    private Bitmap mIcon;


	public StatusBar(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		LayoutInflater.from(context).inflate(R.layout.statusbar, this, true);

		tvCellCount = (TextView) findViewById(R.id.gpsstatus_cell_count);
		tvWifiCount = (TextView) findViewById(R.id.gpsstatus_wifi_count);
		tvNewWifiCount = (TextView) findViewById(R.id.gpsstatus_new_wifi_count);
		tvAccuracy = (TextView) findViewById(R.id.gpsstatus_accuracy);
		tvAccuracy.setText(getResources().getString(R.string.empty));

		ivSatIndicator = (ImageView) findViewById(R.id.gpsstatus_sat_indicator);
		ivSatIndicator.setImageResource(R.drawable.sat_indicator_unknown);

        ivCellSymbol = (ImageView) findViewById(R.id.cell_symbol);
        ivCellSymbol.setImageTintList(null);

        flashSymbolTask= new Runnable() {
            @Override
            public void run() {
                ivCellSymbol.clearColorFilter();
            }
        };
		if (context instanceof TabHostActivity) {
			this.context = context;
			registerReceiver();
            EventBus.getDefault().register(this);
		} else {
            Log.e(TAG, "Not called from TabHostActivity");
        }
	}

	/**
	 * Receives SAT info updates.
	 */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
            if (Constants.INTENT_POSITION_SAT_INFO.equals(intent.getAction())) {

				final String status = intent.getExtras().getString("STATUS");
				final int satCount = intent.getExtras().getInt("SAT_COUNT");

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
		}
	};

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
        Log.d(TAG, "Received onCellChanged event");
        if (flashHandler != null) {
            flashHandler.removeCallbacks(flashSymbolTask);
            flashHandler.postDelayed(flashSymbolTask, FADE_TIME);
            ivCellSymbol.setColorFilter(Color.argb(255, 255, 0, 0));
        }
    }

    @Subscribe
	public void onEvent (onWifisAdded event) {
		if (mDataHelper != null) {
			tvWifiCount.setText(String.valueOf(mDataHelper.countWifis(mDataHelper.getCurrentSessionID())));
			tvNewWifiCount.setText(String.valueOf(mDataHelper.countNewWifis(mDataHelper.getCurrentSessionID())));
		}
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		mDataHelper = new DataHelper(context);

        tvWifiCount.setText(String.valueOf(mDataHelper.countWifis(mDataHelper.getCurrentSessionID())));
        tvNewWifiCount.setText(String.valueOf(mDataHelper.countNewWifis(mDataHelper.getCurrentSessionID())));

		registerReceiver();
	}

	@Override
	protected void onDetachedFromWindow() {
		mDataHelper = null;
		unregisterReceiver();
		super.onDetachedFromWindow();
	}

	private void registerReceiver() {
		if (context == null) {
			Log.e(TAG, "Can't register for gps status updates: context is null");
			return;
		}
		final IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.INTENT_POSITION_SAT_INFO);
        context.registerReceiver(mReceiver, filter);
	}

	/**
	 * Unregisters receivers for GPS and wifi updates
	 */
	private void unregisterReceiver() {
		if (context == null) {
			Log.e(TAG, "Can't unregister gps status updates: context is null");
			return;
		}

		try {
			context.unregisterReceiver(mReceiver);
		} catch (final IllegalArgumentException e) {
			// do nothing here {@see http://stackoverflow.com/questions/2682043/how-to-check-if-mReceiver-is-registered-in-android}
		}

		if (EventBus.getDefault().isRegistered(this)) {
			EventBus.getDefault().unregister(this);
		}
	}
}
