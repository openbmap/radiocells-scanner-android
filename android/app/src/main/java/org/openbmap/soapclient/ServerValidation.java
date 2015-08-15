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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.AsyncTask;
import android.util.Log;

import org.openbmap.Preferences;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Checks whether this client version is outdated.
 * Allowed client version is retrieved from openbmap server
 */

public final class ServerValidation extends AsyncTask<String, Object, Object[]> {

	private static final String	TAG	= ServerValidation.class.getSimpleName();

	public interface ServerReply {
		void onServerGood();
		void onServerBad(String text);
		void onServerCheckFailed();
	}
	
	/**
	 * 
	 */
	private static final int	CONNECTION_TIMEOUT	= 10000;

	/**
	 * Especially after wifi sleep / wifi repair, it takes a couple of seconds before
	 * device goes into connecting state
	 * How many seconds should we wait for state connecting?
	 */
	private static final int	WAIT_FOR_CONNECTING	= 10;

	/**
	 * After device is in connecting state, it takes a couple of seconds to final connect
	 * How many seconds should we wait for state connected?
	 */
	private static final int	WAIT_FOR_CONNECTED	= 5;
	
	public enum ServerAnswer {
		OK,
		OUTDATED,
		NO_REPLY,
		UNKNOWN_ERROR
	};

	private String serverVersion = "";

	private final Context	mContext;
	
	private final ServerReply	mListener;

	public ServerValidation(final Context context, final ServerReply listener) {
		mListener = listener;
		mContext = context;
	}

	/* (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	@Override
	protected Object[] doInBackground(final String... params) {
		try {
			final Object[]  result = new Object[2];
			result[0] = ServerAnswer.UNKNOWN_ERROR;
			result[1] = "Uninitialized";

			//check whether we have a connection to openbmap.org
			if (!isOnline()) {
				// if not, check whether connecting, if so wait
				Log.i(TAG, "Offline! Device migth just been switched on, so wait a bit");
				waitForConnect();
				if (!isOnline()) {
					Log.i(TAG, "Waiting didn't help. Still no connection");
					result[0] = ServerAnswer.NO_REPLY;
					result[1] = "No online connection!";
					return result;
				}
			}

			final SAXParserFactory factory = SAXParserFactory.newInstance();
			final DefaultHandler handler = new DefaultHandler() {

				private boolean versionElement = false;

				public void startElement(final String uri, final String localName, final String qName, 
						final Attributes attributes) throws SAXException {

					if (qName.equalsIgnoreCase("ALLOWED")) {
						versionElement = true;
					}
				}

				public void endElement(final String uri, final String localName, final String qName) throws SAXException {
					// Log.d(TAG, "End Element :" + qName);
				}

				public void characters(final char[] ch, final int start, final int length) throws SAXException {
					if (versionElement) {
						serverVersion = new String(ch, start, length);
						versionElement = false;
					}
				}
			};

			Log.i(TAG, "Verifying client version at" + Preferences.VERSION_CHECK_URL);
			// version file is opened as stream, thus preventing immediate timeout issues
			final URL url = new URL(Preferences.VERSION_CHECK_URL);
			final InputStream stream = url.openStream();

			final SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(stream, handler);
			stream.close();

			if (serverVersion.equals(params[0])) {
				Log.i(TAG, "Client version is up-to-date: " + params[0]);
				result[0] = ServerAnswer.OK;
				result[1] = "Everything fine! You're using the most up-to-date version!";
				return result;
			} else {
				Log.i(TAG, "Client version is outdated: server " + serverVersion + " client " + params[0]);
				result[0] = ServerAnswer.OUTDATED;
				result[1] = "New version available:" + serverVersion;
				return result;
			}
		} catch (final IOException e) {
			Log.e(TAG, "Error occured on version check. Are you online?");
			final Object[] noreply = new Object[]{ ServerAnswer.NO_REPLY, "Couldn't contact server"};
			return noreply;
		} catch (final Exception e) {
			Log.e(TAG, "Error occured on version check: " + e.getMessage());
			e.printStackTrace();
			final Object[] generic = new Object[]{ ServerAnswer.UNKNOWN_ERROR, "Error: " + e.getMessage()};
			return generic;
		}
	}

	@Override
	protected void onPostExecute(final Object[] result) {

		if (result.length == 2) {
			//ServerAnswer version = s.isAllowedVersion(RadioBeacon.SW_VERSION);
			final ServerAnswer version = (ServerAnswer) result[0];
			final String text = (String) result[1];

			if (version == ServerAnswer.OK) {
				if (mListener != null) {
					mListener.onServerGood();
				}

			} else if (version == ServerAnswer.OUTDATED) {
				// cancel, if client version to old
				if (mListener != null) {
					mListener.onServerBad(text);
				}
			} else if (version == ServerAnswer.NO_REPLY || version == ServerAnswer.UNKNOWN_ERROR) {
				Log.e(TAG, "Couldn't verify server version. Are you offline?");
				// cancel, if client version couldn't be verified
				if (mListener != null) {
					mListener.onServerCheckFailed();
				}
			}
		}
	}

	/**
	 * 
	 */
	private void waitForConnect() {
		final ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = null;

		// wait for state change
		for (int i = 0; i < WAIT_FOR_CONNECTING; i++) {
			try {
				networkInfo = cm.getActiveNetworkInfo();
				if ((networkInfo != null) && (networkInfo.getState() == State.CONNECTING || networkInfo.getState() == State.CONNECTED)) {
					break;
				}
				Log.i(TAG, "Network neighter connected nor connecting. Wait 1 sec..");
				Thread.sleep(1000);
			} catch (final InterruptedException e) {
				// ignore
			}
		}
		networkInfo = cm.getActiveNetworkInfo();
		if (networkInfo != null) {
			Log.i(TAG, "Current " + networkInfo.toString());
		}
		
		// once we're in CONNECTING state, wait another couple of seconds
		if (networkInfo != null && networkInfo.getState().equals(State.CONNECTING)) {
			// if in connecting state, wait 1 second for connection
			// this process is repeated multiple times according to retries
			Log.i(TAG, "Hoorray: after all connecting.. Wait for connection ready");
			for (int i = 0; i < WAIT_FOR_CONNECTED; i++) {
				if (isOnline()) {
					return;
				}
				Log.i(TAG, "Connection not yet ready. Waiting 1 sec..");
				try {
					Thread.sleep(1000);         
				} catch (final InterruptedException e) {
					// ignore
				}
			}
		}
		
		networkInfo = cm.getActiveNetworkInfo();
		if (networkInfo != null) {
			Log.i(TAG, "Waited enough: now " + networkInfo.toString());
		}
	}

	/**
	 * Checks connection to openbmap.org
	 * @return true on successful http connection
	 */
	private boolean isOnline() {
		// try to connect openbmap.org
		try {
			final URL url = new URL(Preferences.VERSION_CHECK_URL);
			final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestProperty("Connection", "close");
			connection.setConnectTimeout(CONNECTION_TIMEOUT);
			connection.connect();
			if (connection.getResponseCode() == 200) {
				Log.i(TAG, "Device is online");
				return true;
			} else {
				Log.w(TAG, "Http ping failed (return code != 200). Trying to setup connection");
			}
		} catch (final IOException e) {
			Log.w(TAG, "Http ping failed (no response). Trying to setup connection ..");
		}
		return false;
	}

}
