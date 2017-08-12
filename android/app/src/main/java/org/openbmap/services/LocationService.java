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

package org.openbmap.services;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.Constants;
import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.events.onLocationStart;
import org.openbmap.events.onLocationStop;
import org.openbmap.events.onLocationUpdated;
import org.openbmap.events.onSatInfo;
import org.openbmap.utils.PermissionHelper;

/**
 * GPS position service.
 */
public class LocationService extends Service implements GpsStatus.Listener, LocationListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = LocationService.class.getSimpleName();

    // flag for GPS status
    boolean isGPSEnabled = false;

    // flag for network status
    boolean isNetworkEnabled = false;

    // last known location
    Location lastLocation;

    // time when last update was received (system millis)
    private long lastLocationAt;

    private int lastSatCount = -1;

    protected LocationManager lm;

    private long minTimeBetweenUpdates;

    private boolean isTracking;

    private long lastTimestamp;

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public final int onStartCommand(final Intent intent, final int flags, final int startId) {
        return START_STICKY; // run until explicitly stopped.
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        } else {
            Log.w(TAG, "Event bus receiver already registered");
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            PermissionHelper.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    Constants.PERM_REQUEST_LOCATION_NOTIFICATION,
                    this.getApplicationContext().getString(R.string.permission_setup_title),
                    this.getApplicationContext().getString(R.string.permission_setup_location_explanation),
                    R.drawable.ic_security);
        }

        minTimeBetweenUpdates = Long.parseLong(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(
                Preferences.KEY_GPS_LOGGING_INTERVAL,
                Preferences.DEFAULT_GPS_LOGGING_INTERVAL)) * 1000;
    }

    @Override
    public final void onLocationChanged(final Location location) {
        if (isTracking) {
            if ((lastTimestamp + minTimeBetweenUpdates) < System.currentTimeMillis()) {
                EventBus.getDefault().post(new onLocationUpdated(location));
                EventBus.getDefault().post(new onSatInfo(location, "UPDATE", lastSatCount));
                lastTimestamp = System.currentTimeMillis();
                lastLocation = location;
            }
        }
    }

    @Override
    public void onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        super.onDestroy();
        Log.i(TAG, "LocationService stopped");
    }

    @Subscribe
    public void onEvent(onLocationStart event) {
        Log.d(TAG, "ACK StartLocationEvent event");
        requestUpdates();
    }

    @Subscribe
    public void onEvent(onLocationStop event) {
        Log.d(TAG, "ACK StopLocationEvent event");
        removeUpdates();
    }

    public Location requestUpdates() {
        try {
            lm = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
            isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!isGPSEnabled /*&& !isNetworkEnabled*/) {
                // no network provider is enabled
                Log.w(TAG, "GPS is disabled!");
            } else {
                // First get location from Network Provider
                if (isNetworkEnabled) {
                    try {
                        Log.i(TAG, "Trying to obtain location quick-fix (networks)");
                        lm.requestLocationUpdates(
                                LocationManager.NETWORK_PROVIDER,
                                minTimeBetweenUpdates,
                                0, this);
                        if (lm != null) {
                            lastLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                            if (lastLocation != null) {
                                EventBus.getDefault().post(new onSatInfo(lastLocation, "OUT_OF_SERVICE", -1));
                            }
                        }
                    } catch (SecurityException e) {
                        Log.w(TAG, "Location permission denied - won't receive locations");
                        isTracking = false;
                    }
                }

                if (isGPSEnabled) {
                    try {
                        if (lm != null) {
                            lm.requestLocationUpdates(
                                    LocationManager.GPS_PROVIDER,
                                    minTimeBetweenUpdates,
                                    0, this);
                            isTracking = true;
                            lastLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        }
                    } catch (SecurityException e) {
                        Log.w(TAG, "Location permission denied - won't receive locations");
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return lastLocation;
    }

    /**
     * Stop using GPS listener
     */
    public void removeUpdates() {
        if (lm != null) {
            lm.removeUpdates(LocationService.this);
        }
        isTracking = false;
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        switch (status) {
            // Don't do anything for status AVAILABLE, as this event occurs frequently,
            // changing the graphics cause flickering .
            case android.location.LocationProvider.OUT_OF_SERVICE:
                EventBus.getDefault().post(new onSatInfo(null, "OUT_OF_SERVICE", -1));
                break;
            case android.location.LocationProvider.TEMPORARILY_UNAVAILABLE:
                EventBus.getDefault().post(new onSatInfo(null, "TEMPORARILY_UNAVAILABLE", -1));
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        for (int i = 0; i < grantResults.length; i++)
            if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) && (grantResults[i] == PackageManager.PERMISSION_GRANTED)) {
                Log.i(TAG, "Location permission was granted");
                if (isTracking) {
                    try {
                        // tracking request was sent previously, but failed due to missing permissions
                        // restart now..
                        requestUpdates();
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting tracking");
                    }
                }
            }
    }

    @Override
    public void onGpsStatusChanged(final int event) {
        lastSatCount = -1;
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission denied - can't access GPS status");
            return;
        }
        switch (event) {
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                lastSatCount = 0;
                break;
            case GpsStatus.GPS_EVENT_STARTED:
                lastSatCount = -1;
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
                lastSatCount = -1;
                break;
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                // limit updates
                if ((lastTimestamp + minTimeBetweenUpdates) < System.currentTimeMillis()) {
                    lastTimestamp = System.currentTimeMillis(); // save the time of this fix

                    final GpsStatus status = lm.getGpsStatus(null);
                    // Count active satellites
                    lastSatCount = 0;
                    for (@SuppressWarnings("unused") final GpsSatellite sat : status.getSatellites()) {
                        lastSatCount++;
                    }
                }
                break;
            default:
                break;
        }
    }
}