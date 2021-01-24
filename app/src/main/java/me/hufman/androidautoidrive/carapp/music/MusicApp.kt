package me.hufman.androidautoidrive.carapp.music

import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.utils.Utils.loadZipfile
import me.hufman.androidautoidrive.carapp.*
import me.hufman.androidautoidrive.carapp.music.views.*
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.utils.removeFirst
import me.hufman.idriveconnectionkit.CDS
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationIdempotent
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationSynchronized
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.*
import java.util.*

const val TAG = "MusicApp"

class MusicApp(val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, val carAppAssets: CarAppResources, val musicImageIDs: MusicImageIDs, val phoneAppResources: PhoneAppResources, val graphicsHelpers: GraphicsHelpers, val musicAppDiscovery: MusicAppDiscovery, val musicController: MusicController, val musicAppMode: MusicAppMode) {
	val carConnection: BMWRemotingServer
	var rhmiHandle = -1
	val carAppSwappable: RHMIApplicationSwappable
	val carApp: RHMIApplication

	val avContext: AVContextHandler
	val amAppList: AMAppList<MusicAppInfo>

	val globalMetadata: GlobalMetadata
	var hmiContextChangedTime = 0L
	val playbackView: PlaybackView
	val appSwitcherView: AppSwitcherView
	val enqueuedView: EnqueuedView
	val browseView: BrowseView
	val inputState: RHMIState
	val customActionsView: CustomActionsView

	init {
		val cdsData = CDSDataProvider()
		val carappListener = CarAppListener(cdsData)
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		synchronized(carConnection) {
			carAppSwappable = RHMIApplicationSwappable(createRhmiApp())
			carApp = RHMIApplicationSynchronized(carAppSwappable, carConnection)
			carappListener.app = carApp
			carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

			avContext = AVContextHandler(iDriveConnectionStatus, carConnection, musicController, graphicsHelpers, musicAppMode)

			// locate specific windows in the app
			val carAppImages = loadZipfile(carAppAssets.getImagesDB(iDriveConnectionStatus.brand
					?: "common"))
			val unclaimedStates = LinkedList(carApp.states.values)
			val playbackStates = LinkedList<RHMIState>().apply {
				addAll(carApp.states.values.filterIsInstance<RHMIState.AudioHmiState>())
				addAll(carApp.states.values.filterIsInstance<RHMIState.ToolbarState>())
			}
			playbackView = PlaybackView(playbackStates.removeFirst { PlaybackView.fits(it) }, musicController, carAppImages, phoneAppResources, graphicsHelpers, musicImageIDs)
			appSwitcherView = AppSwitcherView(unclaimedStates.removeFirst { AppSwitcherView.fits(it) }, musicAppDiscovery, avContext, graphicsHelpers, musicImageIDs)
			enqueuedView = EnqueuedView(unclaimedStates.removeFirst { EnqueuedView.fits(it) }, musicController, graphicsHelpers, musicImageIDs)
			browseView = BrowseView(listOf(unclaimedStates.removeFirst { BrowseView.fits(it) }, unclaimedStates.removeFirst { BrowseView.fits(it) }, unclaimedStates.removeFirst { BrowseView.fits(it) }), musicController, musicImageIDs, graphicsHelpers, this)
			inputState = unclaimedStates.removeFirst { it.componentsList.filterIsInstance<RHMIComponent.Input>().firstOrNull()?.suggestModel ?: 0 > 0 }
			customActionsView = CustomActionsView(unclaimedStates.removeFirst { CustomActionsView.fits(it) }, graphicsHelpers, musicController)
			globalMetadata = GlobalMetadata(carApp, musicController)

			Log.i(TAG, "Selected state ${appSwitcherView.state.id} for App Switcher")
			Log.i(TAG, "Selected state ${playbackView.state.id} for Playback")
			Log.i(TAG, "Selected state ${enqueuedView.state.id} for Enqueued")
			Log.i(TAG, "Selected state ${browseView.states[0].id} for Browse Page 1")
			Log.i(TAG, "Selected state ${browseView.states[1].id} for Browse Page 2")
			Log.i(TAG, "Selected state ${browseView.states[2].id} for Browse Page 3")
			Log.i(TAG, "Selected state ${inputState.id} for Input")
			Log.i(TAG, "Selected state ${customActionsView.state.id} for Custom Actions")

			initWidgets()

			// listen for HMI Context events
			cdsData.setConnection(CDSConnectionEtch(carConnection))
			cdsData.subscriptions.defaultIntervalLimit = 50
			cdsData.subscriptions[CDS.HMI.GRAPHICALCONTEXT] = {
				hmiContextChangedTime = System.currentTimeMillis()
			}
		}

		// set up AM Apps
		amAppList = AMAppList(carConnection, graphicsHelpers, "me.hufman.androidautoidrive.music")

		musicAppDiscovery.listener = Runnable {
			// make sure the car has AV Context
			avContext.createAvHandle()

			// switch the interface to the currently playing app
			val nowPlaying = musicController.musicSessions.getPlayingApp()
			val changedApp = musicController.currentAppInfo != nowPlaying
			if (nowPlaying != null && changedApp) {
				val discoveredApp = musicAppDiscovery.validApps.firstOrNull {
					it == nowPlaying
				} ?: nowPlaying
				musicController.connectAppAutomatically(discoveredApp)
			}

			// connect to the previous app, if we aren't currently connected
			if (musicController.currentAppController == null) {
				val appName = musicController.loadDesiredApp()
				val appInfo = musicAppDiscovery.validApps.firstOrNull { it.packageName == appName }
				if (appInfo != null) {
					musicController.connectAppAutomatically(appInfo)
				}
			}

			// update the AM apps list
			updateAmApps()

			// redraw the internal app list
			if (appSwitcherView.visible) {
				appSwitcherView.redraw()
			}
		}
		musicAppDiscovery.discoverApps()    // trigger the discovery, to show the apps when the handler starts running

		musicController.listener = Runnable {
			redraw()
		}
	}

