package me.hufman.androidautoidrive.carapp.maps

import android.graphics.Bitmap
import android.media.ImageReader
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.Utils.etchAsInt
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.*
import java.io.IOError
import java.lang.RuntimeException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

const val TAG = "MapView"

class MapView(val carAppAssets: CarAppResources, val interaction: MapInteractionController, val map: VirtualDisplayScreenCapture) {

	val carappListener = CarAppListener()
	val carConnection: BMWRemotingServer
	val carApp: RHMIApplicationEtch
	val stateMenu: RHMIState.PlainState
	val menuMap: RHMIComponent.List // turns out you can set cells of a list to arbitrary images
	val menuList: RHMIComponent.List
	val stateMap: RHMIState.PlainState
	val viewFullMap: RHMIComponent.Image
	val mapInputList: RHMIComponent.List
	val menuEntries = arrayOf("View Full Map")

	// map state
	val frameUpdater: FrameUpdater

	init {
		carConnection = IDriveConnection.getEtchConnection(IDriveConnectionListener.host ?: "127.0.0.1", IDriveConnectionListener.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = SecurityService.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive.mapview", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive.mapview", "me.hufman"))
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getUiDescription()?.readBytes(), BMWRemoting.RHMIResourceType.DESCRIPTION)
//		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getTextsDB("common")?.readBytes(), BMWRemoting.RHMIResourceType.TEXTDB)
		carConnection.rhmi_setResource(rhmiHandle, carAppAssets.getImagesDB("common")?.readBytes(), BMWRemoting.RHMIResourceType.IMAGEDB)
		carConnection.rhmi_initialize(rhmiHandle)

		carApp = RHMIApplicationEtch(carConnection, rhmiHandle)
		carappListener.app = carApp
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		// figure out the components to use
		Log.i(TAG, "Locating components to use")
		stateMenu = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first {
					it.componentsList.filterIsInstance<RHMIComponent.Label>().isNotEmpty() &&   // show whether currently navigating
					it.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()   // a list of commands
		}
		menuMap = stateMenu.componentsList.filterIsInstance<RHMIComponent.List>()[0]
		menuList = stateMenu.componentsList.filterIsInstance<RHMIComponent.List>()[1]
		stateMap = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first {
			it.componentsList.filterIsInstance<RHMIComponent.Image>().isNotEmpty() &&
					it.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()
		}
		viewFullMap = stateMap.componentsList.filterIsInstance<RHMIComponent.Image>().first()
		mapInputList = stateMap.componentsList.filterIsInstance<RHMIComponent.List>().first()

		// connect buttons together
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach{
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateMenu.id
			Log.i(TAG, "Registering entry button ${it.id} model ${it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.id} to point to main state ${stateMenu.id}")
		}

		// set up the components
		Log.i(TAG, "Setting up component behaviors")
		val rhmiListContents = RHMIModel.RaListModel.RHMIListConcrete(3)
		menuEntries.forEach { rhmiListContents.addRow(arrayOf("", "", it)) }
		menuList.getModel()?.setValue(rhmiListContents,0, menuEntries.size, menuEntries.size)
		stateMap.componentsList.forEach {
			it.setVisible(false)
		}
		stateMenu.componentsList.forEach {
			it.setVisible(false)
		}
		menuMap.setVisible(true)
		menuMap.setSelectable(true)
//		menuMap.setProperty(18, -20)
//		menuMap.setProperty(19, -100)
		menuMap.setProperty(6, "350,0,*")
