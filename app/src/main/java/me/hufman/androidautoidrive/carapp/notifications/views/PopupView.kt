package me.hufman.androidautoidrive.carapp.notifications.views

import me.hufman.androidautoidrive.notifications.CarNotification

interface PopupView {
	var currentNotification: CarNotification?
	fun initWidgets()
	fun showNotification(sbn: CarNotification)
	fun hideNotification()
}