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
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import org.openbmap.R;

/**
 * Helper class Alert Dialogs
 *
 */
public class AlertDialogUtils extends DialogFragment {

	/**
	 * Creates a new alert dialog
	 * @param id Alert dialog id
	 * @param title Alert title resource
	 * @param message Alert message resource
	 * @param args opional argument (e.g. session id)
	 * @param neutralOnly show only neutral button
	 * @return
	 */
	public static AlertDialogUtils newInstance(final int id, final String title, final String message, final String args, final boolean neutralOnly) {
		final AlertDialogUtils frag = new AlertDialogUtils();
		// Caution: Don't set setRetainInstance(true) explicitly. This will cause the dialog to disappear
		// see http://stackoverflow.com/questions/11307390/dialogfragment-disappears-on-rotation-despite-setretaininstancetrue
		//frag.setRetainInstance(true);
		final Bundle bundle = new Bundle();
		bundle.putInt("dialog_id", id);
		bundle.putString("title", title);
		bundle.putString("message", message);
		bundle.putString("args", args);
		bundle.putBoolean("only_neutral", neutralOnly);
		frag.setArguments(bundle);
		return frag;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState) {
		final int dialogId = getArguments().getInt("dialog_id");
		final String title = getArguments().getString("title");
		final String message = getArguments().getString("message");
		final String args = getArguments().getString("args");
		final boolean neutralOnly = getArguments().getBoolean("only_neutral");

		final Builder dialog = new AlertDialog.Builder(getActivity())
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
			dialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				public void onClick(final DialogInterface dialog, final int whichButton) {
					((OnAlertClickInterface)getActivity()).onAlertPositiveClick(dialogId, args);
				}
			})
			.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				public void onClick(final DialogInterface dialog, final int whichButton) {
					((OnAlertClickInterface)getActivity()).onAlertNegativeClick(dialogId, args);
				}
			});
		}
		return dialog.create();
	}
	
}