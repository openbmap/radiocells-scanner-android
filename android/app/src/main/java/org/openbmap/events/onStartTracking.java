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

package org.openbmap.events;

import org.openbmap.RadioBeacon;

public class onStartTracking {
    public final int session;

    /**
     * Default constructor: no session id provided, database will auto-assign session id
     */
     public onStartTracking() {
        this.session = RadioBeacon.SESSION_NOT_TRACKING;
     }

    /**
     * Constructor to resume an existing session
     * @param session session id to resume
     */
    public onStartTracking(int session) {
        this.session = session;
    }

}
