package me.hufman.androidautoidrive.carapp.music

import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.Utils.loadZipfile
import me.hufman.androidautoidrive.carapp.RHMIApplicationIdempotent
import me.hufman.androidautoidrive.carapp.RHMIApplicationSynchronized
import me.hufman.androidautoidrive.carapp.music.views.*
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.removeFirst
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.*
import java.util.*

const val TAG = "MusicApp"

class MusicApp(val carAppAssets: CarAppResources, val phoneAppResources: PhoneAppResources, val musicAppDiscovery: MusicAppDiscovery, val musicController: MusicController) {
	val carApp = createRHMIApp()

	val avContext = AVContextHandler(((carApp.app as RHMIApplicationIdempotent).app as RHMIApplicationEtch), musicController, phoneAppResources)
	val globalMetadata = GlobalMetadata(carApp, musicController)
	var playbackViewVisible = false
	val playbackView: PlaybackView
	val appSwitcherView: AppSwitcherView
	val enqueuedView: EnqueuedView
	val browseView: BrowseView
	val inputState: RHMIState
	val customActionsView: CustomActionsView

	private fun createRHMIApp(): RHMIApplicationSynchronized {
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
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getTextsDB(IDriveConnectionListener.brand ?: "common")?.readBytes(), BMWRemoting.RHMIResourceType.TEXTDB)
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getImagesDB(IDriveConnectionListener.brand ?: "common")?.readBytes(), BMWRemoting.RHMIResourceType.IMAGEDB)
		carConnection.rhmi_initialize(rhmiHandle)

		// set up the app in the car
		val carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)))
		carappListener.app = carApp
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.music", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.music", -1, -1)

		return carApp
	}

	init {
		// locate specific windows in the app
		val carAppImages = loadZipfile(carAppAssets.getImagesDB(IDriveConnectionListener.brand ?: "common"))
		val unclaimedStates = LinkedList(carApp.states.values)
		playbackView = PlaybackView(unclaimedStates.removeFirst { PlaybackView.fits(it) }, musicController, carAppImages, phoneAppResources)
		appSwitcherView = AppSwitcherView(unclaimedStates.removeFirst { AppSwitcherView.fits(it) }, musicAppDiscovery, avContext, phoneAppResources)
		enqueuedView = EnqueuedView(unclaimedStates.removeFirst { EnqueuedView.fits(it) }, musicController, phoneAppResources)
		browseView = BrowseView(listOf(unclaimedStates.removeFirst { BrowseView.fits(it) }, unclaimedStates.removeFirst { BrowseView.fits(it) }), musicController)
		inputState = unclaimedStates.removeFirst { it.componentsList.filterIsInstance<RHMIComponent.Input>().firstOrNull()?.suggestModel ?: 0 > 0 }
		customActionsView = CustomActionsView(unclaimedStates.removeFirst { CustomActionsView.fits(it) }, phoneAppResources, musicController)

		Log.i(TAG, "Selected state ${appSwitcherView.state.id} for App Switcher")
		Log.i(TAG, "Selected state ${playbackView.state.id} for Playback")
		Log.i(TAG, "Selected state ${enqueuedView.state.id} for Enqueued")
		Log.i(TAG, "Selected state ${inputState.id} for Input")
		Log.i(TAG, "Selected state ${customActionsView.state.id} for Custom Actions")

		initWidgets()

		musicAppDiscovery.listener = Runnable {
			avContext.updateApps(musicAppDiscovery.validApps)
		}
		avContext.updateApps(musicAppDiscovery.validApps)

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
			val msg = "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId args=${args?.toString()}"
			Log.i(TAG, msg)
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
			if (componentId == enqueuedView.state.id &&
					eventId == 1 &&
					args?.get(4.toByte()) as? Boolean == true)   // Focus
			{
				enqueuedView.show()
			}
			if (componentId == customActionsView.state.id &&
					eventId == 1 &&
					args?.get(4.toByte()) as? Boolean == true) {
				customActionsView.show()
			}

			// generic event handler
			app?.states?.get(componentId)?.onHmiEvent(eventId, args)
			app?.components?.get(componentId)?.onHmiEvent(eventId, args)
		}

		override fun av_connectionGranted(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
			val msg = "Received av_connectionGranted: handle=$handle connectionType=$connectionType"
			Log.i(TAG, msg)
			synchronized(avContext) {
				avContext.av_connectionGranted(handle, connectionType)
			}
		}

		override fun av_connectionDeactivated(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
			val msg = "Received av_connectionDeactivated: handle=$handle connectionType=$connectionType"
			Log.i(TAG, msg)
			synchronized(avContext) {
				avContext.av_connectionDeactivated(handle, connectionType)
			}
		}

		override fun av_requestPlayerState(handle: Int?, connectionType: BMWRemoting.AVConnectionType?, playerState: BMWRemoting.AVPlayerState?) {
			val msg = "Received av_requestPlayerState: handle=$handle connectionType=$connectionType playerState=$playerState"
			Log.i(TAG, msg)
			synchronized(avContext) {
				avContext.av_requestPlayerState(handle, connectionType, playerState)
			}
		}

		override fun av_multimediaButtonEvent(handle: Int?, event: BMWRemoting.AVButtonEvent?) {
			val msg = "Received av_multimediaButtonEvent: handle=$handle event=$event"
			Log.i(TAG, msg)
			avContext.av_multimediaButtonEvent(handle, event)
		}

		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			Log.i(TAG, "Received am_onAppEvent: handle=$handle ident=$ident appId=$appId event=$event")
			if (appId != null) {
				avContext.av_requestContext(appId)
			}
			app?.events?.values?.filterIsInstance<RHMIEvent.FocusEvent>()?.firstOrNull()?.triggerEvent(mapOf(0.toByte() to playbackView.state.id))
			server?.am_showLoadedSuccessHint(avContext.amHandle)
		}
	}

	private fun initWidgets() {
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			it.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
				if (musicController.currentApp == null || musicController.currentApp?.connected != true) {
					it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = appSwitcherView.state.id
				} else {
					it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id

					val desiredApp = avContext.desiredApp
					if (desiredApp != null) {
						avContext.av_requestContext(desiredApp)
					}
				}
			}
		}

		globalMetadata.initWidgets()
		appSwitcherView.initWidgets(playbackView)
		playbackView.initWidgets(appSwitcherView, enqueuedView, browseView, customActionsView)
		enqueuedView.initWidgets(playbackView)
		browseView.initWidgets(playbackView, inputState)
		customActionsView.initWidgets(playbackView)
	}

	fun redraw() {
		if (playbackViewVisible) {
			playbackView.redraw()
		}
		globalMetadata.redraw()
	}

}