	private fun createRhmiApp(): RHMIApplication {
		// create the app in the car
		rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.music", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive.music", "me.hufman"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppAssets.getTextsDB(iDriveConnectionStatus.brand ?: "common"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB(iDriveConnectionStatus.brand ?: "common"))
		carConnection.rhmi_initialize(rhmiHandle)

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.music", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.music", -1, -1)

		// return a convenient adapter
		return RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle))
	}

	private fun recreateRhmiApp() {
		synchronized(carConnection) {
			// pause events to the underlying connection
			carAppSwappable.isConnected = false
			// destroy the previous RHMI app
			carConnection.rhmi_dispose(rhmiHandle)

			// clear out the cached displayed values
			globalMetadata.forgetDisplayedInfo()
			playbackView.forgetDisplayedInfo()
			enqueuedView.forgetDisplayedInfo()

			// create a new one
			carAppSwappable.app = createRhmiApp()
			// reconnect, triggering a sync down to the new RHMI Etch app
			carAppSwappable.isConnected = true
		}
	}

	fun updateAmApps() {
		val amRadioAdjustment = musicAppMode.getRadioAppName()?.let {AMAppInfo.getAppWeight(it) - (800 - 500)} ?: 0
		val amSpotifyAdjustment = AMAppInfo.getAppWeight("Spotify") - (800 - 500)

		val amApps = musicAppDiscovery.validApps.filter {
			!(it.packageName == "com.spotify.music" && playbackView.state is RHMIState.AudioHmiState)
		}.map {
			// enforce some AM settings
			when {
				musicAppMode.isId4() -> {
					// if we are in id4, don't show any Radio icons
					it.clone(forcedCategory = AMCategory.MULTIMEDIA)
				}
				playbackView.state is RHMIState.AudioHmiState && it.category == AMCategory.MULTIMEDIA -> {
					// if we are the Spotify icon, adjust the other Multimedia icons to sort properly
					it.clone(weightAdjustment = amSpotifyAdjustment)
				}
				it.category == AMCategory.RADIO -> {
					it.clone(weightAdjustment = amRadioAdjustment)
				}
				else -> {
					it.clone()
				}
			}
		}
		amAppList.setApps(amApps)
	}

	inner class CarAppListener(val cdsEventHandler: CDSEventHandler): BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null
		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
