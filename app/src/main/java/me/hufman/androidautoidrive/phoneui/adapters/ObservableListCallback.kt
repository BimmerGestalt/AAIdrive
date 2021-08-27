package me.hufman.androidautoidrive.phoneui.adapters

import androidx.databinding.ObservableList

class ObservableListCallback<T>(val callback: (ObservableList<T>?) -> Unit): ObservableList.OnListChangedCallback<ObservableList<T>>() {
	override fun onChanged(sender: ObservableList<T>?) {
		callback(sender)
	}

	override fun onItemRangeChanged(sender: ObservableList<T>?, positionStart: Int, itemCount: Int) {
		callback(sender)
	}

	override fun onItemRangeInserted(sender: ObservableList<T>?, positionStart: Int, itemCount: Int) {
		callback(sender)
	}

	override fun onItemRangeMoved(sender: ObservableList<T>?, fromPosition: Int, toPosition: Int, itemCount: Int) {
		callback(sender)
	}

	override fun onItemRangeRemoved(sender: ObservableList<T>?, positionStart: Int, itemCount: Int) {
		callback(sender)
	}
}