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

package net.avabook.shelves.provider.tools;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;

import net.avabook.shelves.base.BaseItem;
import net.avabook.shelves.provider.tools.ToolsStore.Tool;
import net.avabook.shelves.util.IOUtilities;
import net.avabook.shelves.util.ImageUtilities;
import net.avabook.shelves.util.ImportUtilities;
import net.avabook.shelves.util.Preferences;
import net.avabook.shelves.util.loan.Calendars;

public class ToolsManager {
	static int TOOL_COVER_WIDTH;
	static int TOOL_COVER_HEIGHT;

	private static String sIdSelection;
	private static String sSelection;

	private static String[] sArguments1 = new String[1];
	private static String[] sArguments4 = new String[4];

	private static final String[] PROJECTION_ID_IID = new String[] {
			BaseItem._ID, BaseItem.INTERNAL_ID };

	private static final String[] PROJECTION_ID = new String[] { BaseItem._ID };

	static {
		StringBuilder selection = new StringBuilder();
		selection.append(BaseItem.INTERNAL_ID);
		selection.append(" LIKE ?");
		sIdSelection = selection.toString();

		selection = new StringBuilder();
		selection.append(sIdSelection);
		selection.append(" OR ");
		selection.append(BaseItem.EAN);
		selection.append(" LIKE ? OR ");
		selection.append(BaseItem.UPC);
		selection.append(" LIKE ? OR ");
		selection.append(BaseItem.ISBN);
		selection.append(" LIKE ?");
		sSelection = selection.toString();
	}

	private ToolsManager() {
	}

	public static String findToolId(ContentResolver contentResolver, String id,
			String sortOrder) {
		String internalId = null;
		Cursor c = null;

		try {
			final String[] arguments4 = sArguments4;
			arguments4[0] = arguments4[1] = arguments4[2] = arguments4[3] = id;
			c = contentResolver.query(ToolsStore.Tool.CONTENT_URI,
					PROJECTION_ID_IID, sSelection, arguments4, sortOrder);
			if (c.getCount() > 0) {
				if (c.moveToFirst()) {
					internalId = c.getString(c
							.getColumnIndexOrThrow(BaseItem.INTERNAL_ID));
				}
			}
		} finally {
			if (c != null)
				c.close();
		}

		return internalId;
	}

	public static boolean toolExists(ContentResolver contentResolver,
			String id, String sortOrder, IOUtilities.inputTypes type) {
		boolean exists;
		Cursor c = null;

		try {
			final String[] arguments4 = sArguments4;
			arguments4[0] = arguments4[1] = arguments4[2] = arguments4[3] = id
					.replaceFirst("^0+(?!$)", "");

			c = contentResolver.query(ToolsStore.Tool.CONTENT_URI,
					PROJECTION_ID, sSelection, arguments4, sortOrder);
			exists = c.getCount() > 0;
		} finally {
			if (c != null)
				c.close();
		}

		return exists;
	}

	public static ToolsStore.Tool loadAndAddTool(ContentResolver resolver,
			String id, ToolsStore toolsStore,
			IOUtilities.inputTypes mSavedImportType, Context context) {

		final ToolsStore.Tool tool = toolsStore.findTool(id, mSavedImportType,
				context);
		if (tool != null) {
			Bitmap bitmap = null;

			bitmap = Preferences.getBitmapForManager(tool);
			TOOL_COVER_WIDTH = Preferences.getWidthForManager();
			TOOL_COVER_HEIGHT = Preferences.getHeightForManager();

			if (bitmap != null) {
				bitmap = ImageUtilities.createCover(bitmap, TOOL_COVER_WIDTH,
						TOOL_COVER_HEIGHT);
				ImportUtilities.addCoverToCache(tool.getInternalId(), bitmap);
				bitmap.recycle();
			}

			// Should kill duplicate item entry bug...
			Cursor c = null;
			sArguments1[0] = tool.getInternalId();
			c = resolver.query(ToolsStore.Tool.CONTENT_URI, null,
					BaseItem.INTERNAL_ID + "='" + tool.getInternalId() + "'",
					null, null);
			if (c.moveToFirst()) {
				if (c.getCount() < 1) {
					final Uri uri = resolver.insert(
							ToolsStore.Tool.CONTENT_URI,
							tool.getContentValues());
					if (uri != null) {
						if (c != null) {
							c.close();
						}
						return tool;
					}
				}
			} else {
				if (c != null) {
					c.close();
				}
				final Uri uri = resolver.insert(ToolsStore.Tool.CONTENT_URI,
						tool.getContentValues());
				return tool;
			}
		}

		return null;
	}

	public static boolean deleteTool(ContentResolver contentResolver,
			String toolId) {
		Tool tool = ToolsManager.findTool(contentResolver, toolId, null);
		int eventId = 0;

		if (tool != null) {
			eventId = tool.getEventId();
		}

		final String[] arguments1 = sArguments1;
		arguments1[0] = toolId;
		int count = contentResolver.delete(ToolsStore.Tool.CONTENT_URI,
				sIdSelection, arguments1);
		ImageUtilities.deleteCachedCover(toolId);

		if (eventId > 0) {
			Calendars.deleteCalendar(contentResolver, tool);
		}

		return count > 0;
	}

	public static ToolsStore.Tool findTool(ContentResolver contentResolver,
			String id, String sortOrder) {
		ToolsStore.Tool tool = null;
		Cursor c = null;

		try {
			sArguments1[0] = id;
			c = contentResolver.query(ToolsStore.Tool.CONTENT_URI, null,
					sIdSelection, sArguments1, sortOrder);
			if (c.getCount() > 0) {
				if (c.moveToFirst()) {
					tool = ToolsStore.Tool.fromCursor(c);
				}
			}
		} finally {
			if (c != null)
				c.close();
		}

		return tool;
	}

	public static ToolsStore.Tool findTool(ContentResolver contentResolver,
			Uri data, String sortOrder) {
		ToolsStore.Tool tool = null;
		Cursor c = null;

		try {
			c = contentResolver.query(data, null, null, null, null);
			if (c != null && c.getCount() > 0) {
				if (c.moveToFirst()) {
					tool = ToolsStore.Tool.fromCursor(c);
				}
			}
		} finally {
			if (c != null)
				c.close();
		}

		return tool;
	}

	public static ToolsStore.Tool findToolById(ContentResolver contentResolver,
			String id, String sortOrder) {
		ToolsStore.Tool Tool = null;
		Cursor c = null;

		try {
			final String[] arguments4 = sArguments4;
			arguments4[0] = arguments4[1] = arguments4[2] = arguments4[3] = id;

			c = contentResolver.query(ToolsStore.Tool.CONTENT_URI, null,
					sSelection, sArguments4, sortOrder);
			if (c.getCount() > 0) {
				if (c.moveToFirst()) {
					Tool = ToolsStore.Tool.fromCursor(c);
				}
			}
		} finally {
			if (c != null)
				c.close();
		}

		return Tool;
	}
}
