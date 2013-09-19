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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.openbmap.R;
import org.openbmap.db.DataHelper;
import org.openbmap.db.RadioBeaconContentProvider;
import org.openbmap.db.Schema;
import org.openbmap.db.model.Session;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
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
import android.widget.Toast;

/**
 * Fragment for displaying all sessions
 */
public class SessionListFragment extends ListFragment implements LoaderCallbacks<Cursor> {
	private static final String	TAG	= SessionListFragment.class.getSimpleName();

	private SimpleCursorAdapter	adapter;

	private boolean mAdapterUpdatePending = true;

	/**
	 * Listener to handle onClick events.
	 */
	private SessionFragementListener mListener;

	/**
	 * Id currently selected with mContext menu.
	 */
	private int mSelectedId = -1;

	private ContentObserver	mObserver;

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

		String[] from = new String[] {
				Schema.COL_ID,
				Schema.COL_CREATED_AT,
				Schema.COL_IS_ACTIVE,
				Schema.COL_HAS_BEEN_EXPORTED,
				Schema.COL_NUMBER_OF_CELLS,
				Schema.COL_NUMBER_OF_WIFIS
		};

		int[] to = new int[] {
				R.id.sessionlistfragment_id,
				R.id.sessionlistfragment_created_at,
				R.id.sessionlistfragment_statusicon,
				R.id.sessionlistfragment_uploadicon,
				R.id.sessionlistfragment_no_cells,
				R.id.sessionlistfragment_no_wifis};

		adapter = new SimpleCursorAdapter(getActivity().getBaseContext(),
				R.layout.sessionlistfragment, null, from, to, 0);
		adapter.setViewBinder(new SessionViewBinder());

		// Trying to add a Header View.
		View header = (View) getLayoutInflater(savedInstanceState).inflate(
				R.layout.sessionlistheader, null);
		this.getListView().addHeaderView(header);

		// setup data adapters
		setListAdapter(adapter);
		getActivity().getSupportLoaderManager().initLoader(0, null, this);

		// register for change notifications
		mObserver = new ContentObserver(new Handler()) {
			@Override
			public void onChange(final boolean selfChange) {
				refreshAdapter();
			};
		};

