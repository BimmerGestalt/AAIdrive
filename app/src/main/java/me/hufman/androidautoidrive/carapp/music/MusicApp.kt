package me.hufman.androidautoidrive.carapp.music

import android.os.Handler
import android.os.Looper
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.carapp.music.views.AppSwitcherView
import me.hufman.androidautoidrive.carapp.music.views.PlaybackView
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.removeFirst
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.RHMIAction
import me.hufman.idriveconnectionkit.rhmi.RHMIApplication
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationEtch
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import java.util.*

const val TAG = "MusicApp"

class MusicApp(val carAppAssets: CarAppResources, val phoneAppResources: PhoneAppResources, val musicAppDiscovery: MusicAppDiscovery, val musicController: MusicController) {
	val carApp = createRHMIApp()

	var playbackViewVisible = false
	val playbackView: PlaybackView
	val appSwitcherView: AppSwitcherView

	private fun createRHMIApp(): RHMIApplication {
		val carappListener = CarAppListener()
		val carConnection = IDriveConnection.getEtchConnection(IDriveConnectionListener.host ?: "127.0.0.1", IDriveConnectionListener.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = SecurityService.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.music", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive.music", "me.hufman"))
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getUiDescription()?.readBytes(), BMWRemoting.RHMIResourceType.DESCRIPTION)
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getTextsDB("common")?.readBytes(), BMWRemoting.RHMIResourceType.TEXTDB)
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getImagesDB("common")?.readBytes(), BMWRemoting.RHMIResourceType.IMAGEDB)
		carConnection.rhmi_initialize(rhmiHandle)

		// set up the app in the car
		val carApp = RHMIApplicationEtch(carConnection, rhmiHandle)
		carappListener.app = carApp
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.music", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.music", -1, -1)

		return carApp
	}

	init {
		// locate specific windows in the app
		val unclaimedStates = LinkedList(carApp.states.values)
		playbackView = PlaybackView(unclaimedStates.removeFirst { PlaybackView.fits(it) }, musicController, phoneAppResources)
		appSwitcherView = AppSwitcherView(unclaimedStates.removeFirst { AppSwitcherView.fits(it) }, musicAppDiscovery, musicController, phoneAppResources)

		Log.i(TAG, "Selected state ${appSwitcherView.state.id} for App Switcher")
		Log.i(TAG, "Selected state ${playbackView.state.id} for Playback")

		initWidgets()

		musicController.listener = Runnable {
			redraw()
		}
	}

	inner class CarAppListener: BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null
		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
//			Log.i(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId")
			try {
				app?.actions?.get(actionId)?.asRAAction()?.rhmiActionCallback?.onActionEvent(args)
			} catch (e: Exception) {
				Log.e(TAG, "Exception while calling onActionEvent handler!", e)
			}
			server?.rhmi_ackActionEvent(handle, actionId, 1, true)
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
//			val msg = "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId args=${args?.toString()}"
//			Log.i(TAG, msg)
			if (componentId == appSwitcherView.state.id &&
					eventId == 1 // FOCUS event
			) {
				appSwitcherView.show()
			}
			if (componentId == playbackView.state.id &&
					eventId == 11 // VISIBLE event
			) {
				playbackViewVisible = args?.get(23.toByte()) as? Boolean == true
				// redraw after a new window is shown
				if (playbackViewVisible) {
					playbackView.show()
				}
			}
		}
	}

	private fun initWidgets() {
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			it.getAction()?.asRAAction()?.rhmiActionCallback = object: RHMIAction.RHMIActionCallback {
				override fun onActionEvent(args: Map<*, *>?) {
					if (musicController.currentApp == null) {
						it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = appSwitcherView.state.id
					} else {
						it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id
					}
				}
			}
		}

		appSwitcherView.initWidgets(playbackView)
		playbackView.initWidgets(appSwitcherView)
	}

	fun redraw() {
		if (playbackViewVisible) {
			playbackView.redraw()
		}
	}

}