package org.openbmap.activity;

import org.openbmap.R;
import org.openbmap.db.RadioBeaconContentProvider;
import org.openbmap.db.Schema;
import org.openbmap.db.model.WifiRecord;

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

public class WifiDetailsFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>  {
	private static final String TAG = WifiDetailsFragment.class.getSimpleName();

	private SimpleCursorAdapter adapter;

	private WifiRecord mWifiSelected;
	
	/**
	 * mListener to handle onClick events
	 */
	private OnWifiSelectedListener mListener;

	@Override
	public final void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);	
		
		mWifiSelected = ((WifiDetailsActivity) getActivity()).getWifi();
		String [] from = new String []{Schema.COL_TIMESTAMP, Schema.COL_LEVEL};
		int [] to = new int [] {
				R.id.celldetailsfragment_timestamp,
				R.id.celldetailsfragment_progress
		};
		adapter = new SimpleCursorAdapter(getActivity().getBaseContext(), R.layout.wifidetailsfragment, null, from, to, 0);
		adapter.setViewBinder(new ProgressBarViewBinder());
		setListAdapter(adapter);

		getActivity().getSupportLoaderManager().initLoader(0, null, this); 
	}

	private static class ProgressBarViewBinder implements ViewBinder {

		/**
		 * max value for progress bar indicator, assuming -50 dBm is highest possible level
		 */
		private static final int MAX_VALUE = 100;

		/**
		 * indicators for signal strenght
		 */
		public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
			if (view instanceof ProgressBar) {
				String result = cursor.getString(cursor.getColumnIndex(Schema.COL_LEVEL));
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

	public interface OnWifiSelectedListener {
		void onWifiSelected(long id);
	}

	public final void setOnWifiSelectedListener(final OnWifiSelectedListener listener) {
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
		mListener.onWifiSelected(id);
	}

	@Override
	public final Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
		String[] bssid = {"-1"};
		if (mWifiSelected != null) {
			bssid[0] = mWifiSelected.getBssid();
		}

		String[] projection = { Schema.COL_ID, Schema.COL_SSID, Schema.COL_LEVEL, Schema.COL_TIMESTAMP, Schema.COL_END_POSITION_ID};
		// query data from content provider
		CursorLoader cursorLoader =
				new CursorLoader(getActivity().getBaseContext(),
						RadioBeaconContentProvider.CONTENT_URI_WIFI, projection, Schema.COL_BSSID + " = ?", bssid, Schema.COL_LEVEL + " DESC");
		return cursorLoader;
	}

	@Override
	public final void onLoadFinished(final Loader<Cursor> arg0, final Cursor cursor) {
		adapter.swapCursor(cursor);
		Log.d(TAG, "onLoadFinished called. We have " + adapter.getCount() + " records");
	}

	@Override
	public final void onLoaderReset(final Loader<Cursor> arg0) {
		Log.d(TAG, "onLoadReset called");
		adapter.swapCursor(null);
	}
}
