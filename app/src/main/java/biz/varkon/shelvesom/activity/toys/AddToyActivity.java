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

package biz.varkon.shelvesom.activity.toys;

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

import biz.varkon.shelvesom.R;
import biz.varkon.shelvesom.activity.SettingsActivity;
import biz.varkon.shelvesom.base.AddBaseItemActivity;
import biz.varkon.shelvesom.base.BaseItem;
import biz.varkon.shelvesom.drawable.FastBitmapDrawable;
import biz.varkon.shelvesom.provider.toys.ToysManager;
import biz.varkon.shelvesom.provider.toys.ToysStore;
import biz.varkon.shelvesom.provider.toys.ToysStore.Toy;
import biz.varkon.shelvesom.util.ImageUtilities;
import biz.varkon.shelvesom.util.TextUtilities;
import biz.varkon.shelvesom.util.UIUtilities;

public class AddToyActivity extends AddBaseItemActivity implements
		AdapterView.OnItemClickListener {

	private static final String STATE_ADD_TOY = "shelves.add.toy";
	private static final String STATE_SEARCH_QUERY = "shelves.search.toy";
	private static final String STATE_TOY_TO_ADD = "shelves.add.toyToAdd";

	private SearchTask mSearchTask;
	private AddTask mAddTask;

	private SearchResultsAdapter mToysAdapter;
	private ToysStore.Toy mToyToAdd;

	static void show(Context context) {
		final Intent intent = new Intent(context, AddToyActivity.class);
		context.startActivity(intent);
	}

	@Override
	protected void setupViews() {
		super.setupViews();

		mSearchQuery.setHint(R.string.search_add_hint_toy);

		mSearchButton = findViewById(R.id.button_go);
		mSearchButton.setOnClickListener(this);
		mSearchButton.setEnabled(false);

		final FastBitmapDrawable cover = new FastBitmapDrawable(
				ImageUtilities.createShadow(BitmapFactory.decodeResource(
						getResources(), R.drawable.unknown_cover_no_shadow),
						COVER_WIDTH, COVER_HEIGHT));

		mToysAdapter = new SearchResultsAdapter(this, cover);

		final SearchResultsAdapter resultsAdapter = mToysAdapter;
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
		restoreToyToAdd(savedInstanceState);
		restoreAddTask(savedInstanceState);
		restoreSearchTask(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (isFinishing()) {
			saveToyToAdd(outState);
			saveAddTask(outState);
			saveSearchTask(outState);
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return mToysAdapter;
	}

	private void saveToyToAdd(Bundle outState) {
		if (mToyToAdd != null) {
			outState.putParcelable(STATE_TOY_TO_ADD, mToyToAdd);
		}
	}

	private void restoreToyToAdd(Bundle savedInstanceState) {
		final Object data = savedInstanceState.get(STATE_TOY_TO_ADD);
		if (data != null) {
			mToyToAdd = (ToysStore.Toy) data;
		}
	}

	private void saveAddTask(Bundle outState) {
		final AddTask task = mAddTask;
		if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
			final String toyId = task.getToyId();
			task.cancel(true);

			if (toyId != null) {
				outState.putBoolean(STATE_ADD_IN_PROGRESS, true);
				outState.putString(STATE_ADD_TOY, toyId);
			}

			mAddTask = null;
		}
	}

	private void restoreAddTask(Bundle savedInstanceState) {
		if (savedInstanceState.getBoolean(STATE_ADD_IN_PROGRESS)) {
			final String id = savedInstanceState.getString(STATE_ADD_TOY);
			if (!ToysManager.toyExists(getContentResolver(), id,
					SettingsActivity.getSortOrder(this), null)) {
				mAddTask = (AddTask) new AddTask().execute(id);
			}
		}
	}

	private void saveSearchTask(Bundle outState) {
		final SearchTask task = mSearchTask;
		if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
			final String toyId = task.getQuery();
			task.cancel(true);

			if (toyId != null) {
				outState.putBoolean(STATE_SEARCH_IN_PROGRESS, true);
				outState.putString(STATE_SEARCH_QUERY, toyId);
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
		mToyToAdd = mToysAdapter.getItem(position).toy;
		showDialog(DIALOG_ADD);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_ADD:
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(mToyToAdd != null ? mToyToAdd.getTitle() : " ");

			builder.setMessage(R.string.dialog_add_message);
			builder.setPositiveButton(R.string.add_label,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							if (mToyToAdd != null) {
								onAdd(mToyToAdd.getInternalIdNoPrefix());
								mToyToAdd = null;
							}
						}
					});
			builder.setNegativeButton(R.string.dialog_add_cancel,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							mToyToAdd = null;
							dismissDialog(DIALOG_ADD);
						}
					});
			builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					mToyToAdd = null;
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
			if (mToyToAdd != null && dialog != null) {
				dialog.setTitle(mToyToAdd.getTitle());
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
		if (!ToysManager.toyExists(
				getApplicationContext().getContentResolver(), id,
				SettingsActivity.getSortOrder(this), null)) {
			mAddTask = (AddTask) new AddTask().execute(id);
		} else {
			UIUtilities.showToast(
					this,
					getString(R.string.error_item_exists,
							getString(R.string.toy_label)));
		}
	}

	private void onCancelAdd() {
		if (mAddTask != null
				&& mAddTask.getStatus() == AsyncTask.Status.RUNNING) {
			mAddTask.cancel(true);
			mAddTask = null;
		}
	}

	private class AddTask extends AsyncTask<String, Void, ToysStore.Toy> {
		private final Object mLock = new Object();
		private String mToyId;
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

		String getToyId() {
			synchronized (mLock) {
				return mToyId;
			}
		}

		@Override
		public ToysStore.Toy doInBackground(String... params) {
			synchronized (mLock) {
				mToyId = params[0];
			}
			return ToysManager.loadAndAddToy(getContentResolver(), mToyId,
					new ToysStore(), null, AddToyActivity.this);
		}

		@Override
		public void onCancelled() {
			enableSearchPanel();
			hidePanel(mAddPanel, false);
		}

		@Override
		public void onPostExecute(ToysStore.Toy toy) {
			enableSearchPanel();
			if (toy == null) {
				UIUtilities.showToast(
						AddToyActivity.this,
						getString(R.string.error_adding_item,
								getString(R.string.toy_label)));
			} else {
				UIUtilities.showFormattedImageToast(AddToyActivity.this,
						R.string.success_added, ImageUtilities.getCachedCover(
								toy.getInternalId(), mDefaultCover), toy
								.getTitle());
			}
			hidePanel(mAddPanel, false);
		}
	}

	private class SearchTask extends AsyncTask<String, ResultToy, Void>
			implements ToysStore.ToySearchListener {

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
				mToysAdapter.clear();
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
			new ToysStore().searchToys(mQuery, String.valueOf(mPage), this,
					AddToyActivity.this);

			return null;
		}

		@Override
		public void onProgressUpdate(ResultToy... values) {
			for (ResultToy toy : values) {
				mToysAdapter.add(toy);
			}
		}

		@Override
		public void onPostExecute(Void ignore) {
			enableSearchPanel();

			UIUtilities.showToast(
					AddToyActivity.this,
					getString(R.string.success_item_found,
							mToysAdapter.getCount(),
							getString(R.string.toy_label_plural_small)));
			hidePanel(mSearchPanel, true);
		}

		@Override
		public void onCancelled() {
			enableSearchPanel();

			hidePanel(mSearchPanel, true);
		}

		public void onToyFound(ToysStore.Toy toy, ArrayList<ToysStore.Toy> toys) {
			if (toy != null && !isCancelled()) {
				publishProgress(new ResultToy(toy));
			}
		}
	}

	private static class SearchResultsAdapter extends ArrayAdapter<ResultToy> {
		private final LayoutInflater mLayoutInflater;
		private final FastBitmapDrawable mDefaultCover;

		SearchResultsAdapter(AddToyActivity addToyActivity,
				FastBitmapDrawable cover) {
			super(addToyActivity, 0);
			mDefaultCover = cover;
			mLayoutInflater = LayoutInflater.from(addToyActivity);
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

			final ResultToy toy = getItem(position);
			holder.toy = toy.toy;
			holder.title.setText(toy.title);
			holder.author.setText(toy.authors);

			final boolean hasCover = toy.cover != null;
			holder.cover.setImageDrawable(hasCover ? toy.cover : mDefaultCover);

			return convertView;
		}
	}

	private static class ViewHolder {
		ImageView cover;
		TextView title;
		TextView author;
		ToysStore.Toy toy;
	}

	private static class ResultToy {
		final ToysStore.Toy toy;
		final String text;
		final String title;
		final String authors;

		final FastBitmapDrawable cover;

		ResultToy(ToysStore.Toy toy) {
			this.toy = toy;
			Bitmap bitmap = ImageUtilities.createShadow(
					toy.loadCover(BaseItem.ImageSize.THUMBNAIL), COVER_WIDTH,
					COVER_HEIGHT);
			if (bitmap != null) {
				cover = new FastBitmapDrawable(bitmap);
			} else {
				cover = null;
			}

			title = toy.getTitle();
			authors = toy.getAuthors();
			text = title + ' ' + authors;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	// GJT: Added these, for showing toy descriptions before add
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		menu.setHeaderTitle(mToysAdapter.getItem((int) info.id).toy.getTitle());

		getMenuInflater().inflate(R.menu.add_item, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();

		switch (item.getItemId()) {
		case R.id.context_menu_add_details:
			onShowDetails(mToysAdapter.getItem((int) info.id).toy);
			return true;
		case R.id.context_menu_item_buy:
			onBuy(mToysAdapter.getItem((int) info.id).toy);
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	public void onShowDetails(ToysStore.Toy toy) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(TextUtilities.join(toy.getDescriptions(), "\n\n"))
				.setCancelable(true).setTitle(toy.getTitle());
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void onBuy(Toy toy) {
		final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(toy
				.getDetailsUrl()));
		try {
			startActivity(intent);
		} catch (ActivityNotFoundException e) {
			Log.e("BrowserNotFound", e.toString());
		}
	}
}
