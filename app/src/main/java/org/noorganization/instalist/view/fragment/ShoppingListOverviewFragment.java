package org.noorganization.instalist.view.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.software.shell.fab.ActionButton;

import org.noorganization.instalist.GlobalApplication;
import org.noorganization.instalist.R;
import org.noorganization.instalist.controller.IListController;
import org.noorganization.instalist.controller.implementation.ControllerFactory;
import org.noorganization.instalist.model.ListEntry;
import org.noorganization.instalist.model.ShoppingList;
import org.noorganization.instalist.touchlistener.OnRecyclerItemTouchListener;
import org.noorganization.instalist.view.ChangeHandler;
import org.noorganization.instalist.view.MainShoppingListView;
import org.noorganization.instalist.view.customview.AmountPicker;
import org.noorganization.instalist.view.datahandler.SelectableBaseItemListEntryDataHolder;
import org.noorganization.instalist.view.decoration.DividerItemListDecoration;
import org.noorganization.instalist.view.interfaces.IBaseActivity;
import org.noorganization.instalist.view.listadapter.ShoppingItemListAdapter;
import org.noorganization.instalist.view.sorting.AlphabeticalListEntryComparator;
import org.noorganization.instalist.view.sorting.PriorityListEntryComparator;
import org.noorganization.instalist.view.utils.ViewUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A ShoppingListOverviewFragment containing a list view.
 */
public class ShoppingListOverviewFragment extends Fragment {

    private static final String LOG_TAG = ShoppingListOverviewFragment.class.toString();

    private String mCurrentListName;
    private ShoppingList mCurrentShoppingList;
    private long mShoppingListId;

    private ActionBar mActionBar;
    private Context mContext;

    private ActionButton mAddButton;

    private LinearLayoutManager mLayoutManager;

    private ShoppingItemListAdapter mShoppingItemListAdapter;
    private RecyclerView mRecyclerView;

    private IListController mListController;

    private IBaseActivity mBaseActivityInterface;

    private ActionMode mActionMode; // usage of support.v7.ActionMode!


    private static String PREFERENCES_NAME = "SHOPPING_LIST_FRAGMENT";

    private static String SORT_MODE = "SORT_MODE";
    /**
     * Contains the mapping from a Integer to comperators.
     */
    private Map<Integer, Comparator> mMapComperable;

    private static Integer SORT_BY_NAME = 0;
    private static Integer SORT_BY_PRIORITY = 1;

    /**
     * Used to inflate the actionbar.
     */
    private ActionMode.Callback mActionModeCallback;

    /**
     * Listener for Callback of ActionMode when editing an ListEntry.
     */
    private class OnShoppingListItemActionModeListener implements ActionMode.Callback {

        private Context mContext;
        private View    mView;
        private long mListEntryId;

        /**
         * Constructor of OnShoppingListItemActionModeListener.
         *
         * @param _Context  the context of the Fragment.
         * @param _View     the View of the selected element. Used to read the data from it.
         * @param _ListEntryId the Id of the clicked ListEntry.
         */
        public OnShoppingListItemActionModeListener(Context _Context, View _View, long _ListEntryId) {
            mContext = _Context;
            mView  = _View;
            mListEntryId = _ListEntryId;

        }

        @Override
        public boolean onCreateActionMode(ActionMode _Mode, Menu _Menu) {
            _Menu.clear();
            MenuInflater menuInflater = _Mode.getMenuInflater();
            menuInflater.inflate(R.menu.menu_contextual_actionmode_options, _Menu);

            ListEntry listEntry = ListEntry.findById(ListEntry.class, mListEntryId);
            _Mode.setTitle(listEntry.mProduct.mName);
            return true;
        }

        // called after onCreateActionMode
        @Override
        public boolean onPrepareActionMode(ActionMode _Mode, Menu _Menu) {
            return true;
        }

