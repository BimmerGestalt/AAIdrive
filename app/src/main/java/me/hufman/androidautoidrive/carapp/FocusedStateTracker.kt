package me.hufman.androidautoidrive.carapp

/**
 * Used by an HMI Event handler to track which state is currently focused
 */
class FocusedStateTracker {
	private val _isFocused = HashMap<Int, Boolean>()
	val isFocused: Map<Int, Boolean> = _isFocused

	fun onFocus(id: Int, focused: Boolean = true) {
		_isFocused[id] = focused
	}

	fun onBlur(id: Int) {
		_isFocused[id] = false
	}

	fun getFocused(): Int? {
		return isFocused.filterValues { it }.keys.firstOrNull()
	}
}