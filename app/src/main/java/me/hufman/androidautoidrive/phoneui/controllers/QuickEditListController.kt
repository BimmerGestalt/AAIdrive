package me.hufman.androidautoidrive.phoneui.controllers

import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class QuickEditListController(val items: MutableList<String>, val itemTouchHelper: ItemTouchHelper) {
	// the adapter to notify when adding data
	var adapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>? = null

	// the input field's contents, as a LiveData to be cleared out on submit
	val currentInput = MutableLiveData("")
	fun addItem(): Boolean {
		val input = currentInput.value
		currentInput.value = ""
		if (input?.isNotBlank() == true) {
			items.add(input)
			adapter?.notifyItemInserted(items.size - 1)
		}
		return true
	}

	fun startDrag(view: View) {
		var child = view
		while (child.parent != null && child.parent !is RecyclerView) {
			child = child.parent as? View ?: return
		}
		val recyclerView = child.parent as? RecyclerView ?: return

		val viewHolder = recyclerView.getChildViewHolder(child) ?: return
		itemTouchHelper.startDrag(viewHolder)
	}
}