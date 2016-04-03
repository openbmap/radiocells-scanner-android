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

import org.mapsforge.core.model.LatLong;
import org.openbmap.Radiobeacon;

/**
 * LatLong type with support for session
 */
public class SessionLatLong extends LatLong {

	private static final long	serialVersionUID	= 1L;
		private int	mSession;
		/**
		 * @param latitude
		 * @param longitude
		 */
		public SessionLatLong(final double latitude, final double longitude) {
			super(latitude, longitude);
			setSession(Radiobeacon.SESSION_NOT_TRACKING);
		}
		public SessionLatLong(final double latitude, final double longitude, final int session) {
			super(latitude, longitude);
			this.setSession(session);
		}
		public final int getSession() {
			return mSession;
		}
		public final void setSession(final int session) {
			this.mSession = session;
		}
		
	}
