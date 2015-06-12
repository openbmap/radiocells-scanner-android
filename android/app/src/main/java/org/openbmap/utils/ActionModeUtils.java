/***
  Copyright (c) 2012 CommonsWare, LLC

  Licensed under the Apache License, Version 2.0 (the "License"); you may
  not use this file except in compliance with the License. You may obtain
  a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.openbmap.utils;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

public class ActionModeUtils implements Callback, AdapterView.OnItemLongClickListener {

	public interface LongClickCallback {
		 boolean onItemLongClick(int item, int position, int id);
	}
	
	private final ActionBarActivity host;
	private ActionMode activeMode;
	private final ListView modeView;
	private final LongClickCallback handler;
	private final int menuId;

	public ActionModeUtils(final ActionBarActivity fragmentActivity, final int menuId, final LongClickCallback handler, final ListView modeView) {
		this.host = fragmentActivity;
		this.handler = handler;
		this.modeView = modeView;
		this.menuId = menuId;
	}

	@Override
	public boolean onItemLongClick(final AdapterView<?> view, final View row,
			final int position, final long id) {
		modeView.clearChoices();
		modeView.setItemChecked(position, true);

		if (activeMode == null) {
			activeMode = ((ActionBarActivity) host).startSupportActionMode(this);
		}

		return(true);
	}

	@Override
	public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
		final MenuInflater inflater = host.getMenuInflater();

		inflater.inflate(menuId, menu);
		//mode.setTitle(/*R.string.context_title*/ "Options");

		return(true);
	}

	@Override
	public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
		return(false);
	}

	@Override
	public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
		
		final int id = (modeView.getCheckedItemIds().length > 0 ? (int) modeView.getCheckedItemIds()[0] : -1);
		final boolean result = (handler.onItemLongClick(item.getItemId(),
						modeView.getCheckedItemPosition(), id));

		if (result) {
			activeMode.finish();
		}

		return(result);
	}

	@Override
	public void onDestroyActionMode(final ActionMode mode) {
		activeMode = null;
		modeView.clearChoices();
		modeView.requestLayout();
	}
}