//			Log.i(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId")
			try {
				app?.actions?.get(actionId)?.asRAAction()?.rhmiActionCallback?.onActionEvent(args)
				synchronized(server!!) {
					server?.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			} catch (e: RHMIActionAbort) {
				// Action handler requested that we don't claim success
				synchronized(server!!) {
					server?.rhmi_ackActionEvent(handle, actionId, 1, false)
				}
			} catch (e: Exception) {
				Log.e(TAG, "Exception while calling onActionEvent handler!", e)
				synchronized(server!!) {
					server?.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			}
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
			val msg = "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId args=${args?.toString()}"
			Log.i(TAG, msg)
			try {
				// generic event handler
				app?.states?.get(componentId)?.onHmiEvent(eventId, args)
				app?.components?.get(componentId)?.onHmiEvent(eventId, args)
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling rhmi_onHmiEvent", e)
			}
		}

		override fun av_connectionGranted(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
			val msg = "Received av_connectionGranted: handle=$handle connectionType=$connectionType"
			Log.i(TAG, msg)
			try {
				avContext.av_connectionGranted()
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling av_connectionGranted", e)
			}
		}

		override fun av_connectionDeactivated(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
			val msg = "Received av_connectionDeactivated: handle=$handle connectionType=$connectionType"
			Log.i(TAG, msg)
			try {
				avContext.av_connectionDeactivated()
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling av_connectionDeactivated", e)
			}
		}

		override fun av_connectionDenied(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
			val msg = "Received av_connectionDenied: handle=$handle connectionType=$connectionType"
			Log.i(TAG, msg)
		}

		override fun av_requestPlayerState(handle: Int?, connectionType: BMWRemoting.AVConnectionType?, playerState: BMWRemoting.AVPlayerState?) {
			val msg = "Received av_requestPlayerState: handle=$handle connectionType=$connectionType playerState=$playerState"
			Log.i(TAG, msg)
			try {
				avContext.av_requestPlayerState(handle, connectionType, playerState)
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling av_requestPlayerState", e)
			}
		}

		override fun av_multimediaButtonEvent(handle: Int?, event: BMWRemoting.AVButtonEvent?) {
			val msg = "Received av_multimediaButtonEvent: handle=$handle event=$event"
			Log.i(TAG, msg)
			try {
				avContext.av_multimediaButtonEvent(event)
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling av_multimediaButtonEvent", e)
			}
		}

		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			Log.i(TAG, "Received am_onAppEvent: handle=$handle ident=$ident appId=$appId event=$event")
			appId ?: return
			val appInfo = amAppList.getAppInfo(appId) ?: return
			avContext.av_requestContext(appInfo)
			val focusEvent = app?.events?.values?.filterIsInstance<RHMIEvent.FocusEvent>()?.sortedBy { it.id }?.firstOrNull()
			try {
				focusEvent?.triggerEvent(mapOf(0.toByte() to playbackView.state.id))
			} catch (e: Exception) {
				Log.i(TAG, "Failed to trigger focus event for AM icon, recreating RHMI and trying again")
				try {
					recreateRhmiApp()
					focusEvent?.triggerEvent(mapOf(0.toByte() to playbackView.state.id))
				} catch (e: Exception) {
					Log.e(TAG, "Received exception while handling am_onAppEvent", e)
				}
			}
			synchronized(server!!) {
				amAppList.redrawApp(appInfo)
			}
		}

		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			cdsEventHandler.onPropertyChangedEvent(ident, propertyValue)
		}
	}

	private fun initWidgets() {
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			it.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
				if (musicController.currentAppController == null) {
					it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = appSwitcherView.state.id
				} else {
					it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = playbackView.state.id

					// invoked manually, not by a shortcut button
					// when the Media button is pressed, it shows the Media/Radio window for a short time
					// and then selects the Entrybutton
					val contextChangeDelay = System.currentTimeMillis() - hmiContextChangedTime
					if (musicAppMode.shouldId5Playback() && contextChangeDelay > 1500) {
						// there's no spotify AM icon for the user to push
						// so handle this spotify icon push
						// but only if the user has dwelled on a screen for a second
						val spotifyApp = musicAppDiscovery.validApps.firstOrNull {
							it.packageName == "com.spotify.music"
						}
						if (spotifyApp != null) {
							avContext.av_requestContext(spotifyApp)
						}
					}

					val currentApp = musicController.currentAppInfo
					if (currentApp != null) {
						avContext.av_requestContext(currentApp)
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
		// check if we need to create an av handle, if it's missing
		avContext.createAvHandle()
		if (appSwitcherView.visible) {
			appSwitcherView.redraw()
		}
		if (playbackView.visible || playbackView.state is RHMIState.AudioHmiState) {
			playbackView.redraw()
		}
		if (enqueuedView.visible) {
			enqueuedView.redraw()
		}
		if (browseView.visible) {
			browseView.redraw()
		}

		// if running over USB or audio context is granted, set the global metadata
		if (!musicAppMode.shouldRequestAudioContext() || avContext.currentContext) {
			globalMetadata.redraw()
		}
	}

}