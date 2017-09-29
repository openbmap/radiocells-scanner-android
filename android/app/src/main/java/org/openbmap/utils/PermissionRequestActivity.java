package org.openbmap.utils;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;

/**
 * A blank {@link Activity} on top of which permission request dialogs can be displayed
 */
public class PermissionRequestActivity extends AppCompatActivity {
    ResultReceiver resultReceiver;
    String[] permissions;
    int requestCode;

    /**
     * Called when the user has made a choice in the permission dialog.
     * <p>
     * This method wraps the responses in a {@link Bundle} and passes it to the {@link ResultReceiver}
     * specified in the {@link Intent} that started the activity, then closes the activity.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Bundle resultData = new Bundle();
        resultData.putStringArray("permissions", permissions);
        resultData.putIntArray("grantResults", grantResults);
        resultReceiver.send(requestCode, resultData);
        finish();
    }


    /**
     * Called when the activity is started.
     * <p>
     * This method obtains several extras from the {@link Intent} that started the activity: the request
     * code, the requested permissions and the {@link ResultReceiver} which will receive the results.
     * After that, it issues the permission request.
     */
    @Override
    protected void onStart() {
        super.onStart();

        resultReceiver = this.getIntent().getParcelableExtra("resultReceiver");
        permissions = this.getIntent().getStringArrayExtra("permissions");
        requestCode = this.getIntent().getIntExtra("requestCode", 0);

        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }
}
