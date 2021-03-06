/*
 * Copyright (C) 2010 Garen J. Torikian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.avabook.shelves.activity.gadgets;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.avabook.shelves.R;
import net.avabook.shelves.activity.SettingsActivity;
import net.avabook.shelves.base.AddBaseItemActivity;
import net.avabook.shelves.base.BaseItem;
import net.avabook.shelves.drawable.FastBitmapDrawable;
import net.avabook.shelves.provider.gadgets.GadgetsManager;
import net.avabook.shelves.provider.gadgets.GadgetsStore;
import net.avabook.shelves.provider.gadgets.GadgetsStore.Gadget;
import net.avabook.shelves.util.ImageUtilities;
import net.avabook.shelves.util.TextUtilities;
import net.avabook.shelves.util.UIUtilities;

public class AddGadgetActivity extends AddBaseItemActivity implements
		AdapterView.OnItemClickListener {

	private static final String STATE_ADD_GADGET = "shelves.add.gadget";
	private static final String STATE_SEARCH_QUERY = "shelves.search.gadget";
	private static final String STATE_GADGET_TO_ADD = "shelves.add.gadgetToAdd";

	private SearchTask mSearchTask;
	private AddTask mAddTask;

	private SearchResultsAdapter mGadgetsAdapter;
	private GadgetsStore.Gadget mGadgetToAdd;

	static void show(Context context) {
		final Intent intent = new Intent(context, AddGadgetActivity.class);
		context.startActivity(intent);
	}

	@Override
	protected void setupViews() {
		super.setupViews();

		mSearchQuery.setHint(R.string.search_add_hint_gadget);

		mSearchButton = findViewById(R.id.button_go);
		mSearchButton.setOnClickListener(this);
		mSearchButton.setEnabled(false);

		final FastBitmapDrawable cover = new FastBitmapDrawable(
				ImageUtilities.createShadow(BitmapFactory.decodeResource(
						getResources(), R.drawable.unknown_cover_no_shadow),
						COVER_WIDTH, COVER_HEIGHT));

		mGadgetsAdapter = new SearchResultsAdapter(this, cover);

		final SearchResultsAdapter resultsAdapter = mGadgetsAdapter;
		final SearchResultsAdapter oldAdapter = (SearchResultsAdapter) getLastNonConfigurationInstance();

		if (oldAdapter != null) {
			final int count = oldAdapter.getCount();
			for (int i = 0; i < count; i++) {
				resultsAdapter.add(oldAdapter.getItem(i));
			}
		}

		final ListView searchResults = (ListView) findViewById(R.id.list_search_results);
		searchResults.setAdapter(resultsAdapter);
		searchResults.setOnItemClickListener(this);
		registerForContextMenu(searchResults);
	}

	@Override
	protected void getNextResults() {
		mPage++;
		mSearchTask = (SearchTask) new SearchTask(false).execute(mSearchQuery
				.getText().toString());
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		onCancelAdd();
		onCancelSearch();
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		restoreGadgetToAdd(savedInstanceState);
		restoreAddTask(savedInstanceState);
		restoreSearchTask(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (isFinishing()) {
			saveGadgetToAdd(outState);
			saveAddTask(outState);
			saveSearchTask(outState);
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return mGadgetsAdapter;
	}

	private void saveGadgetToAdd(Bundle outState) {
		if (mGadgetToAdd != null) {
			outState.putParcelable(STATE_GADGET_TO_ADD, mGadgetToAdd);
		}
	}

	private void restoreGadgetToAdd(Bundle savedInstanceState) {
		final Object data = savedInstanceState.get(STATE_GADGET_TO_ADD);
		if (data != null) {
			mGadgetToAdd = (GadgetsStore.Gadget) data;
		}
	}

	private void saveAddTask(Bundle outState) {
		final AddTask task = mAddTask;
		if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
			final String gadgetId = task.getGadgetId();
			task.cancel(true);

			if (gadgetId != null) {
				outState.putBoolean(STATE_ADD_IN_PROGRESS, true);
				outState.putString(STATE_ADD_GADGET, gadgetId);
			}

			mAddTask = null;
		}
	}

	private void restoreAddTask(Bundle savedInstanceState) {
		if (savedInstanceState.getBoolean(STATE_ADD_IN_PROGRESS)) {
			final String id = savedInstanceState.getString(STATE_ADD_GADGET);
			if (!GadgetsManager.gadgetExists(getContentResolver(), id,
					SettingsActivity.getSortOrder(this), null)) {
				mAddTask = (AddTask) new AddTask().execute(id);
			}
		}
	}

	private void saveSearchTask(Bundle outState) {
		final SearchTask task = mSearchTask;
		if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
			final String gadgetId = task.getQuery();
			task.cancel(true);

			if (gadgetId != null) {
				outState.putBoolean(STATE_SEARCH_IN_PROGRESS, true);
				outState.putString(STATE_SEARCH_QUERY, gadgetId);
			}

			mSearchTask = null;
		}
	}

	private void restoreSearchTask(Bundle savedInstanceState) {
		if (savedInstanceState.getBoolean(STATE_SEARCH_IN_PROGRESS)) {
			final String query = savedInstanceState
					.getString(STATE_SEARCH_QUERY);
			if (!TextUtilities.isEmpty(query)) {
				mSearchTask = (SearchTask) new SearchTask(false).execute(query);
			}
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		mGadgetToAdd = mGadgetsAdapter.getItem(position).gadget;
		showDialog(DIALOG_ADD);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_ADD:
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(mGadgetToAdd != null ? mGadgetToAdd.getTitle()
					: " ");

			builder.setMessage(R.string.dialog_add_message);
			builder.setPositiveButton(R.string.add_label,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							if (mGadgetToAdd != null) {
								onAdd(mGadgetToAdd.getInternalIdNoPrefix());
								mGadgetToAdd = null;
							}
						}
					});
			builder.setNegativeButton(R.string.dialog_add_cancel,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							mGadgetToAdd = null;
							dismissDialog(DIALOG_ADD);
						}
					});
			builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					mGadgetToAdd = null;
					dismissDialog(DIALOG_ADD);
				}
			});
			builder.setCancelable(true);

			return builder.create();
		}

		return super.onCreateDialog(id);
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);

		switch (id) {
		case DIALOG_ADD:
			if (mGadgetToAdd != null && dialog != null) {
				dialog.setTitle(mGadgetToAdd.getTitle());
			}
			break;
		}
	}

	@Override
	protected void onSearch(boolean b) {
		if (mSearchTask == null
				|| mSearchTask.getStatus() == SearchTask.Status.FINISHED) {
			mSearchTask = (SearchTask) new SearchTask(b).execute(mSearchQuery
					.getText().toString());
		} else {
			UIUtilities.showToast(this, R.string.error_search_in_progress);
		}
	}

	private void onCancelSearch() {
		if (mSearchTask != null
				&& mSearchTask.getStatus() == AsyncTask.Status.RUNNING) {
			mSearchTask.cancel(true);
			mSearchTask = null;
		}
	}

	private void onAdd(String id) {
		if (!GadgetsManager.gadgetExists(getApplicationContext()
				.getContentResolver(), id, SettingsActivity.getSortOrder(this),
				null)) {
			mAddTask = (AddTask) new AddTask().execute(id);
		} else {
			UIUtilities.showToast(
					this,
					getString(R.string.error_item_exists,
							getString(R.string.gadget_label)));
		}
	}

	private void onCancelAdd() {
		if (mAddTask != null
				&& mAddTask.getStatus() == AsyncTask.Status.RUNNING) {
			mAddTask.cancel(true);
			mAddTask = null;
		}
	}

	private class AddTask extends AsyncTask<String, Void, GadgetsStore.Gadget> {
		private final Object mLock = new Object();
		private String mGadgetId;
		private FastBitmapDrawable mDefaultCover;

		@Override
		public void onPreExecute() {
			final Bitmap defaultCoverBitmap = BitmapFactory.decodeResource(
					getResources(), R.drawable.unknown_cover);
			mDefaultCover = new FastBitmapDrawable(defaultCoverBitmap);

			if (mAddPanel == null) {
				mAddPanel = ((ViewStub) findViewById(R.id.stub_add)).inflate();
				((ProgressBar) mAddPanel.findViewById(R.id.progress))
						.setIndeterminate(true);

				((TextView) findViewById(R.id.label_import))
						.setText(R.string.adding_label);

				final View cancelButton = mAddPanel
						.findViewById(R.id.button_cancel);
				cancelButton.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						onCancelAdd();
					}
				});
			}

			disableSearchPanel();
			showPanel(mAddPanel, false);
		}

		String getGadgetId() {
			synchronized (mLock) {
				return mGadgetId;
			}
		}

		@Override
		public GadgetsStore.Gadget doInBackground(String... params) {
			synchronized (mLock) {
				mGadgetId = params[0];
			}
			return GadgetsManager
					.loadAndAddGadget(getContentResolver(), mGadgetId,
							new GadgetsStore(), null, AddGadgetActivity.this);
		}

		@Override
		public void onCancelled() {
			enableSearchPanel();
			hidePanel(mAddPanel, false);
		}

		@Override
		public void onPostExecute(GadgetsStore.Gadget gadget) {
			enableSearchPanel();
			if (gadget == null) {
				UIUtilities.showToast(
						AddGadgetActivity.this,
						getString(R.string.error_adding_item,
								getString(R.string.gadget_label)));
			} else {
				UIUtilities.showFormattedImageToast(AddGadgetActivity.this,
						R.string.success_added, ImageUtilities.getCachedCover(
								gadget.getInternalId(), mDefaultCover), gadget
								.getTitle());
			}
			hidePanel(mAddPanel, false);
		}
	}

	private class SearchTask extends AsyncTask<String, ResultGadget, Void>
			implements GadgetsStore.GadgetSearchListener {

		private final Object mLock = new Object();
		private String mQuery;
		private boolean clearResults;

		public SearchTask(boolean clear) {
			clearResults = clear;
		}

		@Override
		public void onPreExecute() {
			disableSearchPanel();

			if (mSearchPanel == null) {
				mSearchPanel = ((ViewStub) findViewById(R.id.stub_search))
						.inflate();

				ProgressBar progress = (ProgressBar) mSearchPanel
						.findViewById(R.id.progress);
				progress.setIndeterminate(true);

				((TextView) findViewById(R.id.label_import))
						.setText(R.string.search_progress);

				final View cancelButton = mSearchPanel
						.findViewById(R.id.button_cancel);
				cancelButton.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						onCancelSearch();
					}
				});
			}

			if (clearResults)
				mGadgetsAdapter.clear();
			showPanel(mSearchPanel, true);
		}

		String getQuery() {
			synchronized (mLock) {
				return mQuery;
			}
		}

		@Override
		public Void doInBackground(String... params) {
			synchronized (mLock) {
				mQuery = params[0];
			}
			new GadgetsStore().searchGadgets(mQuery, String.valueOf(mPage),
					this, AddGadgetActivity.this);

			return null;
		}

		@Override
		public void onProgressUpdate(ResultGadget... values) {
			for (ResultGadget gadget : values) {
				mGadgetsAdapter.add(gadget);
			}
		}

		@Override
		public void onPostExecute(Void ignore) {
			enableSearchPanel();

			UIUtilities.showToast(
					AddGadgetActivity.this,
					getString(R.string.success_item_found,
							mGadgetsAdapter.getCount(),
							getString(R.string.gadget_label_plural_small)));
			hidePanel(mSearchPanel, true);
		}

		@Override
		public void onCancelled() {
			enableSearchPanel();

			hidePanel(mSearchPanel, true);
		}

		public void onGadgetFound(GadgetsStore.Gadget gadget,
				ArrayList<GadgetsStore.Gadget> gadgets) {
			if (gadget != null && !isCancelled()) {
				publishProgress(new ResultGadget(gadget));
			}
		}
	}

	private static class SearchResultsAdapter extends
			ArrayAdapter<ResultGadget> {
		private final LayoutInflater mLayoutInflater;
		private final FastBitmapDrawable mDefaultCover;

		SearchResultsAdapter(AddGadgetActivity addGadgetActivity,
				FastBitmapDrawable cover) {
			super(addGadgetActivity, 0);
			mDefaultCover = cover;
			mLayoutInflater = LayoutInflater.from(addGadgetActivity);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = mLayoutInflater.inflate(R.layout.search_results,
						parent, false);

				holder = new ViewHolder();
				holder.cover = (ImageView) convertView
						.findViewById(R.id.image_cover);
				holder.title = (TextView) convertView
						.findViewById(R.id.label_title);
				holder.author = (TextView) convertView
						.findViewById(R.id.label_author);

				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			final ResultGadget gadget = getItem(position);
			holder.gadget = gadget.gadget;
			holder.title.setText(gadget.title);
			holder.author.setText(gadget.authors);

			final boolean hasCover = gadget.cover != null;
			holder.cover.setImageDrawable(hasCover ? gadget.cover
					: mDefaultCover);

			return convertView;
		}
	}

	private static class ViewHolder {
		ImageView cover;
		TextView title;
		TextView author;
		GadgetsStore.Gadget gadget;
	}

	private static class ResultGadget {
		final GadgetsStore.Gadget gadget;
		final String text;
		final String title;
		final String authors;

		final FastBitmapDrawable cover;

		ResultGadget(GadgetsStore.Gadget gadget) {
			this.gadget = gadget;
			Bitmap bitmap = ImageUtilities.createShadow(
					gadget.loadCover(BaseItem.ImageSize.THUMBNAIL),
					COVER_WIDTH, COVER_HEIGHT);
			if (bitmap != null) {
				cover = new FastBitmapDrawable(bitmap);
			} else {
				cover = null;
			}

			title = gadget.getTitle();
			authors = gadget.getAuthors();
			text = title + ' ' + authors;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	// GJT: Added these, for showing gadget descriptions before add
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		menu.setHeaderTitle(mGadgetsAdapter.getItem((int) info.id).gadget
				.getTitle());

		getMenuInflater().inflate(R.menu.add_item, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();

		switch (item.getItemId()) {
		case R.id.context_menu_add_details:
			onShowDetails(mGadgetsAdapter.getItem((int) info.id).gadget);
			return true;
		case R.id.context_menu_item_buy:
			onBuy(mGadgetsAdapter.getItem((int) info.id).gadget);
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	public void onShowDetails(GadgetsStore.Gadget gadget) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(TextUtilities.join(gadget.getDescriptions(), "\n\n"))
				.setCancelable(true).setTitle(gadget.getTitle());
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void onBuy(Gadget gadget) {
		final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(gadget
				.getDetailsUrl()));
		try {
			startActivity(intent);
		} catch (ActivityNotFoundException e) {
			Log.e("BrowserNotFound", e.toString());
		}
	}
}
