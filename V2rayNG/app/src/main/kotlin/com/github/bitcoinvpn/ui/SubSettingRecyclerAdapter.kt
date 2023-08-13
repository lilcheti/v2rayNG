package com.github.bitcoinvpn.ui

import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.widget.Toast
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.github.bitcoinvpn.R
import com.github.bitcoinvpn.databinding.ItemRecyclerSubSettingBinding
import com.github.bitcoinvpn.util.MmkvManager

class SubSettingRecyclerAdapter(val activity: SubSettingActivity) : RecyclerView.Adapter<SubSettingRecyclerAdapter.MainViewHolder>() {

    private var mActivity: SubSettingActivity = activity
    private val subStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SUB, MMKV.MULTI_PROCESS_MODE) }

    override fun getItemCount() = mActivity.subscriptions.size-2

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val subId = mActivity.subscriptions[position].first
        Log.d("fuck",subId)
        if (subId != "1" && subId != "2"){
        val subItem = mActivity.subscriptions[position].second
        holder.itemSubSettingBinding.tvName.text = subItem.remarks
        holder.itemSubSettingBinding.tvUrl.text = subItem.url
        if (subItem.enabled) {
            holder.itemSubSettingBinding.chkEnable.setBackgroundResource(R.color.colorSelected)
        } else {
            holder.itemSubSettingBinding.chkEnable.setBackgroundResource(R.color.colorUnselected)
        }
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        holder.itemSubSettingBinding.layoutEdit.setOnClickListener {
            mActivity.startActivity(Intent(mActivity, SubEditActivity::class.java)
                    .putExtra("subId", subId)
            )
        }
        holder.itemSubSettingBinding.infoContainer.setOnClickListener {
            subItem.enabled = !subItem.enabled
            subStorage?.encode(subId, Gson().toJson(subItem))
            notifyItemChanged(position)
        }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(ItemRecyclerSubSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    class MainViewHolder(val itemSubSettingBinding: ItemRecyclerSubSettingBinding) : RecyclerView.ViewHolder(itemSubSettingBinding.root)
}
