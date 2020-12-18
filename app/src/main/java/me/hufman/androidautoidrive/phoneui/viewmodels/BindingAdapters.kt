package me.hufman.androidautoidrive.phoneui.viewmodels

import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import androidx.databinding.BindingAdapter
import me.hufman.androidautoidrive.phoneui.visible


@BindingAdapter("android:src")
fun setImageViewBitmap(view: ImageView, bitmap: Bitmap) {
	view.setImageBitmap(bitmap)
}
@BindingAdapter("android:src")
fun setImageViewResource(view: ImageView, resource: Int) {
	view.setImageResource(resource)
}

@BindingAdapter("android:visibility")
fun setViewVisibility(view: View, visible: Boolean) {
	view.visible = visible
}