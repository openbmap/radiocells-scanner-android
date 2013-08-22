/**
 * Reminder
 * All external linking has to be done via BSSID; _id here is a pseudo-id as original id can't be used for GROUP BY clauses
 */
package org.openbmap.activity;

import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.DataHelper;
import org.openbmap.db.RadioBeaconContentProvider;
import org.openbmap.db.Schema;
import org.openbmap.db.model.Session;

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
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class WifiListFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>  {
	@SuppressWarnings("unused")
	private static final String TAG = WifiListFragment.class.getSimpleName();

	/*
	 * Adapter for retrieving wifis.
	 */
	private SimpleCursorAdapter adapter;

	@Override
	public final void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// setup controls
		View header = (View) getLayoutInflater(savedInstanceState).inflate(R.layout.wifilistheader, null);
		this.getListView().addHeaderView(header);

		// setup data
		String [] from = new String []{
				Schema.COL_ID,
				Schema.COL_BSSID,
				Schema.COL_SSID,
				"MAX(" + Schema.COL_LEVEL + ")",
				Schema.COL_IS_NEW_WIFI,
				Schema.COL_CAPABILITIES};

		int [] to = new int [] {
				R.id.wifilistfragment_id,
				R.id.wifilistfragment_bssid,
				R.id.wifilistfragment_ssid,
				R.id.wifilistfragment_level,
				R.id.wifilistfragment_statusicon,
				R.id.wifilistfragment_capabilities};

		adapter = new SimpleCursorAdapter(getActivity().getBaseContext(),
				R.layout.wifilistfragment, null, from, to, 0);
		adapter.setViewBinder(new WifiViewBinder());
		setListAdapter(adapter);

		getActivity().getSupportLoaderManager().initLoader(0, null, this); 

	}

	@Override
	public final void onDestroy() {
		setListAdapter(null);
		super.onDestroy();
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
			String uid = row.getString(row.getColumnIndex(Schema.COL_BSSID));

			Intent intent = new Intent();
			intent.setClass(getActivity(), WifiDetailsActivity.class);
			intent.putExtra(Schema.COL_BSSID, uid);
			startActivity(intent);
		}
	}

	@Override
	public final Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
		DataHelper dataHelper = new DataHelper(getActivity());

		// query data from content provider
		Session active = dataHelper.loadActiveSession();
		int id = RadioBeacon.SESSION_NOT_TRACKING;
		if (active != null) {
			id = active.getId();
		}

		String[] projection = {
				Schema.COL_ID,
				Schema.COL_BSSID,
				Schema.COL_SSID,
				"MAX(" + Schema.COL_LEVEL + ")",
				Schema.COL_IS_NEW_WIFI,
				Schema.COL_CAPABILITIES
		};

		CursorLoader cursorLoader =  new CursorLoader(
				getActivity().getBaseContext(), ContentUris.withAppendedId(Uri.withAppendedPath(
						RadioBeaconContentProvider.CONTENT_URI_WIFI, RadioBeaconContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), id),
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

	/**
	 * Replaces column values with icons.
	 */
	private static class WifiViewBinder implements ViewBinder {
		/**
		 * Sets icons in list view.
		 */
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
				if (encryp.length() < 1) {
					ssid.setTextColor(Color.GREEN);
				} else {
					ssid.setTextColor(Color.GRAY);
				}
				return false;
				/*
				// Capability "" means free wifi, thus check length
				if (cursor.getString(cursor.getColumnIndex(Schema.COL_CAPABILITIES)).length() < 1) {
					Log.d(TAG, "Changing " + cursor.getString(cursor.getColumnIndex(Schema.COL_SSID)));
					// show free wifis (capabilities string empty) in green color
					//final TextView ssid = (TextView) ((View) view.getParent()).findViewById(R.id.wifilistfragment_ssid);
					ssid.setTextColor(Color.GREEN);
					//((TextView) view).setTextColor(Color.GREEN);
					return true;
				} else {
					ssid.setTextColor(ssid.getTextColors().getDefaultColor());
					return true;
				}
				 */
			}
			return false;
		}
	}
}
