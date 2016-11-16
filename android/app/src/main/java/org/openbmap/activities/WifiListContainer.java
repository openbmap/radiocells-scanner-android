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

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.TextView;

import org.openbmap.R;
import org.openbmap.db.ContentProvider;
import org.openbmap.db.DataHelper;
import org.openbmap.db.Schema;
import org.openbmap.utils.TriToggleButton;

/**
 * Parent activity for hosting wifi list
 */
public class WifiListContainer extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {
	
	private static final String TAG = WifiListContainer.class.getSimpleName();

	/**
	 * WifisCommunity cursor loader id
	 * must be different from CELL_LOADER_ID !
	 */
	private static final int WIFI_LOADER_ID	= 1;

	/**
	 * By default, sort by wifi ssid
	 */
	private static final String	DEFAULT_SORT_COLUMN	= Schema.COL_TIMESTAMP ;
	
	/**
	 * Default sort order
	 */
	private static final String DEFAULT_SORT_ORDER = " ASC";

	/**
	 * Be careful:
	 * All external linking (e.g. to map) must rely on BSSID.
	 * (_id is a pseudo-id as original id can't be used for GROUP BY clauses)
	 */

	private CursorLoader mCursorLoader;

	/**
	 * Adapter for retrieving wifis.
	 */
	private SimpleCursorAdapter mAdapter;

	/**
	 * WHERE clause for loader
	 */
	private String mSelection = null;

	/**
	 * WHERE clause arguments for loader
	 */
	private String[] mSelectionArgs = null;
	
	/** 
	 * Sort order for loader
	 */
	private String mSortColumn = DEFAULT_SORT_COLUMN;

	/** 
	 * Sort order for loader
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

	private TriToggleButton	sortButton;
	
	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		mHheader = inflater.inflate(R.layout.wifilistheader, null);
		return inflater.inflate(R.layout.wifilist, container, false);
	}
	
	@Override
	public final void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);

		this.getListView().addHeaderView(mHheader);
		registerForContextMenu(mHheader);

		initUi();

		// setup data
		initData();

		getActivity().getLoaderManager().initLoader(WIFI_LOADER_ID, null, this);
	}
	
	/**
	 * Setup Ui controls
	 */
	private void initUi() {
		sortButton = (TriToggleButton) mHheader.findViewById(R.id.triToggleButton1);
		sortButton.setPositiveImage(getResources().getDrawable(R.drawable.ic_action_up));
		sortButton.setNeutralImage(getResources().getDrawable(R.drawable.ic_action_unsorted));
		sortButton.setNegativeImage(getResources().getDrawable(R.drawable.ic_action_down));
		sortButton.setOnClickListener(new View.OnClickListener() {	
			
			// Sort button: handler for user clicks
			@Override
			public void onClick(final View v) {
				final int state = sortButton.getState();

				try	{	
					switch(state) {
						case 0: 
							mSortOrder = " DESC";
							reload();
							break;
						case 1: 
							mSortOrder = DEFAULT_SORT_ORDER;
							reload();
							break;
						case 2:
							mSortOrder = " ASC";				
							reload();
							break;
						default:
							break; // Should never occur
					}
				} catch (final Exception e) {
					Log.e(TAG, "Error onClick");
				}
			}
		});	
	}

	private void initData() {
		final DataHelper dataHelper = new DataHelper(getActivity());
		mSession = dataHelper.getActiveSessionId();
		
		final String[] from = new String []{
				Schema.COL_ID,
				Schema.COL_BSSID,
				Schema.COL_SSID,
				"MAX(" + Schema.COL_LEVEL + ")",
				/*Schema.COL_IS_NEW_WIFI,*/
				Schema.COL_KNOWN_WIFI,
				Schema.COL_CAPABILITIES};

		final int[] to = new int [] {
				R.id.wifilistfragment_id,
				R.id.wifilistfragment_bssid,
				R.id.wifilistfragment_ssid,
				R.id.wifilistfragment_level,
				R.id.wifilistfragment_statusicon,
				R.id.wifilistfragment_capabilities};

		mAdapter = new SimpleCursorAdapter(getActivity().getBaseContext(),
				R.layout.wifilistitems, null, from, to, 0);
		mAdapter.setViewBinder(new WifiViewBinder());
		setListAdapter(mAdapter);
	}

