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

import org.mapsforge.map.android.view.MapView;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * View pager, that disables swipe scrolling for map tab
 */
public class CustomViewPager extends ViewPager {
	
	private static final String TAG = CustomViewPager.class.getSimpleName();
	
    public CustomViewPager(final Context context) {
        super(context);
    }

    public CustomViewPager(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean canScroll(final View v, final boolean checkV, final int dx, final int x, final int y) {
        if (v instanceof MapView) {
        	Log.i(TAG, "Scrolling disabled for map view");
            return true;
        }
        return super.canScroll(v, checkV, dx, x, y);
    }
}