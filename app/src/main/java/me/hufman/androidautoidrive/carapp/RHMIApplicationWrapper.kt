package me.hufman.androidautoidrive.carapp

import me.hufman.idriveconnectionkit.rhmi.RHMIApplication

interface RHMIApplicationWrapper {
	fun unwrap(): RHMIApplication
}