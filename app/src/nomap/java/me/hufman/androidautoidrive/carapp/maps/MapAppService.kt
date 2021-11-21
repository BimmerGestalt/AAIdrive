package me.hufman.androidautoidrive.carapp.maps

import me.hufman.androidautoidrive.carapp.CarAppService

class MapAppService: CarAppService() {
	override fun onCarStart() {
		stopSelf()
	}

	override fun onCarStop() {
	}
}