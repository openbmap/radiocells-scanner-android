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


/**
 * Sanitizes XML strings by replacing <,>,", ' and &
 */
package org.openbmap.utils;

import java.util.regex.Pattern;

/**
 * Replaces critical characters in xml files
 *
 */
public final class XmlSanitizer {
	
	private static final Pattern PURE_ASCII_STRING = Pattern.compile("^\\p{ASCII}*$"); // "[^\\p{ASCII}]+"
	
	/**
	 * Checks if string contains &, <, >, ", ' or non-ascii characters
	 * @param raw
	 * @return
	 */
	public static boolean isValid(final String raw){
		boolean result =  !raw.contains("&") && !raw.contains("<") && !raw.contains(">") && !raw.contains("\"") && !raw.contains("'");
		result = result && PURE_ASCII_STRING.matcher(raw).matches();
		return result;
	}
	
	private XmlSanitizer() {
		
	}

}
