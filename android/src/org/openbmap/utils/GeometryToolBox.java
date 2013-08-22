/*
 * Created on Apr 3, 2012
 * Author: Paul Woelfel
 * Email: frig@frig.at
 */
package org.openbmap.utils;

import android.hardware.SensorManager;

/**
 * @author Paul Woelfel (paul@woelfel.at)
 */
public final class GeometryToolBox {
	
	private GeometryToolBox() {
		
	}
	
	/**
	 * normalize the mAngle to the range 0 to 2π
	 * @param mAngle in radiant
	 * @return normalized mAngle
	 */
	public static float normalizeAngle(float angle){
		angle = (float) (angle % (2 * Math.PI));
		return (float) (angle < 0 ? angle + 2 * Math.PI:angle);
	}
	
	public static float normalizeAngleBetweenPI(final float angle) {
		return (float) (angle % (2 * Math.PI));
	}

	public static double lowpassFilter(final double oldValue, final double newValue, final double filter) {
		return oldValue + filter * (newValue - oldValue);
	}

	public static float lowpassFilter(final float oldValue, final float newValue, final float filter) {
		return oldValue + filter * (newValue - oldValue);
	}

	public static float calculateAngleDifference(final float angle1, final float angle2) {
		float difference = 0.0f;
		
		difference = angle1 - angle2;
		
		difference = normalizeAngle(difference);
		
		if (difference > Math.PI) {
			difference = (float) (difference - Math.PI * 2);
		}
		
		return difference;
	}
	
	public static float getSmoothAngleFromSensorData(final float oldAngle, final float[] gravity, final float[] geomag) {
		
		float newAngle = 0.0f;
		float smoothAngle = 0.0f;
		
		float[] orientVals = new float[3];
		float[] inR = new float[16];
		float[] I = new float[16];
		
		
		if (gravity != null && geomag != null) {
			boolean success = SensorManager.getRotationMatrix(inR, I, gravity, geomag);
		
			if (success) {				
				SensorManager.getOrientation(inR, orientVals);
				newAngle = GeometryToolBox.normalizeAngle(orientVals[0]);
				
				float minimumAngleChange = (float) Math.toRadians(2.0f);
				float smoothFactorCompass = 0.8f;
				float smoothThresholdCompass = (float) Math.toRadians(30.0f);
				float halfCirle = (float) Math.PI;
				float wholeCircle = (float) (2 * Math.PI);
								
				if (Math.abs(newAngle - oldAngle) < minimumAngleChange) {
					smoothAngle = oldAngle;
				} else  if (Math.abs(newAngle - oldAngle) < halfCirle) {
				    if (Math.abs(newAngle - oldAngle) > smoothThresholdCompass) {
				    	smoothAngle = newAngle;
				    } else {
				    	smoothAngle = oldAngle + smoothFactorCompass * (newAngle - oldAngle);
				    }
				} else {
				    if (wholeCircle - Math.abs(newAngle - oldAngle) > smoothThresholdCompass) {
				    	smoothAngle = newAngle;
				    } else {
				        if (oldAngle > newAngle) {
				        	smoothAngle = (oldAngle + smoothFactorCompass * ((wholeCircle + newAngle - oldAngle) % wholeCircle) + wholeCircle) % wholeCircle;
				        } else {
				        	smoothAngle = (oldAngle - smoothFactorCompass * ((wholeCircle - newAngle + oldAngle) % wholeCircle) + wholeCircle) % wholeCircle;
				        }
				    }
				}
			}
		}
		
		return smoothAngle;
	}
}
