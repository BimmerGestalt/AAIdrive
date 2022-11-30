package me.hufman.androidautoidrive.utils

import android.util.SparseArray

/*
API31 broke SparseArray.set extension: https://stackoverflow.com/q/69132082/169035
operator fun <E> SparseArray<E>.set(key: Int, value: E) {
	put(key, value)
}
*/

fun <E> SparseArray<E>.setDefault(key: Int, defaultValueFactory: (Int) -> E): E {
	val existingIndex = indexOfKey(key)
	return if (existingIndex >= 0) {
		get(key)
	} else {
		val new = defaultValueFactory(key)
		put(key, new)
		new
	}
}

fun <E> SparseArray<E>.forEach(block: (key: Int, value: E) -> Unit) {
	for (x in 0 until size()) {
		block(keyAt(x), valueAt(x))
	}
}