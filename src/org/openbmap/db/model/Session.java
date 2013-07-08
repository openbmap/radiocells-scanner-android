/**
 * 
 */
package org.openbmap.db.model;

import org.openbmap.RadioBeacon;

import android.net.Uri;

/**
 * @author power
 *
 */
public class Session {
	private int mId;
	private long mCreatedAt;
	private long mLastUpdated;
	private String mDescription;

	/**
	 * Has session been uploaded to website?
	 */
	private boolean mHasBeenExported;
	private boolean mIsActive;

	/**
	 * Number of wifi belonging to session
	 */
	private int	mNumberOfWifis;
	/**
	 * Number of cells belonging to session
	 */
	private int	mNumberOfCells;


	/**
	 * @param id
	 * @param createdAt
	 * @param lastUpdated
	 * @param description
	 * @param hasBeenExported
	 * @param isActive
	 */
	public Session(final int id, final long createdAt, final long lastUpdated, final String description, final int hasBeenExported,
			final int isActive) {
		this(id, createdAt, lastUpdated, description, hasBeenExported, isActive, 0, 0);
	}

	/**
	 * @param id
	 */
	public Session(final int id) {
		setId(id);
	}

	/**
	 * 
	 */
	public Session() {
		this(RadioBeacon.SESSION_NOT_TRACKING);
	}

	/**
	 * @param int1
	 * @param long1
	 * @param long2
	 * @param string
	 * @param int2
	 * @param int3
	 * @param int4
	 * @param int5
	 */
	public Session(final int id, final long createdAt, final long lastUpdated, final String description, final int hasBeenExported,
			final int isActive, final int numberOfCells, final int numberOfWifis) {
		setId(id);
		setCreatedAt(createdAt);
		setLastUpdated(lastUpdated);
		setDescription(description);
		hasBeenExported(hasBeenExported != 0);
		isActive(isActive != 0);
		setNumberOfCells(numberOfCells);
		setNumberOfWifis(numberOfWifis);
	}

	public final int getId() {
		return mId;
	}
	public final void setId(final int id) {
		this.mId = id;
	}

	/**
	 * Extracts Id from URI under which session has been saved
	 * Uri's last path segment must contain id
	 * @param persistanceUri
	 */
	public final void setId(final Uri persistanceUri) {
		this.mId = Integer.valueOf(persistanceUri.getLastPathSegment());
	}

	public final long getCreatedAt() {
		return mCreatedAt;
	}
	public final long getLastUpdated() {
		return mLastUpdated;
	}
	public final void setLastUpdated(final long lastUpdated) {
		this.mLastUpdated = lastUpdated;
	}
	public final void setCreatedAt(final long createdAt) {
		this.mCreatedAt = createdAt;
	}
	public final String getDescription() {
		return mDescription;
	}
	public final void setDescription(final String description) {
		this.mDescription = description;
	}
	public final boolean hasBeenExported() {
		return mHasBeenExported;
	}
	public final void hasBeenExported(final boolean hBeenExported) {
		this.mHasBeenExported = hBeenExported;
	}
	public final boolean isActive() {
		return mIsActive;
	}
	public final void isActive(final boolean isActive) {
		this.mIsActive = isActive;
	}

	public final void setNumberOfWifis(final int numberOfWifis) {
		mNumberOfWifis = numberOfWifis;
	}

	public final void setNumberOfCells(final int numberOfCells) {
		mNumberOfCells = numberOfCells;
	}

	public final int getNumberOfWifis() {
		return mNumberOfWifis;
	}

	public final int getNumberOfCells() {
		return mNumberOfCells;
	}



	@Override
	public final String toString() {
		return getId() + " / " + getDescription() + " / Created at " + getCreatedAt() + " / Updated at " + getLastUpdated() + " / Exported? " + hasBeenExported() + " / Active? " + isActive(); 
	}





}
