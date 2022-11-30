package me.hufman.androidautoidrive.utils

class CachedData<E>(val ttl: Long, val _timeProvider: () -> Long = {System.currentTimeMillis()}, val provider: () -> E?) {
	// when the data was last updated
	var lastChangedTime = 0L
		private set

	// whether to serve results from the cache
	var enabled = false

	// the actual data
	var value: E? = null
		set(value) {
			field = value
			lastChangedTime = _timeProvider()
		}
		get() {
			if (!enabled || lastChangedTime + ttl < _timeProvider()) {
				value = provider()
			}
			return field
		}
}