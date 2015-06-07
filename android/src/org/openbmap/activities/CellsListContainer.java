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

import org.openbmap.R;
import org.openbmap.db.DataHelper;
import org.openbmap.db.RadioBeaconContentProvider;
import org.openbmap.db.Schema;
import org.openbmap.db.models.CellRecord;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter.ViewBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;

/**
 * Parent activity for hosting cell list
 */
public class CellsListContainer extends SherlockListFragment implements LoaderManager.LoaderCallbacks<Cursor>{

	@SuppressWarnings("unused")
	private static final String TAG = CellsListContainer.class.getSimpleName();

	/**
	 * 
	 */
	private static final int CELL_LOADER_ID	= 2;

	private SimpleCursorAdapter	mAdapter;
	
	/**
	 * WHERE clause for loader
	 */
	private String	mSelection;

	/**
	 * WHERE clause arguments for loader
	 */
	private String[] mSelectionArgs = null;
	
	/**
	 * Session id
	 */
	private int	mSession;

	/**
	 * @see http://stackoverflow.com/questions/6317767/cant-add-a-headerview-to-a-listfragment
	 *      Fragment lifecycle
	 *      onAttach(Activity) called once the fragment is associated with its activity.
	 *      onCreate(Bundle) called to do initial creation of the fragment.
	 *      onCreateView(LayoutInflater, ViewGroup, Bundle) creates and returns the view hierarchy associated with the fragment.
	 *      onActivityCreated(Bundle) tells the fragment that its activity has completed its own Activity.onCreate.
	 *      onStart() makes the fragment visible to the user (based on its containing activity being started).
	 *      onResume() makes the fragment interacting with the user (based on its containing activity being resumed).
	 */

	@Override
	public final void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// Trying to add a Header View.
		final View header = (View) getLayoutInflater(savedInstanceState).inflate(R.layout.celllistheader, null);
		this.getListView().addHeaderView(header);

		// setup data
		initData();
		getActivity().getSupportLoaderManager().initLoader(CELL_LOADER_ID, null, this);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		return inflater.inflate(R.layout.cellslist, container, false);
	}
	
	/**
	 * 
	 */
	private void initData() {
		final DataHelper dataHelper = new DataHelper(getActivity());
		mSession = dataHelper.getActiveSessionId();

		final String[] from = new String[] {
				Schema.COL_ACTUAL_CELLID,
				Schema.COL_OPERATORNAME,
				Schema.COL_NETWORKTYPE,
				"MAX(" + Schema.COL_STRENGTHDBM + ")" 
		};

		final int[] to = new int[] {
				R.id.textViewCellID,
				R.id.textViewOperator,
				R.id.textViewNetworkType,
				R.id.textViewStrenghtDbm
		};

		mAdapter = new SimpleCursorAdapter(getActivity().getBaseContext(),
				R.layout.celllistitems, null, from, to, 0);

		mAdapter.setViewBinder(new NetworkTypeDescriptionViewBinder());
		setListAdapter(mAdapter);
	}

	/**
	 * User has clicked on cell record.
	 * @param lv listview; this
	 * @param iv item clicked
	 * @param position position within list
	 * @param id  track ID
	 */
	@Override
	public final void onListItemClick(final ListView lv, final View iv, final int position, final long id) {
		if (position != 0) {
			final Intent intent = new Intent();
			intent.setClass(getActivity(), CellDetailsActivity.class);
			intent.putExtra(Schema.COL_ID, (int) id);
			startActivity(intent);
		}
	}

	@Override
	public final void onDestroy() {
		setListAdapter(null);
		super.onDestroy();
	}

	@Override
	public final Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
		final DataHelper dataHelper = new DataHelper(getActivity());

		final String[] projection = {
				Schema.COL_ID,
				Schema.COL_ACTUAL_CELLID,
				Schema.COL_OPERATORNAME,
				Schema.COL_NETWORKTYPE,
				"MAX(" + Schema.COL_STRENGTHDBM + ")"
		};

		final int session = dataHelper.getActiveSessionId();

		// TODO: mSelection and mSelectionArgs are yet ignored
		final CursorLoader cursorLoader = new CursorLoader(getActivity().getBaseContext(), ContentUris.withAppendedId(Uri.withAppendedPath(
						RadioBeaconContentProvider.CONTENT_URI_CELL, RadioBeaconContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), session),
						projection, mSelection, mSelectionArgs, null);
		return cursorLoader;
	}

	@Override
	public final void onLoadFinished(final Loader<Cursor> arg0, final Cursor cursor) {
		mAdapter.swapCursor(cursor);
	}

	@Override
	public final void onLoaderReset(final Loader<Cursor> arg0) {
		mAdapter.swapCursor(null);
	}

	/**
	 * Forces reload
	 */
	private void reload() {
		getLoaderManager().restartLoader(CELL_LOADER_ID, null, CellsListContainer.this);
	}
	
	/**
	 * Sets loader filters. Won't become effective until next reload
	 * @param selection
	 * @param selectionArgs
	 */
	private void setFilters(final String selection, final String[] selectionArgs) {
		mSelection = selection;
		mSelectionArgs = selectionArgs;
	}
	
	/**
	 * Clears filters. Won't become effective until next reload
	 */
	private void resetFilters() {
		mSelection = null;
		mSelectionArgs = null;
	}
	
	private static class NetworkTypeDescriptionViewBinder implements ViewBinder {

		/**
		 * NETWORK_TYPE column index in cursor
		 * If you're unsure on index, check getColumnIndex(Schema.COL_NETWORKTYPE)
		 */
		//private static final int INDEX_NETWORK_TYPE	= 12;

		/**
		 * Translates network type (int) to human-readable description.
		 */
		public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
			if (columnIndex == cursor.getColumnIndex(Schema.COL_NETWORKTYPE)) { 
				final int result = cursor.getInt(columnIndex);
				((TextView) view).setText(CellRecord.TECHNOLOGY_MAP().get(result));
				return true;
			}
			return false;
		}
	}

}
