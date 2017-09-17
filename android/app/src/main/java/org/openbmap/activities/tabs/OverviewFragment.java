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
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.ViewById;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.R;
import org.openbmap.activities.details.WifiDetailsActivity_;
import org.openbmap.db.DataHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.models.CellRecord;
import org.openbmap.events.onBlacklisted;
import org.openbmap.events.onCellChanged;
import org.openbmap.events.onCellSaved;
import org.openbmap.events.onFreeWifi;
import org.openbmap.events.onWifisAdded;

import java.util.ArrayList;
import java.util.List;

import static com.github.mikephil.charting.components.LimitLine.LimitLabelPosition.RIGHT_BOTTOM;
import static com.github.mikephil.charting.components.LimitLine.LimitLabelPosition.RIGHT_TOP;

/**
 * Activity for displaying basic session infos (# of cells, wifis, etc.)
 */
@EFragment(R.layout.overview_fragment)
public class OverviewFragment extends Fragment {

    private static final String TAG = OverviewFragment.class.getSimpleName();

    /**
     * Fade message after which time (in millis)
     */
    private static final long FADE_TIME = 10 * 1000;

    /**
     * Display periodic messages how often (in millis)
     */
    private final static int REFRESH_INTERVAL = 1000 * 2;

    /**
     * UI controls
     */
    @ViewById(R.id.overview__cell_description)
    public TextView tvCellDescription;
    @ViewById(R.id.overview_cell_strength)
    public TextView tvCellStrength;
    @ViewById(R.id.overview_wifi_description)
    public TextView tvWifiDescription;
    @ViewById(R.id.overview_wifi_strength)
    public TextView tvWifiStrength;
    @ViewById(R.id.overview_technology)
    public TextView tvTechnology;
    @ViewById(R.id.overview_blacklisted)
    public TextView tvIgnored;
    @ViewById(R.id.overview_free_wifi_found)
    public TextView tvFree;
    @ViewById(R.id.overview_free_wifi_found_icon)
    public ImageView ivFree;
    @ViewById(R.id.overview_alert_icon)
    public ImageView ivAlert;
    @ViewById(R.id.overview_graph)
    public LineChart gvGraph;
    @ViewById(R.id.overview_wifi_details_button)
    public ImageButton btnWifiDetails;
    @ViewById(R.id.overview_cell_details_button)
    public ImageButton btnCellDetails;

    final int greenColor = android.R.color.holo_green_dark;
    final int redColor = android.R.color.holo_red_dark;

    private String lastCid;
    private String lastTech;

    /**
     * Last wifi as displayed
     */
    private String prevBssid;

    /**
     * Time of last new wifi in millis
     */
    private long prevWifiUpdateTime;

    /**
     * Time of last new cell in millis
     */
    private long prevCellUpdateTime;

    /**
     * Fades ignore messages after certain time
     */
    private Runnable mFadeIgnoreTask;
    private final Handler mFadeIgnoreHandler = new Handler();

    private Runnable mFadeFreeTask;
    private final Handler mFadeFreeHandler = new Handler();

    /**
     * Update certain infos at periodic intervals
     */
    private Runnable refreshTask;
    private final Handler refreshHandler = new Handler();

    private int currentLevel;
    private String prevTechnology;

    private float prevHandoverLabel;
    private int prevLabelOffset = -1;

    private List<Integer> colors = new ArrayList<>();

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFadeIgnoreTask = () -> {
            if (tvIgnored != null) {
                tvIgnored.setVisibility(View.INVISIBLE);
                ivAlert.setVisibility(View.INVISIBLE);
            }
        };

        mFadeFreeTask = () -> {
            if (tvFree != null) {
                tvFree.setVisibility(View.INVISIBLE);
                ivFree.setVisibility(View.INVISIBLE);
            }
        };

        refreshTask = () -> {
            updateTimeSinceUpdate();
            updateGraph();
            refreshHandler.postDelayed(refreshTask, REFRESH_INTERVAL);
        };

        // setup broadcast filters
        registerReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();