		// register context menu
		registerForContextMenu(getListView());
	}

	@Override
	public final void onResume() {
		super.onResume();
		getActivity().getContentResolver().registerContentObserver(RadioBeaconContentProvider.CONTENT_URI_SESSION, true, mObserver);
	}

	@Override
	public final void onPause() {
		getActivity().getContentResolver().unregisterContentObserver(mObserver);
		
		super.onPause();
	}

	@Override
	public final void onDestroy() {
		setListAdapter(null);

		super.onDestroy();
	}

	@Override
	public final void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		AdapterView.AdapterContextMenuInfo amenuInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;

		int menuId;
		if (amenuInfo.position == 0) {
			// reduced menu for title bar: Delete all and Create new session
			mSelectedId = -1;
			menuId = R.menu.session_context_min;
		} else {
			// otherwise load full mContext menu
			mSelectedId = (int) amenuInfo.id;
			menuId = R.menu.session_context;
		}

		MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(menuId, menu);
	}

	@Override
	public final boolean onContextItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_upload_session:
				stop(mSelectedId);
				mListener.exportCommand(mSelectedId);
				return true;
			case R.id.menu_delete_session:
				new AlertDialog.Builder(getActivity())
				.setTitle(R.string.session_uploaded)
				.setMessage(R.string.do_you_want_to_delete_this_session)
				.setCancelable(true)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						stop(mSelectedId);
						mListener.deleteCommand(mSelectedId);
						dialog.dismiss();
					}
				})
				.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						dialog.cancel();
					}
				}).create().show();
				
				return true;
			case R.id.menu_delete_all_sessions:
				stop(mSelectedId);
				mListener.deleteAllCommand();
				return true;
			case R.id.menu_resume_session:
				resume(mSelectedId);
				return true;
			case R.id.menu_stop_session:
				stop(mSelectedId);
				return true;
			default:
				break;
		}
		return super.onContextItemSelected(item);
	}

	/**
	 * Stops session (and services)
	 */
	private void stop(final int id) {
		mListener.stopCommand(mSelectedId);
	}

	/**
	 * Resumes session, if session hasn't been uploaded yet.
	 */
	private void resume(final int id) {
		DataHelper datahelper = new DataHelper(this.getActivity());
		Session check = datahelper.loadSession(id);
		if (check != null && !check.hasBeenExported()) {
			mListener.resumeCommand(id);
		} else {
			Toast.makeText(this.getActivity(), R.string.warning_session_closed, Toast.LENGTH_SHORT).show();
		}
	}

	/*
	 * OnListClick resumes corresponding session.
	 */
	@Override
	public final void onListItemClick(final ListView lv, final View iv, final int position, final long id) {
		// ignore clicks on list header (position 0)
		if (position != 0) {
			resume((int) id);
		}
	}

	public final void setOnSessionSelectedListener(final SessionFragementListener listener) {
		this.mListener = listener;
	}

	@Override
	public final Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
		String[] projection = {
				Schema.COL_ID,
				Schema.COL_CREATED_AT,
				Schema.COL_IS_ACTIVE, 
				Schema.COL_HAS_BEEN_EXPORTED, 
				Schema.COL_NUMBER_OF_CELLS,
				Schema.COL_NUMBER_OF_WIFIS
		};
		CursorLoader cursorLoader = new CursorLoader(getActivity().getBaseContext(),
				RadioBeaconContentProvider.CONTENT_URI_SESSION, projection, null, null, Schema.COL_CREATED_AT + " DESC");
		return cursorLoader;
	}

	@Override
	public final void onLoadFinished(final Loader<Cursor> arg0, final Cursor cursor) {
		adapter.swapCursor(cursor);
		mAdapterUpdatePending = false;
	}

	@Override
	public final void onLoaderReset(final Loader<Cursor> arg0) {
		Log.d(TAG, "onLoadReset called");
		adapter.swapCursor(null);
	}

	/**
	 * Forces an adapter refresh.
	 */
	public final void refreshAdapter() {
		if (!mAdapterUpdatePending) {
			mAdapterUpdatePending = true;
			getActivity().getSupportLoaderManager().restartLoader(0, null, this);
		} else {
			Log.d(TAG, "refreshAdapter skipped. Another update is in progress");
		}
	}

	/**
	 * Replaces column values with icons and formats date to human-readable format.
	 */
	private static class SessionViewBinder implements ViewBinder {
		public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
			ImageView imgStatus = (ImageView) view.findViewById(R.id.sessionlistfragment_statusicon);
			ImageView imgUpload = (ImageView) view.findViewById(R.id.sessionlistfragment_uploadicon);
			TextView tvCreatedAt = (TextView) view.findViewById(R.id.sessionlistfragment_created_at);
			if (columnIndex == cursor.getColumnIndex(Schema.COL_IS_ACTIVE)) {
				//Log.d(TAG, "Modifying col " + cursor.getColumnIndex(Schema.COL_IS_ACTIVE));
				// symbol for active track
				int result = cursor.getInt(columnIndex);
				if (result > 0) {
					// Yellow clock icon for Active
					imgStatus.setImageResource(android.R.drawable.presence_away);
					imgStatus.setVisibility(View.VISIBLE);
				} else {
					imgStatus.setVisibility(View.INVISIBLE);				
				}
				return true;
			} else if (columnIndex == cursor.getColumnIndex(Schema.COL_HAS_BEEN_EXPORTED)) {
				// symbol for uploaded tracks
				int result = cursor.getInt(columnIndex);
				if (result > 0) {
					// Lock icon for uploaded sessions
					imgUpload.setImageResource(android.R.drawable.ic_lock_lock);
					imgUpload.setVisibility(View.VISIBLE);
				} else {
					imgUpload.setVisibility(View.INVISIBLE);				
				}
				return true;
			} else if (columnIndex == cursor.getColumnIndex(Schema.COL_CREATED_AT)) {
				Date result = new Date(cursor.getLong(columnIndex));
				SimpleDateFormat date = new SimpleDateFormat("yyyyMMdd", Locale.US);
				tvCreatedAt.setText(date.format(result));
				return true;
			}
			return false;
		}
	}

	/**
	 * Interface for activity.
	 */
	public interface SessionFragementListener {
		void deleteCommand(int id);
		/**
		 * Creates a new session.
		 */
		void startCommand();
		/**
		 * Stops session
		 */
		void stopCommand(int id);
		/**
		 * Resumes session.
		 * @param id
		 *		Session to resume
		 */
		void resumeCommand(int id);
		/**
		 * Deletes all sessions.
		 * @param id
		 *		Session to resume
		 */
		void deleteAllCommand();
		/**
		 * Exports and uploads session.
		 * @param id
		 *		Session to resume
		 */
		void exportCommand(int id);

		void reloadFragment();
	}
}
