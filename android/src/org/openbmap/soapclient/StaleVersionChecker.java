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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.openbmap.Preferences;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.os.AsyncTask;
import android.util.Log;

/**
 * Checks whether this client version is outdated.
 * Allowed client version is retrieved from openbmap server
 */

public final class StaleVersionChecker extends AsyncTask<String, Object, Object[]> {

	public enum ServerAnswer {
		OK,
		OUTDATED,
		NO_REPLY,
		UNKNOWN_ERROR
	};

	private static final String	TAG	= StaleVersionChecker.class.getSimpleName();

	private String serverVersion = "";


	public StaleVersionChecker() {
	}

	/* (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	@Override
	protected Object[] doInBackground(final String... params) {
		try {
			Object[]  result = new Object[2];
			result[0] = ServerAnswer.UNKNOWN_ERROR;
			result[1] = "Uninitialized";

			SAXParserFactory factory = SAXParserFactory.newInstance();
			DefaultHandler handler = new DefaultHandler() {

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

			// version file is opened as stream, thus preventing immediate timeout issues
			URL url = new URL(Preferences.VERSION_CHECK_URL);
			InputStream stream = url.openStream();

			SAXParser saxParser = factory.newSAXParser();
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
		} catch (IOException e) {
			Log.e(TAG, "Error occured on version check. Are you online?");
			Object[] noreply = new Object[]{ ServerAnswer.NO_REPLY, "Couldn't contact server"};
			return noreply;
		} catch (Exception e) {
			Log.e(TAG, "Error occured on version check: " + e.getMessage());
			e.printStackTrace();
			Object[] generic = new Object[]{ ServerAnswer.UNKNOWN_ERROR, "Error: " + e.getMessage()};
			return generic;
		}
	}
}
