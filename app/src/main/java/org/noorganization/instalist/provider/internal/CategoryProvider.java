package org.noorganization.instalist.provider.internal;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import org.noorganization.instalist.model.ListEntry;
import org.noorganization.instalist.provider.InstalistProvider;
import org.noorganization.instalist.utils.SQLiteUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TODO: implement and describe.
 * Created by damihe on 21.10.15.
 */
public class CategoryProvider implements IInternalProvider {

    private SQLiteDatabase mDatabase;
    private UriMatcher     mMatcher;

    private static final int MULTIPLE_CATEGORIES = 1;
    private static final int SINGLE_CATEGORY = 2;
    private static final int MULTIPLE_LISTS = 3;
    private static final int SINGLE_LIST = 4;
    private static final int MULTIPLE_ENTRIES = 5;
    private static final int SINGLE_ENTRY = 6;

    private static final String URI_MULTIPLE_CATEGORIES = "category";

    @Override
    public void onCreate(SQLiteDatabase _db) {
        mDatabase = _db;
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(InstalistProvider.AUTHORITY, URI_MULTIPLE_CATEGORIES, MULTIPLE_CATEGORIES);
        mMatcher.addURI(InstalistProvider.AUTHORITY, "category/*", SINGLE_CATEGORY);
        mMatcher.addURI(InstalistProvider.AUTHORITY, "category/*/list", MULTIPLE_LISTS);
        mMatcher.addURI(InstalistProvider.AUTHORITY, "category/*/list/*", SINGLE_LIST);
        mMatcher.addURI(InstalistProvider.AUTHORITY, "category/*/list/*/entry", MULTIPLE_ENTRIES);
        mMatcher.addURI(InstalistProvider.AUTHORITY, "category/*/list/*/entry/*", SINGLE_ENTRY);
    }

    @Override
    public Cursor query(@NonNull Uri _uri, String[] _projection, String _selection, String[] _selectionArgs, String _sortOrder) {
        switch (mMatcher.match(_uri)) {
            case MULTIPLE_CATEGORIES:
                return mDatabase.query("category", _projection, _selection, _selectionArgs, null, null, _sortOrder);
            case SINGLE_CATEGORY: {
                String selection = SQLiteUtils.prependSelection("_id = ?", _selection);
                String[] selectionArgs = SQLiteUtils.prependSelectionArgs(_uri.getLastPathSegment(),
                        _selectionArgs);
                return mDatabase.query("category", _projection, selection, selectionArgs, null, null, _sortOrder);
            }
            case MULTIPLE_LISTS: {
                String category = _uri.getPathSegments().get(1);
                if (category.equals("-")) {
                    String selection = SQLiteUtils.prependSelection("category IS NULL", _selection);
                    return mDatabase.query("list", _projection, selection,_selectionArgs, null,
                            null, _sortOrder);
                } else {
                    String selection = SQLiteUtils.prependSelection("category = ?", _selection);
                    String[] selectionArgs = SQLiteUtils.prependSelectionArgs(category,
                            _selectionArgs);
                    return mDatabase.query("list", _projection, selection, selectionArgs, null, null,
                            _sortOrder);
                }
            }
            case SINGLE_LIST: {
                String category = _uri.getPathSegments().get(1);
                if (category.equals("-")) {
                    String selection = SQLiteUtils.prependSelection("category IS NULL AND _id = ?",
                            _selection);
                    String[] selectionArgs = SQLiteUtils.prependSelectionArgs(
                            _uri.getLastPathSegment(),
                            _selectionArgs);
                    return mDatabase.query("list", _projection, selection, selectionArgs, null,
                            null, _sortOrder);
                } else {
                    String selection = SQLiteUtils.prependSelection("category = ? AND _id = ?",
                            _selection);
                    String[] selectionArgs = SQLiteUtils.prependSelectionArgs(
                            new String[]{category, _uri.getLastPathSegment()},
                            _selectionArgs);
                    return mDatabase.query("list", _projection, selection, selectionArgs, null, null,
                            _sortOrder);
                }
            }
            case MULTIPLE_ENTRIES: {
                String category = _uri.getPathSegments().get(1);
                String list = _uri.getPathSegments().get(3);
                SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
                queryBuilder.setTables("(listentry INNER JOIN list ON (list._id = listentry.list)) " +
                        "INNER JOIN product ON (product._id = listentry.product)");
                queryBuilder.setProjectionMap(SQLiteUtils.generateProjectionMap("listentry",
                        "_id", "amount", "priority", "product", "list", "struck"));
                if (category.equals("-")) {
                    String selection = SQLiteUtils.prependSelection(
                            "list.category IS NULL AND list = ?",
                            _selection);
                    String[] args = SQLiteUtils.prependSelectionArgs(list, _selectionArgs);
                    return queryBuilder.query(mDatabase, _projection, selection, args, null, null,
                            _sortOrder);
                } else {
                    String selection = SQLiteUtils.prependSelection("list.category = ? AND list = ?",
                            _selection);
                    String[] args = SQLiteUtils.prependSelectionArgs(new String[]{category, list},
                            _selectionArgs);
                    return queryBuilder.query(mDatabase, _projection, selection, args, null, null,
                            _sortOrder);
                }
            }
            case SINGLE_ENTRY: {
                String category = _uri.getPathSegments().get(1);
                String list = _uri.getPathSegments().get(3);
                String entry = _uri.getLastPathSegment();
                SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
                queryBuilder.setTables("(listentry INNER JOIN list ON (list._id = listentry.list)) " +
                        "INNER JOIN product ON (product._id = listentry.product)");
                queryBuilder.setProjectionMap(SQLiteUtils.generateProjectionMap("listentry",
                        "_id", "amount", "priority", "product", "list", "struck"));
                if (category.equals("-")) {
                    String selection = SQLiteUtils.prependSelection(
                            "list.category IS NULL AND list = ? AND listentry._id = ?",
                            _selection);
                    String[] args = SQLiteUtils.prependSelectionArgs(new String[] {
                            list,
                            entry
                    }, _selectionArgs);
                    return queryBuilder.query(mDatabase, _projection, selection, args, null, null,
                            _sortOrder);
                } else {
                    String selection = SQLiteUtils.prependSelection("list.category = ? AND " +
                                    "list = ? AND listentry._id = ?",
                            _selection);
                    String[] args = SQLiteUtils.prependSelectionArgs(new String[]{
                                    category,
                                    list,
                                    entry
                            },
                            _selectionArgs);
                    return queryBuilder.query(mDatabase, _projection, selection, args, null, null,
                            _sortOrder);
                }
            }
            default:
                return null;
        }
    }

