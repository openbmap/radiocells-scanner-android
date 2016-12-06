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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
import android.os.AsyncTask;
import android.util.Log;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.util.MercatorProjection;
import org.openbmap.utils.FastBlur;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Builds heat map bitmap. For inspirations have a look at
 * https://chiuki.github.io/android-shaders-filters
 *
 */
public class HeatmapBuilder extends AsyncTask<Object, Integer, Boolean> {

    private static final String TAG = HeatmapBuilder.class.getSimpleName();

    public static final int WEAK = 30;
    public static final int VERY_LIGHT = 40;
    public static final int LIGHT = 50;
    public static final int MEDIUM_LIGHT = 70;
    public static final int MEDIUM = 100;
    public static final int MEDIUM_STRONG = 150;
    public static final int STRONG = 240;
    public static final int VERY_STRONG = 250;
    public static final int EXTRA_ORDINARY = 255;

    private static final float BLUR_RADIUS = 10;

    private final Canvas mCanvas;
	private final Bitmap mBackbuffer;
	private final int mWidth;
	private final int mHeight;
	private final float mScaleFactor;
	private final int mTileSize;
	private final float mRadius;

	private final BoundingBox mBbox;
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

        Log.i(TAG, "Canvas width: " + width);
        Log.i(TAG, "Canvas height: " + height);
        Log.i(TAG, "Radius: " + radius);

