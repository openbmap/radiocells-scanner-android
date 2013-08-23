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
import org.openbmap.db.DataHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.model.CellRecord;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.CheckedTextView;
import android.widget.TextView;

/**
 * Parent activity for hosting cell detail fragement
 */
public class CellDetailsActivity  extends FragmentActivity implements CellDetailsFragment.OnCellSelectedListener {


	private TextView	tvNetworkType;
	private TextView	tvCellId;
	private TextView	tvBaseId;
	private TextView	tvOperator;
	private TextView	tvMcc;
	private TextView	tvMnc;
	private TextView	tvStrength;
	private CheckedTextView	chkIsServing;
	
	private DataHelper mDatahelper;
	
	private CellRecord mDisplayed;


	/** Called when the activity is first created. */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);	
		setContentView(R.layout.celldetails);

		tvNetworkType = (TextView) findViewById(R.id.celldetails_networktype);
		tvCellId = (TextView) findViewById(R.id.celldetails_cellid);
		tvBaseId = (TextView) findViewById(R.id.celldetails_baseid);
		tvOperator = (TextView) findViewById(R.id.celldetails_operator);
		tvMcc = (TextView) findViewById(R.id.celldetails_mcc);
		tvMnc = (TextView) findViewById(R.id.celldetails_mnc);
		tvStrength = (TextView) findViewById(R.id.celldetails_strength);
		chkIsServing = (CheckedTextView) findViewById(R.id.celldetails_chkIsServing);
		
		CellDetailsFragment detailsFragment = (CellDetailsFragment) getSupportFragmentManager().findFragmentById(R.id.cellDetailsFragment);	
		detailsFragment.setOnCellSelectedListener(this);

		mDatahelper = new DataHelper(this);
	}

	@Override
	protected final void onResume() {
		super.onResume();

		// get the cell _id
		Bundle extras = getIntent().getExtras();
		int id = extras.getInt(Schema.COL_ID);
		// query content provider for cell details
		mDisplayed = mDatahelper.loadCellById(id);

		displayRecord(mDisplayed);
	}

	/**
	 * @param cell 
	 * 
	 */
	private void displayRecord(final CellRecord cell) {
		if (cell != null) {
			tvCellId.setText(String.valueOf(cell.getCid()));
			tvBaseId.setText(cell.getBaseId());
			tvMcc.setText(cell.getMcc());
			tvMnc.setText(cell.getMnc());
			tvNetworkType.setText(CellRecord.NETWORKTYPE_MAP().get(cell.getNetworkType()));
			tvOperator.setText(cell.getOperatorName());
			tvStrength.setText(String.valueOf(cell.getStrengthdBm()));
			chkIsServing.setChecked(!cell.isNeighbor());
		}
	}

	@Override
	public final void onStart() {
		super.onStart();
	}

	public final CellRecord getCell() {
		return mDisplayed;
	}

	/**
	 * highlights selected wifi on MapView
	 * @param id
	 */
	public final void onCellSelected(final long id) {
		Intent intent = new Intent(this, MapViewActivity.class);
		intent.putExtra(Schema.COL_ID, (int) id);
		startActivity(intent);
	}

}
