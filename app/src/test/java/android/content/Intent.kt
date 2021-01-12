package android.content

class Intent(val action: String) {
	var packageName = ""
	val _extras = HashMap<String, Any>()
	var flags: Int = 0

	constructor(context: Context, cls: Class<Any>) : this("")

	fun setPackage(packageName: String): Intent {
		this.packageName = packageName
		return this
	}
	fun putExtra(key: String, value: String): Intent {
		_extras[key] = value
		return this
	}
	fun getStringExtra(key: String): String? {
		return _extras[key] as? String
	}
	fun setFlags(flags: Int): Intent {
		this.flags = flags
		return this
	}
}