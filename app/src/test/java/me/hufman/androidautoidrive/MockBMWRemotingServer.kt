package me.hufman.androidautoidrive

import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BaseBMWRemotingServer
import java.util.concurrent.CountDownLatch

class MockBMWRemotingServer: BaseBMWRemotingServer() {
	val waitForApp = CountDownLatch(1)
	val resources = HashMap<BMWRemoting.RHMIResourceType, ByteArray>()
	val addedActionHandler = HashMap<String, Boolean>()
	val addedEventHandler = HashMap<String, Boolean>()
	val properties = HashMap<Int, MutableMap<Int, Any>>()
	val data = HashMap<Int, Any>()
	val triggeredEvents = HashMap<Int, Map<*, *>>()
	val amApps = ArrayList<String>()
	val avConnections = HashMap<Int, String>()
	var avCurrentContext = -1
	var avCurrentState = BMWRemoting.AVPlayerState.AV_PLAYERSTATE_STOP

	override fun sas_certificate(data: ByteArray?): ByteArray {
		return ByteArray(16)
	}

	override fun sas_login(data: ByteArray?) {

	}

	override fun rhmi_create(token: String?, metaData: BMWRemoting.RHMIMetaData?): Int {
		return 1
	}

	override fun rhmi_initialize(handle: Int?) {
		waitForApp.countDown()
	}

	override fun rhmi_setResource(handle: Int?, data: ByteArray?, type: BMWRemoting.RHMIResourceType?) {
		if (type != null && data != null)
			resources[type] = data
	}

	override fun rhmi_addActionEventHandler(handle: Int?, ident: String?, actionId: Int?) {
		addedActionHandler[ident as String] = true
	}

	override fun rhmi_addHmiEventHandler(handle: Int?, ident: String?, componentId: Int?, eventId: Int?) {
		addedEventHandler[ident as String] = true
	}

	override fun rhmi_ackActionEvent(handle: Int?, actionId: Int?, confirmId: Int?, success: Boolean?) {

	}

	override fun rhmi_triggerEvent(handle: Int?, eventId: Int?, args: MutableMap<*, *>?) {
		triggeredEvents[eventId!!] = args!!
	}

	override fun rhmi_setProperty(handle: Int?, componentId: Int?, propertyId: Int?, values: MutableMap<*, *>?) {
		properties.putIfAbsent(componentId as Int, HashMap())
		properties[componentId]?.put(propertyId as Int, values?.get(0) as Any)
	}

	override fun rhmi_setData(handle: Int?, modelId: Int?, value: Any?) {
//		System.out.println("Updated data $modelId: $value")
		data[modelId!!] = value!!
	}

	override fun rhmi_getCapabilities(component: String?, handle: Int?): Map<*, *> {
		return mapOf(
				"hmi.display-width" to "1280",
				"hmi.display-height" to "480"
		)
	}

	override fun cds_create(): Int {
		return 1
	}

	override fun cds_addPropertyChangedEventHandler(handle: Int?, propertyName: String?, ident: String?, intervalLimit: Int?) {

	}

	override fun cds_getPropertyAsync(handle: Int?, ident: String?, propertyName: String?) {

	}

	override fun am_create(deviceId: String?, bluetoothAddress: ByteArray?): Int {
		return 1
	}
	override fun am_addAppEventHandler(handle: Int?, ident: String?) {
	}
	override fun am_registerApp(handle: Int?, appId: String?, values: MutableMap<*, *>?) {
		amApps.add(appId ?: "")
	}
	override fun am_showLoadedSuccessHint(handle: Int?) {
	}

	override fun av_create(instanceID: Int?, id: String?): Int {
		val handle = avConnections.size
		avConnections[handle] = id!!
		return handle
	}

	override fun av_requestConnection(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
		avCurrentContext = handle ?: return
	}

	override fun av_dispose(handle: Int?) {
		if (handle != null) avConnections.remove(handle)
	}

	override fun av_playerStateChanged(handle: Int?, connectionType: BMWRemoting.AVConnectionType?, playerState: BMWRemoting.AVPlayerState?) {
		avCurrentState = playerState ?: return
	}
}