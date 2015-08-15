/*
 * @source: http://stackoverflow.com/questions/1258275/vertical-rotated-label-in-android
 * 
 * Copyright (C) 2010 Karl Ostmo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openbmap.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import org.openbmap.R;

/** 
 * Vertical label control
 */
public class VerticalLabelView extends View {
	
	private static final String TAG = VerticalLabelView.class.getSimpleName();
	
	/**
	 * Default label text size in SP
	 */
	private static final int DEFAULT_TEXT_SIZE = 14;
	private static final int DEFAULT_PADDING = 3;
	private static final int DEFAULT_COLOR = 0xFF000000;
	
	/**
	 * Rotation mAngle
	 */
	private static final int COUNTER_CLOCKWISE = -90;

	private TextPaint mTextPaint;
	private String mText;
	private int mAscent;
	private final Rect mTextBounds = new Rect();
	
	public VerticalLabelView(final Context context) {
		super(context);
		initLabelView();
	}

	public VerticalLabelView(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		Log.d(TAG, "VerticalLabelView(Context mContext, AttributeSet attrs) called");
		initLabelView();

		final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.VerticalLabelView);

		final CharSequence s = a.getString(R.styleable.VerticalLabelView_text);
		if (s != null) {
			setText(s.toString());
		}

		setTextColor(a.getColor(R.styleable.VerticalLabelView_textColor, DEFAULT_COLOR));

		final int textSize = a.getDimensionPixelOffset(R.styleable.VerticalLabelView_textSize, 0);
		if (textSize > 0) {
			setTextSize(textSize);
		}

		a.recycle();
	}

	private void initLabelView() {
	
		
		mTextPaint = new TextPaint();
		mTextPaint.setAntiAlias(true);
		mTextPaint.setTextSize((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
				DEFAULT_TEXT_SIZE, getResources().getDisplayMetrics()));
		mTextPaint.setColor(DEFAULT_COLOR);
		mTextPaint.setTextAlign(Align.CENTER);
		setPadding(DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING);
	}

	public final void setText(final String text) {
		mText = text;
		requestLayout();
		invalidate();
	}

	public final void setTextSize(final int size) {
		mTextPaint.setTextSize(size);
		requestLayout();
		invalidate();
	}

	public final void setTextColor(final int color) {
		mTextPaint.setColor(color);
		invalidate();
	}

	@Override
	protected final void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
	
		mTextPaint.getTextBounds(mText, 0, mText.length(), mTextBounds);
		setMeasuredDimension(
				measureWidth(widthMeasureSpec),
				measureHeight(heightMeasureSpec));
	}

	private int measureWidth(final int measureSpec) {
		int result = 0;
		final int specMode = MeasureSpec.getMode(measureSpec);
		final int specSize = MeasureSpec.getSize(measureSpec);


		if (specMode == MeasureSpec.EXACTLY) {
			// We were told how big to be
			result = specSize;
		} else {
			// Measure the text
			result = mTextBounds.height() + getPaddingLeft() + getPaddingRight();


			if (specMode == MeasureSpec.AT_MOST) {
				// Respect AT_MOST value if that was what is called for by measureSpec
				result = Math.min(result, specSize);
			}
		}
		return result;
	}

	private int measureHeight(final int measureSpec) {
		int result = 0;
		final int specMode = MeasureSpec.getMode(measureSpec);
		final int specSize = MeasureSpec.getSize(measureSpec);

		mAscent = (int) mTextPaint.ascent();
		if (specMode == MeasureSpec.EXACTLY) {
			// We were told how big to be
			result = specSize;
		} else {
			// Measure the text
			result = mTextBounds.width() + getPaddingTop() + getPaddingBottom();


			if (specMode == MeasureSpec.AT_MOST) {
				// Respect AT_MOST value if that was what is called for by measureSpec
				result = Math.min(result, specSize);
			}
		}
		return result;
	}


	@Override
	protected final void onDraw(final Canvas canvas) {
		super.onDraw(canvas);

		final float textHorizontallyCenteredOriginX = getPaddingLeft() + mTextBounds.width() / 2f;
		final float textHorizontallyCenteredOriginY = getPaddingTop() - mAscent;

		canvas.translate(textHorizontallyCenteredOriginY, textHorizontallyCenteredOriginX);
		canvas.rotate(COUNTER_CLOCKWISE);
		canvas.drawText(mText, 0, 0, mTextPaint);
	}
}