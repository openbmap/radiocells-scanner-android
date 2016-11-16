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
package org.openbmap;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.greenrobot.eventbus.EventBus;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.model.DisplayModel;
import org.openbmap.services.ManagerService;

public class RadiobeaconApplication extends Application {

	public static final String SETTING_SCALE = "scale";
	public static final String SETTING_SCALEBAR = "scalebar";
	public static final String SETTING_SCALEBAR_METRIC = "metric";
	public static final String SETTING_SCALEBAR_IMPERIAL = "imperial";
	public static final String SETTING_SCALEBAR_BOTH = "both";
	public static final String SETTING_SCALEBAR_NONE = "none";
	public static final String TAG = "SAMPLES APP";

	@Override
	public void onCreate() {
		super.onCreate();

        EventBus.builder().addIndex(new MyEventBusIndex()).installDefaultEventBus();

		Intent serviceIntent = new Intent(getApplicationContext(), ManagerService.class);
		startService(serviceIntent);

		AndroidGraphicFactory.createInstance(this);
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		final float fs = Float.valueOf(preferences.getString(SETTING_SCALE,
				Float.toString(DisplayModel.getDefaultUserScaleFactor())));
		if (fs != DisplayModel.getDefaultUserScaleFactor()) {
			DisplayModel.setDefaultUserScaleFactor(fs);
		}
	}
}
