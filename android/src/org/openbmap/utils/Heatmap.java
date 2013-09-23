/**
 * 
 */
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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.util.MercatorProjection;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RadialGradient;
import android.graphics.Shader.TileMode;

/**
 * A {@code Polygon} draws a connected series of line segments specified by a list of {@link LatLong LatLongs}. If the
 * first and the last {@code LatLong} are not equal, the {@code Polygon} will be closed automatically.
 * <p>
 * A {@code Polygon} holds two {@link Paint} objects to allow for different outline and filling. These paints define
 * drawing parameters such as color, stroke width, pattern and transparency.
 */
class HeatTask implements Runnable {
	private final List<LatLong> latLongs = new CopyOnWriteArrayList<LatLong>();

	private Bitmap bitmap;
	private Canvas myCanvas;
	private Bitmap backbuffer;
	private int width;
	private int height;
	private float radius;

	//private List<HeatPoint> points;
	private List<LatLong> points;

	private Paint	paintFill;

	private Paint	paintStroke;

	private byte	zoom;


	public HeatTask(int width, int height, byte zoom, float radius, List<LatLong> points){

		backbuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		this.zoom = zoom;
		myCanvas = new Canvas(backbuffer);
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);

		p.setColor(Color.TRANSPARENT);

		this.width = width;

		this.height = height;

		this.points = points;

		myCanvas.drawRect(0, 0, width, height, p);

		this.radius = radius;
	}


	@Override
	public void run() {

		Point out = new Point(1, 1);

		//for(HeatPoint p : points){
		for(LatLong p : points){

			//GeoPoint in = new GeoPoint((int)(p.lat*1E6),(int)(p.lon*1E6));
			//proj.toPixels(in, out);
			//proj.toPixels(p, out);
			addPoint((float) MercatorProjection.longitudeToPixelX(p.longitude, zoom), (float) MercatorProjection.latitudeToPixelY(p.longitude, zoom), 1);
			//addPoint(out.x, out.y, /*p.intensity*/ 1);

		}

		colorize(0, 0);

		/*
		lock.lock();

		layer = backbuffer;

		lock.unlock();

		mapView.postInvalidate();
*/
	}

	private void addPoint(float x, float y, int times) {
		RadialGradient g = new RadialGradient(x, y, radius, Color.argb(Math.max(10 * times, 255), 0, 0, 0), Color.TRANSPARENT, TileMode.CLAMP);

		Paint gp = new Paint();
		gp.setShader(g);

		myCanvas.drawCircle(x, y, radius, gp);
	}



	private void colorize(float x, float y) {
		int[] pixels = new int[(int) (this.width * this.height)];

		backbuffer.getPixels(pixels, 0, this.width, 0, 0, this.width,this.height);

		for (int i = 0; i < pixels.length; i++) {
			int r = 0, g = 0, b = 0, tmp = 0;
			int alpha = pixels[i] >>> 24;

			if (alpha == 0) {
				continue;
			}

			if (alpha <= 255 && alpha >= 235) {
				tmp = 255 - alpha;
				r = 255 - tmp;
				g = tmp * 12;
			} else if (alpha <= 234 && alpha >= 200) {

				tmp = 234 - alpha;
				r = 255 - (tmp * 8);
				g = 255;

			} else if (alpha <= 199 && alpha >= 150) {
				tmp = 199 - alpha;
				g = 255;
				b = tmp * 5;

			} else if (alpha <= 149 && alpha >= 100) {
				tmp = 149 - alpha;
				g = 255 - (tmp * 5);
				b = 255;
			} else
				b = 255;

			pixels[i] = Color.argb((int) alpha / 2, r, g, b);
		}

		backbuffer.setPixels(pixels, 0, this.width, 0, 0, this.width, this.height);
	}


	/**
	 * @return a thread-safe list of LatLongs in this polygon.
	 */
	public List<LatLong> getLatLongs() {
		return this.latLongs;
	}

	/**
	 * @return the {@code Paint} used to fill this polygon (may be null).
	 */
	public synchronized Paint getPaintFill() {
		return this.paintFill;
	}

	/**
	 * @return the {@code Paint} used to stroke this polygon (may be null).
	 */
	public synchronized Paint getPaintStroke() {
		return this.paintStroke;
	}

	/**
	 * @param paintFill
	 *            the new {@code Paint} used to fill this polygon (may be null).
	 */
	public synchronized void setPaintFill(Paint paintFill) {
		this.paintFill = paintFill;
	}

	/**
	 * @param paintStroke
	 *            the new {@code Paint} used to stroke this polygon (may be null).
	 */
	public synchronized void setPaintStroke(Paint paintStroke) {
		this.paintStroke = paintStroke;
	}
}

