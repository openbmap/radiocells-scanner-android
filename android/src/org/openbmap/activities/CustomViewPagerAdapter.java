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

package org.openbmap.activities;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

/**
 * Handles tab pages
 */
public class CustomViewPagerAdapter extends FragmentPagerAdapter {

	/**
	 * Number of ViewPager pages
	 */
	int pageCount = 3;
	
	/**
	 * Are maps enabled?
	 */
	private boolean	mMapsEnabled;

	public CustomViewPagerAdapter(FragmentManager fm) {
		super(fm);
	}

	@Override
	public Fragment getItem(int arg0) {
		switch (arg0) {
			case 0:
				StatsActivity stats = new StatsActivity();
				return stats;
			case 1:
				WifiListContainer wifis = new WifiListContainer();
				return wifis;
			case 2:
				CellsListContainer cells = new CellsListContainer();
				return cells;
			case 3:
				if (mMapsEnabled) {
					 MapViewActivity map = new MapViewActivity();
                     return map;
				}
		}
		return null;
	}

	@Override
	public int getCount() {
		return pageCount;
	}

	/**
	 * 
	 */
	public void enableMaps() {
		pageCount +=1;
		mMapsEnabled = true;
	}
}