//		menuMap.setProperty(42, 6)  // 6 == 200px
		menuMap.setProperty(10, 90)
		menuMap.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateMap.id

		menuList.setProperty(6, "100,0,*")
		menuList.setVisible(true)
		menuList.getAction()?.asRAAction()?.rhmiActionCallback = object: RHMIAction.RHMIActionCallback {
			override fun onActionEvent(args: Map<*, *>?) {
				if (args == null) return
				val listIndex = etchAsInt(args[1.toByte()])
				if (listIndex == 0) {   // view full map
					Log.i(TAG, "User pressed to view full map, setting target ${menuList.getAction()?.asHMIAction()?.getTargetModel()?.id} to ${stateMap.id}")
					menuList.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateMap.id
				}
			}
		}

		viewFullMap.setVisible(true)
		viewFullMap.setProperty(20, 0)    // positionX
		viewFullMap.setProperty(21, 0)    // positionY
		viewFullMap.setProperty(9, 700)
		viewFullMap.setProperty(10, 400)
		mapInputList.setVisible(true)
		mapInputList.setProperty(20, 50000)  // positionX, so that we don't see it but should still be interacting with it
		mapInputList.setProperty(21, 50000)  // positionY, so that we don't see it but should still be interacting with it
		val scrollList = RHMIModel.RaListModel.RHMIListConcrete(3)
		(0..2).forEach { scrollList.addRow(arrayOf("+", "", "")) }  // zoom in
		scrollList.addRow(arrayOf("=", "", "")) // neutral
		(4..6).forEach { scrollList.addRow(arrayOf("-", "", "")) }  // zoom out
		mapInputList.getModel()?.asRaListModel()?.setValue(scrollList, 0, scrollList.height, scrollList.height)
		carApp.triggerHMIEvent(carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first().id, mapOf(0 to mapInputList.id, 41 to 3))  // set focus to the middle of the list
		mapInputList.getSelectAction()?.asRAAction()?.rhmiActionCallback = object: RHMIAction.RHMIActionCallback {
			override fun onActionEvent(args: Map<*, *>?) {
				if (args == null) return
				val listIndex = etchAsInt(args[1.toByte()])
				Log.i(TAG, "Detected scroll on map! to index $listIndex")
				if (listIndex in 0..2) {
					interaction.zoomIn()
				}
				if (listIndex in 4..6) {
					interaction.zoomOut()
				}
				carApp.triggerHMIEvent(carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first().id, mapOf(0 to mapInputList.id, 41 to 3))  // set focus to the middle of the list
			}
		}

		// prepare the map transfer
		Log.i(TAG, "Setting up map transfer")
		frameUpdater = FrameUpdater(map)
		frameUpdater.start()

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.mapview", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.mapview", -1, -1)
	}

	fun onCreate() {

	}
	fun onDestroy() {
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

			// if the main map is changing its focus state
			if (componentId == stateMenu.id &&
					eventId == 1  // FOCUS event
			) {
				if (args?.get(4.toByte()) as? Boolean == true) {
					Log.i(TAG, "Showing map on menu")
					frameUpdater.showMode("menuMap")
					interaction.showMap()
				} else {
					Log.i(TAG, "Hiding map on menu")
					frameUpdater.hideMode("menuMap")
				}
			}
			// if the full map is changing its focus state
			if (componentId == stateMap.id &&
					eventId == 1  // FOCUS event
			) {
				if (args?.get(4.toByte()) as? Boolean == true) {
					Log.i(TAG, "Showing map on full screen")
					frameUpdater.showMode("mainMap")
					interaction.showMap()
				} else {
					Log.i(TAG, "Hiding map on full screen")
					frameUpdater.hideMode("mainMap")
				}
			}
		}
	}

	inner class FrameUpdater(val display: VirtualDisplayScreenCapture): Thread("CarMapFrameUpdater") {
		var frameIsReady = Semaphore(1)
		var currentMode = ""
		var isRunning = true

		override fun run() {
			super.run()

			display.registerImageListener(ImageReader.OnImageAvailableListener // Called from the UI thread to say a new image is available
			{
				// let the car thread consume the image
				frameIsReady.release()
			})

			while (isRunning) {
				var bitmap = display.getFrame()
				if (bitmap != null) {
					sendImage(bitmap)
				}
				// wait for the next frame
				frameIsReady.tryAcquire(1000, TimeUnit.SECONDS)
			}
		}

		fun shutDown() {
			isRunning = false
			frameIsReady.release()  // trigger an immediate loop
		}

		fun showMode(mode: String) {
			currentMode = mode
			when (mode) {
				"menuMap" ->
					map.changeImageSize(350, 90)
				"mainMap" ->
					map.changeImageSize(700, 400)
			}
			frameIsReady.release()
		}
		fun hideMode(mode: String) {
			if (currentMode == mode) {
				currentMode = ""
			}
		}

		private fun sendImage(bitmap: Bitmap) {
			val imageData = display.compressBitmap(bitmap)
			try {
				if (bitmap.width >= 700)   // main map
					viewFullMap.getModel()?.asRaImageModel()?.value = imageData
				else if (bitmap.width >= 90) { // menu map
					val list = RHMIModel.RaListModel.RHMIListConcrete(3)
					list.addRow(arrayOf(BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, imageData), "", ""))
					menuMap.getModel()?.asRaListModel()?.setValue(list, 0, 1, 1)
				} else {
					Log.w(TAG, "Unknown image size: ${bitmap.width}x${bitmap.height} in mode: $currentMode")
				}
			} catch (e: RuntimeException) {
			} catch (e: org.apache.etch.util.TimeoutException) {
				// don't crash if the phone is unplugged during a frame update
			}
		}
	}
}
