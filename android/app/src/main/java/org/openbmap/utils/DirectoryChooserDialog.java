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

    Credits for this piece of code:
 	Gregory Shpitalnik
 	http://www.codeproject.com/Articles/547636/Android-Ready-to-use-simple-directory-chooser-dial?msg=4923192#xx4923192xx
 */

package org.openbmap.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnKeyListener;
import android.text.Editable;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.openbmap.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DirectoryChooserDialog {

    private static final String TAG = DirectoryChooserDialog.class.getSimpleName();

    private boolean mIsNewFolderEnabled = true;
    private String mSdcardDirectory = "";
    private final Context mContext;
    private TextView mTitleView;
    private TextView mSubtitleView;

    private String mDir = "";
    private List<String> mSubdirs = null;
    private ChosenDirectoryListener mChosenDirectoryListener = null;
    private ArrayAdapter<String> mListAdapter = null;

    /*
     * Callback interface for selected directory
     */
    public interface ChosenDirectoryListener {
        void onChosenDir(String chosenDir);
    }

    public DirectoryChooserDialog(final Context context, final ChosenDirectoryListener chosenDirectoryListener) {
        mContext = context;
        // default to apps SD card folder
        mSdcardDirectory = new File(context.getExternalFilesDir(null).getAbsolutePath()).getAbsolutePath();
        mChosenDirectoryListener = chosenDirectoryListener;
    }

    /*
     * setNewFolderEnabled() - enables or disables new folder button
     * @param isNewFolderEnabled
     */
    public void setNewFolderEnabled(final boolean isNewFolderEnabled) {
        mIsNewFolderEnabled = isNewFolderEnabled;
    }

    public boolean getNewFolderEnabled() {
        return mIsNewFolderEnabled;
    }

    /**
     * chooseDirectory() - load directory chooser dialog for initial default sdcard directory
     **/
    public void chooseDirectory() {
        // Initial directory is sdcard directory
        chooseDirectory(mSdcardDirectory);
    }

    /*
     * chooseDirectory(String dir) - load directory chooser dialog for initial input 'dir' directory
     */
    public void chooseDirectory(String dir) {
        final File dirFile = new File(dir);
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            dir = mSdcardDirectory;
        }

        dir = new File(dir).getAbsolutePath();

        mDir = dir;
        mSubdirs = getDirectories(dir);

        class DirectoryOnClickListener implements DialogInterface.OnClickListener {
            public void onClick(final DialogInterface dialog, final int item) {
            	// handle folder up clicks
            	if (((AlertDialog) dialog).getListView().getAdapter().getItem(item).equals("..")) {
            		// handle '..' (directory up) clicks
            		mDir = mDir.substring(0, mDir.lastIndexOf("/"));
            	}
            	else {
            		// otherwise descend into sub-directory
            		mDir += "/" + ((AlertDialog) dialog).getListView().getAdapter().getItem(item);
            	}
                updateDirectory();
            }
        }

        final AlertDialog.Builder dialogBuilder =
                createDirectoryChooserDialog(mContext.getString(R.string.select_folder), "Current folder:" + "\n" + dir, mSubdirs, new DirectoryOnClickListener());

        dialogBuilder.setPositiveButton("OK", new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                // Current directory chosen
                if (mChosenDirectoryListener != null) {
                    // Call registered listener supplied with the chosen directory
                    mChosenDirectoryListener.onChosenDir(mDir);
                }
            }
        }).setNegativeButton("Cancel", null);

        final AlertDialog dirsDialog = dialogBuilder.create();

        dirsDialog.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(final DialogInterface dialog, final int keyCode, final KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                    // Back button pressed
                    if (mDir.equals(mSdcardDirectory)) {
                        // The very top level directory, do nothing
                        return false;
                    } else {
                        // Navigate back to an upper directory
                        mDir = new File(mDir).getParent();
                        updateDirectory();
                    }

                    return true;
                } else {
                    return false;
                }
            }
        });

        // Show directory chooser dialog
        dirsDialog.show();
    }

    private boolean createSubDir(final String newDir) {
        final File newDirFile = new File(newDir);
        if (!newDirFile.exists()) {
            return newDirFile.mkdir();
        }

        return false;
    }

    private List<String> getDirectories(final String dir) {
        final List<String> dirs = new ArrayList<>();
        dirs.add("..");
        try {
            final File dirFile = new File(dir);
            if (!dirFile.exists() || !dirFile.isDirectory()) {
                return dirs;
            }

            for (final File file : dirFile.listFiles()) {
                if (file.isDirectory()) {
                    dirs.add(file.getName());
                }
            }
        } catch (final Exception e) {
            Log.e(TAG, e.toString(), e);
        }

        Collections.sort(dirs, new Comparator<String>() {
            public int compare(final String o1, final String o2) {
                return o1.compareTo(o2);
            }
        });

        return dirs;
    }

    private AlertDialog.Builder createDirectoryChooserDialog(final String title, final String subtitle, final List<String> listItems,
                                                             final DialogInterface.OnClickListener onClickListener) {
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);

        // Create custom view for AlertDialog title containing
        // current directory TextView and possible 'New folder' button.
        // Current directory TextView allows long directory path to be wrapped to multiple lines.
        final LinearLayout titleLayout = new LinearLayout(mContext);
        titleLayout.setOrientation(LinearLayout.VERTICAL);

        mTitleView = new TextView(mContext);
        mTitleView.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
        mTitleView.setTextAppearance(mContext, android.R.style.TextAppearance_Large);
        mTitleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        mTitleView.setText(title);

        mSubtitleView = new TextView(mContext);
        mSubtitleView.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
        mSubtitleView.setTextAppearance(mContext, android.R.style.TextAppearance_Large);
        mSubtitleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        mSubtitleView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
        mSubtitleView.setText(subtitle);

        final Button newDirButton = new Button(mContext);
        newDirButton.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
        newDirButton.setText("Create new folder");
        newDirButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                final EditText input = new EditText(mContext);

                // Show new folder name input dialog
                new AlertDialog.Builder(mContext).
                        setTitle("New folder name").
                        setView(input).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int whichButton) {
                        final Editable newDir = input.getText();
                        final String newDirName = newDir.toString();
                        // Create new directory
                        if (createSubDir(mDir + "/" + newDirName)) {
                            // Navigate into the new directory
                            mDir += "/" + newDirName;
                            updateDirectory();
                        } else {
                            Toast.makeText(mContext, "Failed to create '" + newDirName +
                                            "' folder", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
            }
        });

        if (!mIsNewFolderEnabled) {
            newDirButton.setVisibility(View.GONE);
        }

        titleLayout.addView(mTitleView);
        titleLayout.addView(mSubtitleView);
        titleLayout.addView(newDirButton);

        dialogBuilder.setCustomTitle(titleLayout);

        mListAdapter = createListAdapter(listItems);

        dialogBuilder.setSingleChoiceItems(mListAdapter, -1, onClickListener);
        dialogBuilder.setCancelable(false);

        return dialogBuilder;
    }

    // Reloads folder structure
    private void updateDirectory() {
        mSubdirs.clear();
        mSubdirs.addAll(getDirectories(mDir));
        mSubtitleView.setText(mDir);

        mListAdapter.notifyDataSetChanged();
    }

    private ArrayAdapter<String> createListAdapter(final List<String> items) {
        return new ArrayAdapter<String>(mContext,
                android.R.layout.select_dialog_item, android.R.id.text1, items) {
            @Override
            public View getView(final int position, final View convertView,
                                final ViewGroup parent) {
                final View v = super.getView(position, convertView, parent);

                if (v instanceof TextView) {
                    // Enable list item (directory) text wrapping
                    final TextView tv = (TextView) v;
                    tv.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
                    tv.setEllipsize(null);
                }
                return v;
            }
        };
    }
}
