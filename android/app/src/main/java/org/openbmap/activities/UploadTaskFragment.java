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

package org.openbmap.activities;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;

import org.openbmap.Preferences;
import org.openbmap.R;
import org.openbmap.Radiobeacon;
import org.openbmap.soapclient.ExportSessionTask;
import org.openbmap.soapclient.ExportSessionTask.UploadTaskListener;
import org.openbmap.soapclient.CheckServerTask;
import org.openbmap.soapclient.CheckServerTask.ServerAnswer;
import org.openbmap.soapclient.CheckServerTask.ServerCheckerListener;
import org.openbmap.utils.FileUtils;

import java.io.File;
import java.util.Vector;

/**
 * Fragment manages export background tasks and retains itself across configuration changes.
 */
public class UploadTaskFragment extends Fragment implements UploadTaskListener, ServerCheckerListener {

    private static final String TAG = UploadTaskFragment.class.getSimpleName();
    private boolean mBadPasswordFlag;

    private enum CheckResult {UNKNOWN, FAILED, PASSED}

    private CheckResult sdCardWritable = CheckResult.UNKNOWN;
    private CheckResult serverReply = CheckResult.UNKNOWN;

    private final Vector<Integer> toExport = new Vector<Integer>();
    private ExportSessionTask mExportDataTask;

    private String mTitle;
    private String mMessage;
    private int mProgress;
    private boolean mIsExecuting = false;

    /**
     * This method will only be called once when the retained
     * Fragment is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retain this fragment across configuration changes.
        setRetainInstance(true);
    }

    /**
     * Saves progress dialog state for later restore (e.g. on device rotation)
     * @param title
     * @param message
     * @param progress
     */
    public void retainProgress(final String title, final String message, final int progress) {
        mTitle = title;
        mMessage = message;
        mProgress = progress;
    }

    /**
     * Restores previously retained progress dialog state
     *
     * @param progressDialog
     */
    public void restoreProgress(final ProgressDialog progressDialog) {
        progressDialog.setTitle(mTitle);
        progressDialog.setMessage(mMessage);
        progressDialog.setProgress(mProgress);
    }


    /**
     * Returns number of sessions to export
     * @return # sessions
     */
    public int size() {
        return toExport.size();
    }

    /**
     * Adds a sessions to the upload queue
     * @param id session id
     */
    public void add(final int id) {
        toExport.add(id);
    }

    /**
     * Starts export after all checks have been passed
     */
    public void execute() {
        mIsExecuting = true;
        stageVersionCheck();
    }

    /**
     * Exports while sessions on the list
     */
    private void looper() {
        Log.i(TAG, "Looping over pending exports");
        if (toExport.size() > 0) {
            Log.i(TAG, "Will process export " + toExport.get(0));
            process(toExport.get(0));
        } else {
            Log.i(TAG, "No more pending exports left. Finishing");
            mIsExecuting = false;
        }
    }

    public boolean isExecuting() {
        return this.mIsExecuting;
    }

    /**
     * Does the actual work
     *
     * @param session
     */

    private void process(final int session) {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final boolean anonymousUpload = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(Preferences.KEY_ANONYMOUS_UPLOAD, false);

        String user = null;
        String password = null;
        if (!anonymousUpload) {
            user = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(Preferences.KEY_CREDENTIALS_USER, null);
            password = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);
        }

        final String targetPath = getActivity().getExternalFilesDir(null).getAbsolutePath() + File.separator;
        final boolean skipUpload = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(Preferences.KEY_SKIP_UPLOAD, Preferences.VAL_SKIP_UPLOAD);
        final boolean skipDelete = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(Preferences.KEY_KEEP_XML, Preferences.VAL_KEEP_XML);
        final boolean anonymiseSsid = prefs.getBoolean(Preferences.KEY_ANONYMISE_SSID, Preferences.VAL_ANONYMISE_SSID);
        final boolean saveGpx = prefs.getBoolean(Preferences.KEY_SAVE_GPX, Preferences.VAL_SAVE_GPX);

        mExportDataTask = new ExportSessionTask(getActivity(), this, session, targetPath, user, password, anonymousUpload);

        // set extras
        mExportDataTask.setExportCells(true);
        mExportDataTask.setExportWifis(true);
        mExportDataTask.setAnonymiseSsid(anonymiseSsid);
        mExportDataTask.setSaveGpx(saveGpx);
        // currently deactivated to prevent crashes
        mExportDataTask.setUpdateWifiCatalog(false);

        // debug settings
        mExportDataTask.setSkipUpload(skipUpload);
        mExportDataTask.setKeepXml(skipDelete);

