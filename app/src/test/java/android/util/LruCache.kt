package android.util

import java.util.*
import kotlin.collections.HashMap

class LruCache<K, V>(val size: Int) {
	val order = LinkedList<K>()
	val storage = HashMap<K, V>(size)

	fun put(key: K, value: V): V {
		// move the existing key to the front of the list
		order.remove(key)
		order.push(key)

		// if size is too big, prune
		if (order.size > size) {
			val removeKey = order.removeLast()
			storage.remove(removeKey)
		}

		storage[key] = value
		return value
	}

	fun get(key: K): V? {
		// move the key to the front of the list
		val exists = order.remove(key)
		if (exists) {
			order.push(key)
		}

		return storage[key]
	}
}