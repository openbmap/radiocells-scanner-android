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

import android.annotation.SuppressLint;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *  Translates wifi frequency to channel
 *  Source: http://en.wikipedia.org/wiki/List_of_WLAN_channels
 */
@SuppressLint("UseSparseArrays")
public class WifiChannel {
	private static final Map<Integer, String> frequencyMap;
	
	static {
		Map<Integer, String> aMap = new HashMap<Integer, String>();
		aMap.put(2412,"1");
		aMap.put(2417,"2");
		aMap.put(2422,"3");
		aMap.put(2427,"4");
		aMap.put(2432,"5");
		aMap.put(2437,"6");
		aMap.put(2442,"7");
		aMap.put(2447,"8");
		aMap.put(2452,"9");
		aMap.put(2457,"10");
		aMap.put(2462,"11");
		aMap.put(2467,"12");
		aMap.put(2472,"13");
		aMap.put(2484,"14");
		aMap.put(5180,"36");
		aMap.put(5200,"40");
		aMap.put(5220,"44");
		aMap.put(5240,"48");
		aMap.put(5260,"52");
		aMap.put(5280,"56");
		aMap.put(5300,"60");
		aMap.put(5320,"64");
		aMap.put(5500,"100");
		aMap.put(5520,"104");
		aMap.put(5540,"108");
		aMap.put(5560,"112");
		aMap.put(5580,"116");
		aMap.put(5600,"120");
		aMap.put(5620,"124");
		aMap.put(5640,"128");
		aMap.put(5660,"132");
		aMap.put(5680,"136");
		aMap.put(5700,"140");
		aMap.put(5735,"147");
		aMap.put(5755,"151");
		aMap.put(5775,"155");
		aMap.put(5835,"167");
		
		frequencyMap = Collections.unmodifiableMap(aMap);
	}
	
	public static final String getChannel(int freq) {
		return frequencyMap.get(freq);
	}

}
