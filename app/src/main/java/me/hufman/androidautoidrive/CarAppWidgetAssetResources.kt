package me.hufman.androidautoidrive

import android.content.Context
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import java.io.InputStream

class CarAppWidgetAssetResources(context: Context, name: String): CarAppAssetResources(context, name) {
	// BMWOne has a widgets DB to upload
	fun getWidgetsDB(brand: String): InputStream? {
		return loadFile("carapplications/$name/rhmi/${brand.toLowerCase()}/widgetdb.zip") ?:
		loadFile("carapplications/$name/rhmi/common/widgetdb.zip")
	}
}