        mExportDataTask.execute((Void[]) null);
    }

    /**
     * Sanity check - first round: validate local version vs. server
     */
    private void stageVersionCheck() {
        if (serverReply == CheckResult.UNKNOWN) {
            // will call {@Link ServerCheckerListener} on completion
            final String user = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(Preferences.KEY_CREDENTIALS_USER, null);
            final String password = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(Preferences.KEY_CREDENTIALS_PASSWORD, null);

            final String[] params = { Radiobeacon.VERSION_COMPATIBILITY, user, password};
            new CheckServerTask(getActivity(), this).execute(params);
        } else if (serverReply == CheckResult.PASSED) {
            stageLocalChecks();
        }
    }

    /**
     * Sanity check - second round: check whether local device is ready
     */
    private void stageLocalChecks() {
        if (sdCardWritable == CheckResult.UNKNOWN) {
            sdCardWritable = (FileUtils.isSdCardWritable() ? CheckResult.PASSED : CheckResult.FAILED);
        }
        summarizeChecks();
    }

    /**
     * Evaluates whether sanity checks (first + second round) are good, before starting export
     */
    private void summarizeChecks() {
        // so now it's time to decide whether we start or not..
        if (serverReply == CheckResult.PASSED && sdCardWritable == CheckResult.PASSED) {
            looper();
        } else if (serverReply == CheckResult.FAILED) {
            // version is outdated or wrong credentials
            if (mBadPasswordFlag) {
                final int id = toExport.size() > 0 ? toExport.get(0) : Radiobeacon.SESSION_NOT_TRACKING;
                onUploadFailed(id, getResources().getString(R.string.warning_bad_password));
            } else {
                final int id = toExport.size() > 0 ? toExport.get(0) : Radiobeacon.SESSION_NOT_TRACKING;
                onUploadFailed(id, getResources().getString(R.string.warning_outdated_client));
            }
        } else if (serverReply == CheckResult.UNKNOWN) {
            // couldn't verify online version
            final int id = toExport.size() > 0 ? toExport.get(0) : Radiobeacon.SESSION_NOT_TRACKING;
            onUploadFailed(id, getResources().getString(R.string.warning_client_version_not_checked));
        } else if (sdCardWritable == CheckResult.FAILED) {
            final int id = toExport.size() > 0 ? toExport.get(0) : Radiobeacon.SESSION_NOT_TRACKING;
            onUploadFailed(id, getResources().getString(R.string.warning_sd_not_writable));
        } else {
            final int id = toExport.size() > 0 ? toExport.get(0) : Radiobeacon.SESSION_NOT_TRACKING;
            onUploadFailed(id, "Unknown error");
        }

    }

    /* (non-Javadoc)
     * @see org.openbmap.soapclient.ServerValidation.ServerReply#onServerAllowsUpload()
     */
    @Override
    public void onServerAllowsUpload() {
        serverReply = CheckResult.PASSED;
        stageLocalChecks();
    }

    /* (non-Javadoc)
     * @see org.openbmap.soapclient.ServerValidation.ServerReply#onServerDeclinesUpload(java.lang.String)
     */
    @Override
    public void onServerDeclinesUpload(final ServerAnswer code, final String description) {
        Log.e(TAG, description);
        serverReply = CheckResult.FAILED;
        if (code == ServerAnswer.BAD_PASSWORD) {
            mBadPasswordFlag = true;
        }
        // skip local checks, server can't be reached anyways
        summarizeChecks();
    }

    /* (non-Javadoc)
     * @see org.openbmap.soapclient.ServerValidation.ServerReply#onServerCheckFailed()
     */
    @Override
    public void onServerCheckFailed() {
        serverReply = CheckResult.UNKNOWN;
        // skip local checks, server can't be reached anyways
        summarizeChecks();
    }

    /* (non-Javadoc)
     * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onExportCompleted(int)
     */
    @Override
    public void onUploadCompleted(final int id) {
        // forward to activity
        ((UploadTaskListener) getActivity()).onUploadCompleted(id);

        Log.i(TAG, "Export completed. Processing next");
        toExport.remove(Integer.valueOf(id));
        looper();
    }

    @Override
    public void onDryRunCompleted(final int id) {
        // forward to activity
        ((UploadTaskListener) getActivity()).onDryRunCompleted(id);

        Log.i(TAG, "Export simulated. Processing next");
        toExport.remove(Integer.valueOf(id));
        looper();
    }

    /* (non-Javadoc)
     * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onExportFailed(java.lang.String)
     */
    @Override
    public void onUploadFailed(final int id, final String error) {
        // forward to activity
        ((UploadTaskListener) getActivity()).onUploadFailed(id, error);

        Log.e(TAG, "Error exporting session " + id + ": " + error);
        toExport.remove(Integer.valueOf(id));
        looper();
    }

    /* (non-Javadoc)
     * @see org.openbmap.soapclient.ExportSessionTask.ExportTaskListener#onProgressUpdate(java.lang.Object[])
     */
    @Override
    public void onUploadProgressUpdate(final Object... values) {
        // forward to activity
        ((UploadTaskListener) getActivity()).onUploadProgressUpdate(values);
    }
}
