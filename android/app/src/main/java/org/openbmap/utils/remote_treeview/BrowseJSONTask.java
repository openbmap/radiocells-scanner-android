/*
 * Copyright © 2013–2016 Michael von Glasow.
 *
 * This file is part of LSRN Tools.
 *
 * LSRN Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSRN Tools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSRN Tools.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openbmap.utils.remote_treeview;


import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A task which retrieves the contents of a remote directory in the background and notifies a listener
 * upon completion.
 */
public class BrowseJSONTask extends BrowseAbstractTask {
    private static final String TAG = BrowseJSONTask.class.getSimpleName();
    private static final RemoteFileComparator comparator = new RemoteFileComparator();

    private RemoteDirListListener listener = null;
    private RemoteFile parent = null;

    /**
     * Creates a new {@code BrowseHTTPTask} task, and registers it with a listener.
     *
     * @param listener The {@link RemoteDirListListener} which will be notified when the task has completed.
     * @param parent   The directory to be listed. When this task finishes, it populates the {@code children}
     *                 member of {@code parent} with the objects it retrieved. May be {@code null}.
     */
    public BrowseJSONTask(RemoteDirListListener listener, RemoteFile parent) {
        super();
        this.listener = listener;
        this.parent = parent;
    }


    @Override
    protected RemoteFile[] doInBackground(String... params) {
        OkHttpClient client = new OkHttpClient();

        Request.Builder builder = new Request.Builder();
        builder.url(params[0]);
        Request request = builder.build();
        try {
            Log.i(TAG, "Retrieving catalog list from" + params[0]);
            Response response = client.newCall(request).execute();

            try (ResponseBody responseBody = response.body()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                ArrayList<RemoteFile> list = new ArrayList<>();
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
                df.setTimeZone(TimeZone.getDefault());

                JSONObject json = new JSONObject(responseBody.string());
                json = (JSONObject) json.get("downloads");
                for (Iterator<String> regions = json.keys(); regions.hasNext(); ) {
                    String region = regions.next();
                    RemoteFile rf = new RemoteFile("https://cdn.radiocells.org/catalogs/",
                            true, region, -1, 0);
                    JSONArray childArray = json.getJSONArray(region);

                    ArrayList<RemoteFile> children = new ArrayList<>();
                    for (int i = 0; i < childArray.length(); i++) {
                        JSONObject c = childArray.getJSONObject(i);
                        children.add(new RemoteFile("https://cdn.radiocells.org/catalogs/",
                                false,
                                c.getString("url").substring(
                                        c.getString("url").lastIndexOf('/') + 1,
                                        c.getString("url").length()),
                                -1, 0));
                    }
                    rf.children = (children.toArray(new RemoteFile[children.size()]));
                    list.add(rf);
                }
                Collections.sort(list, comparator);
                return list.toArray(new RemoteFile[]{});
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
    }


    protected void onPostExecute(RemoteFile[] result) {
        if (parent != null) {
            parent.children = result;
        }
        if (listener != null) {
            listener.onRemoteDirListReady(this, result);
        }
    }
}

/**
 * public class BrowseJSONTask extends BrowseAbstractTask {
 * private static final String TAG = BrowseJSONTask.class.getSimpleName();
 * <p>
 * private RemoteDirListListener listener = null;
 * private RemoteFile parent = null;
 * <p>
 * private static final RemoteFileComparator comparator = new RemoteFileComparator();
 * <p>
 * public BrowseJSONTask(RemoteDirListListener listener, RemoteFile parent) {
 * super();
 * this.listener = listener;
 * this.parent = parent;
 * }
 *
 * @Override protected RemoteFile[] doInBackground(String... params) {
 * ArrayList<RemoteFile> list = new ArrayList<>();
 * SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
 * df.setTimeZone(TimeZone.getDefault());
 * <p>
 * RemoteFile rf = new RemoteFile("https://cdn.radiocells.org/catalogs/",
 * false, "catalog-de.sqlite", 123, 0); //getFileInfo(url, href);
 * list.add(rf);
 * Collections.sort(list, comparator);
 * <p>
 * return list.toArray(new RemoteFile[]{});
 * }
 * <p>
 * protected void onPostExecute(RemoteFile[] result) {
 * if (parent != null)
 * parent.children = result;
 * if (listener != null)
 * listener.onRemoteDirListReady(this, result);
 * }
 * }
 **/