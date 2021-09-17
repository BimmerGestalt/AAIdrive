package me.hufman.androidautoidrive.phoneui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import me.hufman.androidautoidrive.BR

class DataBoundArrayAdapter<T, C>(context: Context, val layoutId: Int, contents: List<T>, val callback: C? = null): ArrayAdapter<T>(context, layoutId, contents) {

	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
		val layoutInflater = LayoutInflater.from(context)
		val item = getItem(position)
		val binding = (convertView?.tag as? ViewDataBinding) ?:
			DataBindingUtil.inflate(layoutInflater, layoutId, parent, false)
		binding.setVariable(BR.data, item)
		if (callback != null) {
			binding.setVariable(BR.callback, callback)
		}
		binding.executePendingBindings()
		return binding.root
	}
}