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

import java.text.Collator;
import java.util.Comparator;

/**
 * A Comparator for {@link RemoteFile}s.
 *
 * Sorting is done by name, which may alternate between files and directories (unlike the customary file
 * manager experience, where directories tend to be listed first). Sort order is determined by the default
 * locale.
 */
public class RemoteFileComparator implements Comparator<RemoteFile> {

    @Override
    public int compare(RemoteFile lhs, RemoteFile rhs) {
        Collator collator = Collator.getInstance();
        return collator.compare(lhs.name, rhs.name);
    }

}