        // create transparent rect as canvas
		final Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.TRANSPARENT);
		this.mWidth = width;
		this.mHeight = height;
		this.mCanvas.drawRect(0, 0, width, height, p);

		this.mRadius = radius;
	}

    private class StrengthComparator implements Comparator<HeatLatLong> {
        @Override
        public int compare(HeatLatLong o1, HeatLatLong o2) {
            if (o1.getStrength() > o2.getStrength()) {return 1;}
            else if (o1.getStrength() < o2.getStrength()) {return -1;}
            else {return 0;}
        }
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

		ArrayList<HeatLatLong> heatLatLongs = ((ArrayList<HeatLatLong>) params[0]);
        // Sort by strength ascending
        Collections.sort(heatLatLongs, new StrengthComparator());

        // build the canvas
		for (final HeatLatLong heat : heatLatLongs) {
			if (isVisible(heat, mBbox)) {
				final float leftBorder = (float) MercatorProjection.longitudeToPixelX(mBbox.minLongitude,
                        MercatorProjection.getMapSize(mZoom, mTileSize));
				final float topBorder = (float) MercatorProjection.latitudeToPixelY(mBbox.maxLatitude,
                        MercatorProjection.getMapSize(mZoom, mTileSize));

				final float x = (float) (MercatorProjection.longitudeToPixelX(heat.longitude,
                        MercatorProjection.getMapSize(mZoom, mTileSize)) - leftBorder);
				final float y = (float) (MercatorProjection.latitudeToPixelY(heat.latitude,
                        MercatorProjection.getMapSize(mZoom, mTileSize)) - topBorder);

				addPoint(x, y, heat.getStrength(), mRadius);

				// skip loop if canceled..
				if (isCancelled()) {
					return false;
				}
			}
		}
        //blur();
        // add colors
		colorize();

		return !isCancelled();
	}

    // blur and upscale
    private void blur() {

        final int[] blurred = new int[this.mWidth * this.mHeight];
        Bitmap.createScaledBitmap(
                FastBlur.fastblur(mBackbuffer, 0.5f, 200), this.mWidth, this.mHeight, true).getPixels(
                blurred, 0, this.mWidth, 0, 0, this.mWidth, this.mHeight);

        mBackbuffer.setPixels(
                blurred,
                0,
                this.mWidth,
                0,
                0,
                this.mWidth,
                this.mHeight);

        //FastBlur.fastblur(mBackbuffer, 0.2f, 40).getPixels(blurred, 0, this.mWidth, 0, 0, this.mWidth, this.mHeight);
        //mBackbuffer.setPixels(blurred, 0, this.mWidth, 0, 0, this.mWidth, this.mHeight);

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

    /**
     * Cheaks whether point is in visible range
     * @param point
     * @param visible BBOX visible range
     * @return true if point is in visible range
     */
    private boolean isVisible(HeatLatLong point, BoundingBox visible) {
        return (point.longitude >= visible.minLongitude && point.longitude <= visible.maxLongitude
                && point.latitude >= visible.minLatitude && point.latitude <= visible.maxLatitude);
    }


    /**
     * Adds a point to the canvas. Basically a black circle is added at center x,y with given radius
     * Alpha value at center depends on field strength at that point. Highest intensity will have
     * an alpha value of 255, lowest an alpha value of 0. To make thing look more smooth,
     * a radial gradient is applied around x,y, thus surrounding pixels will have an alpha value > 0
     * too
     *
     * @param x center x
     * @param y center y
     * @param strength intensity at center
     * @param radius radial gradient radius in pixel
     */
	private void addPoint(final float x, final float y, final int strength, float radius) {
/*
        final RadialGradient gradient = new RadialGradient(x, y,
                radius,
                Color.argb(getScaledIntensity(strength), 0, 0, 0),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP);
		final Paint gp = new Paint();
		gp.setShader(gradient);
        */

        Xfermode PORT_DUFF = new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP);
        Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
        gp.setAlpha(getScaledIntensity(strength));
        gp.setXfermode(PORT_DUFF);

  //      gp.setShader(gradient);

		mCanvas.drawCircle(x, y, radius, gp);
	}

    /**
     * Returns the alpha value for the center point at given strength
     * @param strength
     * @return
     */
    private int getScaledIntensity(int strength) {
		// linear scaling
        /*
        final int MIN = -120;
        final int MAX = -45;
        double scaled = ((strength-MIN)/((double)Math.abs(MAX-MIN)) * 255);
        */

        // discrete classes
		int scaled = 0;
		if (strength < -108) { scaled = 50;}
		else if (strength < -108) {scaled = WEAK;}
		else if (strength < -96) {scaled = VERY_LIGHT;}
		else if (strength < -85) {scaled = LIGHT;}
        else if (strength < -80) {scaled = MEDIUM_LIGHT;}
        else if (strength < -75) {scaled = MEDIUM;}
        else if (strength < -70) {scaled = MEDIUM_STRONG;}
        else if (strength < -68) {scaled = STRONG;}
        else if (strength < -68) {scaled = VERY_STRONG;}
        else if (strength > -68) {scaled = EXTRA_ORDINARY;}

        scaled =  Math.min((int)scaled, 255);
		Log.v(TAG, "Heat conversion " + strength + " --> " +  scaled);
        return scaled;
    }

    /**
     * Colorizes canvas. Each pixel with an alpha value set is translated into the corresponding
     * heatmap color. Strong alpha values will appear in red, small alpha in blue. Pixels with
     * alpha = 0 (=empty) are ignored.
     */
    private void colorize() {
		final int[] pixels = new int[this.mWidth * this.mHeight];
		mBackbuffer.getPixels(pixels, 0, this.mWidth, 0, 0, this.mWidth, this.mHeight);

		for (int i = 0; i < pixels.length; i++) {
			int r = 0, g = 0, b = 0, tmp = 0;
			final int alpha = pixels[i] >>> 24;

					if (alpha == 0) {
						continue;
					}

					if (alpha <= 255 && alpha >= 235) {
                        // pixels wit alpha values between 255 and 235 have a strong red component
						tmp = 255 - alpha;
						r = 255 - tmp;
						g = tmp * 12;
					} else if (alpha <= 234 && alpha >= 200) {
                        // pixels wit alpha values between 234 and 200 have a light red component
						tmp = 234 - alpha;
						r = (int)(255f - (tmp * 7.5f));
						g = 255;
					} else if (alpha <= 199 && alpha >= 150) {
                        // pixels wit alpha values between 199 and 150 have a strong green component
						tmp = 199 - alpha;
						g = 255;
						b = tmp * 5;
					} else if (alpha <= 149 && alpha >= 100) {
                        // everything below green/blue
						tmp = 149 - alpha;
						g = 255 - (tmp * 5);
						b = 255;	
					} else {
                        // everything else strong blue
						b = 255;
					}
					pixels[i] = Color.argb(alpha / 2, r, g, b);
		}

        // write modified pixels to backbuffer
		mBackbuffer.setPixels(pixels, 0, this.mWidth, 0, 0, this.mWidth, this.mHeight);
	}


}
