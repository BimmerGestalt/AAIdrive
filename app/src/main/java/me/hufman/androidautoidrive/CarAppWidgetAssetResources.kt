package me.hufman.androidautoidrive

import android.content.Context
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import java.io.InputStream
import java.util.*

class CarAppWidgetAssetResources(context: Context, name: String): CarAppAssetResources(context, name) {
	// BMWOne has a widgets DB to upload
	fun getWidgetsDB(brand: String): InputStream? {
		return loadFile("carapplications/$name/rhmi/${brand.lowercase(Locale.ROOT)}/widgetdb.zip") ?:
		loadFile("carapplications/$name/rhmi/common/widgetdb.zip")
	}
}