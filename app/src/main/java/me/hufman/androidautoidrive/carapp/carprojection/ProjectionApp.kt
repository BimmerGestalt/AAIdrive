package me.hufman.androidautoidrive.carapp.carprojection

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.GraphicsHelpers
import me.hufman.androidautoidrive.carapp.InputState
import me.hufman.androidautoidrive.carapp.RHMIApplicationIdempotent
import me.hufman.androidautoidrive.carapp.RHMIApplicationSynchronized
import me.hufman.androidautoidrive.carapp.RHMIUtils
import me.hufman.androidautoidrive.carapp.maps.FrameModeListener
import me.hufman.androidautoidrive.carapp.maps.FrameUpdater
import me.hufman.androidautoidrive.carapp.maps.VirtualDisplayScreenCapture
import me.hufman.androidautoidrive.carapp.maps.views.FullImageInteraction
import me.hufman.androidautoidrive.carapp.maps.views.FullImageView
import me.hufman.androidautoidrive.removeFirst
import me.hufman.carprojection.AppDiscovery
import me.hufman.carprojection.ProjectionAppInfo
import me.hufman.carprojection.parcelables.InputFocusChangedEvent
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.*
import java.util.*
import kotlin.math.min
import kotlin.math.roundToInt

class ProjectionApp(val context: Context, val carAppAssets: CarAppResources, val appDiscovery: AppDiscovery, val graphicsHelpers: GraphicsHelpers, val virtualDisplay: VirtualDisplayScreenCapture) {

	companion object {
		val MAX_WIDTH = 700
		val MAX_HEIGHT = 400
		val TAG = "ProjectionApp"
	}

	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	val carappListener = CarAppListener()

	val fullImageView: FullImageView
	val stateInput: RHMIState
	val viewInput: RHMIComponent.Input

	val rhmiWidth: Int
	val mapWidth: Int
		get() = min(rhmiWidth - 280, if (AppSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()) MAX_WIDTH else 700)
	val mapHeight = 400

	var frameUpdater = FrameUpdater(virtualDisplay, object: FrameModeListener {
		override fun onResume() {
			ProjectionState.carProjectionHost?.projection?.onProjectionResume(0)
			val imageCapture = virtualDisplay.imageCapture
			val displayRect = Rect(0, 0, imageCapture.width, imageCapture.height)
			val inputFocus = InputFocusChangedEvent.build(context, true, false, 0, displayRect)
			ProjectionState.carProjectionHost?.projection?.onInputFocusChange(inputFocus)
		}
		override fun onPause() {
			ProjectionState.carProjectionHost?.projection?.onProjectionStop(0)
		}
	})

	val host: ProjectionHost

