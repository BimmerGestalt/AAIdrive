package me.hufman.androidautoidrive.carapp.maps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.RHMIApplicationSynchronized
import me.hufman.androidautoidrive.carapp.RHMIUtils
import me.hufman.androidautoidrive.carapp.maps.views.MapView
import me.hufman.androidautoidrive.carapp.maps.views.MenuView
import me.hufman.androidautoidrive.removeFirst
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.*
import java.lang.RuntimeException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

const val TAG = "MapView"

class MapApp(val carAppAssets: CarAppResources, val interaction: MapInteractionController, val map: VirtualDisplayScreenCapture) {
	var handler: Handler? = null    // will be set in onCreate()
	val carappListener = CarAppListener()
	val carConnection: BMWRemotingServer
	var mapResultsUpdater = MapResultsUpdater()
	val mapListener = MapResultsReceiver(mapResultsUpdater)
	val carApp: RHMIApplication
	var searchResults = ArrayList<MapResult>()
	var selectedResult: MapResult? = null

	val menuView: MenuView
	val mapView: MapView
	val stateInput: RHMIState.PlainState
	val viewInput: RHMIComponent.Input
	val stateInputState: InputState<MapResult>

	val MAX_WIDTH = 1000
	val MAX_HEIGHT = 4000
	val rhmiWidth: Int
	val mapWidth: Int
		get() = min(rhmiWidth - 280, if (AppSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()) 1000 else 700)
	val mapHeight = 400

	// map state
	var frameUpdater = FrameUpdater(map)

	init {
		carConnection = IDriveConnection.getEtchConnection(IDriveConnectionListener.host ?: "127.0.0.1", IDriveConnectionListener.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = SecurityService.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.mapview", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive.mapview", "me.hufman"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
//		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppAssets.getTextsDB("common"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB("common"))
		carConnection.rhmi_initialize(rhmiHandle)

		carApp = RHMIApplicationSynchronized(RHMIApplicationEtch(carConnection, rhmiHandle))
		carappListener.app = carApp
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		// figure out the components to use
		Log.i(TAG, "Locating components to use")
		val unclaimedStates = LinkedList(carApp.states.values)
		menuView = MenuView(unclaimedStates.removeFirst { MenuView.fits(it) }, interaction, frameUpdater)
		mapView = MapView(unclaimedStates.removeFirst { MapView.fits(it) }, interaction, frameUpdater, MAX_WIDTH, MAX_HEIGHT)

		stateInput = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first {
			it.componentsList.filterIsInstance<RHMIComponent.Input>().filter { it.suggestAction > 0 }.isNotEmpty()
		}
		viewInput = stateInput.componentsList.filterIsInstance<RHMIComponent.Input>().first()

		// connect buttons together
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach{
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = menuView.state.id
			Log.i(TAG, "Registering entry button ${it.id} model ${it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.id} to point to main state ${menuView.state.id}")
		}

		// get the car capabilities
		val capabilities = carConnection.rhmi_getCapabilities("", 255)
		rhmiWidth = (capabilities["hmi.display-width"] as? String?)?.toIntOrNull() ?: 720
		Log.i(TAG, "Detected HMI width of $rhmiWidth")

		// set up the components
		Log.i(TAG, "Setting up component behaviors")
		menuView.initWidgets(mapView.state, stateInput)
		mapView.initWidgets(menuView.state)
		// set up the components for the input widget
		stateInputState = object: InputState<MapResult>(viewInput) {
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

		viewInput.getSuggestAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = mapView.state.id
		viewInput.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = mapView.state.id

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.mapview", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.mapview", -1, -1)
	}

	fun onCreate(context: Context, handler: Handler?) {
		this.handler = handler
		if (handler == null) {
			context.registerReceiver(mapListener, IntentFilter(INTENT_MAP_RESULTS))
			context.registerReceiver(mapListener, IntentFilter(INTENT_MAP_RESULT))

			frameUpdater.start(Handler(Looper.myLooper()))
		} else {
			context.registerReceiver(mapListener, IntentFilter(INTENT_MAP_RESULTS), null, handler)
			context.registerReceiver(mapListener, IntentFilter(INTENT_MAP_RESULT), null, handler)

			// prepare the map transfer
			Log.i(TAG, "Setting up map transfer")
			frameUpdater.start(handler)
		}
	}
	fun onDestroy(context: Context) {
		context.unregisterReceiver(mapListener)
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

	inner class FrameUpdater(val display: VirtualDisplayScreenCapture): Runnable {
		var currentMode = ""
		var isRunning = true
		private var handler: Handler? = null

		fun start(handler: Handler) {
			this.handler = handler
			Log.i(TAG, "Starting FrameUpdater thread with handler $handler")
			display.registerImageListener(ImageReader.OnImageAvailableListener // Called from the UI thread to say a new image is available
			{
				// let the car thread consume the image
				schedule()
			})
			schedule()  // check for a first image
		}

		override fun run() {
			var bitmap = display.getFrame()
			if (bitmap != null) {
				sendImage(bitmap)
				schedule()  // check if there's another frame ready for us right now
			} else {
				// wait for the next frame, unless the callback comes back sooner
				schedule(1000)
			}
		}

		fun schedule(delayMs: Int = 0) {
			handler?.removeCallbacks(this)   // remove any previously-scheduled invocations
			handler?.postDelayed(this, delayMs.toLong())
		}

		fun shutDown() {
			isRunning = false
			display.registerImageListener(null)
			handler?.removeCallbacks(this)
		}

		fun showMode(mode: String) {
			currentMode = mode
			Log.i(TAG, "Changing map mode to $mode")
			when (mode) {
				"menuMap" ->
					map.changeImageSize(350, 90)
				"mainMap" ->
					map.changeImageSize(mapWidth, mapHeight)
			}
		}
		fun hideMode(mode: String) {
			if (currentMode == mode) {
				currentMode = ""
			}
		}

		private fun sendImage(bitmap: Bitmap) {
			val imageData = display.compressBitmap(bitmap)
			try {
				if (bitmap.height >= 150)   // main map
					mapView.mapImage.getModel()?.asRaImageModel()?.value = imageData
				else if (bitmap.height >= 80) { // menu map
					val list = RHMIModel.RaListModel.RHMIListConcrete(3)
					list.addRow(arrayOf(BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, imageData), "", ""))
					menuView.menuMap.getModel()?.asRaListModel()?.setValue(list, 0, 1, 1)
				} else {
					Log.w(TAG, "Unknown image size: ${bitmap.width}x${bitmap.height} in mode: $currentMode")
				}
			} catch (e: RuntimeException) {
			} catch (e: org.apache.etch.util.TimeoutException) {
				// don't crash if the phone is unplugged during a frame update
			}
		}
	}

	/** Receives updates about the map search results and delegates to the given controller */
	class MapResultsReceiver(val updater: MapResultsController): BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (context?.packageName == null || intent?.`package` == null || context.packageName != intent.`package`) return
			if (intent.action == INTENT_MAP_RESULTS) {
				Log.i(TAG, "Received map results: ${intent.getSerializableExtra(EXTRA_MAP_RESULTS)}")
				updater.onSearchResults(intent.getSerializableExtra(EXTRA_MAP_RESULTS) as? Array<MapResult> ?: return)
			}
			if (intent.action == INTENT_MAP_RESULT) {
				updater.onPlaceResult(intent.getSerializableExtra(EXTRA_MAP_RESULT) as? MapResult ?: return)
			}
		}

	}

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
