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

import java.util.ArrayList;

import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.RadioBeaconContentProvider;
import org.openbmap.db.Schema;
import org.openbmap.db.model.CellRecord;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter.ViewBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;

/**
 * Fragment for displaying cell detail information
 */
public class CellDetailsFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>  {
	private static final String TAG = CellDetailsFragment.class.getSimpleName();

	private SimpleCursorAdapter adapter;

	/**
	 * Listener to handle onClick events
	 */
	private OnCellDetailsListener mListener;

	private int	mCellId;

	private CellRecord	mCell;

	@Override
	public final void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);	

		mCell = ((CellDetailsActivity) getActivity()).getCell();
		String [] from = new String []{Schema.COL_TIMESTAMP, Schema.COL_STRENGTHDBM};
		int [] to = new int [] {
				R.id.celldetailsfragment_timestamp,
				R.id.celldetailsfragment_progress
		};
		adapter = new SimpleCursorAdapter(getActivity().getBaseContext(), R.layout.celldetailsfragment, null, from, to, 0);
		adapter.setViewBinder(new ProgressBarViewBinder());
		setListAdapter(adapter);

		getActivity().getSupportLoaderManager().initLoader(0, null, this); 
	}

	private static class ProgressBarViewBinder implements ViewBinder {

		/**
		 * 
		 */
		private static final int	COL_IDX_STRENGTH_DBM	= 1;
		/**
		 * max value for progress bar indicator, assuming -50 dBm is highest possible level
		 */
		private static final int MAX_VALUE = 100;

		/**
		 * indicators for signal strength
		 */
		public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
			if (view instanceof ProgressBar) {
				String result = cursor.getString(COL_IDX_STRENGTH_DBM);
				int resInt = MAX_VALUE - (-Integer.parseInt(result));
				if (resInt == 0) {
					((ProgressBar) view).setIndeterminate(true);
					return true;
				} else {
					((ProgressBar) view).setProgress(resInt);
					return true;
				}
			}
			return false;
		}
	}

	@Override
	public final View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	public final void onDestroy() {
		setListAdapter(null);
		super.onDestroy();
	}

	public interface OnCellDetailsListener {
		void onCellSelected(long id);
		void onMeasurementsLoaded(int count);
	}

	public final void setOnCellSelectedListener(final OnCellDetailsListener listener) {
		this.mListener = listener;
	}

	/**
	 * User has clicked on wifi record.
	 * @param lv listview; this
	 * @param iv item clicked
	 * @param position position within list
	 * @param id ID
	 */
	@Override
	public final void onListItemClick(final ListView lv, final View iv, final int position, final long id) {
		mListener.onCellSelected(id);
	}

	@Override
	public final Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
		// set query params: id and session id
		ArrayList<String> args = new ArrayList<String>();
		String selectSql = "";
		
		if (mCell != null && mCell.getCid() != -1) {
			args.add(String.valueOf(mCell.getCid()));
			selectSql = Schema.COL_CELLID + " = ?";
		} else if (mCell != null && mCell.isCdma()
				&& !mCell.getBaseId().equals("-1") && !mCell.getNetworkId().equals("-1") && !mCell.getSystemId().equals("-1")) {
			args.add(mCell.getBaseId());
			args.add(mCell.getNetworkId());
			args.add(mCell.getSystemId());
			selectSql = Schema.COL_BASEID + " = ? AND " + Schema.COL_NETWORKID + " = ? AND " + Schema.COL_SYSTEMID + " = ?";
		}

		DataHelper dbHelper = new DataHelper(this.getActivity());
		args.add(String.valueOf(dbHelper.getActiveSessionId()));
		if (selectSql.length() > 0) {
			selectSql += " AND ";
		}
		selectSql += Schema.COL_SESSION_ID + " = ?";

		String[] projection = {Schema.COL_ID, Schema.COL_STRENGTHDBM, Schema.COL_TIMESTAMP};
		// query data from content provider
		CursorLoader cursorLoader =
				new CursorLoader(getActivity().getBaseContext(),
						RadioBeaconContentProvider.CONTENT_URI_CELL, projection, selectSql, args.toArray(new String[args.size()]), Schema.COL_STRENGTHDBM + " DESC");
		return cursorLoader;
	}

	@Override
	public final void onLoadFinished(final Loader<Cursor> arg0, final Cursor cursor) {
		adapter.swapCursor(cursor);
		mListener.onMeasurementsLoaded(cursor.getCount());
	}

	@Override
	public final void onLoaderReset(final Loader<Cursor> arg0) {
		Log.d(TAG, "onLoadReset called");
		adapter.swapCursor(null);
	}
}