        // called when user selected an item.
        @Override
        public boolean onActionItemClicked(ActionMode _Mode, MenuItem _Item) {
            ListEntry entry = ListEntry.findById(ListEntry.class, mListEntryId);

            switch (_Item.getItemId()) {
                case R.id.menu_add_action:

                    int position = mShoppingItemListAdapter.getPositionForId(mListEntryId);
                    LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();

                    View view = layoutManager.findViewByPosition(position);
                    AmountPicker amountPicker = (AmountPicker) view.findViewById(R.id.list_product_shopping_product_amount_edit);

                    if(amountPicker == null){
                        Log.e(LOG_TAG, "amountPicker is null.");
                        return true;
                    }

                    float value = amountPicker.getValue();
                    if(value == 0.0f){
                        // TODO: some error messaging
                        return true;
                    }
                    entry.mAmount = value;
                    // entry.mUnit = unit;

                    ControllerFactory.getListController().addOrChangeItem(mCurrentShoppingList, entry.mProduct, value);
                    _Mode.finish();
                    break;
                case R.id.menu_cancel_action:
                    _Mode.finish();
                    break;
                /*case R.id.menu_delete_action:
                    ControllerFactory.getListController().removeItem(ListEntry.findById(ListEntry.class, mListEntryId));
                    _Mode.finish();
                    break;*/
                default:
                    return false;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode _Mode) {
            mShoppingItemListAdapter.resetEditModeView();
            mView.setSelected(false);
            mActionMode = null;
        }
    }




    // --------------------------------------------------------------------------------------------


    public ShoppingListOverviewFragment() {
    }


