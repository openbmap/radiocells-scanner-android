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

package org.openbmap.heatmap;

import org.mapsforge.core.model.LatLong;

public class HeatLatLong extends LatLong {

	/**
	 * LatLong type with support for (heat map) intensity
	 */
	private static final long	serialVersionUID	= 1L;
	private int mStrength;

	/**
	 * @param latitude
	 * @param longitude
	 */
	public HeatLatLong(final double latitude, final double longitude) {
		super(latitude, longitude);
		setStrength(1);
	}
	public HeatLatLong(final double latitude, final double longitude, final int strength) {
		super(latitude, longitude);
		this.setStrength(strength);
	}
	public final int getStrength() {
		return mStrength;
	}
	public final void setStrength(final int strength) {
		this.mStrength = strength;
	}

}