        registerReceiver();
        startRepeatingTask();
    }

    @Override
    public void onPause() {
        stopRepeatingTask();

        super.onPause();
    }


    @Subscribe
    public void onEvent(onWifisAdded event) {
        if (event.items != null && event.items.size() > 0) {
            tvWifiDescription.setText(event.items.get(0).getSsid());
            tvWifiStrength.setText(String.format("%d dBm", event.items.get(0).getLevel()));
            prevBssid = event.items.get(0).getBssid();
        } else {
            tvWifiDescription.setText(getString(R.string.n_a));
            tvWifiStrength.setText("");
        }

        prevWifiUpdateTime = System.currentTimeMillis();
    }

    @Subscribe
    public void onEvent(onFreeWifi event) {
        mFadeFreeHandler.removeCallbacks(mFadeFreeTask);
        final String ssid = event.ssid;
        if (ssid != null) {
            tvFree.setText(getResources().getString(R.string.free_wifi) + "\n" + ssid);
        }
        tvFree.setVisibility(View.VISIBLE);
        ivFree.setVisibility(View.VISIBLE);
        mFadeFreeHandler.postDelayed(mFadeFreeTask, FADE_TIME);
    }

    /**
     * Displays explanation for blacklisting
     *
     * @param event
     */
    @Subscribe
    public void onEvent(onBlacklisted event) {
        mFadeIgnoreHandler.removeCallbacks(mFadeIgnoreTask);

        switch (event.reason) {
            case BadSSID:
                tvIgnored.setText(event.message + " " + getResources().getString(R.string.blacklisted_bssid));
                break;
            case BadBSSID:
                tvIgnored.setText(event.message + " " + getResources().getString(R.string.blacklisted_ssid));
                break;
            case BadLocation:
                tvIgnored.setText(R.string.blacklisted_area);
                break;
        }
        tvIgnored.setVisibility(View.VISIBLE);
        ivAlert.setVisibility(View.VISIBLE);
        mFadeIgnoreHandler.postDelayed(mFadeIgnoreTask, FADE_TIME);
    }

    @Subscribe
    public void onEvent(final onCellSaved event) {
        Log.d(TAG, "Cell update received, level" + event.level);

        currentLevel = event.level;
        prevCellUpdateTime = System.currentTimeMillis();

        String description = event.operator;

        if (description.length() > 0) {
            description += "\n";
        }
        if (!event.mcc.equals(CellRecord.MCC_UNKNOWN) && !event.mnc.equals(CellRecord.MNC_UNKNOWN)) {
            // typical available information for GSM/LTE cells: MCC, MNC and area
            description += String.format("%s/%s/%s", event.mcc, event.mnc, event.area);
        }
        if (!event.sid.equals(CellRecord.SYSTEM_ID_UNKNOWN) &&
                !event.nid.equals(CellRecord.NETWORK_ID_UNKOWN) &&
                !event.bid.equals(CellRecord.BASE_ID_UNKNOWN)) {
            // typical available information for CDMA cells: system id, network id and base id
            description += String.format("%s/%s/%s", event.sid, event.nid, event.bid);
        }
        description += String.format("/%s", event.cell_id);

        tvCellDescription.setText(description);
        tvCellStrength.setText(String.format("%d dBm", currentLevel));

        if (!event.technology.equals(prevTechnology)) {
            prevTechnology = event.technology;
            final Animation in = new AlphaAnimation(0.0f, 1.0f);
            in.setDuration(2000);
            final Animation out = new AlphaAnimation(1.0f, 0.0f);
            out.setDuration(2000);
            out.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    tvTechnology.setText(event.technology);
                    tvTechnology.startAnimation(in);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            tvTechnology.startAnimation(out);
        }
    }

    private void startRepeatingTask() {
        refreshTask.run();
    }

    private void stopRepeatingTask() {
        refreshHandler.removeCallbacks(refreshTask);
    }


    /**
     * Fired when serving cell has changed
     * @param event
     */
    @Subscribe
    public void onEvent(onCellChanged event) {
        LineData data = gvGraph.getData();
        ILineDataSet set = data.getDataSetByIndex(0);
        if (set != null) {
            float pos = (float) set.getEntryCount();
            LimitLine ll = new LimitLine(pos);
            ll.setLineColor(Color.RED);
            ll.setTextColor(Color.WHITE);

            if (event.cellId != null && !event.cellId.equals(lastCid)) {
                Log.i(TAG, "Cell handover from " + lastCid + " to " + event.cellId + "(" + event.technology + ")");
                ll.setLabel(getString(R.string.handover) + "\n" + event.cellId);
                ll.setLineWidth(4f);
                ll.setTextSize(12f);
                ll.setLabelPosition(RIGHT_TOP);
                if (pos - prevHandoverLabel < 20f) {
                    ll.setYOffset((prevLabelOffset + 1) * 15f);
                    if (prevLabelOffset < 2) {
                        prevLabelOffset += 1;
                    } else {
                        prevLabelOffset = -1;
                    }
                } else {
                    // last limit line far enough away - probably nothing overlapping
                    prevLabelOffset = -1;
                }
                prevHandoverLabel = pos;
                lastCid = event.cellId;
                lastTech = event.technology;
            } else if (event.technology != null) {
                Log.i(TAG, "Cell type changed from " + lastTech + " to " + event.technology + "(cell " + event.cellId + ")");
                ll.setLabel(event.technology);
                ll.setLineWidth(2f);
                ll.setTextSize(8f);
                ll.setLabelPosition(RIGHT_BOTTOM);
                if (event.cellId != null) {
                    lastCid = event.cellId;
                }
                lastTech = event.technology;
            }

            XAxis xAxis = gvGraph.getXAxis();
            xAxis.setDrawLimitLinesBehindData(true);
            xAxis.addLimitLine(ll);
        }
    }

    /**
     * Init UI contols
     */
    @AfterViews
    public void initUI() {
        // enable touch gestures
        gvGraph.setTouchEnabled(true);

        // enable scaling and dragging
        gvGraph.setDragEnabled(true);
        gvGraph.setScaleEnabled(true);
        gvGraph.setDrawGridBackground(false);
        gvGraph.getDescription().setEnabled(false);

        // if disabled, scaling can be done on x- and y-axis separately
        gvGraph.setPinchZoom(true);

        //gvGraph.setBackgroundColor(Color.LTGRAY);

        // add empty data
        LineData data = new LineData();
        gvGraph.setData(data);

        // get the legend (only possible after setting data)
        //Legend l = gvGraph.getLegend();
        //l.setForm(Legend.LegendForm.LINE);
        //l.setTextColor(Color.WHITE);
        gvGraph.getLegend().setEnabled(false);

        XAxis x = gvGraph.getXAxis();
        x.setTextColor(Color.WHITE);
        x.setDrawGridLines(false);
        x.setAvoidFirstLastClipping(true);
        x.setEnabled(false);

        YAxis y = gvGraph.getAxisLeft();
        y.setTextColor(Color.WHITE);
        y.setAxisMaximum(-50f);
        y.setAxisMinimum(-110f);
        y.setDrawGridLines(true);

        YAxis yr = gvGraph.getAxisRight();
        yr.setEnabled(false);
    }

    @Click(R.id.overview_wifi_details_button)
    void wifiDetailsClicked() {
        if (prevBssid != null) {
            DataHelper helper = new DataHelper(getActivity().getApplicationContext());
            int session = helper.getCurrentSessionID();
            final Intent intent = new Intent();
            intent.setClass(getActivity(), WifiDetailsActivity_.class);
            intent.putExtra(Schema.COL_BSSID, prevBssid);
            intent.putExtra(Schema.COL_SESSION_ID, session);
            startActivity(intent);
        } else {
            Toast.makeText(getActivity(), getString(R.string.no_wifi_details_available), Toast.LENGTH_LONG).show();
        }
    }

    @Click(R.id.overview_cell_details_button)
    void cellDetailsClicked() {

    }

    /**
     * Registers broadcast receivers.
     */
    private void registerReceiver() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        } else {
            Log.i(TAG, "Event bus receiver already registered");
        }
    }


    /**
     * Displays time since last cell/wifi update
     */
    private void updateTimeSinceUpdate() {
        final String deltaCellString = "Last cell update " + getTimeSinceLastUpdate(prevCellUpdateTime) + " ago";
        final String deltaWifiString = "Last cell update " + getTimeSinceLastUpdate(prevWifiUpdateTime) + " ago";
    }

    /**
     * Redraws cell strength graph
     */
    private void updateGraph() {
        LineData data = gvGraph.getData();

        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(0);
            if (set == null) {
                set = createDefaultSet();
                // WORK-AROUND: Add dummy data, otherwise MPCharts crashes
                // see https://github.com/PhilJay/MPAndroidChart/issues/2455
                set.addEntry(new Entry(0, 0));
                data.addDataSet(set);
            }

            if (currentLevel != -1) {
                //mMeasurements.appendData(new DataPoint(graph2LastXValue, currentLevel), true, 60);
                data.addEntry(new Entry(set.getEntryCount(), (float) currentLevel), 0);
            } else {
                //mMeasurements.appendData(new DataPoint(graph2LastXValue, -100d), true, 60);
                data.addEntry(new Entry(set.getEntryCount(), (float) -100f), 0);
            }

            colors.add(currentLevel >= -70 ? Color.parseColor("#11702b") : Color.WHITE);
            colors = set.getColors();
            ((LineDataSet) set).setCircleColors(colors);

            data.notifyDataChanged();
            //currentLevel = -1;

            // let the chart know it's data has changed
            gvGraph.notifyDataSetChanged();

            // limit the number of visible entries
            gvGraph.setVisibleXRangeMaximum(50);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            gvGraph.moveViewToX(data.getEntryCount());

            // this automatically refreshes the chart (calls invalidate())
            // mChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }

    private LineDataSet createDefaultSet() {
        LineDataSet set = new LineDataSet(null, this.getString(R.string.graph_title));
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);

        set.setColor(ColorTemplate.getHoloBlue());
        set.setLineWidth(1.75f);

        set.setCircleColor(Color.WHITE);
        set.setCircleRadius(5f);
        //set.setCircleHoleRadius(4f);
        set.setDrawValues(false);
        return set;
    }

    /**
     * Returns time since base value as human-readable string
     *
     * @return
     */
    private String getTimeSinceLastUpdate(final long base) {
        String deltaString = "";

        // no previous updates
        if (base == 0) {
            return deltaString;
        }

        final long delta = (System.currentTimeMillis() - base);

        if (delta < 60000) {
            deltaString = String.valueOf(delta / 1000) + getString(R.string.seconds);
        } else {
            deltaString = String.valueOf(delta / 60000) + getString(R.string.minutes);
        }
        return deltaString;
    }
}
