package me.hufman.androidautoidrive.carapp

import java.lang.Exception

/**
 * Used to signal from an RHMIActionCallback that the rhmi_onActionEvent should return success=false
 */
class RHMIActionAbort: Exception()