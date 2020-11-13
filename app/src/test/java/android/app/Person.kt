package android.app

import android.graphics.drawable.Icon
import android.os.Parcel
import android.os.Parcelable

class Person(): Parcelable {
	override fun writeToParcel(p0: Parcel?, p1: Int) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun describeContents(): Int {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	fun getIcon(): Icon {
		return Icon.createWithContentUri("content:///mock")
	}
}