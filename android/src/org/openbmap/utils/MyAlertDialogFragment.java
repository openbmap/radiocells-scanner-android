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

public class MyAlertDialogFragment extends DialogFragment {

	private static OnAlertClickInterface onClick;

	public static MyAlertDialogFragment newInstance(OnAlertClickInterface onClickInterface, int id, int title, int message, boolean neutralOnly) {
		onClick = onClickInterface;

		MyAlertDialogFragment frag = new MyAlertDialogFragment();
		// Caution: Don't set setRetainInstance(true) explicitly. This will cause the dialog to disappear
		// see http://stackoverflow.com/questions/11307390/dialogfragment-disappears-on-rotation-despite-setretaininstancetrue
		//frag.setRetainInstance(true);
		Bundle args = new Bundle();
		args.putInt("id", id);
		args.putInt("title", title);
		args.putInt("message", message);
		args.putBoolean("only_neutral", neutralOnly);
		frag.setArguments(args);
		return frag;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final int id = getArguments().getInt("id");
		int title = getArguments().getInt("title");
		int message = getArguments().getInt("message");
		boolean neutralOnly = getArguments().getBoolean("only_neutral");

		Builder dialog = new AlertDialog.Builder(getActivity())
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setTitle(title)
		.setMessage(message);


		if (neutralOnly) {
			dialog.setNeutralButton(android.R.string.ok, new OnClickListener() {
				@Override
				public void onClick(final DialogInterface alert, final int which) {
					onClick.onAlertNeutralClick(id);					
				}});
		} else {
			dialog.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					onClick.onAlertPositiveClick(id);
				}
			})
			.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					onClick.onAlertNegativeClick(id);
				}
			});
		}
		return dialog.create();
	}
}