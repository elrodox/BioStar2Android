/*
 * Copyright 2015 Suprema(biostar2@suprema.co.kr)
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
package com.supremainc.biostar2.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.supremainc.biostar2.BuildConfig;
import com.supremainc.biostar2.R;
import com.supremainc.biostar2.adapter.CardAdapter;
import com.supremainc.biostar2.adapter.NewCardAdapter;
import com.supremainc.biostar2.meta.Setting;
import com.supremainc.biostar2.sdk.models.v2.card.Card;
import com.supremainc.biostar2.sdk.models.v2.card.CardsList;
import com.supremainc.biostar2.sdk.models.v2.card.ListCard;
import com.supremainc.biostar2.sdk.models.v2.common.ResponseStatus;
import com.supremainc.biostar2.sdk.models.v2.common.VersionData;
import com.supremainc.biostar2.sdk.models.v2.device.ListDevice;
import com.supremainc.biostar2.sdk.models.v2.user.User;
import com.supremainc.biostar2.view.SubToolbar;
import com.supremainc.biostar2.widget.ScreenControl.ScreenType;
import com.supremainc.biostar2.widget.popup.Popup.OnPopupClickListener;
import com.supremainc.biostar2.widget.popup.Popup.PopupType;
import com.supremainc.biostar2.widget.popup.SelectCustomData;
import com.supremainc.biostar2.widget.popup.SelectPopup;
import com.supremainc.biostar2.widget.popup.SelectPopup.OnSelectResultListener;
import com.supremainc.biostar2.widget.popup.SelectPopup.SelectType;

import java.io.Serializable;
import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CardFragment extends BaseFragment {
    private static final int ASSGIGN_CARD = 1;
    private static final int CARD_READER = 0;
    private static final int MODE_DELETE = 1;
    private NewCardAdapter mItemAdapter;
    private CardAdapter mOldItemAdapter;
    private SelectPopup<ListCard> mSelectCardPopup;
    private SelectPopup<ListDevice> mSelectDevicePopup;
    private SubToolbar mSubToolbar;
    private User mUserInfo;

    private String mDeviceId;
    private boolean mIsDisableModify;
    private int mReplacePosition = -1;

    private SubToolbar.SubToolBarListener mSubToolBarEvent = new SubToolbar.SubToolBarListener() {
        @Override
        public void onClickSelectAll() {
            if (mSubToolbar.showReverseSelectAll()) {
                if (mItemAdapter != null) {
                    mItemAdapter.selectChoices();
                    mSubToolbar.setSelectedCount(mItemAdapter.getCheckedItemCount());
                }
                if (mOldItemAdapter != null) {
                    mOldItemAdapter.selectChoices();
                    mSubToolbar.setSelectedCount(mOldItemAdapter.getCheckedItemCount());
                }
            } else {
                if (mItemAdapter != null) {
                    mItemAdapter.clearChoices();
                    mSubToolbar.setSelectedCount(0);
                }
                if (mOldItemAdapter != null) {
                    mOldItemAdapter.clearChoices();
                    mSubToolbar.setSelectedCount(0);
                }
            }
        }
    };
    private Callback<CardsList> mCardsListener = new Callback<CardsList>() {
        @Override
        public void onFailure(Call<CardsList> call, Throwable t) {
            if (isIgnoreCallback(call,true)) {
                return;
            }
            showErrorPopup(t.getMessage(),true);
        }

        @Override
        public void onResponse(Call<CardsList> call, Response<CardsList> response) {
            if (isIgnoreCallback(call,response,true)) {
                return;
            }
            if (isInvalidResponse( response,true,true)) {
                return;
            }
            mUserInfo.cards = response.body().records;
            mUserInfo.card_count = mUserInfo.cards.size();
            refreshValue();
        }
    };

    private Callback<Card> mScanListener = new Callback<Card>() {
        @Override
        public void onFailure(Call<Card> call, Throwable t) {
            if (isIgnoreCallback(call,true)) {
                return;
            }
            clearValue();
            showErrorPopup(t.getMessage(),false);
        }

        @Override
        public void onResponse(Call<Card> call, final Response<Card> response) {
            if (isIgnoreCallback(call,response,true)) {
                return;
            }
            if (isInvalidResponse( response,true,false)) {
                return;
            }
            if (!response.body().unassigned) {
                showErrorPopup(getString(R.string.already_assigned),false);
                return;
            }
            mPopup.show(PopupType.CARD_CONFIRM, mOldItemAdapter.getName(mReplacePosition), response.body().card_id, new OnPopupClickListener() {
                @Override
                public void OnNegative() {
                }
                @Override
                public void OnPositive() {
                    setCard(response.body());
                }
            }, getString(R.string.ok), null, false);
        }
    };

    private Callback<ResponseStatus> mModifyCardListener = new Callback<ResponseStatus>() {
        @Override
        public void onFailure(Call<ResponseStatus> call, Throwable t) {
            if (isIgnoreCallback(call,true)) {
                return;
            }
            showErrorPopup(t.getMessage(),false);
        }

        @Override
        public void onResponse(Call<ResponseStatus> call, Response<ResponseStatus> response) {
            if (isIgnoreCallback(call,response,true)){
                return;
            }
            if (isInvalidResponse( response,true,true)) {
                return;
            }
            mPopup.showWait(mCancelExitListener);
            request(mUserDataProvider.getCards(mUserInfo.user_id, mCardsListener));
        }
    };

    public CardFragment() {
        super();
        setType(ScreenType.CARD);
        TAG = getClass().getSimpleName() + String.valueOf(System.currentTimeMillis());
    }

    private void clearValue() {
        mReplacePosition = -1;
    }

    private void deleteConfirm(int selectedCount) {
        mPopup.show(PopupType.ALERT, getString(R.string.delete_confirm_question), getString(R.string.selected_count) + " " + selectedCount, new OnPopupClickListener() {
            @Override
            public void OnNegative() {
            }

            @Override
            public void OnPositive() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (VersionData.getCloudVersion(mActivity) > 1) {
                            if (mItemAdapter != null) {
                                int i = mItemAdapter.getCount() - 1;
                                ArrayList<ListCard> cards = (ArrayList<ListCard>) mUserInfo.cards.clone();
                                for (; i >= 0; i--) {
                                    boolean isCheck = mItemAdapter.isItemChecked(i);
                                    if (isCheck) {
                                        cards.remove(i);
                                    }
                                }

                                mPopup.showWait(new DialogInterface.OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        mScreenControl.backScreen();
                                    }
                                });
                                request(mUserDataProvider.modifyCards(mUserInfo.user_id, cards, mModifyCardListener));
                            } else {
                                refreshValue();
                            }
                        } else {
                            if (mOldItemAdapter != null) {
                                int i = mOldItemAdapter.getCount() - 1;
                                for (; i >= 0; i--) {
                                    boolean isCheck = mOldItemAdapter.isItemChecked(i);
                                    if (isCheck) {
                                        mUserInfo.cards.remove(i);
                                    }
                                }
                            }
                            refreshValue();
                        }
                    }
                });
            }


        }, getString(R.string.ok), getString(R.string.cancel));
    }

    private void initValue(Bundle savedInstanceState) {
        if (mUserInfo == null) {
            mUserInfo = getExtraData(User.TAG, savedInstanceState);
        }
        Boolean disable = getExtraData(Setting.DISABLE_MODIFY, savedInstanceState);
        if (disable != null) {
            mIsDisableModify = disable;
        }

        if (mSubToolbar == null) {
            mSubToolbar = (SubToolbar) mRootView.findViewById(R.id.subtoolbar);
            mSubToolbar.init(mSubToolBarEvent, getActivity());
            mSubToolbar.setVisibleSearch(false, null);
            mSubToolbar.showMultipleSelectInfo(false, 0);
        }
        if (mUserInfo.cards == null) {
            mUserInfo.cards = new ArrayList<ListCard>();
        }

        if (VersionData.getCloudVersion(mActivity) > 1) {
            if (mItemAdapter == null) {
                mItemAdapter = new NewCardAdapter(mActivity, mUserInfo.user_id, mUserInfo.cards, getListView(), new OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        if (mSubToolbar == null) {
                            return;
                        }
                        if (mSubMode == MODE_DELETE) {
                            mSubToolbar.setSelectAllViewOff();
                            int count = mItemAdapter.getCheckedItemCount();
                            mSubToolbar.setSelectedCount(count);
                            if (count == mItemAdapter.getAvailableTotal()) {
                                if (!mSubToolbar.getSelectAll()) {
                                    mSubToolbar.showReverseSelectAll();
                                }
                            }
                        }
                    }
                }, mPopup, null, mIsDisableModify);
            }
        } else {
            if (mOldItemAdapter == null) {
                mOldItemAdapter = new CardAdapter(mActivity, mUserInfo.cards, getListView(), new OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        if (mSubToolbar == null) {
                            return;
                        }
                        if (mSubMode == MODE_DELETE) {
                            mSubToolbar.setSelectAllViewOff();
                            int count = mOldItemAdapter.getCheckedItemCount();
                            mSubToolbar.setSelectedCount(count);
                            if (count == mOldItemAdapter.getCount()) {
                                if (!mSubToolbar.getSelectAll()) {
                                    mSubToolbar.showReverseSelectAll();
                                }
                            }
                        } else {
                            mReplacePosition = position;
                            showSelectItem();
                        }
                    }
                }, mPopup, null, mIsDisableModify);
            }
        }
        refreshValue();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        unRegisterBroadcast();
        if (mUserInfo != null && mUserInfo.cards != null) {
            if (mUserInfo.card_count != mUserInfo.cards.size()) {
                mUserInfo.card_count = mUserInfo.cards.size();
            }
            try {
                sendLocalBroadcast(Setting.BROADCAST_UPDATE_CARD, (Serializable) mUserInfo.clone());
            } catch (Exception e) {

            }
        }
        if (mItemAdapter != null) {
            mItemAdapter.clearItems();
        }
        if (mOldItemAdapter != null) {
            mOldItemAdapter.clearItems();
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case R.id.action_delete_confirm:
                int selectedCount = 0;
                if (mItemAdapter != null) {
                    selectedCount = mItemAdapter.getCheckedItemCount();
                } else {
                    selectedCount = mOldItemAdapter.getCheckedItemCount();
                }
                if (selectedCount < 1) {
                    mToastPopup.show(getString(R.string.selected_none), null);
                    return true;
                }
                deleteConfirm(selectedCount);
                break;
            case R.id.action_add:
                if (mUserInfo.cards.size() >= 8) {
                    mToastPopup.show(getString(R.string.max_size), null);
                    return true;
                }
                if (VersionData.getCloudVersion(mActivity) > 1) {
                    Bundle bundle = new Bundle();
                    try {
                        bundle.putSerializable(User.TAG, mUserInfo.clone());
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                        return true;
                    }
                    mScreenControl.addScreen(ScreenType.CARD_RIGISTER, bundle);
                } else {
                    selectAddOption();
                }
                break;
            case R.id.action_delete:
                setSubMode(MODE_DELETE);
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onSearch(String query) {
        if (super.onSearch(query)) {
            return true;
        }
        if (mSelectCardPopup != null && mSelectCardPopup.isExpand()) {
            mSelectCardPopup.onSearch(query);
            return true;
        }
        if (mSelectDevicePopup != null && mSelectDevicePopup.isExpand()) {
            mSelectDevicePopup.onSearch(query);
            return true;
        }
        return true;
    }

    @Override
    protected void setSubMode(int mode) {
        mSubMode = mode;
        switch (mode) {
            case MODE_NORMAL:
                if (mItemAdapter != null) {
                    mItemAdapter.setChoiceMode(ListView.CHOICE_MODE_NONE);
                } else {
                    mOldItemAdapter.setChoiceMode(ListView.CHOICE_MODE_NONE);
                }
                mSubToolbar.showMultipleSelectInfo(false, 0);
                break;
            case MODE_DELETE:
                if (mItemAdapter != null) {
                    mItemAdapter.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                    mSubToolbar.showMultipleSelectInfo(true, mItemAdapter.getCheckedItemCount());
                } else {
                    mOldItemAdapter.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                    mSubToolbar.showMultipleSelectInfo(true, mOldItemAdapter.getCheckedItemCount());
                }

                break;
        }
        mActivity.invalidateOptionsMenu();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setResID(R.layout.fragment_list);
        super.onCreateView(inflater, container, savedInstanceState);
        if (!mIsReUsed) {
            initValue(savedInstanceState);
            initActionbar(getString(R.string.card));
            mRootView.invalidate();
        }

        if (mUserInfo == null) {
            Log.e(TAG, "data is null");
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mToastPopup.show(getString(R.string.none_data), null);
                    mScreenControl.backScreen();
                }
            }, 1000);
            return null;
        }
        if (VersionData.getCloudVersion(mActivity) > 1) {
            if (!mIsDisableModify) {
                mPopup.showWait(mCancelExitListener);
                request(mUserDataProvider.getCards( mUserInfo.user_id, mCardsListener));
            }
        }
        return mRootView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.e(TAG, "onSaveInstanceState");
        User bundleItem = null;
        try {
            bundleItem = (User) mUserInfo.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return;
        }
        outState.putSerializable(User.TAG, bundleItem);
        outState.putSerializable(Setting.DISABLE_MODIFY, mIsDisableModify);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        MenuInflater inflater = mActivity.getMenuInflater();
        if (mIsDisableModify) {
            return;
        }
        switch (mSubMode) {
            default:
            case MODE_NORMAL:
                initActionbar(getString(R.string.card));
                inflater.inflate(R.menu.add_delete, menu);
                break;
            case MODE_DELETE:
                initActionbar(getString(R.string.delete_card));
                inflater.inflate(R.menu.delete_confirm, menu);
                break;
        }
        super.onPrepareOptionsMenu(menu);
    }

    private void refreshValue() {
        clearValue();
        if (mSelectCardPopup == null) {
            mSelectCardPopup = new SelectPopup<ListCard>(mActivity, mPopup);
        }
        if (mSelectDevicePopup == null) {
            mSelectDevicePopup = new SelectPopup<ListDevice>(mActivity, mPopup);
        }
        if (mUserInfo.cards == null) {
            mUserInfo.cards = new ArrayList<ListCard>();
        }
        if (mItemAdapter != null) {
            mItemAdapter.setData(mUserInfo.cards);
            mItemAdapter.clearChoices();
            if (mSubToolbar != null) {
                mSubToolbar.setSelectedCount(mItemAdapter.getCheckedItemCount());
                if (mItemAdapter != null) {
                    mSubToolbar.setTotal(mItemAdapter.getCount());
                }
            }
        }
        if (mOldItemAdapter != null) {
            mOldItemAdapter.setData(mUserInfo.cards);
            mOldItemAdapter.clearChoices();
            if (mSubToolbar != null) {
                mSubToolbar.setSelectedCount(mOldItemAdapter.getCheckedItemCount());
                if (mOldItemAdapter != null) {
                    mSubToolbar.setTotal(mOldItemAdapter.getCount());
                }
            }
        }

    }

    @Override
    protected void registerBroadcast() {
        if (mReceiver == null) {
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (mIsDestroy) {
                        return;
                    }
                    if (action.equals(Setting.BROADCAST_UPDATE_CARD)) {
                        User user = getExtraData(Setting.BROADCAST_UPDATE_CARD, intent);
                        if (user == null || user.cards == null) {
                            if (VersionData.getCloudVersion(mActivity) > 1) {
                                if (!mIsDisableModify) {
                                    mPopup.showWait(mCancelExitListener);
                                    request(mUserDataProvider.getCards(mUserInfo.user_id, mCardsListener));
                                }
                            }
                            return;
                        }
                        if (mUserInfo != null) {
                            mUserInfo.cards = user.cards;
                            mUserInfo.card_count = user.cards.size();
                        }
                        refreshValue();
                        return;
                    }
                }
            };
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Setting.BROADCAST_UPDATE_CARD);
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mReceiver, intentFilter);
        }
    }

    private void selectAddOption() {
        mReplacePosition = mUserInfo.cards.size();
        SelectPopup<SelectCustomData> selectPopup = new SelectPopup<SelectCustomData>(mActivity, mPopup);
        ArrayList<SelectCustomData> registerationOption = new ArrayList<SelectCustomData>();
        registerationOption.add(new SelectCustomData(getString(R.string.registeration_option_card_reader), CARD_READER, false));
        registerationOption.add(new SelectCustomData(getString(R.string.registeration_option_assign_card), ASSGIGN_CARD, false));

        selectPopup.show(SelectType.CUSTOM, new OnSelectResultListener<SelectCustomData>() {
            @Override
            public void OnResult(ArrayList<SelectCustomData> selectedItem, boolean isPositive) {
                if (isInValidCheck()) {
                    return;
                }
                if (selectedItem == null) {
                    clearValue();
                    return;
                }
                int registrationOption = selectedItem.get(0).getIntId();
                switch (registrationOption) {
                    case CARD_READER: {
                        showSelectDevice();
                        break;
                    }
                    case ASSGIGN_CARD: {
                        showSelectItem();
                        break;
                    }
                }
            }
        }, registerationOption, getString(R.string.registeration_option), false, false);
    }

    private void setCard(ListCard card) {
        for (ListCard item : mUserInfo.cards) {
            // TODO card_id 인지.type까지 같이 비교해야할지..id로 비교해야할지..
            if (item.card_id.equals(card.card_id) && item.type.equals(card.type)) {
                mToastPopup.show(getString(R.string.already_assigned), null);
                return;
            }
        }
        try {
            if (mReplacePosition == -1 || mReplacePosition >= mUserInfo.cards.size()) {
                mUserInfo.cards.add(card.clone());
            } else {
                mUserInfo.cards.set(mReplacePosition, card.clone());
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, " " + e.getMessage());
            }
        }
        initValue(null);
    }

    private void showSelectDevice() {
        mSelectDevicePopup.show(SelectType.DEVICE_CARD, new OnSelectResultListener<ListDevice>() {
            @Override
            public void OnResult(ArrayList<ListDevice> selectedItem, boolean isPositive) {
                if (isInValidCheck()) {
                    return;
                }
                if (selectedItem == null) {
                    clearValue();
                    return;
                }
                mDeviceId = selectedItem.get(0).id;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mPopup.show(PopupType.CARD, mOldItemAdapter.getName(mReplacePosition), getString(R.string.card_on_device), null, null, null, false);
                        request(mDeviceDataProvider.scanCard(mDeviceId, mScanListener));
                    }
                });
            }
        }, null, getString(R.string.registeration_option_card_reader), false, true);
    }

    private void showSelectItem() {
        mSelectCardPopup.show(SelectType.CARD, new OnSelectResultListener<ListCard>() {
            @Override
            public void OnResult(ArrayList<ListCard> selectedItem, boolean isPositive) {
                if (isInValidCheck()) {
                    return;
                }
                if (selectedItem == null) {
                    clearValue();
                    return;
                }
                ListCard card = null;
                try {
                    card = selectedItem.get(0).clone();
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                    return;
                }
                setCard(card);
            }
        }, null, getString(R.string.registeration_option_assign_card), false, true);
    }
}
