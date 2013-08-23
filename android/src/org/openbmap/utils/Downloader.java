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

package org.openbmap.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Downloads a file from an url. If download was successful, i.e. downloaded size equals total size callback function
 * "onDownloadCompleted" is called on mListener, "onDownloadFailed" otherwise.
 * TODO: check if we can use http://developer.android.com/reference/android/app/DownloadManager.html
 */
public class Downloader extends AsyncTask<Object, Object, Integer> {

	private static final String	TAG	= Downloader.class.getSimpleName();

	private static final int BUFFER_SIZE	= 1024;

	private ProgressDialog	mDialog;

	/**
	 * Total size of file to download in bytes
	 */
	private int	mTotalSize;

	/**
	 * Listener to handle onClick events
	 */
	private DownloadListener mListener;

	private URL	mSource;

	private String	mTarget;
	
	/**
	 * Defines callback function once download has been completed
	 */
	public interface DownloadListener {
		void onDownloadCompleted(String filename);
		void onDownloadFailed(String filename);
	}

	public Downloader(final Context context) {
		mDialog = new ProgressDialog(context);	
	}

	@Override
	protected final void onPreExecute() {
		mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mDialog.setTitle("Downloading ..");
		mDialog.setCancelable(false);
		mDialog.setProgress(0);
		mDialog.show();
	}

	/**
	 * Updates progress bar.
	 * @param values values[0] contains downloaded bytes, values[1] contains total bytes
	 */
	@Override
	protected final void onProgressUpdate(final Object... values) {
		mDialog.setProgress((Integer) values[0]);
		mDialog.setMax((Integer) values[1]);
	}

	/**
	 * Downloads file from URL
	 * @param args
	 * 			args[0]: URL to download
	 * 			args[1]: Filename
	 */
	@Override
	protected final Integer doInBackground(final Object... args) {         
		
		mSource = (URL) args[0];
		mTarget = (String) args[1];
		
		Log.i(TAG, "Save " + mSource + " @ " + mTarget);
		int downloadedSize = 0;
		try {
			//create the new connection
			HttpURLConnection urlConnection = (HttpURLConnection) mSource.openConnection();
			urlConnection.setRequestMethod("GET");
			urlConnection.setDoOutput(true);
			urlConnection.connect();

			mTotalSize = urlConnection.getContentLength();

			File file = new File(mTarget);
			//this will be used to write the downloaded data into the file we created
			FileOutputStream fileOutput = new FileOutputStream(file, false);

			//this will be used in reading the data from the internet
			InputStream inputStream = urlConnection.getInputStream();
			//create a buffer...
			byte[] buffer = new byte[BUFFER_SIZE];
			int bufferLength = 0; //used to store a temporary size of the buffer

			//now, read through the input buffer and write the contents to the file
			while ((bufferLength = inputStream.read(buffer)) > 0) {
				fileOutput.write(buffer, 0, bufferLength);
				downloadedSize += bufferLength;
				publishProgress(downloadedSize, mTotalSize);

			}

			fileOutput.close();
			//catch some possible errors...
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return downloadedSize;
	}

	@Override
	protected final void onPostExecute(final Integer result) {
		if (this.mDialog.isShowing()) {
			this.mDialog.dismiss();
			if (result != mTotalSize) {
				Log.e(TAG, "Downloaded size doesn't match total file size");
				if (mListener != null) {
					mListener.onDownloadFailed(mTarget);
				}
			}
			mListener.onDownloadCompleted(mTarget);
		}
	}
	
	public final void setListener(final DownloadListener listener) {
		this.mListener = listener;
	}
}
