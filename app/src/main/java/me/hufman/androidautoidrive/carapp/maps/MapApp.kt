package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.carapp.*
import me.hufman.androidautoidrive.carapp.maps.views.MenuView
import me.hufman.androidautoidrive.utils.removeFirst
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationIdempotent
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationSynchronized
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.*
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

const val TAG = "MapView"

class MapApp(iDriveConnectionStatus: IDriveConnectionStatus, securityAccess: SecurityAccess, val carAppAssets: CarAppResources, val mapAppMode: MapAppMode, val interaction: MapInteractionController, val map: VirtualDisplayScreenCapture) {

	val carappListener = CarAppListener()
	val carConnection: BMWRemotingServer
	var mapResultsUpdater = MapResultsReceiver(MapResultsUpdater())
	val carApp: RHMIApplication
	var searchResults = ArrayList<MapResult>()
	var selectedResult: MapResult? = null

	val menuView: MenuView
	val fullImageView: FullImageView
	val stateInput: RHMIState.PlainState
	val stateInputState: InputState<MapResult>

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
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
//		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppAssets.getTextsDB("common"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB("common"))
		carConnection.rhmi_initialize(rhmiHandle)

		carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)), carConnection)
		carappListener.app = carApp
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		// figure out the components to use
		Log.i(TAG, "Locating components to use")
		val unclaimedStates = LinkedList(carApp.states.values)
		menuView = MenuView(unclaimedStates.removeFirst { MenuView.fits(it) }, interaction, frameUpdater)
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

		stateInput = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first {
			it.componentsList.filterIsInstance<RHMIComponent.Input>().filter { it.suggestAction > 0 }.isNotEmpty()
		}

		// connect buttons together
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach{
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = menuView.state.id
			Log.i(TAG, "Registering entry button ${it.id} model ${it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.id} to point to main state ${menuView.state.id}")
		}

		// set up the components
		Log.i(TAG, "Setting up component behaviors")
		menuView.initWidgets(fullImageView.state, stateInput)
		fullImageView.initWidgets()
		// set up the components for the input widget
		stateInputState = object: InputState<MapResult>(stateInput) {
			override fun onEntry(input: String) {
				interaction.searchLocations(input)
			}

			override fun onSelect(item: MapResult, index: Int) {
				selectedResult = item
				interaction.stopNavigation()
				if (item.location == null) {
					interaction.resultInformation(item.id)    // ask for LatLong, to navigate to
				} else {
					interaction.navigateTo(item.location)
				}
			}
		}

		stateInputState.inputComponent.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = fullImageView.state.id
		stateInputState.inputComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = fullImageView.state.id

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.mapview", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.mapview", -1, -1)
	}

	fun onCreate(context: Context, handler: Handler) {
		Log.i(TAG, "Setting up map transfer")
		context.registerReceiver(mapResultsUpdater, IntentFilter(INTENT_MAP_RESULTS), null, handler)
		context.registerReceiver(mapResultsUpdater, IntentFilter(INTENT_MAP_RESULT), null, handler)
		frameUpdater.start(handler)
	}
	fun onDestroy(context: Context) {
		try {
			context.unregisterReceiver(mapResultsUpdater)
		} catch (e: IllegalArgumentException) {}
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch (e: java.lang.Exception) {}
		frameUpdater.shutDown()
	}

	inner class CarAppListener: BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null
		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			Log.w(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId args=$args")
			try {
				app?.actions?.get(actionId)?.asRAAction()?.rhmiActionCallback?.onActionEvent(args)
			} catch (e: Exception) {
				Log.e(me.hufman.androidautoidrive.carapp.notifications.TAG, "Exception while calling onActionEvent handler!", e)
			}
			server?.rhmi_ackActionEvent(handle, actionId, 1, true)
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
			val msg = "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId args=${args?.toString()}"
			Log.w(TAG, msg)

			// generic event handler
			app?.states?.get(componentId)?.onHmiEvent(eventId, args)
			app?.components?.get(componentId)?.onHmiEvent(eventId, args)
		}
	}

	/** Receives updates about the map search results and delegates to the given controller */
	inner class MapResultsUpdater: MapResultsController {
		override fun onSearchResults(results: Array<MapResult>) {
			Log.i(TAG, "Received query results")
			searchResults.clear()
			searchResults.addAll(results)
			stateInputState.sendSuggestions(searchResults)
		}

		override fun onPlaceResult(result: MapResult) {
			var updated = false
			searchResults.forEachIndexed { index, searchResult ->
				if (searchResult.id == result.id) {
					Log.i(TAG, "Updating address information for ${searchResult.name}")
					updated = true
					searchResults[index] = result
				}
			}
			if (updated) {
				stateInputState.sendSuggestions(searchResults)
			}
			// check if we were trying to navigate to this destination
			if (result.id == selectedResult?.id) {
				if (result.location != null)
					interaction.navigateTo(result.location)
			} else if (!updated) {
				Log.i(TAG, "Received unexpected result info ${result.name}, but expected selectedResult ${selectedResult?.name}")
			}
		}
	}
}
