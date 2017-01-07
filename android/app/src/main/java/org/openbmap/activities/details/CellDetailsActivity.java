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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.openbmap.R;
import org.openbmap.activities.tabs.MapFragment;
import org.openbmap.db.DataHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.models.CellRecord;

import java.util.ArrayList;

/**
 * Display details for specific cell. CellDetailsActivity also takes care of
 * loading the records from the database. It also hosts the heatmap fragment
 */
@EActivity(R.layout.celldetails)
public class CellDetailsActivity  extends FragmentActivity {

    public static final int MAX_HEATMAP_POINTS = 1000;

    @ViewById(R.id.celldetails_networktype)	TextView tvNetworkType;
    @ViewById(R.id.celldetails_cellid)	TextView tvCellId;
    @ViewById(R.id.celldetails_operator) TextView tvOperator;
    @ViewById(R.id.celldetails_mcc) TextView tvMcc;
    @ViewById(R.id.celldetails_mnc) TextView tvMnc;
    @ViewById(R.id.celldetails_lac) TextView tvLac;
    @ViewById(R.id.celldetails_strength) TextView tvStrength;
    @ViewById(R.id.celldetails_psc) TextView tvPsc;
    @ViewById(R.id.celldetails_no_measurements) TextView tvNoMeasurements;
    @ViewById(R.id.celldetails_cdma_row) TableRow rowCdma;
    @ViewById(R.id.celldetails_baseid) TextView tvBaseId;
    @ViewById(R.id.celldetails_system_id) TextView tvSystemId;
    @ViewById(R.id.celldetails_network_id) TextView tvNetworkId;
    @ViewById(R.id.celldetails_utran_row) TableRow rowUtran;
    @ViewById(R.id.celldetails_lcid) TextView tvLcid;
    @ViewById(R.id.celldetails_rnc) TextView tvRnc;
    @ViewById(R.id.celldetails_serving) ImageView ivIsIserving;

    private DataHelper mDatahelper;

    private CellRecord mCell;
    private Integer mSession;

    private ArrayList<CellRecord> mMeasurements;

    @Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);	

		mDatahelper = new DataHelper(this);

        // get the cell _id
		final Bundle extras = getIntent().getExtras();
		final int id = extras.getInt(Schema.COL_ID);
        mSession = null;
        if (extras.getInt(Schema.COL_SESSION_ID) != 0) {
            mSession = extras.getInt(Schema.COL_SESSION_ID);
        }

        mCell = mDatahelper.getCellByRowID(id);
        // load only session measurements
        //this.mMeasurements = mDatahelper.getAllMeasurementsForCell(this.mCell, this.mSession);
        // load all measurements
        mMeasurements = mDatahelper.getAllMeasurementsForCell(this.mCell, null, MAX_HEATMAP_POINTS);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// get the cell _id
		final Bundle extras = getIntent().getExtras();
		final int id = extras.getInt(Schema.COL_ID);

		// query content provider for cell details
		mCell = mDatahelper.getCellByRowID(id);
		displayRecord(mCell);
	}

	/**
	 * @param cell 
	 * 
	 */
	private void displayRecord(final CellRecord cell) {
		if (cell != null) {
			
			// handle UTRAN lcids
			if (cell.getLogicalCellId() > 0xFFFF) {
				tvCellId.setText(String.valueOf(cell.getActualCellId()));
				rowUtran.setVisibility(View.VISIBLE);
				tvLcid.setText(String.valueOf(cell.getLogicalCellId()));
				tvRnc.setText(String.valueOf(cell.getUtranRnc()));
			} else {
				tvCellId.setText(String.valueOf(cell.getLogicalCellId()));
				rowUtran.setVisibility(View.GONE);
			}
			
			// handle cdma fields
			if (cell.isCdma()) {
				rowCdma.setVisibility(View.VISIBLE);
				tvBaseId.setText(cell.getBaseId());
				tvSystemId.setText(cell.getSystemId());
				tvNetworkId.setText(cell.getNetworkId());
			} else {
				rowCdma.setVisibility(View.GONE);
			}
			
			tvMcc.setText(cell.getMcc());
			tvMnc.setText(cell.getMnc());
			tvLac.setText(String.valueOf(cell.getArea()));
			tvNetworkType.setText(CellRecord.TECHNOLOGY_MAP().get(cell.getNetworkType()));
			tvOperator.setText(cell.getOperatorName());
			tvStrength.setText(String.valueOf(cell.getStrengthdBm()));
			tvPsc.setText(String.valueOf(cell.getPsc()));
			if (cell.isServing()) {
				ivIsIserving.setImageResource(android.R.drawable.checkbox_on_background);
			} else {
				ivIsIserving.setImageResource(android.R.drawable.checkbox_off_background);
			}
			//chkIsServing.setChecked(!cell.isNeighbor());
		}
	}

	public CellRecord getCell() {
		return mCell;
	}

	/**
	 * highlights selected wifi on MapView
	 * @param id
	 */
	public void onCellSelected(final long id) {
		final Intent intent = new Intent(this, MapFragment.class);
		intent.putExtra(Schema.COL_ID, (int) id);
		startActivity(intent);
	}

	/**
	 * 
	 */
	public void setNoMeasurements(final int count) {
		tvNoMeasurements.setText(String.valueOf(count));
	}


    public ArrayList<CellRecord> getMeasurements() {
        return mMeasurements;
    }
}
