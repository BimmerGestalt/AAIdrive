package me.hufman.carprojection.parcelables

import android.content.Context
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import me.hufman.carprojection.Gearhead

class InputFocusChangedEvent(val transport: Parcelable): Parcelable by transport {
	companion object CREATOR : Parcelable.Creator<InputFocusChangedEvent> {
		override fun createFromParcel(parcel: Parcel): InputFocusChangedEvent {
			TODO()
		}

		override fun newArray(size: Int): Array<InputFocusChangedEvent?> {
			return arrayOfNulls(size)
		}

		fun build(context: Context, hasFocus: Boolean, inTouchMode: Boolean, direction: Int, focusRect: Rect): InputFocusChangedEvent {
			return InputFocusChangedEvent(Gearhead.createParcelable(context, "InputFocusChangedEvent", hasFocus, inTouchMode, direction, focusRect))
		}
	}
}