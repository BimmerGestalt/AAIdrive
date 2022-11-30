package me.hufman.androidautoidrive.carapp.maps

import android.os.Handler
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.Utils.rhmi_setResourceCached
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.carapp.FullImageInteraction
import me.hufman.androidautoidrive.carapp.FullImageView
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.maps.views.MenuView
import me.hufman.androidautoidrive.carapp.maps.views.PlaceSearchView
import me.hufman.androidautoidrive.carapp.maps.views.SearchResultsView
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import me.hufman.androidautoidrive.maps.MapResult
import me.hufman.androidautoidrive.utils.removeFirst
import java.util.*
import kotlin.collections.ArrayList

const val TAG = "MapView"

class MapApp(iDriveConnectionStatus: IDriveConnectionStatus, securityAccess: SecurityAccess, val carAppAssets: CarAppResources,
             val mapAppMode: MapAppMode, val locationProvider: CarLocationProvider,
             val interaction: MapInteractionController, val mapPlaceSearch: MapPlaceSearch, val map: VirtualDisplayScreenCapture) {

	val carappListener = CarAppListener()
	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	var searchResults = ArrayList<MapResult>()
	var selectedResult: MapResult? = null

	val menuView: MenuView
	val fullImageView: FullImageView
	val stateInput: RHMIState.PlainState
	val stateInputState: InputState<MapResult>
	val searchResultsView: SearchResultsView

	// map state
	var frameUpdater = FrameUpdater(map, object: FrameModeListener {
		override fun onResume() { interaction.showMap()	}
		override fun onPause() { interaction.pauseMap() }
	})

	init {
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.mapview", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive.mapview", "me.hufman"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
//		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppAssets.getTextsDB("common"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB("common"))
		carConnection.rhmi_initialize(rhmiHandle)

		carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)), carConnection)
		carappListener.app = carApp
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		// figure out the components to use
		Log.i(TAG, "Locating components to use")
		val unclaimedStates = LinkedList(carApp.states.values)
		menuView = MenuView(unclaimedStates.removeFirst { MenuView.fits(it) }, interaction, mapPlaceSearch, frameUpdater, mapAppMode)
		fullImageView = FullImageView(unclaimedStates.removeFirst { FullImageView.fits(it) }, "Map", mapAppMode, object : FullImageInteraction {
			override fun navigateUp() {
				interaction.zoomIn(1)
			}
			override fun navigateDown() {
				interaction.zoomOut(1)
			}
			override fun click() {
			}
			override fun getClickState(): RHMIState {
				return menuView.state
			}
		}, frameUpdater)

		stateInput = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first { state ->
			state.componentsList.filterIsInstance<RHMIComponent.Input>().any { it.suggestAction > 0 }
		}
		stateInputState = PlaceSearchView(stateInput, mapPlaceSearch, interaction)
		searchResultsView = SearchResultsView(unclaimedStates.removeFirst { SearchResultsView.fits(it) }, mapPlaceSearch, interaction, mapAppMode, locationProvider)

		// connect buttons together
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach{
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = menuView.state.id
			Log.i(TAG, "Registering entry button ${it.id} model ${it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.id} to point to main state ${menuView.state.id}")
		}

		// set up the components
		Log.i(TAG, "Setting up component behaviors")
		menuView.initWidgets(fullImageView.state, stateInput)
		fullImageView.initWidgets()
		stateInputState.initWidgets(fullImageView, searchResultsView)
		searchResultsView.initWidgets(fullImageView)

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.mapview", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.mapview", -1, -1)
	}

	fun onCreate(handler: Handler) {
		Log.i(TAG, "Setting up map transfer")
		frameUpdater.start(handler)
	}
	fun onDestroy() {
		frameUpdater.shutDown()
	}
	fun disconnect() {
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch (e: java.lang.Exception) {}
	}

	inner class CarAppListener: BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null
		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			Log.w(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId args=$args")
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
				Log.e(me.hufman.androidautoidrive.carapp.notifications.TAG, "Exception while calling onActionEvent handler!", e)
				synchronized(server!!) {
					server?.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			}
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
			val msg = "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId args=${args?.toString()}"
			Log.w(TAG, msg)

			// generic event handler
			app?.states?.get(componentId)?.onHmiEvent(eventId, args)
			app?.components?.get(componentId)?.onHmiEvent(eventId, args)
		}
	}
}
