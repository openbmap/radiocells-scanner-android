package org.openbmap.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;

import com.fastaccess.permission.base.activity.BasePermissionActivity;
import com.fastaccess.permission.base.model.PermissionModel;
import com.fastaccess.permission.base.model.PermissionModelBuilder;

import org.openbmap.R;

import java.util.ArrayList;
import java.util.List;

public class IntroActivity extends BasePermissionActivity {

    private static final String TAG = IntroActivity.class.getSimpleName();

    private boolean isPermissionGranted(String permission) {
        return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    @Override
    protected List<PermissionModel> permissions() {

        List<PermissionModel> permissions = new ArrayList<>();
        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissions.add(PermissionModelBuilder.withContext(this)
                    .withCanSkip(false)
                    .withPermissionName(Manifest.permission.ACCESS_FINE_LOCATION)
                    .withTitle(R.string.location_permission_required)
                    .withMessage(R.string.location_permission_explanation)
                    //.withExplanationMessage("We need this permission to save your captured images and videos to your SD-Card")
                    .build());
        }
        if (!isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) {
            permissions.add(PermissionModelBuilder.withContext(this)
                    .withCanSkip(false)
                    .withPermissionName(Manifest.permission.READ_PHONE_STATE)
                    .withTitle(R.string.phone_state_permission_required)
                    .withMessage(R.string.phone_state_permission_explanation)
                    //.withExplanationMessage("We need this permission to collect cell tower information")
                    .build());
        }
        if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            permissions.add(PermissionModelBuilder.withContext(this)
                    .withCanSkip(false)
                    .withPermissionName(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .withTitle(R.string.external_storage_permission_required)
                    .withMessage(R.string.external_storage_permission_explanation)
                    //.withExplanationMessage("We need this permission to store map files on your SD card")
                    .build());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isPermissionGranted(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
                permissions.add(PermissionModelBuilder.withContext(this)
                        .withCanSkip(false)
                        .withPermissionName(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .withTitle(R.string.ignore_battery_permission)
                        .withMessage(R.string.ignore_battery_explanation)
                        //.withExplanationMessage("We need this permission to store map files on your SD card")
                        .build());
            }
        }

        return permissions;
    }

    @Override
    protected int theme() {
        return 0;
    }

    @Override
    protected void onIntroFinished() {
        // bring up UI
        final Intent mainActivity = new Intent(this, StartscreenActivity.class);
        mainActivity.putExtra("new_session", true);
        startActivity(mainActivity);
        this.finish();
    }

    /**
     * Ignore permission check on Android <M
     */
    @Override
    public void onNoPermissionNeeded() {
        final Intent mainActivity = new Intent(this, StartscreenActivity.class);
        mainActivity.putExtra("new_session", true);
        startActivity(mainActivity);
    }

    @Nullable
    @Override
    protected ViewPager.PageTransformer pagerTransformer() {
        return null;
    }

    @Override
    protected boolean backPressIsEnabled() {
        return false;
    }

    @Override
    protected void permissionIsPermanentlyDenied(@NonNull String permissionName) {

    }

    @Override
    protected void onUserDeclinePermission(@NonNull String permissionName) {

    }

}
