/*
 * Copyright © 2013–2016 Michael von Glasow.
 *
 * This file is part of LSRN Tools.
 *
 * LSRN Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSRN Tools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSRN Tools.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openbmap.utils.remote_treeview;

import android.os.AsyncTask;

/**
 * A task which retrieves the contents of a remote directory in the background and notifies a listener
 * upon completion.
 */
public abstract class BrowseAbstractTask extends AsyncTask<String, Void, RemoteFile[]> {

}