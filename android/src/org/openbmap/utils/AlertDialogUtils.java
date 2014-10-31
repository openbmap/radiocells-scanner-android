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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockDialogFragment;

/**
 * Helper class Alert Dialogs
 *
 */
public class AlertDialogUtils extends SherlockDialogFragment{

	/**
	 * Creates a new alert dialog
	 * @param id Alert dialog id
	 * @param title Alert title resource
	 * @param message Alert message resource
	 * @param args opional argument (e.g. session id)
	 * @param neutralOnly show only neutral button
	 * @return
	 */
	public static AlertDialogUtils newInstance(int id, String title, String message, String args, boolean neutralOnly) {
		AlertDialogUtils frag = new AlertDialogUtils();
		// Caution: Don't set setRetainInstance(true) explicitly. This will cause the dialog to disappear
		// see http://stackoverflow.com/questions/11307390/dialogfragment-disappears-on-rotation-despite-setretaininstancetrue
		//frag.setRetainInstance(true);
		Bundle bundle = new Bundle();
		bundle.putInt("dialog_id", id);
		bundle.putString("title", title);
		bundle.putString("message", message);
		bundle.putString("args", args);
		bundle.putBoolean("only_neutral", neutralOnly);
		frag.setArguments(bundle);
		return frag;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final int dialogId = getArguments().getInt("dialog_id");
		final String title = getArguments().getString("title");
		final String message = getArguments().getString("message");
		final String args = getArguments().getString("args");
		final boolean neutralOnly = getArguments().getBoolean("only_neutral");

		Builder dialog = new AlertDialog.Builder(getActivity())
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setTitle(title)
		.setMessage(message)
		.setCancelable(false);

		if (neutralOnly) {
			dialog.setNeutralButton(android.R.string.ok, new OnClickListener() {
				@Override
				public void onClick(final DialogInterface alert, final int which) {
					((OnAlertClickInterface)getActivity()).onAlertNeutralClick(dialogId, args);					
				}});
		} else {
			dialog.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					((OnAlertClickInterface)getActivity()).onAlertPositiveClick(dialogId, args);
				}
			})
			.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					((OnAlertClickInterface)getActivity()).onAlertNegativeClick(dialogId, args);
				}
			});
		}
		return dialog.create();
	}
	
}