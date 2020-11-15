package android.content

class IntentFilter(val action: String? = null) {
	val addedActions = HashSet<String>()
	fun addAction(action: String?) {
		action ?: return
		addedActions.add(action)
	}
	fun hasAction(action: String?): Boolean {
		return this.action == action ||
				addedActions.contains(action)
	}
}