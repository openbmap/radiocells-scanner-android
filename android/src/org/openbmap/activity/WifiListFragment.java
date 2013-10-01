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
import org.openbmap.db.RadioBeaconContentProvider;
import org.openbmap.db.Schema;
import org.openbmap.utils.TriToggleButton;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter.ViewBinder;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;


/**
 * Fragment for displaying all tracked wifis
 */
public class WifiListFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>  {
	/**
	 * By default, show newest wifi first
	 */
	private static final String	DEFAULT_SORT_ORDER	= Schema.COL_TIMESTAMP + " DESC";

	/**
	 * Be careful:
	 * All external linking (e.g. to map) must rely on BSSID.
	 * (_id is a pseudo-id as original id can't be used for GROUP BY clauses)
	 */
	private static final String TAG = WifiListFragment.class.getSimpleName();

	private CursorLoader mCursorLoader;

	/**
	 * Adapter for retrieving wifis.
	 */
	private SimpleCursorAdapter mAdapter;

	/** 
	 * Sort order
	 */
	private String mSortOrder = DEFAULT_SORT_ORDER;

	/**
	 * Session id
	 */
	private int	mSession;

	/**
	 * List header
	 */
	private View mHheader;

	private String	mSelection;

	private String[] mSelectionArgs = null;

	private TriToggleButton	sortButton;


	@Override
	public final void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		mHheader = (View) getLayoutInflater(savedInstanceState).inflate(R.layout.wifilistheader, null);
		this.getListView().addHeaderView(mHheader);
		registerForContextMenu(mHheader);

		initUi();

		// setup data
		initData();

