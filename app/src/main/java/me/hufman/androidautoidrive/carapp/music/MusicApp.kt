package me.hufman.androidautoidrive.carapp.music

import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.GraphicsHelpers
import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.Utils.loadZipfile
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.RHMIUtils
import me.hufman.androidautoidrive.carapp.music.views.*
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.removeFirst
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationIdempotent
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationSynchronized
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.*
import java.util.*

const val TAG = "MusicApp"

class MusicApp(val securityAccess: SecurityAccess, val carAppAssets: CarAppResources, val musicImageIDs: MusicImageIDs, val phoneAppResources: PhoneAppResources, val graphicsHelpers: GraphicsHelpers, val musicAppDiscovery: MusicAppDiscovery, val musicController: MusicController, val musicAppMode: MusicAppMode) {
	val carApp = createRHMIApp()

	val avContext = AVContextHandler(carApp, musicController, graphicsHelpers, musicAppMode)
	val globalMetadata = GlobalMetadata(carApp, musicController)
	var hmiContextChangedTime = 0L
	var appListViewVisible = false
	var playbackViewVisible = false
	var enqueuedViewVisible = false
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
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.music", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive.music", "me.hufman"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppAssets.getTextsDB(IDriveConnectionListener.brand ?: "common"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB(IDriveConnectionListener.brand ?: "common"))
		carConnection.rhmi_initialize(rhmiHandle)

