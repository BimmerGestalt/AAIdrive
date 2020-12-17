package me.hufman.androidautoidrive.carapp

import me.hufman.idriveconnectionkit.rhmi.RHMIModel

open class RHMIListAdapter<T>(width: Int, val realData: List<T>) : RHMIModel.RaListModel.RHMIList(width) {
	override fun get(index: Int): Array<Any> {
		return convertRow(index, realData[index])
	}

	open fun convertRow(index: Int, item: T): Array<Any> {
		return arrayOf("", "", item.toString())
	}

	override var height: Int
		get() = realData.size
		set(_) {}
}