package org.openbmap.utils;

/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.File;
import java.io.IOException;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.cache.InMemoryTileCache;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.cache.TwoLevelTileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.model.MapViewPosition;
import org.mapsforge.map.reader.MapDataStore;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.openbmap.Preferences;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;

/**
 * Utility functions that can be used across different mapsforge based activities
 */

public final class MapUtils {
	private static final String TAG = MapUtils.class.getSimpleName();

	/**
	 * Compatibility method
	 * 
	 * @param a
	 *            the current activity
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static void enableHome(final Activity a) {
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			// Show the Up button in the action bar.
			a.getActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}


	public static interface onLongPressHandler{
		void onLongPress(LatLong tapLatLong, Point thisXY, Point tapXY);
	}

	/**
	 * @param c
	 *            the Android context
	 * @param id
	 *            name for the directory
	 * @return a new cache created on the external storage
	 */
	@Deprecated
	public static TileCache createExternalStorageTileCache(final Context c, final String id) {
		final TileCache firstLevelTileCache = new InMemoryTileCache(32);
		final String cacheDirectoryName = c.getExternalCacheDir().getAbsolutePath() + File.separator + id;
		final File cacheDirectory = new File(cacheDirectoryName);
		if (!cacheDirectory.exists()) {
			cacheDirectory.mkdir();
		}
		final TileCache secondLevelTileCache = new FileSystemTileCache(1024, cacheDirectory, AndroidGraphicFactory.INSTANCE);
		return new TwoLevelTileCache(firstLevelTileCache, secondLevelTileCache);
	}

	static Marker createMarker(final Context c, final int resourceIdentifier, final LatLong latLong) {
		final Drawable drawable = c.getResources().getDrawable(resourceIdentifier);
		final Bitmap bitmap = AndroidGraphicFactory.convertToBitmap(drawable);
		return new Marker(latLong, bitmap, 0, -bitmap.getHeight() / 2);
	}

	public static Paint createPaint(final int color, final int strokeWidth, final Style style) {
		final Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
		paint.setColor(color);
		paint.setStrokeWidth(strokeWidth);
		paint.setStyle(style);
		return paint;
	}

	/**
	 * @param c
	 *            the Android context
	 * @return a new cache
	 */
	/*static TileCache createTileCache(Context c, String id) {
		TileCache firstLevelTileCache = new InMemoryTileCache(32);
		File cacheDirectory = c.getDir(id, Context.MODE_PRIVATE);
		TileCache secondLevelTileCache = new FileSystemTileCache(1024, cacheDirectory, AndroidGraphicFactory.INSTANCE);
		return new TwoLevelTileCache(firstLevelTileCache, secondLevelTileCache);
	};*/

	/**
	 * Creates a tile layer, which optionally supports long press actions and custom render themes
	 * @param tileCache
	 * @param mapViewPosition
	 * @param mapFile
	 * @param longPressHandler
	 * @param renderTheme
	 * @return
	 */
	public static Layer createTileRendererLayer(final TileCache tileCache, final MapViewPosition mapViewPosition,
			final MapFile mapFile, final onLongPressHandler longPressHandler, final XmlRenderTheme renderTheme) {
		
		if (mapFile == null) {
			return null;
		}
		
		if (longPressHandler != null) {	
			// add support for onLongClick events
			final TileRendererLayer tileRendererLayer = new TileRendererLayer (tileCache, (MapDataStore) mapFile,
					mapViewPosition, false, true, AndroidGraphicFactory.INSTANCE) {
				@Override
				public boolean onLongPress(final LatLong tapLatLong, final Point thisXY, final Point tapXY) {
					longPressHandler.onLongPress(tapLatLong, thisXY, tapXY);
					return true;
				}
			};

			if (renderTheme == null) {
				tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);
			} else {
				tileRendererLayer.setXmlRenderTheme(renderTheme);
			}

			tileRendererLayer.setTextScale(1.5f);
			return tileRendererLayer;	
		} else {
			// just a plain vanilla layer
			final TileRendererLayer tileRendererLayer = new TileRendererLayer (tileCache, (MapDataStore) mapFile,
					mapViewPosition, false, true, AndroidGraphicFactory.INSTANCE);

			//tileRendererLayer.setMapFile(mapFile);
			
			if (renderTheme == null) {
				tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);
			} else {
				tileRendererLayer.setXmlRenderTheme(renderTheme);
			}
			
			tileRendererLayer.setTextScale(1.5f);
			return tileRendererLayer;
		}
	}

	static Bitmap viewToBitmap(final Context c, final View view) {
		view.measure(MeasureSpec.getSize(view.getMeasuredWidth()), MeasureSpec.getSize(view.getMeasuredHeight()));
		view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
		view.setDrawingCacheEnabled(true);
		final Drawable drawable = new BitmapDrawable(c.getResources(), android.graphics.Bitmap.createBitmap(view
				.getDrawingCache()));
		view.setDrawingCacheEnabled(false);
		return AndroidGraphicFactory.convertToBitmap(drawable);
	}

	/**
	 * Checks whether a valid map file has been selected
	 * @param context
	 * @return true, if map file is not none
	 */
	public static Boolean isMapSelected(final Context context) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return (!prefs.getString(Preferences.KEY_MAP_FILE, Preferences.VAL_MAP_FILE).equals(Preferences.VAL_MAP_NONE));
	}

	/**
	 * Opens map file
	 * @param context
	 * @return
	 * @throws IOException 
	 */
	public static MapFile getMapFile(final Context context) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		File file = new File(prefs.getString(Preferences.KEY_MAP_FOLDER,
				context.getExternalFilesDir(null).getAbsolutePath() + File.separator + Preferences.MAPS_SUBDIR), 
				prefs.getString(Preferences.KEY_MAP_FILE, Preferences.VAL_MAP_FILE));
		
		if (file.exists()) {
			return new MapFile(file);
		} else { 
			Log.e(TAG, "Map file doesn't exist");
			return null;
		}
	}

	private MapUtils() {
		throw new IllegalStateException();
	}
}