	init {
		carConnection = IDriveConnection.getEtchConnection(IDriveConnectionListener.host
				?: "127.0.0.1", IDriveConnectionListener.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand
				?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = SecurityService.signChallenge(challenge = sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.carprojection", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive.carprojection", "me.hufman"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
//		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppAssets.getTextsDB("common"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB("common"))
		carConnection.rhmi_initialize(rhmiHandle)
		carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)))
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)
		carappListener.app = carApp

		// connect buttons together
		val capabilities = carConnection.rhmi_getCapabilities("", 255)
		rhmiWidth = (capabilities["hmi.display-width"] as? String?)?.toIntOrNull() ?: 720

		val unclaimedStates = LinkedList(carApp.states.values)
		fullImageView = FullImageView(unclaimedStates.removeFirst { FullImageView.fits(it) }, "AndroidAuto", object: FullImageInteraction {
			override fun navigateUp() {
				val time = SystemClock.uptimeMillis()
				sendInput(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0, KeyEvent.META_SHIFT_ON))
				sendInput(KeyEvent(time, time+10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 0, KeyEvent.META_SHIFT_ON))
			}
			override fun navigateDown() {
				sendInput(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
				sendInput(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB))
			}
			override fun click() {
				sendInput(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
				sendInput(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
			}
			fun sendInput(event: KeyEvent) {
				ProjectionState.carProjectionHost?.projection?.onKeyEvent(event)
			}
		}, frameUpdater, { mapWidth }, { mapHeight })
		fullImageView.initWidgets()

		stateInput = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first {
			// maybe we don't need suggestAction in this instance?
			it.componentsList.filterIsInstance<RHMIComponent.Input>().filter { it.suggestAction > 0 }.isNotEmpty()
		}
		viewInput = stateInput.componentsList.filterIsInstance<RHMIComponent.Input>().first()
		viewInput.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = fullImageView.state.id
		val stateInputState = object: InputState<String>(viewInput) {
			override fun onEntry(input: String) {
				sendSuggestions(listOf(input))
			}
			override fun onSelect(item: String, index: Int) {
				ProjectionState.carProjectionHost?.inputConnection?.commitText(item)
			}

		}

		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach{
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = fullImageView.state.id
			Log.i(TAG, "Registering entry button ${it.id} model ${it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.id} to point to main state ${fullImageView.state.id}")
		}

		host = ProjectionHost(context, virtualDisplay, stateInput)

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.carprojection", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.carprojection", -1, -1)

		// create AM icons
		init_am()
	}

	fun init_am() {
		val amHandle = carConnection.am_create("0", "\u0000\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
		carConnection.am_addAppEventHandler(amHandle, "my.hufman.androidautoidrive.carprojection")
		appDiscovery.discoverApps().filter {
			it.packageName != "com.google.android.projection.gearhead"
		}.forEach {
			Log.i(TAG, "Adding Projection icon for ${it.name} -> ${it.className}")
			carConnection.am_registerApp(amHandle, "androidautoidrive.projection.${it.name}", getAmInfo(it))
		}
	}

	fun getAmInfo(app: ProjectionAppInfo): Map<Int, Any> {
		val amInfo = mutableMapOf<Int, Any>(
				0 to 145,   // basecore version
				1 to app.name,  // app name
				2 to graphicsHelpers.compress(app.icon, 48, 48), // icon
				3 to "OnlineServices",   // section
				4 to true,
				5 to 400 - getAppWeight(app),   // weight
				8 to -1  // mainstateId
		)
		// language translations, dunno which one is which
		for (languageCode in 101..123) {
			amInfo[languageCode] = app.name
		}

		return amInfo
	}

	fun getAppWeight(app: ProjectionAppInfo): Int {
		val name = app.name.toLowerCase().toCharArray().filter { it.isLetter() }
		var score = min(name[0].toInt() - 'a'.toInt(), 'z'.toInt())
		score = score * 6 + ((name[1].toInt() / 6.0).roundToInt())
		return score
	}

	inner class CarAppListener : BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null
		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			Log.w(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId args=$args")
			try {
				app?.actions?.get(actionId)?.asRAAction()?.rhmiActionCallback?.onActionEvent(args)
			} catch (e: Exception) {
				Log.e(TAG, "Exception while calling onActionEvent handler!", e)
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

		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			Log.i(TAG, "Received am_onAppEvent: handle=$handle ident=$ident appId=$appId event=$event")
			appDiscovery.discoverApps().forEach {
				if (appId == "androidautoidrive.projection.${it.name}") {
					host.connect(it)
					app?.events?.values?.filterIsInstance<RHMIEvent.FocusEvent>()?.firstOrNull()?.triggerEvent(mapOf(0.toByte() to fullImageView.state.id))
				}
			}
		}
	}

	fun onCreate(handler: Handler) {
		Log.i(TAG, "Setting up frame transfer")
		frameUpdater.start(handler)
		ProjectionState.openInput = {
			handler.post {

				carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().firstOrNull()?.triggerEvent(mapOf(0 to viewInput.id))
			}
		}
	}
	fun onDestroy() {
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch (e: java.lang.Exception) {}
		frameUpdater.shutDown()
		host.onDestroy()
	}
}