package org.openbmap.db.model;

import java.util.ArrayList;
import org.openbmap.RadioBeacon;


public class LogFile implements Comparable<LogFile> {

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
		wifis = new ArrayList<AbstractLogEntry<WifiRecord>>();
		cells = new ArrayList<AbstractLogEntry<CellRecord>>();
	}

	/**
	 * used for contains method
	 */
	public final boolean equals(final Object aLog) {
		LogFile oneCell = (LogFile) aLog;

		return ((getManufacturer().equals(oneCell.getManufacturer()))
				&& (getModel().equals(oneCell.getModel()))
				&& (getRevision() == oneCell.getRevision())
				&& (getSwid() == oneCell.getSwid())
				&& (getSwVersion() == oneCell.getSwVersion()));
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