		// set up the app in the car
		val carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)))
		carappListener.app = carApp
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		// register for events from the car
		carApp.runSynchronized {
			carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.music", -1)
			carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.music", -1, -1)

			// listen for HMI Context events
			val cdsHandle = carConnection.cds_create()
			carConnection.cds_addPropertyChangedEventHandler(cdsHandle, "hmi.graphicalContext", "114", 50)
		}

		return carApp
	}

	init {
		// locate specific windows in the app
		val carAppImages = loadZipfile(carAppAssets.getImagesDB(IDriveConnectionListener.brand ?: "common"))
		val unclaimedStates = LinkedList(carApp.states.values)
		val playbackStates = LinkedList<RHMIState>().apply {
			addAll(carApp.states.values.filterIsInstance<RHMIState.AudioHmiState>())
			addAll(carApp.states.values.filterIsInstance<RHMIState.ToolbarState>())
		}
		playbackView = PlaybackView(playbackStates.removeFirst { PlaybackView.fits(it) }, musicController, carAppImages, phoneAppResources, graphicsHelpers, musicImageIDs)
		appSwitcherView = AppSwitcherView(unclaimedStates.removeFirst { AppSwitcherView.fits(it) }, musicAppDiscovery, avContext, graphicsHelpers, musicImageIDs)
		enqueuedView = EnqueuedView(unclaimedStates.removeFirst { EnqueuedView.fits(it) }, musicController, graphicsHelpers, musicImageIDs)
		browseView = BrowseView(listOf(unclaimedStates.removeFirst { BrowseView.fits(it) }, unclaimedStates.removeFirst { BrowseView.fits(it) }), musicController, musicImageIDs)
		inputState = unclaimedStates.removeFirst { it.componentsList.filterIsInstance<RHMIComponent.Input>().firstOrNull()?.suggestModel ?: 0 > 0 }
		customActionsView = CustomActionsView(unclaimedStates.removeFirst { CustomActionsView.fits(it) }, graphicsHelpers, musicController)

		Log.i(TAG, "Selected state ${appSwitcherView.state.id} for App Switcher")
		Log.i(TAG, "Selected state ${playbackView.state.id} for Playback")
		Log.i(TAG, "Selected state ${enqueuedView.state.id} for Enqueued")
		Log.i(TAG, "Selected state ${inputState.id} for Input")
		Log.i(TAG, "Selected state ${customActionsView.state.id} for Custom Actions")

		initWidgets()

		musicAppDiscovery.listener = Runnable {
			avContext.updateApps(musicAppDiscovery.connectableApps)
			// redraw the app list
			if (appListViewVisible) {
				appSwitcherView.redraw()
			}
			// switch the interface to the currently playing app
			val nowPlaying = musicController.musicSessions.getPlayingApp()
			val changedApp = musicController.currentAppInfo != nowPlaying
			if (nowPlaying != null && changedApp) {
				val discoveredApp = musicAppDiscovery.validApps.firstOrNull {
					it == nowPlaying
				} ?: nowPlaying
				musicController.connectAppAutomatically(discoveredApp)
			}
		}
		musicAppDiscovery.discoverApps()    // trigger the discovery, to show the apps when the handler starts running

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
				carApp.runSynchronized {
					server?.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			} catch (e: RHMIActionAbort) {
				// Action handler requested that we don't claim success
				carApp.runSynchronized {
					server?.rhmi_ackActionEvent(handle, actionId, 1, false)
				}
			} catch (e: Exception) {
				Log.e(TAG, "Exception while calling onActionEvent handler!", e)
				carApp.runSynchronized {
					server?.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			}
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
			val msg = "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId args=${args?.toString()}"
			Log.i(TAG, msg)
			try {
				if (componentId == appSwitcherView.state.id &&
						eventId == 1 // FOCUS event
				) {
					appListViewVisible = args?.get(4.toByte()) as? Boolean == true
					if (appListViewVisible) {
						appSwitcherView.show()
						musicAppDiscovery.discoverAppsAsync()
					}
				}
				if (componentId == playbackView.state.id &&
						eventId == 1 // FOCUS event
				) {
					playbackViewVisible = args?.get(4.toByte()) as? Boolean == true
					// redraw after a new window is shown
					if (playbackViewVisible) {
						playbackView.show()
					}
				}
				//gained focus
				if (componentId == enqueuedView.state.id &&
						eventId == 1 &&
						args?.get(4.toByte()) as? Boolean == true
				) {
					enqueuedViewVisible = true
					enqueuedView.show()
				}
				//lost focus
				else if (componentId == enqueuedView.state.id &&
						eventId == 1 &&
						args?.get(4.toByte()) as? Boolean == false)
				{
					enqueuedViewVisible = false
				}
				if (componentId == customActionsView.state.id &&
						eventId == 1 &&
						args?.get(4.toByte()) as? Boolean == true) {
					customActionsView.show()
				}

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
				avContext.av_connectionGranted(handle, connectionType)
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling av_connectionGranted", e)
			}
		}

		override fun av_connectionDeactivated(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
			val msg = "Received av_connectionDeactivated: handle=$handle connectionType=$connectionType"
			Log.i(TAG, msg)
			try {
				avContext.av_connectionDeactivated(handle, connectionType)
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
				avContext.av_multimediaButtonEvent(handle, event)
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling av_multimediaButtonEvent", e)
			}
		}

		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			Log.i(TAG, "Received am_onAppEvent: handle=$handle ident=$ident appId=$appId event=$event")
			appId ?: return
			try {
				val appInfo = avContext.getAppInfo(appId) ?: return
				avContext.av_requestContext(appInfo)
				app?.events?.values?.filterIsInstance<RHMIEvent.FocusEvent>()?.firstOrNull()?.triggerEvent(mapOf(0.toByte() to playbackView.state.id))
				carApp.runSynchronized {
					server?.am_showLoadedSuccessHint(avContext.amHandle)
				}
				avContext.amRecreateApp(appInfo)
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling am_onAppEvent", e)
			}
		}

		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			if (propertyName == "hmi.graphicalContext") {
//				Log.i(TAG, "Received graphicalContext: $propertyValue")
				hmiContextChangedTime = System.currentTimeMillis()
			}
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
		if (appListViewVisible) {
			appSwitcherView.redraw()
		}
		if (playbackViewVisible || playbackView.state is RHMIState.AudioHmiState) {
			playbackView.redraw()
		}
		if (enqueuedViewVisible) {
			enqueuedView.redraw()
		}
		// if running over USB or audio context is granted, set the global metadata
		if (!musicAppMode.shouldRequestAudioContext() || avContext.currentContext) {
			globalMetadata.redraw()
		}
	}

}