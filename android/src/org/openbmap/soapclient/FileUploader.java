/**
 * Uploads xml files as multipart message to webservice.
 * Parameters: openBmap_login and openBmap_passwd
 * 
 */
package org.openbmap.soapclient;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.os.AsyncTask;
import android.util.Log;


public class FileUploader extends AsyncTask<String, Integer, Boolean> {

	/**
	 * Socket and connection parameters for http upload
	 */
	private static final int SOCKET_TIMEOUT = 30000;
	private static final int CONNECTION_TIMEOUT = 30000;

	private static final String TAG = FileUploader.class.getSimpleName();

	/**
	 * Field for multipart message: username
	 */
	private static final String	LOGIN_FIELD	= "openBmap_login";

	/**
	 * Field for multipart message: password
	 */
	private static final String	PASSWORD_FIELD	= "openBmap_passwd";


	/**
	 * Used for callbacks.
	 */
	private UploadTaskListener mListener;

	public interface UploadTaskListener {
		void onUploadCompleted(ArrayList<String> uploaded);
		void onUploadFailed(final String error);
	}

	/**
	 * Message in case of an error
	 */
	private String errorMsg = null;

	private final String mUser;
	private final String mPassword;
	private String	mServer;
	private ArrayList<String> uploaded;

	public FileUploader(final UploadTaskListener listener, final String user, final String password, final String server) {
		mListener = listener;
		mUser = user;
		mPassword = password;
		mServer = server;
		uploaded = new ArrayList<String>();
	}

	/**
	 * Background task. Note: When uploading several files
	 * upload is continued even on errors (i.e. will try to upload other files)
	 * @return true on success, false if at least one file upload failed
	 */
	@Override
	protected final Boolean doInBackground(final String... params) {
		Log.v(TAG, "Start background upload task");

		boolean result = true;
		for (int i = 0; i < params.length; i++) {
			Log.i(TAG, "Uploading " + params[i]);

			Boolean uploadOk = uploadFile(params[i]);
			if (uploadOk) {
				uploaded.add(params[i]);
			} else {
				Log.e(TAG, "Error uploading " + params[i]);
			}

			result |= uploadOk;	
		}


		return result;
	}

	@Override
	protected final void onPostExecute(final Boolean success) {

		if (success) {
			if (mListener != null) {
				mListener.onUploadCompleted(uploaded);
			}
			return;
		} else {
			if (mListener != null) {
				Log.e(TAG, "Upload failed " + errorMsg);
				mListener.onUploadFailed(errorMsg);
			}
			return;
		}
	}

	/**
	 * 
	 * @param file File to upload (full path).
	 * @return true on success, false on error
	 */
	private boolean uploadFile(final String file) {
		
		// TODO check network state
		// @see http://developer.android.com/training/basics/network-ops/connecting.html
		
		// Adjust HttpClient parameters
		HttpParams httpParameters = new BasicHttpParams();
		// Set the timeout in milliseconds until a connection is established.
		// The default value is zero, that means the timeout is not used. 
		HttpConnectionParams.setConnectionTimeout(httpParameters, CONNECTION_TIMEOUT);
		// Set the default socket timeout (SO_TIMEOUT) 
		// in milliseconds which is the timeout for waiting for data.
		HttpConnectionParams.setSoTimeout(httpParameters, SOCKET_TIMEOUT);
		DefaultHttpClient httpclient = new DefaultHttpClient(httpParameters);

		HttpPost httppost = new HttpPost(mServer);
		try {
			MultipartEntity entity = new MultipartEntity();

			entity.addPart(LOGIN_FIELD, new StringBody(mUser)); 
			entity.addPart(PASSWORD_FIELD, new StringBody(mPassword));
			entity.addPart("file", new FileBody(new File(file), "text/xml"));

			httppost.setEntity(entity);
			HttpResponse response = httpclient.execute(httppost);

			int reply = response.getStatusLine().getStatusCode();
			Log.i(TAG, "Uploaded " + file + ": Server reply " + reply);

			// everything is ok if we receive HTTP 200
			// TODO: redirects (301, 302) are NOT handled here 
			// thus if something changes on the server side we're dead here
			return (reply == HttpStatus.SC_OK);
		} catch (ClientProtocolException e) {
			Log.e(TAG, e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
		return false;
	}
}