		getActivity().getSupportLoaderManager().initLoader(0, null, this); 
	}

	/**
	 * Setup Ui controls
	 * @param savedInstanceState
	 */
	private void initUi() {

		sortButton = (TriToggleButton) mHheader.findViewById(R.id.triToggleButton1);
		sortButton.setPositiveImage(getResources().getDrawable(R.drawable.ascending));
		sortButton.setNeutralImage(getResources().getDrawable(R.drawable.neutral));
		sortButton.setNegativeImage(getResources().getDrawable(R.drawable.descending));
		sortButton.setOnClickListener(new View.OnClickListener() {	
			// Runs when the user touches the button
			@Override
			public void onClick(final View v) {
				int state = sortButton.getState();

				try	{	
					switch(state) {
						case 0: 
							mSortOrder = Schema.COL_SSID + " DESC";
							resetFilters();
							reload();
							break;
						case 1: 
							mSortOrder = DEFAULT_SORT_ORDER;
							resetFilters();
							reload();
							break;
						case 2:
							mSortOrder = Schema.COL_SSID + " ASC";				
							resetFilters();
							reload();
							break;
						default:
							break; // Should never occur
					}
				} catch (Exception e) {
					Log.e(TAG, "Error onClick");
				}
			}
		});	
	}

	private void initData() {
		DataHelper dataHelper = new DataHelper(getActivity());
		mSession = dataHelper.getActiveSessionId();
		
		String[] from = new String []{
				Schema.COL_ID,
				Schema.COL_BSSID,
				Schema.COL_SSID,
				"MAX(" + Schema.COL_LEVEL + ")",
				Schema.COL_IS_NEW_WIFI,
				Schema.COL_CAPABILITIES};

		int[] to = new int [] {
				R.id.wifilistfragment_id,
				R.id.wifilistfragment_bssid,
				R.id.wifilistfragment_ssid,
				R.id.wifilistfragment_level,
				R.id.wifilistfragment_statusicon,
				R.id.wifilistfragment_capabilities};

		mAdapter = new SimpleCursorAdapter(getActivity().getBaseContext(),
				R.layout.wifilistfragment, null, from, to, 0);
		mAdapter.setViewBinder(new WifiViewBinder());
		setListAdapter(mAdapter);
	}

	@Override
	public final void onDestroy() {
		setListAdapter(null);
		super.onDestroy();
	}

	@Override
	public final void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		int menuId = R.menu.wifilist_context;
		MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(menuId, menu);
	}

	@Override
	public final boolean onContextItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_sort_timestamp:
				mSortOrder = DEFAULT_SORT_ORDER;
				sortButton.setState(1);
				resetFilters();
				reload();
				return true;
			case R.id.menu_sort_ssid:
				mSortOrder = Schema.COL_SSID + " DESC";
				sortButton.setState(1);
				resetFilters();
				reload();
				return true;
			case R.id.menu_display_only_new:
				mSortOrder = Schema.COL_SSID + " DESC";
				sortButton.setState(1);
				setFilters(Schema.COL_IS_NEW_WIFI + " = ?", new String[]{"1"});
				reload();
				return true;
			case R.id.menu_display_free:
				mSortOrder = Schema.COL_SSID + " DESC";
				sortButton.setState(1);
				setFilters(Schema.COL_CAPABILITIES + " = ?", new String[]{"\"[ESS]\""});
				reload();
				return true;
			case R.id.menu_display_all:
				mSortOrder = Schema.COL_SSID + " DESC";
				sortButton.setState(1);
				resetFilters();
				reload();
				return true;
			default:
				break;
		}
		return super.onContextItemSelected(item);
	}

	/**
	 * User has clicked on wifi record.
	 * @param lv listview; this
	 * @param iv item clicked
	 * @param position position within list
	 * @param id  track ID
	 */
	@Override
	public final void onListItemClick(final ListView lv, final View iv, final int position, final long id) {
		if (position != 0) {

			// documentation says call getListView().getItemAtPosition(position) not lv directly
			// see http://developer.android.com/reference/android/app/ListFragment.html
			Cursor row = (Cursor) getListView().getItemAtPosition(position);
			String bssid = row.getString(row.getColumnIndex(Schema.COL_BSSID));

			Intent intent = new Intent();
			intent.setClass(getActivity(), WifiDetailsActivity.class);
			intent.putExtra(Schema.COL_BSSID, bssid);
			intent.putExtra(Schema.COL_SESSION_ID, mSession);
			startActivity(intent);
		}
	}

	@Override
	public final Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
		final String[] projection = {
				Schema.COL_ID,
				Schema.COL_BSSID,
				Schema.COL_SSID,
				"MAX(" + Schema.COL_LEVEL + ")",
				Schema.COL_IS_NEW_WIFI,
				Schema.COL_CAPABILITIES
		};

		mCursorLoader = new CursorLoader(
				getActivity().getBaseContext(), ContentUris.withAppendedId(Uri.withAppendedPath(
						RadioBeaconContentProvider.CONTENT_URI_WIFI, RadioBeaconContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), mSession),
						projection, mSelection, mSelectionArgs, mSortOrder);

		return mCursorLoader;
	}

	@Override
	public final void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
		mAdapter.swapCursor(cursor);
	}

	@Override
	public final void onLoaderReset(final Loader<Cursor> loader) {
		mAdapter.swapCursor(null);
	}

	private void reload() {
		getLoaderManager().restartLoader(0, null, WifiListFragment.this);
	}
	
	/**
	 * @param selection
	 * @param selectionArgs
	 */
	private void setFilters(final String selection, final String[] selectionArgs) {
		mSelection = selection;
		mSelectionArgs = selectionArgs;
	}
	
	private void resetFilters() {
		mSelection = null;
		mSelectionArgs = null;
	}
	/**
	 * Replaces column values with icons and mark free wifis.
	 */
	private class WifiViewBinder implements ViewBinder {

		private final int DEFAULT_TEXT_COLOR = new TextView(getActivity()).getTextColors().getDefaultColor();;

		public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
			ImageView isNew = ((ImageView) ((View) view.getParent()).findViewById(R.id.wifilistfragment_statusicon));
			TextView ssid = ((TextView) view.findViewById(R.id.wifilistfragment_ssid));

			if (columnIndex == cursor.getColumnIndex(Schema.COL_IS_NEW_WIFI)) {
				int result = cursor.getInt(columnIndex);
				// TODO use enumeration instead of result > 1
				if (result > 1) {
					// (+) icon for new wifis
					isNew.setImageResource(android.R.drawable.stat_notify_more);
					isNew.setVisibility(View.VISIBLE);
				} else {
					isNew.setVisibility(View.INVISIBLE);				
				}
				return true;
			}

			if (columnIndex == cursor.getColumnIndex(Schema.COL_SSID)) {
				String encryp = cursor.getString(cursor.getColumnIndex(Schema.COL_CAPABILITIES));
				// some devices report no encryption for free wifis, others (e.g. Nexus 4) 
				// report [ESS]
				if (encryp.length() < 1 || encryp.equals("[ESS]")) {
					ssid.setTextColor(Color.GREEN);
				} else {
					ssid.setTextColor(DEFAULT_TEXT_COLOR);
				}
				return false;
			}
			return false;
		}
	}
}
