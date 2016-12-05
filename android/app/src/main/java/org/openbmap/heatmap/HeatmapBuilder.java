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

package org.openbmap.heatmap;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader.TileMode;
import android.os.AsyncTask;
import android.util.Log;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.util.MercatorProjection;

import java.util.ArrayList;

/**
 * Builds heat map bitmap
 */
public class HeatmapBuilder extends AsyncTask<Object, Integer, Boolean> {

	private static final String TAG = HeatmapBuilder.class.getSimpleName();

	private final Canvas mCanvas;
	private final Bitmap mBackbuffer;
	private final int mWidth;
	private final int mHeight;
	private final float mScaleFactor;
	private final int mTileSize;
	private final float mRadius;

	private final BoundingBox	mBbox;
	private final byte mZoom;

	/**
	 * Used for callbacks.
	 */
	private final HeatmapBuilderListener mListener;

	public interface HeatmapBuilderListener {
		void onHeatmapCompleted(Bitmap backbuffer);
		void onHeatmapFailed();
	}

	public HeatmapBuilder(final HeatmapBuilderListener listener,
						  final int width,
                          final int height,
                          final BoundingBox bbox,
                          final byte zoom,
                          final float scaleFactor,
                          final int tilesize,
                          final float radius) {
		this.mListener = listener;
		this.mBbox = bbox;
		this.mBackbuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		this.mZoom = zoom;
		this.mCanvas = new Canvas(mBackbuffer);
		this.mScaleFactor = scaleFactor;
		this.mTileSize = tilesize;
		
		final Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);

		p.setColor(Color.TRANSPARENT);

		this.mWidth = width;
		this.mHeight = height;
		this.mCanvas.drawRect(0, 0, width, height, p);

		this.mRadius = radius;
	}

	/**
	 * Background task.
	 * @return true on success, false if at least one file upload failed
	 */
	@Override
	protected final Boolean doInBackground(final Object... params) {
		//Point out = new Point(1, 1);
		if (params[0] == null) {
			throw new IllegalArgumentException("No heat points provided");
		}


		final ArrayList<HeatLatLong> heatLatLongs = ((ArrayList<HeatLatLong>) params[0]);

		for (final HeatLatLong heat : heatLatLongs) {

			if (heat.longitude >= mBbox.minLongitude && heat.longitude <= mBbox.maxLongitude
					&& heat.latitude >= mBbox.minLatitude && heat.latitude <= mBbox.maxLatitude) {
				final float leftBorder = (float) MercatorProjection.longitudeToPixelX(mBbox.minLongitude, MercatorProjection.getMapSize(mZoom, mTileSize)); 
				final float topBorder = (float) MercatorProjection.latitudeToPixelY(mBbox.maxLatitude, MercatorProjection.getMapSize(mZoom, mTileSize));

				final float x = (float) (MercatorProjection.longitudeToPixelX(heat.longitude, MercatorProjection.getMapSize(mZoom, mTileSize)) - leftBorder);
				final float y = (float) (MercatorProjection.latitudeToPixelY(heat.latitude, MercatorProjection.getMapSize(mZoom, mTileSize)) - topBorder);

				// Log.i(TAG, "X:" + x + " Y:" + y);
				addPoint(x, y, heat.getIntensity());

				// skip loop if canceled..
				if (isCancelled()) {
					return false;
				}
			}
		}

		colorize(0, 0);

		return !isCancelled();
	}

	@Override
	protected final void onPostExecute(final Boolean success) {
		if (success) {
			if (mListener != null) {
				mListener.onHeatmapCompleted(mBackbuffer);
			}
		} else {
			if (mListener != null) {
				Log.e(TAG, "Heat-map error or thread canceled");
				mListener.onHeatmapFailed();
			}
		}
	}

	private void addPoint(final float x, final float y, final int times) {
		final RadialGradient g = new RadialGradient(x, y,
                mRadius,
                Color.argb(getScaledIntensity(times), 0, 0, 0),
                Color.TRANSPARENT,
                TileMode.CLAMP);

		final Paint gp = new Paint();
		gp.setShader(g);

		mCanvas.drawCircle(x, y, mRadius, gp);
	}

    private int getScaledIntensity(int times) {
        final int MIN = -120;
        final int MAX = -45;

        double scaled = ((times-MIN)/((double)Math.abs(MAX-MIN)) * 255);
        //Log.v(TAG, "Heat conversion " + times + " --> " + scaled);
        return Math.max((int)scaled, 255);
    }

    private void colorize(final float x, final float y) {
		final int[] pixels = new int[this.mWidth * this.mHeight];

		mBackbuffer.getPixels(pixels, 0, this.mWidth, 0, 0, this.mWidth, this.mHeight);

		for (int i = 0; i < pixels.length; i++) {
			int r = 0, g = 0, b = 0, tmp = 0;
			final int alpha = pixels[i] >>> 24;

					if (alpha == 0) {
						continue;
					}

					if (alpha <= 255 && alpha >= 235) {
						tmp = 255 - alpha;
						r = 255 - tmp;
						g = tmp * 12;
					} else if (alpha <= 234 && alpha >= 200) {
						tmp = 234 - alpha;
						r = (int)(255f - (tmp * 7.5f));
						g = 255;
					} else if (alpha <= 199 && alpha >= 150) {
						tmp = 199 - alpha;
						g = 255;
						b = tmp * 5;
					} else if (alpha <= 149 && alpha >= 100) {
						tmp = 149 - alpha;
						g = 255 - (tmp * 5);
						b = 255;	
					} else {
						b = 255;
					}
					pixels[i] = Color.argb(alpha / 2, r, g, b);
		}

		mBackbuffer.setPixels(pixels, 0, this.mWidth, 0, 0, this.mWidth, this.mHeight);
	}


}
