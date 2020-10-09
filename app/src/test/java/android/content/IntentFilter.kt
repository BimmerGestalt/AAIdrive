package android.content

class IntentFilter(val action: String? = null) {
	fun hasAction(action: String?): Boolean {
		return this.action == action
	}
}