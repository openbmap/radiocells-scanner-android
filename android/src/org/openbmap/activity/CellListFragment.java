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
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.RadioBeaconContentProvider;
import org.openbmap.db.Schema;
import org.openbmap.db.model.CellRecord;
import org.openbmap.db.model.Session;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter.ViewBinder;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Fragment for displaying all tracked cells
 */
public class CellListFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {

	public static final String	TAG	= CellListFragment.class.getSimpleName();
	private SimpleCursorAdapter	adapter;

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

		String[] from = new String[] { Schema.COL_CELLID,
				Schema.COL_OPERATORNAME, Schema.COL_NETWORKTYPE,
				"MAX(" + Schema.COL_STRENGTHDBM + ")" };

		int[] to = new int[] { R.id.textViewCellID, R.id.textViewOperator,
				R.id.textViewNetworkType, R.id.textViewStrenghtDbm};

		adapter = new SimpleCursorAdapter(getActivity().getBaseContext(),
				R.layout.celllistfragment, null, from, to, 0);
		adapter.setViewBinder(new NetworkTypeDescriptionViewBinder());

		// Trying to add a Header View.
		View header = (View) getLayoutInflater(savedInstanceState).inflate(
				R.layout.celllistheader, null);
		this.getListView().addHeaderView(header);

		setListAdapter(adapter);
		getActivity().getSupportLoaderManager().initLoader(0, null, this);
	}

	private static class NetworkTypeDescriptionViewBinder implements ViewBinder {

		/**
		 * NETWORK_TYPE column index in cursor
		 * If you're unsure on index, check getColumnIndex(Schema.COL_NETWORKTYPE)
		 */
		private static final int INDEX_NETWORK_TYPE	= 12;

		/**
		 * Translates network type (int) to human-readable description.
		 */
		public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
			if (columnIndex == INDEX_NETWORK_TYPE) { 
				int result = cursor.getInt(columnIndex);
				((TextView) view).setText(CellRecord.NETWORKTYPE_MAP().get(result));
				return true;
			}
			return false;
		}
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
			Intent intent = new Intent();
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
		
		DataHelper dataHelper = new DataHelper(getActivity());
		
		String[] projection = { Schema.COL_ID, Schema.COL_CELLID,
				Schema.COL_OPERATORNAME, Schema.COL_NETWORKTYPE,
				"MAX(" + Schema.COL_STRENGTHDBM + ")"};
		
		int session = dataHelper.getActiveSessionId();
		
		CursorLoader cursorLoader = new CursorLoader(getActivity()
				.getBaseContext(), ContentUris.withAppendedId(Uri.withAppendedPath(
						RadioBeaconContentProvider.CONTENT_URI_CELL, RadioBeaconContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), session),
				projection, null, null, null);
		return cursorLoader;
	}

	@Override
	public final void onLoadFinished(final Loader<Cursor> arg0, final Cursor cursor) {
		adapter.swapCursor(cursor);
	}

	@Override
	public final void onLoaderReset(final Loader<Cursor> arg0) {
		adapter.swapCursor(null);
	}

}
