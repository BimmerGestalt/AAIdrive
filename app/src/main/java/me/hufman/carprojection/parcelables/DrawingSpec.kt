package me.hufman.carprojection.parcelables

import android.content.Context
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.view.Surface
import me.hufman.carprojection.Gearhead

class DrawingSpec(val transport: Parcelable) {
	companion object {
		fun build(context: Context, width: Int, height: Int, dpi: Int, surface: Surface, inset: Rect): DrawingSpec {
			return DrawingSpec(Gearhead.createParcelable(context, "DrawingSpec", width, height, dpi, surface, inset))
		}
	}
}