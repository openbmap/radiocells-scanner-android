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

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;

import org.openbmap.Preferences;
import org.openbmap.soapclient.SaveGpxTask;
import org.openbmap.soapclient.SaveGpxTask.SaveGpxTaskListener;

/**
 * The type Save gpx task fragment.
 */
public class SaveGpxTaskFragment extends Fragment implements SaveGpxTaskListener {

	private String mTitle;
	private String mMessage;
	private int mProgress;
	
	private SaveGpxTask mTask;
	private boolean mIsExecuting;

	/**
	 * This method will only be called once when the retained
	 * Fragment is first created.
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Retain this fragment across configuration changes.
		setRetainInstance(true);
	}

    /**
     * Does the actual work
     *
     * @param session  the session
     * @param path     the path
     * @param filename the filename
     */
    public void execute(final long session, final String path, final String filename) {

		if (path == null || filename == null) {
			throw new IllegalArgumentException("Path and file must not be null");
		}

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final int verbosity = Integer.parseInt(prefs.getString(Preferences.KEY_GPX_VERBOSITY, Preferences.DEFAULT_GPX_VERBOSITY));

		mIsExecuting = true;
		// Create and execute the background task.
		mTask = new SaveGpxTask(this.getActivity(), this, session, path, filename, verbosity);
		mTask.execute();
	}

    /**
     * Saves progress dialog state for later restore (e.g. on device rotation)
     *
     * @param title    the title
     * @param message  the message
     * @param progress the progress
     */
    public void retainProgress(final String title, final String message, final int progress) {
		mTitle = title;
		mMessage = message;
		mProgress = progress;
	}

    /**
     * Restores previously retained progress dialog state
     *
     * @param progressDialog the progress dialog
     */
    public void restoreProgress(final ProgressDialog progressDialog) {
		progressDialog.setTitle(mTitle);
		progressDialog.setMessage(mMessage);
		progressDialog.setProgress(mProgress);
	}

	@Override
	public void onSaveGpxProgressUpdate(final Object... values) {
		((SaveGpxTaskListener) getActivity()).onSaveGpxProgressUpdate(values);
	}

	@Override
	public void onSaveGpxCompleted(final String filename) {
		((SaveGpxTaskListener) getActivity()).onSaveGpxCompleted(filename);
	}

	@Override
	public void onSaveGpxFailed(final long id, final String error) {
		((SaveGpxTaskListener) getActivity()).onSaveGpxFailed(id, error);
	}

    /**
     * Is executing boolean.
     *
     * @return the boolean
     */
    public boolean isExecuting() {
		return mIsExecuting;
	}

}