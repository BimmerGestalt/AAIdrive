package android.content

import android.os.Parcelable

class Intent(val action: String) {
	var packageName = ""
	val _extras = HashMap<String, Any>()

	fun setPackage(packageName: String): Intent {
		this.packageName = packageName
		return this
	}
	fun putExtra(key: String, value: Any): Intent {
		_extras[key] = value
		return this
	}
	fun putExtra(key: String, value: String): Intent {
		_extras[key] = value
		return this
	}
	fun hasExtra(key: String): Boolean {
		return _extras.containsKey(key)
	}
	fun getBooleanExtra(key: String, defaultValue: Boolean): Boolean {
		return _extras[key] as? Boolean ?: defaultValue
	}
	fun getIntExtra(key: String, defaultValue: Int): Int {
		return _extras[key] as? Int ?: defaultValue
	}
	fun getLongExtra(key: String, defaultValue: Long): Long {
		return _extras[key] as? Long ?: defaultValue
	}
	fun getParcelableArrayExtra(key: String): Array<Parcelable>? {
		return _extras[key] as? Array<Parcelable>
	}
	fun getParcelableExtra(key: String): Parcelable? {
		return _extras[key] as? Parcelable
	}
	fun getShortExtra(key: String, defaultValue: Short): Short {
		return _extras[key] as? Short ?: defaultValue
	}
	fun getStringExtra(key: String): String? {
		return _extras[key] as? String
	}
}