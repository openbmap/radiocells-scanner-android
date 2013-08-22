package org.openbmap.activity;

import org.openbmap.R;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class CellsListActivity extends FragmentActivity {

	@SuppressWarnings("unused")
	private static final String TAG = CellsListActivity.class.getSimpleName();

	/** Called when the activity is first created. */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cellslist);
	}

	@Override
	public final void onStart() {
		super.onStart();
	}
}
