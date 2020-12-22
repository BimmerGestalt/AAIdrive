package me.hufman.androidautoidrive.phoneui

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.ChassisCode
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.connections.CarConnectionDebugging
import me.hufman.androidautoidrive.phoneui.viewmodels.ConnectionStatusModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConnectionStatusModelTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	val context = mock<Context> {
		on {getString(any())} doReturn ""
		on {getString(any(), any())} doReturn ""
	}

	@Test
	fun testDisconnected() {
		val connection = mock<CarConnectionDebugging>()
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(false, model.isBtConnected.value)
		assertEquals(false, model.isUsbConnected.value)
		assertEquals(false, model.isBclReady.value)
	}

	@Test
	fun testBtConnected() {
		val connection = mock<CarConnectionDebugging> {
			on {isBTConnected} doReturn true
			on {isA2dpConnected} doReturn false
			on {isSPPAvailable} doReturn false
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(true, model.isBtConnected.value)
		assertEquals(false, model.isA2dpConnected.value)
		assertEquals(false, model.isSppAvailable.value)
		assertEquals(false, model.isUsbConnected.value)
		assertEquals(false, model.isBclReady.value)
	}

	@Test
	fun testBtA2dpConnected() {
		val connection = mock<CarConnectionDebugging> {
			on {isBTConnected} doReturn true
			on {isA2dpConnected} doReturn true
			on {isSPPAvailable} doReturn false
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(true, model.isBtConnected.value)
		assertEquals(true, model.isA2dpConnected.value)
		assertEquals(false, model.isSppAvailable.value)
		assertEquals(false, model.isUsbConnected.value)
		assertEquals(false, model.isBclReady.value)
	}

	@Test
	fun testBtSppConnected() {
		val connection = mock<CarConnectionDebugging> {
			on {isBTConnected} doReturn true
			on {isA2dpConnected} doReturn true
			on {isSPPAvailable} doReturn true
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(true, model.isBtConnected.value)
		assertEquals(true, model.isA2dpConnected.value)
		assertEquals(true, model.isSppAvailable.value)
		assertEquals(false, model.isUsbConnected.value)
		assertEquals(true, model.isBclReady.value)
	}

	@Test
	fun testUsbConnectedCharging() {
		val connection = mock<CarConnectionDebugging> {
			on {isUsbConnected} doReturn true
			on {isUsbTransferConnected} doReturn false
			on {isUsbAccessoryConnected} doReturn false
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(false, model.isBtConnected.value)
		assertEquals(true, model.isUsbConnected.value)
		assertEquals(true, model.isUsbCharging.value)
		assertEquals(false, model.isUsbTransfer.value)
		assertEquals(false, model.isUsbAccessory.value)
		assertEquals(false, model.isBclReady.value)
	}

	@Test
	fun testUsbConnectedTransfer() {
		val connection = mock<CarConnectionDebugging> {
			on {deviceName} doReturn "Test"
			on {isUsbConnected} doReturn true
			on {isUsbTransferConnected} doReturn true
			on {isUsbAccessoryConnected} doReturn false
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(false, model.isBtConnected.value)
		assertEquals(true, model.isUsbConnected.value)
		assertEquals(false, model.isUsbCharging.value)
		assertEquals(true, model.isUsbTransfer.value)
		assertEquals(false, model.isUsbAccessory.value)
		assertEquals(false, model.isBclReady.value)

		context.run(model.hintUsbAccessory.value!!)
		verify(context).getString(eq(R.string.txt_setup_enable_usbacc), eq("Test"))
	}

	@Test
	fun testUsbConnectedAccessory() {
		val connection = mock<CarConnectionDebugging> {
			on {isUsbConnected} doReturn true
			on {isUsbTransferConnected} doReturn false
			on {isUsbAccessoryConnected} doReturn true
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(false, model.isBtConnected.value)
		assertEquals(true, model.isUsbConnected.value)
		assertEquals(false, model.isUsbCharging.value)
		assertEquals(false, model.isUsbTransfer.value)
		assertEquals(true, model.isUsbAccessory.value)
		assertEquals(true, model.isBclReady.value)
	}

	@Test
	fun testBclDisconnected() {
		val connection = mock<CarConnectionDebugging> {
			on {isBTConnected} doReturn true
			on {isSPPAvailable} doReturn true
			on {isBCLConnecting} doReturn false
			on {isBCLConnected} doReturn false
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(true, model.isBtConnected.value)
		assertEquals(false, model.isUsbConnected.value)
		assertEquals(true, model.isBclReady.value)
		assertEquals(true, model.isBclDisconnected.value)
		assertEquals(false, model.isBclConnecting.value)
		assertEquals(false, model.isBclConnected.value)
	}

	@Test
	fun testBclConnecting() {
		val connection = mock<CarConnectionDebugging> {
			on {isBTConnected} doReturn true
			on {isSPPAvailable} doReturn true
			on {isBCLConnecting} doReturn true
			on {isBCLConnected} doReturn false
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(true, model.isBtConnected.value)
		assertEquals(false, model.isUsbConnected.value)
		assertEquals(true, model.isBclReady.value)
		assertEquals(false, model.isBclDisconnected.value)
		assertEquals(true, model.isBclConnecting.value)
		assertEquals(false, model.isBclConnected.value)
	}

	@Test
	fun testBclStuck() {
		val connection = mock<CarConnectionDebugging> {
			on {deviceName} doReturn "Test"
			on {isBTConnected} doReturn true
			on {isSPPAvailable} doReturn true
			on {isBCLConnecting} doReturn true
			on {isBCLStuck} doReturn true
			on {isBCLConnected} doReturn false
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(true, model.isBtConnected.value)
		assertEquals(false, model.isUsbConnected.value)
		assertEquals(true, model.isBclReady.value)
		assertEquals(false, model.isBclDisconnected.value)
		assertEquals(true, model.isBclConnecting.value)
		assertEquals(true, model.isBclStuck.value)
		assertEquals(false, model.isBclConnected.value)

		context.run(model.hintBclMode.value!!)
		verify(context).getString(eq(R.string.txt_setup_enable_bcl_mode), eq("Test"))
		// not actually displayed, but the value is generic
		context.run(model.bclModeText.value!!)
		verify(context).getString(eq(R.string.txt_setup_bcl_connected))
	}

	@Test
	fun testBclConnected() {
		val connection = mock<CarConnectionDebugging> {
			on {isBTConnected} doReturn true
			on {isSPPAvailable} doReturn true
			on {isBCLConnecting} doReturn true      // stays true even while Connected
			on {isBCLConnected} doReturn true
			on {bclTransport} doReturn "BT"
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(true, model.isBtConnected.value)
		assertEquals(false, model.isUsbConnected.value)
		assertEquals(true, model.isBclReady.value)
		assertEquals(false, model.isBclDisconnected.value)
		assertEquals(false, model.isBclConnecting.value)
		assertEquals(true, model.isBclConnected.value)

		context.run(model.bclModeText.value!!)
		verify(context).getString(eq(R.string.txt_setup_bcl_connected_transport), eq("BT"))
	}

	@Test
	fun testConnectedNoBrand() {
		val connection = mock<CarConnectionDebugging>()
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.notification_description))
	}

	@Test
	fun testConnectedBMWBrand() {
		val connection = mock<CarConnectionDebugging>{
			on {carBrand} doReturn "BMW"
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.notification_description_bmw))
	}

	@Test
	fun testConnectedMiniBrand() {
		val connection = mock<CarConnectionDebugging>{
			on {carBrand} doReturn "Mini"
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.notification_description_mini))
	}

	@Test
	fun testChassisCode() {
		val carCapabilities = mapOf(
			"vehicle.type" to "F56"
		)
		val connection = mock<CarConnectionDebugging>()
		val carInfo = mock<CarInformation> {
			on {capabilities} doReturn carCapabilities
		}
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(false, model.isBtConnected.value)
		assertEquals(false, model.isUsbConnected.value)
		assertEquals(false, model.isBclReady.value)
		assertEquals(ChassisCode.F56, model.carChassisCode.value)

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.notification_description_chassiscode), eq(ChassisCode.F56.toString()))
	}
}