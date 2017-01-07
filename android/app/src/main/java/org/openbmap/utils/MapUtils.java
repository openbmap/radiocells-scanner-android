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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidResourceBitmap;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.download.tilesource.OnlineTileSource;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.model.MapViewPosition;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.reader.header.MapFileException;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.openbmap.Preferences;
import org.openbmap.R;

import java.io.File;

/**
 * Utility functions that can be used across different mapsforge based activities
 */

public final class MapUtils {
	private static final String TAG = MapUtils.class.getSimpleName();


    /**
     * Clears Android ressources
     */
    public static void clearAndroidRessources() {
        AndroidResourceBitmap.clearResourceBitmaps();
        AndroidGraphicFactory.clearResourceMemoryCache();
    }

    public interface onLongPressHandler{
		void onLongPress(LatLong tapLatLong, Point thisXY, Point tapXY);
	}

	public static Paint createPaint(final int color, final int strokeWidth, final Style style) {
		final Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
		paint.setColor(color);
		paint.setStrokeWidth(strokeWidth);
		paint.setStyle(style);
		return paint;
	}

    public static Marker createTappableMarker(final Context c, int resourceIdentifier,
                                       LatLong latLong) {
        Drawable drawable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? c.getDrawable(resourceIdentifier) : c.getResources().getDrawable(resourceIdentifier);
        Bitmap bitmap = AndroidGraphicFactory.convertToBitmap(drawable);
        bitmap.incrementRefCount();
        return new Marker(latLong, bitmap, 0, -bitmap.getHeight() / 2) {
            @Override
            public boolean onTap(LatLong geoPoint, Point viewPosition,
                                 Point tapPoint) {
                if (contains(viewPosition, tapPoint)) {
                    Toast.makeText(c,
                            "The Marker was tapped " + geoPoint.toString(),
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        };
    }

	/**
	 * Creates a tile layer, which optionally supports long press actions and custom render themes
	 * @param tileCache
	 * @param mapViewPosition
	 * @param mapFile
	 * @return
	 */
	public static Layer createTileRendererLayer(final TileCache tileCache,
												final MapViewPosition mapViewPosition,
												final MapFile mapFile,
												final onLongPressHandler handler) {

		if (mapFile == null) {
			return null;
		}

		final TileRendererLayer tileRendererLayer = new TileRendererLayer(tileCache, mapFile, mapViewPosition, AndroidGraphicFactory.INSTANCE) {
			@Override
			public boolean onLongPress(LatLong tapLatLong, Point thisXY, Point tapXY) {
                if (handler != null) {
                    handler.onLongPress(tapLatLong, thisXY, tapXY);
                    return true;
                } else {
                    return false;
                }
			}
		};

        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);
        tileRendererLayer.setTextScale(1.5f);
        return tileRendererLayer;
	}

    /**
     * Creates a separate map tile cache
     * @return
     */
    public static final TileCache createTileCache(final Context ctx, int tileSize, double overdrawFactor) {
        return AndroidUtil.createTileCache(ctx.getApplicationContext(), "mapcache", tileSize, 1f, overdrawFactor);
    }


	/**
	 * Checks whether a valid map file has been selected
	 * @param context
	 * @return true, if map file chosen and not in online mode
	 */
	public static Boolean hasOfflineMap(final Context context) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String map = prefs.getString(Preferences.KEY_MAP_FILE, Preferences.DEFAULT_MAP_FILE);
        Log.i(TAG, "Selected map: " + map);
		return (!map.equals(Preferences.VAL_MAP_NONE) && !map.equals(Preferences.VAL_MAP_ONLINE));
	}

    /**
     * Checks whether online maps been selected
     * @param context
     * @return true, if online mode
     */
    public static Boolean useOnlineMaps(final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return (prefs.getString(Preferences.KEY_MAP_FILE, Preferences.DEFAULT_MAP_FILE).equals(Preferences.VAL_MAP_ONLINE));
    }

    /**
	 * Opens map file
	 * @param context
	 * @return map file, null if file not found or error while opening map file
	 */
	public static MapFile getMapFile(final Context context) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		File file = new File(FileUtils.getMapFolder(context).getAbsolutePath(),
				prefs.getString(Preferences.KEY_MAP_FILE, Preferences.DEFAULT_MAP_FILE));

		if (file.exists()) {
			try {
				Log.i(TAG, "Using map " + file);
				return new MapFile(file);
			} catch (MapFileException mfe) {
				Toast.makeText(context,
							   context.getString(R.string.failed_to_load_map_file_can_be_damaged),
							   Toast.LENGTH_SHORT).show();
				Log.e(TAG, "Failed to load map, file may be damaged " + mfe.getMessage(), mfe);
				return null;
			}
		} else {
			Log.e(TAG, "Map file doesn't exist");
			return null;
		}
	}

    /**
     * Creates a online layer
     * @return online layer
     */
    public static OnlineTileSource createOnlineTileSource() {
        final OnlineTileSource layer = new OnlineTileSource(new String[]{
                "a.tile.openstreetmap.org", "b.tile.openstreetmap.org", "c.tile.openstreetmap.org"}, 80);
        layer.setName("osm")
        .setAlpha(false)
        .setBaseUrl("")
        .setExtension("png")
        .setParallelRequestsLimit(8)
        .setProtocol("https")
        .setTileSize(256)
        .setZoomLevelMax((byte) 18)
        .setZoomLevelMin((byte) 0);
        return layer;
    }

	private MapUtils() {
		throw new IllegalStateException();
	}

}
