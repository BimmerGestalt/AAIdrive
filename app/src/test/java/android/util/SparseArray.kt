package android.util

class SparseArray<E> {
	val backing = HashMap<Int, E>()

	// mutations
	fun get(key: Int) = backing[key]
	fun put(key: Int, value: E) {
		backing[key] = value
	}
	fun remove(key: Int) {
		backing.remove(key)
	}

	// interrogations
	fun size() = backing.size
	fun indexOfKey(key: Int): Int = backing.keys.indexOf(key)
	fun keyAt(index: Int) = backing.keys.sorted()[index]
	fun valueAt(index: Int) = backing[backing.keys.sorted()[index]]
}