	@Override
	public final void onDestroy() {
		setListAdapter(null);
		super.onDestroy();
	}

	public void onCreateOptionsMenu (final Menu menu, final MenuInflater inflater){
		inflater.inflate(R.menu.wifilist_context, menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_sort_timestamp:
				mSortColumn = DEFAULT_SORT_COLUMN;
				mSortOrder = " DESC";
				sortButton.setState(1);
				resetFilters();
				reload();
				return true;
			case R.id.menu_sort_ssid:
				mSortColumn = Schema.COL_SSID;
				mSortOrder = " ASC";
				sortButton.setState(1);
				resetFilters();
				reload();
				return true;
			case R.id.menu_display_only_new:
				mSortColumn = Schema.COL_SSID;
				mSortOrder = " ASC";
				sortButton.setState(1);
				//setFilters(Schema.COL_IS_NEW_WIFI + " = ?", new String[]{"1"});
				setFilters(Schema.COL_KNOWN_WIFI + " = ?", new String[]{"0"});
				reload();
				return true;
			case R.id.menu_display_free:
				mSortColumn = Schema.COL_SSID;
				mSortOrder = " ASC";
				sortButton.setState(1);
				setFilters(Schema.COL_CAPABILITIES + " = ?", new String[]{"\"[ESS]\""});
				reload();
				return true;
			case R.id.menu_display_all:
				mSortColumn = Schema.COL_SSID;
				mSortOrder = " ASC";
				sortButton.setState(1);
				resetFilters();
				reload();
				return true;
			default:
				break;
		}
		return super.onOptionsItemSelected(item);
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
			final Cursor row = (Cursor) getListView().getItemAtPosition(position);
			final String bssid = row.getString(row.getColumnIndex(Schema.COL_BSSID));

			final Intent intent = new Intent();
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
				//Schema.COL_IS_NEW_WIFI,
				Schema.COL_KNOWN_WIFI,
				Schema.COL_CAPABILITIES
		};

		mCursorLoader = new CursorLoader(
				getActivity().getBaseContext(), ContentUris.withAppendedId(Uri.withAppendedPath(
						ContentProvider.CONTENT_URI_WIFI, ContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), mSession),
						projection, mSelection, mSelectionArgs, mSortColumn + mSortOrder);

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

	/**
	 * Forces reload
	 */
	private void reload() {
		getLoaderManager().restartLoader(WIFI_LOADER_ID, null, WifiListContainer.this);
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
	
	/**
	 * Replaces column values with icons and mark free wifis.
	 */
	private class WifiViewBinder implements ViewBinder {

		private final int DEFAULT_TEXT_COLOR = new TextView(getActivity()).getTextColors().getDefaultColor();

		public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
			final ImageView isNew = ((ImageView) ((View) view.getParent()).findViewById(R.id.wifilistfragment_statusicon));
			final TextView ssid = ((TextView) view.findViewById(R.id.wifilistfragment_ssid));

			if (columnIndex == cursor.getColumnIndex(Schema.COL_KNOWN_WIFI)) {
				final int result = cursor.getInt(columnIndex);
				if (result == 0) {
					// (+) icon for new wifis
					isNew.setImageResource(android.R.drawable.stat_notify_more);
					isNew.setVisibility(View.VISIBLE);
				} else {
					isNew.setVisibility(View.INVISIBLE);				
				}
				return true;
			}

			if (columnIndex == cursor.getColumnIndex(Schema.COL_SSID)) {
				final String encryp = cursor.getString(cursor.getColumnIndex(Schema.COL_CAPABILITIES));
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
