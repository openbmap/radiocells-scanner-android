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

import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class ActionModeUtils implements ActionMode.Callback, AdapterView.OnItemLongClickListener {
	
	public interface LongClickCallback {
		 boolean onItemLongClick(int item, int position, int id);
	}
	
	private SherlockFragmentActivity host;
	private ActionMode activeMode;
	private ListView modeView;
	private LongClickCallback handler;
	private int menuId;

	public ActionModeUtils(final SherlockFragmentActivity fragmentActivity, final int menuId, final LongClickCallback handler, final ListView modeView) {
		this.host = fragmentActivity;
		this.handler = handler;
		this.modeView = modeView;
		this.menuId = menuId;
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> view, View row,
			int position, long id) {
		modeView.clearChoices();
		modeView.setItemChecked(position, true);

		if (activeMode == null) {
			activeMode = host.startActionMode(this);
		}

		return(true);
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		MenuInflater inflater = host.getSupportMenuInflater();
		
		inflater.inflate(menuId, menu);
		//mode.setTitle(/*R.string.context_title*/ "Options");
		
		return(true);
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return(false);
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		
		int id = (modeView.getCheckedItemIds().length > 0 ? (int) modeView.getCheckedItemIds()[0] : -1);
		boolean result = (handler.onItemLongClick(item.getItemId(),
						modeView.getCheckedItemPosition(), id));

		if (result) {
			activeMode.finish();
		}

		return(result);
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		activeMode = null;
		modeView.clearChoices();
		modeView.requestLayout();
	}
}