    @Override
    public String getType(@NonNull Uri _uri) {
        switch (mMatcher.match(_uri)) {
            case MULTIPLE_CATEGORIES:
                return ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + InstalistProvider.BASE_VENDOR +
                        "category";
            case SINGLE_CATEGORY:
                return ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + InstalistProvider.BASE_VENDOR +
                        "category";
            case MULTIPLE_LISTS:
                return ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + InstalistProvider.BASE_VENDOR +
                        "list";
            case SINGLE_LIST:
                return ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + InstalistProvider.BASE_VENDOR +
                        "list";
            case MULTIPLE_ENTRIES:
                return ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + InstalistProvider.BASE_VENDOR +
                        "entry";
            case SINGLE_ENTRY:
                return ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + InstalistProvider.BASE_VENDOR +
                        "entry";
            default:
                return null;
        }
    }

    @Override
    public Uri insert(@NonNull Uri _uri, ContentValues _values) {
        if (_values == null) {
            return null;
        }
        switch (mMatcher.match(_uri)) {
            case MULTIPLE_CATEGORIES: {
                String name = _values.getAsString("name");
                if (name == null) {
                    return null;
                }
                String newCatUUID = SQLiteUtils.generateId(mDatabase, "category").toString();
                ContentValues toInsert = new ContentValues(2);
                toInsert.put("_id", newCatUUID);
                toInsert.put("name", name);
                if (mDatabase.insert("category", null, toInsert) != -1) {
                    return Uri.parse("content://" + InstalistProvider.AUTHORITY + "/" +
                            URI_MULTIPLE_CATEGORIES + "/" + newCatUUID);
                } else {
                    return null;
                }
            }
            case MULTIPLE_LISTS: {
                String name = _values.getAsString("name");
                if (name == null) {
                    return null;
                }
                String categoryId = _uri.getPathSegments().get(1);
                String newListUUID = SQLiteUtils.generateId(mDatabase, "list").toString();
                ContentValues toInsert = new ContentValues(3);
                toInsert.put("_id", newListUUID);
                toInsert.put("name", name);
                if (!"-".equals(categoryId)) {
                    toInsert.put("category", categoryId);
                }
                if (mDatabase.insert("list", null, toInsert) != -1) {
                    return Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI, "category/" +
                            categoryId + "/list/" + newListUUID);
                } else {
                    return null;
                }
            }
            case MULTIPLE_ENTRIES: {
                if (!_values.containsKey(ListEntry.COLUMN_PRODUCT)) {
                    return null;
                }
                ContentValues toInsert = new ContentValues();
                for (String cvKey : _values.keySet()) {
                    switch (cvKey) {
                        case ListEntry.COLUMN_AMOUNT:
                        case ListEntry.COLUMN_PRIORITY:
                            toInsert.put(cvKey, _values.getAsFloat(cvKey));
                            break;
                        case ListEntry.COLUMN_PRODUCT:
                            toInsert.put(cvKey, _values.getAsString(cvKey));
                            break;
                        case ListEntry.COLUMN_STRUCK:
                            Boolean struckValue = _values.getAsBoolean(cvKey);
                            if (struckValue == null) {
                                struckValue = (_values.getAsInteger(cvKey) != 0);
                            }
                            toInsert.put(cvKey, (struckValue ? 1 : 0));
                            break;
                    }
                }

                String listUUID = _uri.getPathSegments().get(3);
                String newEntryUUID = SQLiteUtils.generateId(mDatabase, ListEntry.TABLE_NAME).toString();
                toInsert.put(ListEntry.COLUMN_ID, newEntryUUID);
                toInsert.put(ListEntry.COLUMN_LIST, listUUID);
                if (mDatabase.insert(ListEntry.TABLE_NAME, null, toInsert) != -1) {
                    return Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI, "category/" +
                            _uri.getPathSegments().get(1) + "/list/" + listUUID + "/entry/" +
                            newEntryUUID);
                } else {
                    return null;
                }
            }
            default:
                return null;
        }
    }

    @Override
    public int delete(@NonNull Uri _uri, String _selection, String[] _selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri _uri, ContentValues _values, String _selection, String[] _selectionArgs) {
        return 0;
    }
}
