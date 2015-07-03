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
	 * Checks if string contains &, <, >, ", ', non-ascii characters or anything other than A-Z, 0-9
	 * @param test String to test
	 * @return true, if string only contains valid chars
	 */
    public static boolean isValid(final String test){
        // check we don't have xml chars in it
		boolean result =  !test.contains("&") && !test.contains("<") && !test.contains(">") && !test.contains("\"") && !test.contains("'");
        // assure we only have ASCII chars
        result = result && PURE_ASCII_STRING.matcher(test).matches();
        // assure we really only A-Z and numbers in it
        result = result && (test.replaceAll("[^a-zA-Z0-9]", "").length() == test.length());

		return result;
	}

    private XmlSanitizer() {
		
	}

}
