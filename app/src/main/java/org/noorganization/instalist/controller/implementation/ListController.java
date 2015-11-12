package org.noorganization.instalist.controller.implementation;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.INotificationSideChannel;
import android.util.Log;

import com.orm.SugarRecord;
import com.orm.query.Condition;
import com.orm.query.Select;

import org.noorganization.instalist.controller.ICategoryController;
import org.noorganization.instalist.controller.IListController;
import org.noorganization.instalist.controller.IProductController;
import org.noorganization.instalist.controller.event.Change;
import org.noorganization.instalist.controller.event.ListChangedMessage;
import org.noorganization.instalist.controller.event.ListItemChangedMessage;
import org.noorganization.instalist.model.Category;
import org.noorganization.instalist.model.ListEntry;
import org.noorganization.instalist.model.Product;
import org.noorganization.instalist.model.ShoppingList;
import org.noorganization.instalist.provider.InstalistProvider;

import java.util.List;

import de.greenrobot.event.EventBus;


/**
 * Implementation of {@link org.noorganization.instalist.controller.IListController} as
 * singleton. Please retrieve your instance per {@link #getInstance()}.
 */
public class ListController implements IListController {

    private static ListController mInstance;

    private EventBus            mBus;
    private Context             mContext;
    private IProductController  mProductController;
    private ICategoryController mCategoryController;
    private ContentResolver     mResolver;

    private ListController(Context _context) {
        mBus = EventBus.getDefault();
        mContext = _context;
        mProductController = ControllerFactory.getProductController();
        mCategoryController = ControllerFactory.getCategoryController();
        mResolver = mContext.getContentResolver();
    }

    static ListController getInstance(Context _context) {
        if (mInstance == null) {
            mInstance = new ListController(_context);
        }

        return mInstance;
    }

    private ListEntry addOrChangeItem(ShoppingList _list, Product _product, float _amount,
                                      boolean _prioUsed, int _prio, boolean _addAmount) {
        if (_list == null || _product == null) {
            return null;
        }

        ShoppingList savedList = getListById(_list.mUUID);
        Product savedProduct = mProductController.getProductById(_product.id);
        if (savedList == null || savedProduct == null) {
            return null;
        }

        Uri listUri = savedList.toUri(InstalistProvider.BASE_CONTENT_URI);
        Cursor entryCheck = mResolver.query(
                Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI, listUri.getPath() + "/entry"),
                new String[]{ ListEntry.COLUMN.ID, ListEntry.COLUMN.AMOUNT },
                ListEntry.PREFIXED_COLUMN.PRODUCT + " = ?",
                new String[]{ _product.id },
                null);
        if (entryCheck == null) {
            Log.e(getClass().getCanonicalName(), "Searching for existing ListEntry (to change it) " +
                    "failed. Returning no ListEntry.");
            return null;
        }
        ContentValues newEntryCV = new ContentValues(5);
        newEntryCV.put(ListEntry.COLUMN.LIST, savedList.mUUID);
        newEntryCV.put(ListEntry.COLUMN.PRODUCT, savedProduct.id);
        if (_prioUsed) {
            newEntryCV.put(ListEntry.COLUMN.PRIORITY, _prio);
        }
        ListEntry rtn;
        if(entryCheck.getCount() == 0) {
            newEntryCV.put(ListEntry.COLUMN.AMOUNT, _amount);
            entryCheck.close();

            Uri newEntryUri = mResolver.insert(
                    Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI,
                            savedList.getUriPath() + "/entry"),
                    newEntryCV);
            if (newEntryUri == null) {
                return null;
            }
            rtn = new ListEntry();
            rtn.mUUID = newEntryUri.getLastPathSegment();
            rtn.mAmount = _amount;
            rtn.mList = savedList;
            rtn.mProduct = savedProduct;
            rtn.mPriority = (_prioUsed ? _prio : ListEntry.DEFAULTS.PRIORITY);
            rtn.mStruck = (ListEntry.DEFAULTS.STRUCK != 0);

