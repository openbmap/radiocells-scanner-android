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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.openbmap.R;

import java.io.File;

import pl.polidea.treeview.TreeStateManager;

public class JsonBrowserTreeViewAdapter extends RemoteFileTreeViewAdapter {
    /**
     * @param activity
     * @param treeStateManager
     * @param numberOfLevels
     */
    public JsonBrowserTreeViewAdapter(Activity activity,
                                      TreeStateManager<RemoteFile> treeStateManager,
                                      int numberOfLevels,
                                      String targetFolder) {
        super(activity, treeStateManager, numberOfLevels, targetFolder);
    }

    @Override
    public void handleItemClick(final View view, final Object id) {
        final RemoteFile remoteFile = (RemoteFile) id;
        if (remoteFile.isDirectory) {
            if (remoteFile.children != null) {
                // Show directory contents (warn if directory is empty)
                if (remoteFile.children.length > 0)
                    super.handleItemClick(view, id);
                else {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.folder_empty),
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                String urlStr = remoteFile.getUriString();
                // Retrieve directory contents from server
                BrowseJSONTask task = new BrowseJSONTask(this, remoteFile);
                listTasks.put(task, remoteFile);
                task.execute(urlStr);
                ProgressBar downloadDirProgress = (ProgressBar) view.findViewById(R.id.downloadDirProgress);
                downloadDirProgress.setVisibility(View.VISIBLE);
            }
        } else {
            // check if a download is already in progress
            if (!downloadsByUri.containsValue(remoteFile.getUri())) {
                // Download file
                final File file = new File(
                        targetFolder,
                        remoteFile.name);

                if (downloadsByFile.containsKey(file)) {
                    // prevent multiple downloads with same map file name
                    Toast.makeText(
                            getActivity(), R.string.already_downloading, Toast.LENGTH_LONG).show();
                    return;
                }

                // check if we have a current version
                // TODO recheck this condition (granularity of timestamps, botched timezones)
                if (file.exists() && (file.lastModified() >= remoteFile.timestamp)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle(R.string.overwrite_file_title);
                    builder.setMessage(R.string.overwrite_file_message);
                    builder.setPositiveButton(getActivity().getString(R.string.yes), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            file.delete();
                            startDownload(remoteFile, file, view);
                        }
                    });

                    builder.setNegativeButton(getActivity().getString(R.string.no), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // NOP
                        }
                    });

                    builder.show();
                } else
                    startDownload(remoteFile, file, view);
            }
        }
    }
}
