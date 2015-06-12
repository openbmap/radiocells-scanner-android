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
import android.os.Bundle;
import android.support.v4.app.Fragment;

import org.openbmap.soapclient.ExportGpxTask;
import org.openbmap.soapclient.ExportGpxTask.ExportGpxTaskListener;

public class ExportGpxTaskFragment extends Fragment implements ExportGpxTaskListener {

	private String mTitle;
	private String mMessage;
	private int mProgress;
	
	private ExportGpxTask mTask;
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
	 * @param session
	 */

	public void execute(final int session, final String path, final String filename) {

		if (path == null || filename == null) {
			throw new IllegalArgumentException("Path and file must not be null");
		}
		
		mIsExecuting = true;
		// Create and execute the background task.
		mTask = new ExportGpxTask(this.getActivity(), this, session, path, filename);
		mTask.execute();
	}

	/**
	 * Saves progress dialog state for later restore (e.g. on device rotation)
	 * @param title
	 * @param message
	 * @param progress
	 */
	public void retainProgress(final String title, final String message, final int progress) {
		mTitle = title;
		mMessage = message;
		mProgress = progress;
	}

	/**
	 * Restores previously retained progress dialog state
	 * @param progressDialog
	 */
	public void restoreProgress(final ProgressDialog progressDialog) {
		progressDialog.setTitle(mTitle);
		progressDialog.setMessage(mMessage);
		progressDialog.setProgress(mProgress);
	}

	@Override
	public void onExportGpxProgressUpdate(final Object... values) {
		((ExportGpxTaskListener) getActivity()).onExportGpxProgressUpdate(values);
	}

	@Override
	public void onExportGpxCompleted(final int id) {
		((ExportGpxTaskListener) getActivity()).onExportGpxCompleted(id);
	}

	@Override
	public void onExportGpxFailed(final int id, final String error) {
		((ExportGpxTaskListener) getActivity()).onExportGpxFailed(id, error);
	}

	public boolean isExecuting() {
		return mIsExecuting;
	}

}