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

package org.openbmap.db.models;

import org.openbmap.RadioBeacon;

import java.util.ArrayList;

/**
 * Model for general log file properties (cell manufacturer, model, etc.)
 */
public class LogFile implements Comparable<LogFile> {

	@SuppressWarnings("unused")
	private static final String TAG = LogFile.class.getSimpleName();

	private String mManufacturer;
	private String mModel;
	private String mRevision;
	private String mSwId;
	private String mSwVersion;
	private int mSessionId;

	private ArrayList<AbstractLogEntry<WifiRecord>> wifis;
	private ArrayList<AbstractLogEntry<CellRecord>> cells;

	public LogFile(final String manufacturer, final String model, final String revision, final String swId, final String swVer) {
		this(manufacturer, model, revision, swId, swVer, RadioBeacon.SESSION_NOT_TRACKING);
	}

	public LogFile(final String manufacturer, final String model, final String revision, final String swId, final String swVer, final int session) {
		setManufacturer(manufacturer);
		setModel(model);
		setRevision(revision);
		setSwId(swId);
		setSwVersion(swVer);
		setSessionId(session);
		wifis = new ArrayList<>();
		cells = new ArrayList<>();
	}

	/**
	 * used for contains method
	 */
	public final boolean equals(final LogFile aLog) {

		return ((getManufacturer().equals(aLog.getManufacturer()))
				&& (getModel().equals(aLog.getModel()))
				&& (getRevision().equals(aLog.getRevision()))
				&& (getSwid().equals(aLog.getSwid()))
				&& (getSwVersion().equals(aLog.getSwVersion())));
	}

	/*
	 * Adds wifi to log file.
	 * @param wifi wifi record to add
	 */
	public final void addElement(final WifiRecord wifi) {
		wifis.add(wifi);
	}

	/*
	 * Adds cell to log file.
	 * @param cell cell record to add
	 */
	public final void addElement(final CellRecord cell) {
		cells.add(cell);
	}

	public final int getSessionId() {
		return mSessionId;
	}

	public final void setSessionId(final int sessionId) {
		this.mSessionId = sessionId;
	}

	@Override
	public final  String toString() {
		return "Manufacturer " + getManufacturer() + " / Model " + getModel() + " / Revision " + getRevision() + " / Swid" +  getSwid() + " / Version " + getSwVersion();
	}

	@Override
	public final int compareTo(final LogFile aCell) {
		if (this.equals(aCell)) {
			return 0;
		}
		return -1;
	}

	public final  String getManufacturer() {
		return mManufacturer;
	}

	public final void setManufacturer(final  String manufacturer) {
		this.mManufacturer = manufacturer;
	}

	public final  String getModel() {
		return mModel;
	}

	public final void setModel(final  String model) {
		this.mModel = model;
	}

	public final  String getRevision() {
		return mRevision;
	}

	public final void setRevision(final  String revision) {
		this.mRevision = revision;
	}

	public final  String getSwid() {
		return mSwId;
	}

	public final void setSwId(final String swid) {
		this.mSwId = swid;
	}

	public final  String getSwVersion() {
		return mSwVersion;
	}

	public final void setSwVersion(final String mSwver) {
		this.mSwVersion = mSwver;
	}

}