    /**
     * Creates an instance of an ShoppingListOverviewFragment.
     *
     * @param _ListName the name of the list that should be shown.
     * @return the new instance of this fragment.
     */
    public static ShoppingListOverviewFragment newInstance(String _ListName) {

        ShoppingListOverviewFragment fragment = new ShoppingListOverviewFragment();
        Bundle args = new Bundle();
        args.putString(MainShoppingListView.KEY_LISTNAME, _ListName);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Creates an instance of an ShoppingListOverviewFragment.
     *
     * @param _ListId id of the @Link{ShoppingList} that should be shown.
     * @return the new instance of this fragment.
     */
    public static ShoppingListOverviewFragment newInstance(long _ListId) {

        ShoppingListOverviewFragment fragment = new ShoppingListOverviewFragment();
        Bundle args = new Bundle();
        args.putLong(MainShoppingListView.KEY_LISTNAME, _ListId);
        fragment.setArguments(args);
        return fragment;
    }

    // --------------------------------------------------------------------------------------------


    @Override
    public void onAttach(Activity _Activity) {
        super.onAttach(_Activity);
        mContext = _Activity;

        try {
            mBaseActivityInterface = (IBaseActivity) _Activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(_Activity.toString()
                    + " has no IBaseActivity interface attached.");
        }

        mListController = ControllerFactory.getListController();
        ((ChangeHandler) ((GlobalApplication) getActivity().getApplication()).getChangeHandler()).setCurrentFragment(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // get bundle args to get the listname that should be shown
        Bundle bundle = this.getArguments();

        mMapComperable = new WeakHashMap<>();
        mMapComperable.put(0, new AlphabeticalListEntryComparator());
        mMapComperable.put(1, new PriorityListEntryComparator());

        if (bundle == null) {
            return;
        }
        mCurrentListName = bundle.getString(MainShoppingListView.KEY_LISTNAME);
        mCurrentShoppingList = /*ShoppingList.findById(mCategoryId);*/ShoppingList.findByName(mCurrentListName);
    }


    // --------------------------------------------------------------------------------------------


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_main, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        SharedPreferences sortDetails = mContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        // swtich which action item was pressed
        switch (id) {
            case R.id.list_items_sort_by_priority:
                mShoppingItemListAdapter.sortByComparator(mMapComperable.get(SORT_BY_PRIORITY));

                sortDetails.edit()
                        .putInt(SORT_MODE, SORT_BY_PRIORITY)
                        .apply();
                break;
            case R.id.list_items_sort_by_name:
                mShoppingItemListAdapter.sortByComparator(mMapComperable.get(SORT_BY_NAME));
                sortDetails.edit()
                        .putInt(SORT_MODE, SORT_BY_NAME)
                        .apply();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // set the title in "main" activity so that the current list name is shown on the actionbar
        mBaseActivityInterface.setToolbarTitle(mCurrentListName);
    }


    // --------------------------------------------------------------------------------------------


    @Override
    public void onPause() {
        super.onPause();
        mAddButton.setOnClickListener(null);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        ((ChangeHandler) ((GlobalApplication) getActivity().getApplication()).getChangeHandler()).setCurrentFragment(null);
    }



    // --------------------------------------------------------------------------------------------


    @Override
    public void onResume() {
        super.onResume();

        SharedPreferences sortDetails = mContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        // decl
        // init
        mRecyclerView = (RecyclerView) getActivity().findViewById(R.id.fragment_shopping_list);

        // assign other listname if none is assigned
        if (mCurrentListName == null) {

            List<ShoppingList> mShoppingLists = ShoppingList.listAll(ShoppingList.class);
            if (mShoppingLists.size() > 0) {
                mCurrentShoppingList = mShoppingLists.get(0);
                mCurrentListName = mCurrentShoppingList.mName;
                mBaseActivityInterface.setToolbarTitle(mCurrentShoppingList.mName);
            } else {
                mBaseActivityInterface.setToolbarTitle(mContext.getResources().getString(R.string.shopping_list_overview_fragment_no_list_available));
                // do something to show that there are no shoppinglists!
                return;
            }
        }
        mBaseActivityInterface.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

        mShoppingItemListAdapter = new ShoppingItemListAdapter(getActivity(), mCurrentShoppingList.getEntries());
        mShoppingItemListAdapter.sortByComparator(mMapComperable.get(sortDetails.getInt(SORT_MODE, SORT_BY_PRIORITY)));

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(mContext);
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);

        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new DividerItemListDecoration(getResources().getDrawable(R.drawable.list_divider), false, false));
        mRecyclerView.setAdapter(mShoppingItemListAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());

        mRecyclerView.addOnItemTouchListener(new OnRecyclerItemTouchListener(mContext, mRecyclerView) {

            @Override
            public void onLongPress(View _ChildView, int _Position) {
                super.onLongPress(_ChildView, _Position);
                if (mActionMode != null) {
                    mActionMode.finish();
                }
                mShoppingItemListAdapter.setToEditMode(_Position);

                mActionModeCallback = new OnShoppingListItemActionModeListener(mContext, _ChildView, mShoppingItemListAdapter.getItemId(_Position));
                // Start the CAB using the Callback defined above
                mActionMode = ((ActionBarActivity)getActivity()).startSupportActionMode(mActionModeCallback);
                _ChildView.setSelected(true);
            }
        });

        mAddButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // reset selected items ... (lazy resetting!)
                SelectableBaseItemListEntryDataHolder.getInstance().clear();
                ViewUtils.addFragment(getActivity(),
                        ProductListDialogFragment.newInstance(mCurrentShoppingList.getId()));
            }
        });

        mBaseActivityInterface.bindDrawerLayout();
    }


    // --------------------------------------------------------------------------------------------


    @Override
    public View onCreateView(LayoutInflater _Inflater, ViewGroup _Container, Bundle _SavedInstanceState) {
        super.onCreateView(_Inflater, _Container, _SavedInstanceState);

        View view = _Inflater.inflate(R.layout.fragment_main_shopping_list_view, _Container, false);
        mAddButton = (ActionButton) view.findViewById(R.id.add_item_main_list_view);

        return view;
    }

    /**
     * Updates the adapter in the shoppinglistadapter with the given item.
     *
     * @param _Entry the item that should be deleted.
     */
    public void onListItemUpdated(ListEntry _Entry) {
        mShoppingItemListAdapter.changeItem(_Entry);
    }

    /**
     * Removes the given item from the containing listarray in the shoppinglistadapter.
     *
     * @param _Entry the item that should be deleted.
     */
    public void onListItemDeleted(ListEntry _Entry) {
        mShoppingItemListAdapter.removeItem(_Entry);
    }

    /**
     * Adds the given listentry to the listentry adapter.
     *
     * @param _Entry The entry that should be added to the list.
     */
    public void onListItemAdded(ListEntry _Entry) {
        if (_Entry.mList.getId().equals(mCurrentShoppingList.getId())) {
            mShoppingItemListAdapter.addItem(_Entry);
        }
    }

    public void onShoppingListItemChanged(ListEntry _Entry) {
        mShoppingItemListAdapter.changeItem(_Entry);
    }
}