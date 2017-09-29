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
import android.util.Log;

import java.io.File;
import java.io.IOException;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Uploads xml files as multipart message to webservice.
 */
public class AsyncUploader extends AsyncTask<String, Integer, Boolean> {
    private static final String TAG = AsyncUploader.class.getSimpleName();

    private UploadResult result;

    public enum UploadResult {
		UNDEFINED, OK, ERROR, WRONG_PASSWORD
	}
	/**
	 * Socket and connection parameters for http upload
	 */
	//private static final int SOCKET_TIMEOUT = 30000;
	//private static final int CONNECTION_TIMEOUT = 30000;

    private static final MediaType MEDIA_TYPE_XML = MediaType.parse("text/xml");

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
    private final FileUploadListener listener;

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
    private final String user;
    private final String password;
    private final String token;

    private boolean passwordValidated = false;

    /**
     * Upload endpoint address
     */
    private final String serverUrl;

	private String mFile;

    /**
     * Tells the length of upload (in bytes)
     */
    private long size;

    /**
     * Achieved upload speed (in KB)
     */
    private long speed;

	/**
	 * 
	 * @param listener UploadTaskListener which is informed about upload result
	 * @param user	   user name
	 * @param password password
     * @param server   remote endpoint URL
     */
	public AsyncUploader(final FileUploadListener listener, final String user, final String password, final String server) {
        this.listener = listener;
        this.user = user;
        this.password = password;
        this.token = null;
        this.serverUrl = server;
    }

    /**
     *
     * @param listener UploadTaskListener which is informed about upload result
     * @param token    server generated one-time token
     * @param serverUrl   remote endpoint URL
     */
    public AsyncUploader(final FileUploadListener listener, final String token, final String serverUrl) {
        this.listener = listener;
        this.user = null;
        this.password = null;
        this.token = token;
        this.serverUrl = serverUrl;
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

        result = upload(mFile);
        if (result == UploadResult.WRONG_PASSWORD) {
            return false;
        }

        speed = calcSpeed(System.currentTimeMillis(), beforeTime, size);

        return (result == UploadResult.OK);
    }

	@Override
	protected final void onPostExecute(final Boolean success) {
        if (listener == null) {
            Log.e(TAG, "Listener is null!");
        }

        if (result == UploadResult.OK) {
            listener.onUploadCompleted(mFile, size, speed);
        } else {
            Log.e(TAG, "Upload failed " + lastErrorMsg);
            listener.onUploadFailed(mFile, lastErrorMsg);
        }
	}

    /**
     * Sends an authenticated http post request to upload file
     * @param fileName Full path to upload file
     * @return true on response code 200, false otherwise
     */
    private UploadResult httpPostRequest(final String fileName) {
        // TODO check network state
        // @see http://developer.android.com/training/basics/network-ops/connecting.html

        OkHttpClient client = new OkHttpClient.Builder()
                .authenticator((route, response) -> {
                    if (response.request().header("Authorization") != null) {
                        return null; // Give up, we've already attempted to authenticate.
                    }

                    if (user == null || password == null) {
                        Log.i(TAG, "No user name or password set - trying anonymous");
                        return null; // Anonymous mode - no need for authentication
                    }
                    String credential = Credentials.basic(user, password);
                    return response.request().newBuilder()
                            .header("Authorization", credential)
                            .build();
                })
                .build();

        File file = new File(fileName);
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                //.addFormDataPart("title", "Square Logo")
                .addFormDataPart(FILE_FIELD, file.getName(),
                        RequestBody.create(MEDIA_TYPE_XML, file));

        if (token != null) {
            Log.i(TAG, "Setting token");
            builder.addFormDataPart(API_FIELD, token);
        }

        RequestBody requestBody = builder.build();
        Request request = new Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) {
                Log.i(TAG, "Server reply 401: bad password");
                return UploadResult.WRONG_PASSWORD;
            }

            // TODO: redirects (301, 302) are NOT handled here
            // thus if something changes on the server side we're dead here
            if (response.isSuccessful()) {
                Log.i(TAG, "Uploaded " + fileName + ": Server reply " + response.code());
                size = response.body().contentLength();
                return UploadResult.OK;
            } else {
                Log.i(TAG, "Server error while uploading " + fileName + ": Server reply " + response.code());
                return UploadResult.ERROR;
            }
            //System.out.println(response.body().string());
        } catch (IOException e) {
            Log.e(TAG, "IOException while executing http request");
            return UploadResult.ERROR;
        }
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
