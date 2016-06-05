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

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.utils.CatalogDownload;
import org.openbmap.utils.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class DialogPreferenceCatalogs extends DialogPreference implements ICatalogsListAdapterListener {

    private static String TAG = DialogPreferenceCatalogs.class.getSimpleName();

    public static final String LIST_DOWNLOADS_URL = RadioBeacon.SERVER_BASE + "/downloads/catalog_downloads.json";

    private DialogPreferenceCatalogsListAdapter mAdapter;
    private final Context mContext;
    private SparseArray<DialogPreferenceCatalogsGroup> groups;
    private List<CatalogDownload> mOnlineResults;
    private DownloadManager mDownloadManager;

    private ProgressDialog checkDialog;

    /*
     * Id of the active Catalog download or -1 if no active download
     */
    private long mCurrentCatalogDownloadId = -1;

    private BroadcastReceiver mReceiver = null;

    public DialogPreferenceCatalogs(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        setDialogLayoutResource(R.layout.dialogpreference_catalogs);

        initDownloadManager();
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        groups = new SparseArray<>();
        ExpandableListView listView = (ExpandableListView) v.findViewById(R.id.list);
        mAdapter = new DialogPreferenceCatalogsListAdapter(getContext(), groups, this);
        listView.setAdapter(mAdapter);

        if (checkDialog == null || !checkDialog.isShowing()) {
            checkDialog = new ProgressDialog(getContext());
        }
        // retrieve online Catalogs
        GetAvailableCatalogsTask data = new GetAvailableCatalogsTask();
        data.execute();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (checkDialog != null && checkDialog.isShowing()) {
            checkDialog.dismiss();
        }
        checkDialog = null;
    }

    /**
     * Initialises download manager for GINGERBREAD and newer
     */
    @SuppressLint("NewApi")
    private void initDownloadManager() {
        mDownloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);

        mReceiver = new BroadcastReceiver() {
            @SuppressLint("NewApi")
            @Override
            public void onReceive(final Context context, final Intent intent) {
                final String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    final long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    final DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    final Cursor c = mDownloadManager.query(query);
                    if (c.moveToFirst()) {
                        final int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            // we're not checking download id here, that is done in handleDownloads
                            final String uriString = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                            handleDownloads(uriString);
                        } else {
                            final int reason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON));
                            Log.e(TAG, "Download failed: " + reason);
                        }
                    }
                }
            }
        };

        getContext().registerReceiver(mReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    /**
     * Selects downloaded file either as wifi catalog / active Catalog (based on file extension).
     *
     * @param file
     */
    public final void handleDownloads(String file) {
        // get current file extension
        final String[] filenameArray = file.split("\\.");
        final String extension = "." + filenameArray[filenameArray.length - 1];

        // TODO verify on newer Android versions (>4.2)
        // replace prefix file:// in filename string
        file = file.replace("file://", "");

        if (extension.equals(Preferences.CATALOG_FILE_EXTENSION)) {
            mCurrentCatalogDownloadId = -1;
            if (file.indexOf(getContext().getExternalCacheDir().getPath()) > -1) {
                // file has been downloaded to cache folder, so move..
                file = moveToFolder(file, FileUtils.getCatalogFolder(getContext()).getAbsolutePath());
            }

            //-initActiveCatalogControl();
            // handling Catalog files
            activateCatalog(file);
        }
    }

    /**
     * Changes Catalog preference item to given filename.
     * Helper method to activate map after successful download
     *
     * @param file absolute filename (including path)
     */
    private void activateCatalog(String file) {
        Log.d(TAG, "Activating " + new File(file).getName());
        SharedPreferences sharedPref = getContext().getSharedPreferences(Preferences.KEY_CATALOG_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(Preferences.KEY_CATALOG_FILE, new File(file).getName());
        editor.commit();
    }

    /**
     * Moves file to specified folder
     *
     * @param file
     * @param folder
     * @return new file name
     */
    private String moveToFolder(final String file, final String folder) {
        // file path contains external cache dir, so we have to move..
        final File source = new File(file);
        final File destination = new File(folder + File.separator + source.getName());
        Log.i(TAG, file + " stored in temp folder. Moving to " + destination.getAbsolutePath());

        try {
            FileUtils.moveFile(source, destination);
        } catch (final IOException e) {
            Log.e(TAG, "I/O error while moving file");
        }
        return destination.getAbsolutePath();
    }

    public void populateListView() {
        DialogPreferenceCatalogsGroup group = null;
        String name;
        int j = 0;
        for (int i = 0; i < mOnlineResults.size(); i++) {
            if (i == 0) {
                name = (mOnlineResults.get(i).getRegion() != null) ? mOnlineResults.get(i).getRegion() : "Unsorted";
                group = new DialogPreferenceCatalogsGroup(name);
                Log.d(TAG, "Added group " + name);
            } else if (!mOnlineResults.get(i).getRegion().equals(mOnlineResults.get(i - 1).getRegion())) {
                name = (mOnlineResults.get(i).getRegion() != null) ? mOnlineResults.get(i).getRegion() : "Unsorted";
                Log.d(TAG, "Added group " + name);
                groups.append(groups.size(), group);
                group = new DialogPreferenceCatalogsGroup(name);
            }
            group.children.add(mOnlineResults.get(i));
        }
        groups.append(j, group);

        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClicked(CatalogDownload catalog) {
        if (mCurrentCatalogDownloadId > -1) {
            Toast.makeText(getContext(), getContext().getString(R.string.other_download_active), Toast.LENGTH_LONG).show();
            return;
        }

        if (catalog.getUrl() == null) {
            Toast.makeText(getContext(), R.string.invalid_download, Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(getContext(), getContext().getString(R.string.downloading) + " " + catalog.getUrl(), Toast.LENGTH_LONG).show();
        // try to create directory
        final File folder = FileUtils.getCatalogFolder(getContext());
        Log.d(TAG, "Download destination" + folder.getAbsolutePath());

        boolean folderAccessible = false;
        if (folder.exists() && folder.canWrite()) {
            folderAccessible = true;
        }

        if (!folder.exists()) {
            folderAccessible = folder.mkdirs();
        }
        if (folderAccessible) {
            final String filename = catalog.getUrl().substring(catalog.getUrl().toString().lastIndexOf('/') + 1);

            final File target = new File(folder.getAbsolutePath() + File.separator + filename);
            if (target.exists()) {
                Log.i(TAG, "Catalog file " + filename + " already exists. Overwriting..");
                target.delete();
            }

            try {
                // try to download to target. If target isn't below Environment.getExternalStorageDirectory(),
                // e.g. on second SD card a security exception is thrown
                final DownloadManager.Request request = new DownloadManager.Request(Uri.parse(catalog.getUrl().toString()));
                request.setDestinationUri(Uri.fromFile(target));
                mCurrentCatalogDownloadId = mDownloadManager.enqueue(request);
            } catch (final SecurityException sec) {
                // download to temp dir and try to move to target later
                Log.w(TAG, "Security exception, can't write to " + target + ", using " + getContext().getExternalCacheDir());
                final File tempFile = new File(getContext().getExternalCacheDir() + File.separator + filename);

                final DownloadManager.Request request = new DownloadManager.Request(Uri.parse(catalog.getUrl().toString()));
                request.setDestinationUri(Uri.fromFile(tempFile));
                mCurrentCatalogDownloadId = mDownloadManager.enqueue(request);
                getDialog().dismiss();
            }
        } else {
            Toast.makeText(getContext(), R.string.error_save_file_failed, Toast.LENGTH_SHORT).show();
        }
        getDialog().dismiss();
    }

    private class GetAvailableCatalogsTask extends AsyncTask<String, Void, List<CatalogDownload>> {

        @Override
        protected void onPreExecute() {
            checkDialog.setTitle(mContext.getString(R.string.prefs_check_server));
            checkDialog.setMessage(mContext.getString(R.string.please_stay_patient));
            checkDialog.setCancelable(false);
            checkDialog.setIndeterminate(true);
            checkDialog.show();
        }

        @Override
        protected List<CatalogDownload> doInBackground(String... params) {
            List<CatalogDownload> result = new ArrayList<CatalogDownload>();

            DefaultHttpClient httpclient = new DefaultHttpClient(new BasicHttpParams());
            HttpGet httpGet = new HttpGet(LIST_DOWNLOADS_URL);
            httpGet.setHeader("Content-type", "application/json");

            InputStream inputStream = null;
            try {
                HttpResponse response = httpclient.execute(httpGet);
                HttpEntity entity = response.getEntity();

                inputStream = entity.getContent();
                // json is UTF-8 by default
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
                StringBuilder sb = new StringBuilder();

                String line = null;
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }

                JSONObject jObject = new JSONObject(sb.toString());
                JSONArray arr = jObject.getJSONArray("downloads");
                for (int i = 0; i < arr.length(); i++) {
                    result.add(jsonToDownload(arr.getJSONObject(i)));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing server reply:" + e.getMessage());
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    return result;
                } catch (Exception squish) {
                    return null;
                }
            }
        }

        @Override
        protected void onPostExecute(List<CatalogDownload> result) {
            super.onPostExecute(result);

            if (checkDialog.isShowing()) {
                checkDialog.dismiss();
            }

            mOnlineResults = result;
            populateListView();
        }

        /**
         * Converts server json in a CatalogDownload record
         *
         * @param obj server reply
         * @return parsed server reply
         * @throws JSONException
         */
        private CatalogDownload jsonToDownload(JSONObject obj) throws JSONException {
            String updated = obj.getString("last_updated");
            String title = obj.getString("title");
            String region = obj.getString("region");
            String url = obj.getString("url");
            String id = obj.getString("id");
            return new CatalogDownload(title, region, url, id, updated);
        }
    }
}
