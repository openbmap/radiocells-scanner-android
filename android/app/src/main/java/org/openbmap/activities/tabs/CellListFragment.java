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

package org.openbmap.activities.tabs;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.openbmap.R;
import org.openbmap.activities.details.CellDetailsActivity;
import org.openbmap.db.ContentProvider;
import org.openbmap.db.DataHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.models.CellRecord;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Cell list fragment
 * lists current sessions wifis
 * logic according to https://github.com/googlesamples/android-RecyclerView/blob/master/Application/src/main/java/com/example/android/recyclerview/RecyclerViewFragment.java
 */
public class CellListFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = CellListFragment.class.getSimpleName();

    private static final String KEY_LAYOUT_MANAGER = "layoutManager";
    private static final int SPAN_COUNT = 2;

    /**
     * Cursor loader id
     * must be different from CELL_LOADER_ID !
     */
    private static final int CELL_LOADER_ID	= 2;

    /**
     * By default, sort by wifi ssid
     */
    private static final String	DEFAULT_SORT_COLUMN	= Schema.COL_TIMESTAMP ;

    /**
     * Default sort order
     */
    private static final String DEFAULT_SORT_ORDER = " ASC";

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

    @BindView(R.id.wifi_list) RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;

    private Unbinder unbinder;
    private int mSession;
    private CellAdapter adapter;

    private ColorStateList defaultColor;

    private enum LayoutManagerType {
        GRID_LAYOUT_MANAGER,
        LINEAR_LAYOUT_MANAGER
    }

    protected LayoutManagerType mCurrentLayoutManagerType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        final DataHelper dataHelper = new DataHelper(getActivity());
        mSession = dataHelper.getActiveSessionId();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        TextView dummy = new TextView(getActivity());
        defaultColor = dummy.getTextColors();
        dummy = null;

        View rootView = inflater.inflate(R.layout.cells, container, false);
        unbinder = ButterKnife.bind(this,rootView);
        rootView.setTag(TAG);

        mRecyclerView.addOnItemTouchListener(
                new RecyclerItemClickListener(getActivity(), new RecyclerItemClickListener.OnItemClickListener() {
                    @Override public void onItemClick(View view, int position) {
                        boolean found = adapter.dataCursor.moveToPosition(position);
                        if (found) {
                            long id = adapter.dataCursor.getLong(adapter.dataCursor.getColumnIndex(Schema.COL_ID));

                            final Intent intent = new Intent();
                            intent.setClass(getActivity(), CellDetailsActivity.class);
                            intent.putExtra(Schema.COL_ID, (int) id);
                            startActivity(intent);
                        }
                    }
                })
        );

        mLayoutManager = new LinearLayoutManager(getActivity());
        mCurrentLayoutManagerType = LayoutManagerType.LINEAR_LAYOUT_MANAGER;

        if (savedInstanceState != null) {
            // Restore saved layout manager type.
            mCurrentLayoutManagerType = (LayoutManagerType) savedInstanceState
                    .getSerializable(KEY_LAYOUT_MANAGER);
        }
        setRecyclerViewLayoutManager(mCurrentLayoutManagerType);

        adapter = new CellAdapter(getActivity(), null);
        mRecyclerView.setAdapter(adapter);
        getLoaderManager().initLoader(CELL_LOADER_ID, null, this);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        unbinder.unbind();
        super.onDestroyView();
    }

    /*
    public void onCreateOptionsMenu (final Menu menu, final MenuInflater inflater){
        inflater.inflate(R.menu.celllist_context, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_sort_timestamp:
                mSortColumn = DEFAULT_SORT_COLUMN;
                mSortOrder = " DESC";
                setFilters(null, null);
                reload();
                return true;
            case R.id.menu_sort_ssid:
                mSortColumn = Schema.COL_SSID;
                mSortOrder = " ASC";
                setFilters(null, null);
                reload();
                return true;
            case R.id.menu_display_only_new:
                mSortColumn = Schema.COL_SSID;
                mSortOrder = " ASC";
                //setFilters(Schema.COL_IS_NEW_WIFI + " = ?", new String[]{"1"});
                setFilters(Schema.COL_KNOWN_WIFI + " = ?", new String[]{"0"});
                reload();
                return true;
            case R.id.menu_display_free:
                mSortColumn = Schema.COL_SSID;
                mSortOrder = " ASC";
                setFilters(Schema.COL_ENCRYPTION + " = ?", new String[]{"\"[ESS]\""});
                reload();
                return true;
            case R.id.menu_display_all:
                mSortColumn = Schema.COL_SSID;
                mSortOrder = " ASC";
                setFilters(null, null);
                reload();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    */

    /**
     * Sets loader filters. Won't become effective until next reload
     * @param selection search pattern, null returns all results
     * @param selectionArgs array of search values, ignored when selection is null
     */
    private void setFilters(final String selection, final String[] selectionArgs) {
        if (selection != null) {
            mSelection = selection;
            mSelectionArgs = selectionArgs;
        } else {
            Log.i(TAG, "Resetting filters - filter pattern was null");
            mSelection = null;
            mSelectionArgs = null;
        }
    }

    /**
     * Forces database reload
     */
    private void reload() {
        getLoaderManager().restartLoader(CELL_LOADER_ID, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Log.d(TAG, "Loading wifis: " + mSelection + ":" + mSelectionArgs + " ~ " + String.format("%s %s", mSortColumn, mSortOrder));
        return new CursorLoader(getActivity(), ContentUris.withAppendedId(Uri.withAppendedPath(
                ContentProvider.CONTENT_URI_CELL, ContentProvider.CONTENT_URI_OVERVIEW_SUFFIX), mSession),
                null,
                mSelection,
                mSelectionArgs,
                String.format("%s %s", mSortColumn, mSortOrder));
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        adapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        adapter.swapCursor(null);
    }

    /**
     * Set RecyclerView's LayoutManager to the one given.
     *
     * @param layoutManagerType Type of layout manager to switch to.
     */
    public void setRecyclerViewLayoutManager(LayoutManagerType layoutManagerType) {
        int scrollPosition = 0;

        // If a layout manager has already been set, get current scroll position.
        if (mRecyclerView.getLayoutManager() != null) {
            scrollPosition = ((LinearLayoutManager) mRecyclerView.getLayoutManager())
                    .findFirstCompletelyVisibleItemPosition();
        }

        switch (layoutManagerType) {
            case GRID_LAYOUT_MANAGER:
                mLayoutManager = new GridLayoutManager(getActivity(), SPAN_COUNT);
                mCurrentLayoutManagerType = LayoutManagerType.GRID_LAYOUT_MANAGER;
                break;
            case LINEAR_LAYOUT_MANAGER:
                mLayoutManager = new LinearLayoutManager(getActivity());
                mCurrentLayoutManagerType = LayoutManagerType.LINEAR_LAYOUT_MANAGER;
                break;
            default:
                mLayoutManager = new LinearLayoutManager(getActivity());
                mCurrentLayoutManagerType = LayoutManagerType.LINEAR_LAYOUT_MANAGER;
        }

        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.scrollToPosition(scrollPosition);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save currently selected layout manager.
        savedInstanceState.putSerializable(KEY_LAYOUT_MANAGER, mCurrentLayoutManagerType);
        super.onSaveInstanceState(savedInstanceState);
    }

    // inner class to hold a reference to each item of RecyclerView
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView cellId;
        private final TextView operator;
        private final TextView technology;
        private final TextView lac;

        public ViewHolder(View itemLayoutView) {
            super(itemLayoutView);

            cellId = (TextView) itemLayoutView.findViewById(R.id.cell_row_cell_id);
            operator = (TextView) itemLayoutView.findViewById(R.id.cell_row_operator);
            technology = (TextView) itemLayoutView.findViewById(R.id.cell_row_technology);
            lac = (TextView) itemLayoutView.findViewById(R.id.cell_row_lac);
        }
    }

    public class CellAdapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor dataCursor;
        private Context context;

        public CellAdapter(Context mContext, Cursor cursor) {
            this.dataCursor = cursor;
            this.context = mContext;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View cardview = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.cell_row, parent, false);
            return new ViewHolder(cardview);
        }

        public Cursor swapCursor(Cursor cursor) {
            if (dataCursor == cursor) {
                return null;
            }
            Cursor oldCursor = dataCursor;
            this.dataCursor = cursor;
            if (cursor != null) {
                this.notifyDataSetChanged();
            }
            return oldCursor;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            dataCursor.moveToPosition(position);

            holder.cellId.setText(dataCursor.getString(dataCursor.getColumnIndex(Schema.COL_ACTUAL_CELLID)));
            holder.operator.setText(dataCursor.getString(dataCursor.getColumnIndex(Schema.COL_OPERATORNAME)));
            holder.technology.setText(
                    CellRecord.TECHNOLOGY_MAP().get(dataCursor.getInt(dataCursor.getColumnIndex(Schema.COL_NETWORKTYPE))));
            holder.lac.setText(dataCursor.getString(dataCursor.getColumnIndex(Schema.COL_AREA)));
        }

        @Override
        public int getItemCount() {
            return (dataCursor == null) ? 0 : dataCursor.getCount();
        }
    }
}