            mBus.post(new ListItemChangedMessage(Change.CREATED, rtn));
        } else {
            entryCheck.moveToFirst();
            if (_addAmount) {
                newEntryCV.put(ListEntry.COLUMN.AMOUNT, _amount + entryCheck.getFloat(
                        entryCheck.getColumnIndex(ListEntry.COLUMN.AMOUNT)));
            } else {
                newEntryCV.put(ListEntry.COLUMN.AMOUNT, _amount);
            }

            String entryUUID = entryCheck.getString(entryCheck.getColumnIndex(ListEntry.COLUMN.ID));
            int updatedItems = mResolver.update(
                    Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI,
                            savedList.getUriPath() + "/entry/" + entryUUID),
                    newEntryCV,
                    null, null);
            rtn = getEntryById(entryUUID);
            if (updatedItems != 0) {
                mBus.post(new ListItemChangedMessage(Change.CHANGED, rtn));
            }
        }
        return rtn;
    }

    @Override
    public ListEntry addOrChangeItem(ShoppingList _list, Product _product, float _amount) {
        return addOrChangeItem(_list, _product, _amount, false, 0, false);
    }

    @Override
    public ListEntry addOrChangeItem(ShoppingList _list, Product _product, float _amount, int _prio) {
        return addOrChangeItem(_list,_product, _amount, true, _prio, false);
    }

    @Override
    public ListEntry addOrChangeItem(ShoppingList _list, Product _product, float _amount, boolean _addAmount) {
        return addOrChangeItem(_list, _product, _amount, false, 0, _addAmount);
    }

    @Override
    public ListEntry addOrChangeItem(ShoppingList _list, Product _product, float _amount, int _prio,
                                     boolean _addAmount) {
        return addOrChangeItem(_list,_product, _amount, true, _prio, true);
    }

    @Override
    public ListEntry getEntryById(@NonNull String _UUID) {
        Cursor entryCursor = mResolver.query(
                Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI, "entry"),
                ListEntry.COLUMN.ALL_COLUMNS,
                ListEntry.COLUMN.ID + " = ?",
                new String[]{_UUID},
                null);
        if (entryCursor == null) {
            Log.e(getClass().getCanonicalName(), "Searching ListEntry by UUID resulted null. " +
                    "Returning no ListEntry.");
            return null;
        }
        if (entryCursor.getCount() == 0) {
            entryCursor.close();
            return null;
        }
        entryCursor.moveToFirst();
        ListEntry rtn = new ListEntry();
        rtn.mUUID = entryCursor.getString(entryCursor.getColumnIndex(ListEntry.COLUMN.ID));
        rtn.mList = getListById(entryCursor.getString(entryCursor.getColumnIndex(
                ListEntry.COLUMN.LIST)));
        rtn.mProduct = mProductController.getProductById(entryCursor.getString(
                entryCursor.getColumnIndex(ListEntry.COLUMN.PRODUCT)));
        rtn.mAmount = entryCursor.getFloat(entryCursor.getColumnIndex(ListEntry.COLUMN.AMOUNT));
        rtn.mPriority = entryCursor.getInt(entryCursor.getColumnIndex(ListEntry.COLUMN.PRIORITY));
        rtn.mStruck = (entryCursor.getInt(entryCursor.getColumnIndex(ListEntry.COLUMN.STRUCK)) != 0);
        return rtn;
    }

    @Override
    public ListEntry getEntryByListAndProduct(@NonNull ShoppingList _list, @NonNull Product _product) {
        Cursor entrySearch = mResolver.query(
                Uri.withAppendedPath(
                        InstalistProvider.BASE_CONTENT_URI,
                        _list.getUriPath() + "/entry"),
                new String[]{ListEntry.COLUMN.ID},
                ListEntry.COLUMN.PRODUCT + " = ?",
                new String[]{_product.id},
                null);
        if (entrySearch == null) {
            Log.e(getClass().getCanonicalName(), "Search for ListEntry by product failed. " +
                    "Returning no found entry.");
            return null;
        } else if (entrySearch.getCount() == 0) {
            return null;
        }
        entrySearch.moveToFirst();
        return getEntryById(entrySearch.getString(entrySearch.getColumnIndex(
                ListEntry.COLUMN.ID)));
    }

    @Override
    public ShoppingList getListById(@NonNull String _UUID) {
        Cursor entryCursor = mContext.getContentResolver().query(
                Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI, "list"),
                ShoppingList.COLUMN.ALL_COLUMNS,
                ShoppingList.COLUMN.ID + " = ?",
                new String[]{_UUID},
                null);
        if (entryCursor == null) {
            Log.e(getClass().getCanonicalName(), "Searching ListEntry by UUID resulted null. " +
                    "Returning no ListEntry.");
            return null;
        }
        if (entryCursor.getCount() == 0) {
            entryCursor.close();
            return null;
        }
        entryCursor.moveToFirst();
        ShoppingList rtn = new ShoppingList();
        rtn.mUUID = entryCursor.getString(entryCursor.getColumnIndex(ShoppingList.COLUMN.ID));
        rtn.mName = entryCursor.getString(entryCursor.getColumnIndex(ShoppingList.COLUMN.NAME));
        rtn.mCategory = mCategoryController.getCategoryByID(entryCursor.getString(
                entryCursor.getColumnIndex(ShoppingList.COLUMN.CATEGORY)));
        return rtn;
    }

    @Override
    public void strikeAllItems(ShoppingList _list) {
        if (_list == null || _list.mUUID == null) {
            return;
        }
        Cursor itemsToStrike = mResolver.query(
                Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI,
                        _list.toUri(InstalistProvider.BASE_CONTENT_URI).getPath() + "/entry"),
                new String[]{ ListEntry.COLUMN.ID },
                null, null, null);
        if (itemsToStrike != null) {
            itemsToStrike.moveToFirst();
            ContentValues strikeCV = new ContentValues(1);
            strikeCV.put(ListEntry.COLUMN.STRUCK, true);
            String prefixPath = _list.toUri(InstalistProvider.BASE_CONTENT_URI).getPath() + "/entry/";
            while (!itemsToStrike.isAfterLast()) {
                String entryUUID = itemsToStrike.getString(itemsToStrike.getColumnIndex(
                        ListEntry.COLUMN.ID));
                if(mResolver.update(Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI,
                        prefixPath + entryUUID), strikeCV, null, null) == 1) {
                    mBus.post(new ListItemChangedMessage(Change.CHANGED, getEntryById(entryUUID)));
                }
                itemsToStrike.moveToNext();
            }
            itemsToStrike.close();
        }
    }

    @Override
    public void unstrikeAllItems(ShoppingList _list) {
        if (_list == null || _list.mUUID == null) {
            return;
        }
        Cursor itemsToStrike = mResolver.query(
                Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI,
                        _list.toUri(InstalistProvider.BASE_CONTENT_URI).getPath() + "/entry"),
                new String[]{ListEntry.COLUMN.ID},
                null, null, null);
        if (itemsToStrike != null) {
            itemsToStrike.moveToFirst();
            ContentValues strikeCV = new ContentValues(1);
            strikeCV.put(ListEntry.COLUMN.STRUCK, false);
            String prefixPath = _list.toUri(InstalistProvider.BASE_CONTENT_URI).getPath() + "/entry/";
            while (!itemsToStrike.isAfterLast()) {
                String entryUUID = itemsToStrike.getString(itemsToStrike.getColumnIndex(
                        ListEntry.COLUMN.ID));
                if(mResolver.update(Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI,
                        prefixPath + entryUUID), strikeCV, null, null) == 1) {
                    mBus.post(new ListItemChangedMessage(Change.CHANGED, getEntryById(entryUUID)));
                }
                itemsToStrike.moveToNext();
            }
            itemsToStrike.close();
        }
    }

    @Override
    public ListEntry strikeItem(ShoppingList _list, Product _product) {
        if (_list == null || _product == null) {
            return null;
        }

        ListEntry toChange = getEntryByListAndProduct(_list, _product);
        return strikeItem(toChange, false);
    }

    @Override
    public ListEntry unstrikeItem(ShoppingList _list, Product _product) {
        if (_list == null || _product == null) {
            return null;
        }

        ListEntry toChange = getEntryByListAndProduct(_list, _product);
        return unstrikeItem(toChange, false);
    }

    private ListEntry unstrikeItem(ListEntry _toChange, boolean _reload) {
        return updateStruckItem(_toChange, _reload, false);
    }

    private ListEntry updateStruckItem(ListEntry _toChange, boolean _reload, boolean _struck) {
        if (_toChange == null) {
            return null;
        }

        ContentValues entryUpdateCV = new ContentValues(1);
        entryUpdateCV.put(ListEntry.COLUMN.STRUCK, _struck);
        int chagedItems = mResolver.update(_toChange.toUri(InstalistProvider.BASE_CONTENT_URI),
                entryUpdateCV,
                null, null);
        ListEntry rtn;
        if (_reload) {
            rtn = getEntryById(_toChange.mUUID);
        } else {
            _toChange.mStruck = _struck;
            rtn = _toChange;
        }
        if (chagedItems > 0) {
            mBus.post(new ListItemChangedMessage(Change.CHANGED, rtn));
        }
        return rtn;
    }

    private ListEntry strikeItem(ListEntry _item, boolean _reload) {
        return updateStruckItem(_item, _reload, true);
    }

    @Override
    public ListEntry strikeItem(ListEntry _item) {
        return strikeItem(_item, true);
    }

    @Override
    public ListEntry unstrikeItem(ListEntry _item) {
        return unstrikeItem(_item, true);
    }

    @Override
    public boolean removeItem(ShoppingList _list, Product _product) {
        if (_list == null || _product == null) {
            return false;
        }

        ListEntry toDelete = getEntryByListAndProduct(_list, _product);
        return removeItem(toDelete);
    }

    @Override
    public boolean removeItem(ListEntry _item) {
        if (_item == null) {
            return false;
        }

        int deletedEntries = mResolver.delete(_item.toUri(InstalistProvider.BASE_CONTENT_URI), null,
                null);
        if (deletedEntries > 0) {
            mBus.post(new ListItemChangedMessage(Change.DELETED, _item));
            return true;
        }
        return false;
    }

    @Override
    public ListEntry setItemPriority(ListEntry _item, int _newPrio) {
        if (_item == null) {
            return null;
        }

        ContentValues entryUpdateCV = new ContentValues(1);
        entryUpdateCV.put(ListEntry.COLUMN.PRIORITY, _newPrio);
        int changedEntries = mResolver.update(_item.toUri(InstalistProvider.BASE_CONTENT_URI),
                entryUpdateCV, null, null);
        ListEntry entry = getEntryById(_item.mUUID);
        if (changedEntries > 0) {
            mBus.post(new ListItemChangedMessage(Change.CHANGED, entry));
        }

        return entry;
    }

    @Override
    public ShoppingList addList(String _name) {
        return addList(_name, null);
    }

    @Override
    public ShoppingList addList(String _name, Category _category) {
        if (_name == null || _name.length() == 0 || existsListName(_name)) {
            return null;
        }

        ContentValues newListCV = new ContentValues(2);
        newListCV.put(ShoppingList.COLUMN.NAME, _name);
        if (_category == null) {
            newListCV.putNull(ShoppingList.COLUMN.CATEGORY);
        } else {
            newListCV.put(ShoppingList.COLUMN.CATEGORY, _category.mUUID);
        }
        Uri createdList = mResolver.insert(Uri.withAppendedPath(InstalistProvider.BASE_CONTENT_URI,
                "category"), newListCV);


        Category targetCategory = null;
        if (_category != null) {
            targetCategory = SugarRecord.findById(Category.class, _category.getId());
            if (targetCategory == null) {
                return null;
            }
        }

        ShoppingList rtn = new ShoppingList(_name, targetCategory);
        rtn.save();

        mBus.post(new ListChangedMessage(Change.CREATED, rtn));

        return rtn;
    }

    @Override
    public boolean removeList(ShoppingList _list) {
        if (_list == null) {
            return false;
        }

        long countOfLinksToList = Select.from(ListEntry.class).where(
                Condition.prop("m_list").eq(_list.getId())).count();

        if (countOfLinksToList > 0) {
            return false;
        }

        Long oldId = _list.getId();
        _list.delete();

        mBus.post(new ListChangedMessage(Change.DELETED, _list));

        return ShoppingList.findById(ShoppingList.class, oldId) == null;
    }

    @Override
    public ShoppingList renameList(ShoppingList _list, String _newName) {
        if (_list == null || _newName == null || _newName.length() == 0 ||
                existsListName(_newName)) {
            return _list;
        }

        ShoppingList rtn = ShoppingList.findById(ShoppingList.class, _list.getId());
        rtn.mName = _newName;
        rtn.save();

        mBus.post(new ListChangedMessage(Change.CHANGED, rtn));

        return rtn;
    }

    @Override
    public ShoppingList moveToCategory(ShoppingList _list, Category _category) {
        if (_list == null) {
            return null;
        }

        ShoppingList listToChange = SugarRecord.findById(ShoppingList.class, _list.getId());
        if (listToChange == null) {
            return null;
        }

        Category targetCategory = null;
        if (_category != null) {
            targetCategory = SugarRecord.findById(Category.class, _category.getId());
            if (targetCategory == null) {
                return listToChange;
            }
        }

        listToChange.mCategory = targetCategory;
        listToChange.save();

        mBus.post(new ListChangedMessage(Change.CHANGED, listToChange));

        return listToChange;
    }

    private boolean existsListName(String _name) {
        long existingListWithSameNameCount = Select.from(ShoppingList.class).where(
                Condition.prop("m_name").eq(_name)).count();

        return (existingListWithSameNameCount > 0);
    }
}
