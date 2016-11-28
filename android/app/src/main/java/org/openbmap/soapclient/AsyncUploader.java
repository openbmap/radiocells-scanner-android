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

package org.openbmap.soapclient;

import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import java.io.File;
import java.io.IOException;

/**
 * Uploads xml files as multipart message to webservice.
 */
public class AsyncUploader extends AsyncTask<String, Integer, Boolean> {

    private UploadResult mResult;

    public enum UploadResult {
		UNDEFINED, OK, ERROR, WRONG_PASSWORD
	}
	/**
	 * Socket and connection parameters for http upload
	 */
	//private static final int SOCKET_TIMEOUT = 30000;
	//private static final int CONNECTION_TIMEOUT = 30000;

	private static final String TAG = AsyncUploader.class.getSimpleName();

    /**
     * Multipart message field: one-time token (for anonymous upload)
     */
    private static final String API_FIELD = "api";

	/**
	 * Multipart message field: file
	 */
	private static final String FILE_FIELD = "file";

	
	/**
	 * Retry upload how many times on failed upload
	 * 0 means no retry
	 */
	private static final int MAX_RETRIES = 2;

	/**
	 * Used for callbacks.
	 */
	private final FileUploadListener mListener;

    public interface FileUploadListener {
		/**
		 * Callback function on successful upload
		 * @param file filename
		 * @param size files in byte
		 * @param speed speed in kb per second
		 */
		void onUploadCompleted(String file, long size, long speed);

        /**
         * Callback function on failed upload
         * @param file filename
         * @param error
         */
		void onUploadFailed(final String file, String error);
	}

	/**
	 * Message in case of an error
	 */
	private String lastErrorMsg = null;

    /**
     * Either user/password oder token must be provided
     */
	private final String mUser;
	private final String mPassword;
    private final String mToken;

    private boolean passwordValidated = false;

    /**
     * Upload target address
     */
	private final String mServer;

	private String mFile;

    /**
     * Tells the length of upload (in bytes)
     */
    private long mSize;

    /**
     * Achieved upload speed (in KB)
     */
    private long mSpeed;

	/**
	 * 
	 * @param listener UploadTaskListener which is informed about upload result
	 * @param user	   user name
	 * @param password password
	 * @param server   remote URL
	 */
	public AsyncUploader(final FileUploadListener listener, final String user, final String password, final String server) {
		mListener = listener;
		mUser = user;
		mPassword = password;
        mToken = null;
		mServer = server;
	}

    /**
     *
     * @param listener UploadTaskListener which is informed about upload result
     * @param token    server generated one-time token
     * @param server   remote URL
     */
    public AsyncUploader(final FileUploadListener listener, final String token, final String server) {
        mListener = listener;
        mUser = null;
        mPassword = null;
        mToken = token;
        mServer = server;
    }

	/**
	 * Background task. Note: When uploading several files
	 * upload is continued even on errors (i.e. will try to upload other files)
	 * @param params filenames 
	 * @return true on success, false if at least one file upload failed
	 */
	@Deprecated
	@Override
	protected final Boolean doInBackground(final String... params) {
		Log.i(TAG, "Uploading " + params[0]);
		mFile = params[0];
        long beforeTime = System.currentTimeMillis();

        mResult = upload(mFile);
        if (mResult == UploadResult.WRONG_PASSWORD) {
            return false;
        }

        mSpeed = calcSpeed(System.currentTimeMillis(), beforeTime, mSize);

		return (mResult == UploadResult.OK);
	}

	@Override
	protected final void onPostExecute(final Boolean success) {
        if (mListener == null) {
            Log.e(TAG, "Listener is null!");
        }

		if (mResult == UploadResult.OK) {
			mListener.onUploadCompleted(mFile, mSize, mSpeed);
		} else {
            Log.e(TAG, "Upload failed " + lastErrorMsg);
            mListener.onUploadFailed(mFile, lastErrorMsg);
		}
	}


    /**
     * Sends an authenticated http post request to upload file
     * @param file File to upload (full path)
     * @return true on response code 200, false otherwise
     */
    private UploadResult httpPostRequest(final String file) {
        // TODO check network state
        // @see http://developer.android.com/training/basics/network-ops/connecting.html

        // Adjust HttpClient parameters
        final HttpParams httpParameters = new BasicHttpParams();
        // Set the timeout in milliseconds until a connection is established.
        // The default value is zero, that means the timeout is not used.
        // HttpConnectionParams.setConnectionTimeout(httpParameters, CONNECTION_TIMEOUT);
        // Set the default socket timeout (SO_TIMEOUT)
        // in milliseconds which is the timeout for waiting for data.
        //HttpConnectionParams.setSoTimeout(httpParameters, SOCKET_TIMEOUT);
        final DefaultHttpClient httpclient = new DefaultHttpClient(httpParameters);
        final HttpPost httppost = new HttpPost(mServer);
        try {
            final MultipartEntity entity = new MultipartEntity();
            entity.addPart(FILE_FIELD, new FileBody(new File(file), "text/xml"));

            if ((mUser != null) && (mPassword != null)) {
                final String authorizationString = "Basic " + Base64.encodeToString((mUser + ":" + mPassword).getBytes(), Base64.NO_WRAP);
                httppost.setHeader("Authorization", authorizationString);
            }

            if (mToken != null) {
                entity.addPart(API_FIELD, new StringBody(mToken));
            }

            httppost.setEntity(entity);
            final HttpResponse response = httpclient.execute(httppost);

            final int reply = response.getStatusLine().getStatusCode();
            if (reply == 200) {
                // everything is ok if we receive HTTP 200
                mSize = entity.getContentLength();
                Log.i(TAG, "Uploaded " + file + ": Server reply " + reply);
                return UploadResult.OK;
            } else if (reply == 401) {
                Log.e(TAG, "Wrong username or password");
                return UploadResult.WRONG_PASSWORD;
            }
			else {
                Log.w(TAG, "Error while uploading" + file + ": Server reply " + reply);
                return UploadResult.ERROR;
            }
            // TODO: redirects (301, 302) are NOT handled here
            // thus if something changes on the server side we're dead here
        } catch (final ClientProtocolException e) {
            Log.e(TAG, e.getMessage());
        } catch (final IOException e) {
            Log.e(TAG, "I/O exception on file " + file);
        }
        return UploadResult.UNDEFINED;
    }

	/**
	 * Uploads file. If upload hasn't succeeded on first attempt, upload is tried again MAX_RETRIES times
	 * @param file File to upload (full path).
	 * @return true on success, false on error
	 */
	private UploadResult upload(final String file) {
        UploadResult result = httpPostRequest(file);

		// simple resume upload mechanism on failed upload
		int i = 0;
		while (result != UploadResult.OK && i < MAX_RETRIES) {
			Log.w(TAG, "Upload failed: Retry " + i + ": " + file);
            result = httpPostRequest(file);
			i++;
		}

		if (result != UploadResult.OK) {
			lastErrorMsg = "Upload failed after " + i + " retries";
			Log.e(TAG, "Upload failed after " + i + " retries");
		}

		return result;
	}

    /**
     * Returns upload speed in KB (crude calculation)
     * @param afterTime
     * @param beforeTime
     * @param bytes Upload size (in bytes)
     */
    private long calcSpeed(long afterTime, long beforeTime, long bytes) {
        return Math.round(bytes / (afterTime - beforeTime));
    }

}
