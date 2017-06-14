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
package com.supremainc.biostar2.adapter;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.supremainc.biostar2.R;
import com.supremainc.biostar2.adapter.base.BaseCardAdapter;
import com.supremainc.biostar2.sdk.models.v2.card.ListCard;
import com.supremainc.biostar2.widget.popup.Popup;

import java.util.ArrayList;

public class SimpleCardAdapter extends BaseCardAdapter {


    public SimpleCardAdapter(Activity context, ArrayList<ListCard> items, ListView listView, OnItemClickListener itemClickListener, Popup popup, OnItemsListener onItemsListener) {
        super(context, items, listView, itemClickListener, popup, onItemsListener);
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        SimpleItemViewHolder viewHolder = (SimpleItemViewHolder) view.getTag();
        setSelector(view, viewHolder.mLink, position);
        super.onItemClick(parent, view, position, id);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (mItems == null || mItems.size() < 1) {
            return null;
        }
        if (null == convertView) {
            convertView = mInflater.inflate(R.layout.list_item, parent, false);
            SimpleItemViewHolder viewHolder = new SimpleItemViewHolder(convertView);
            convertView.setTag(viewHolder);
        }
        SimpleItemViewHolder viewHolder = (SimpleItemViewHolder) convertView.getTag();
        if (viewHolder == null) {
            viewHolder = new SimpleItemViewHolder(convertView);
            convertView.setTag(viewHolder);
        }
        ListCard item = mItems.get(position);
        if (item != null) {
            String name = item.card_id;
//            if (item.wiegand_format != null) {
//                name = name + " " + item.wiegand_format.name;
//            }
//            if (item.smart_card_layout != null) {
//                name = name + " " + item.smart_card_layout.name;
//            }
            viewHolder.mName.setText(item.card_id);
            setSelector(viewHolder.mRoot, viewHolder.mLink, position, true);
        }
        return viewHolder.mRoot;
    }
}
