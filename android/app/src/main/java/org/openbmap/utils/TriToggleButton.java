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

package org.openbmap.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageButton;

import org.openbmap.R;

/**
 * Three state toggle button
 */
public class TriToggleButton extends ImageButton {
	@SuppressWarnings("unused")
	private static final String TAG = TriToggleButton.class.getSimpleName();

	private int mState;

	private static final int[] STATE_NEGATIVE_SET =
		{ R.attr.state_negative};

	private static final int[] STATE_NEUTRAL_SET =
		{ R.attr.state_neutral};

	private static final int[] STATE_POSITIVE_SET =
		{ R.attr.state_positive};

	private Drawable mNegativeImage;
	private Drawable mStateNeutralImage;
	private Drawable mStatePositiveImage;

	// Constructors
	@SuppressLint("NewApi")
	public TriToggleButton(final Context context)	{
		super(context);

		mState = 1;
		this.setImageDrawable(mStateNeutralImage);
		if (Build.VERSION.SDK_INT >= 16) {
			this.setBackground(null);
		} else {
			this.setBackgroundDrawable(null);
		}
		onCreateDrawableState(0);
	}

	@SuppressLint("NewApi")
	public TriToggleButton(final Context context, final AttributeSet attrs) {
		super(context, attrs);

		mState = 1;
		this.setImageDrawable(mStateNeutralImage);
		if (Build.VERSION.SDK_INT >= 16) {
			this.setBackground(null);
		} else {
			this.setBackgroundDrawable(null);
		}
	}

	@SuppressLint("NewApi")
	public TriToggleButton(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);

		mState = 1;
		this.setImageDrawable(mStateNeutralImage);
		if (Build.VERSION.SDK_INT >= 16) {
			this.setBackground(null);
		} else {
			this.setBackgroundDrawable(null);
		}
	}

	@Override
	public final boolean performClick() {
		nextState();
		return super.performClick();
	}

	@Override
	public final int[] onCreateDrawableState(final int extraSpace) 	{
		final int[] drawableState = super.onCreateDrawableState(extraSpace + 3);

		if (mState == 0)	{
			mergeDrawableStates(drawableState, STATE_NEGATIVE_SET);
		} else if (mState == 1) {
			mergeDrawableStates(drawableState, STATE_NEUTRAL_SET);
		} else if (mState == 2) {
			mergeDrawableStates(drawableState, STATE_POSITIVE_SET);
		}

		return drawableState;
	}

	public final void setState(final int state) {
		if ((state > -1) && (state < 3)) {
			mState = state;
			setImage();
		}
	}

	public final int getState() {
		return mState;
	}

	public final void nextState()	{
		mState++;

		if (mState > 2) {
			mState = 0;
		}

		setImage();
	}

	public final void previousState()	{
		mState--;

		if (mState < 0) {
			mState = 2;
		}

		setImage();
	}

	private void setImage()	{
		switch(mState) {
		case 0: 
			if (mNegativeImage != null) {
				this.setImageDrawable(mNegativeImage);
			}
			break;
		case 1:
			if (mStateNeutralImage != null) {
				this.setImageDrawable(mStateNeutralImage);
			}
			break;
		case 2: 
			if (mStatePositiveImage != null) {
				this.setImageDrawable(mStatePositiveImage);
			}
			break;
		default: 
			this.setImageDrawable(null); // Should never happen, but just in case
			break;
		}
	}

	public final Drawable getNegativeImage() {
		return mNegativeImage;
	}

	public final void setNegativeImage(final Drawable mStateOneImage) {
		this.mNegativeImage = mStateOneImage;
		setImage();
	}

	public final Drawable getNeutralImage() {
		return mStateNeutralImage;
	}

	public final void setNeutralImage(final Drawable mStateTwoImage) {
		this.mStateNeutralImage = mStateTwoImage;
		setImage();
	}

	public final Drawable getPositiveImage() {
		return mStatePositiveImage;
	}

	public final void setPositiveImage(final Drawable mStateThreeImage) {
		this.mStatePositiveImage = mStateThreeImage;
		setImage();
		
	}
}
