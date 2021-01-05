package me.hufman.androidautoidrive.phoneui

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockito_kotlin.*
import kotlinx.coroutines.delay
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.ChassisCode
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.TestCoroutineRule
import me.hufman.androidautoidrive.connections.CarConnectionDebugging
import me.hufman.androidautoidrive.phoneui.viewmodels.ConnectionStatusModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt

class ConnectionStatusModelTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	@Rule
	@JvmField
	val testCoroutineRule = TestCoroutineRule()

	@Suppress("DEPRECATION")
	val resources: Resources = mock {
		on {getColor(any())} doAnswer {context.getColor(it.arguments[0] as Int)}
		on {getColor(any(), any())} doAnswer {context.getColor(it.arguments[0] as Int)}
		on {getDrawable(any())} doAnswer{context.getDrawable(it.arguments[0] as Int)}
		on {getValue(anyInt(), any(), any())} doAnswer { (it.arguments[1] as TypedValue).resourceId = it.arguments[0] as Int }
	}
	val context: Context = mock {
		on {getString(any())} doReturn ""
		on {getString(any(), any())} doReturn ""
		on {resources} doReturn resources
	}

	@Test
	fun testDisconnected() {
		val connection = mock<CarConnectionDebugging>()
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(false, model.isBtConnected.value)
		assertEquals(false, model.isUsbConnected.value)
		assertEquals(false, model.isBclReady.value)
		assertEquals("", model.bclTransport.value)

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.connectionStatusWaiting))
		assertEquals("", context.run(model.carConnectionHint.value!!))
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

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.connectionStatusWaiting))
		assertEquals("", context.run(model.carConnectionHint.value!!))
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

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.connectionStatusWaiting))
		assertEquals("", context.run(model.carConnectionHint.value!!))
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

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.txt_setup_bcl_waiting))
		assertEquals("", context.run(model.carConnectionHint.value!!))
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

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.connectionStatusWaiting))
		context.run(model.carConnectionHint.value!!)
		verify(context).getString(eq(R.string.txt_setup_enable_usbmtp))
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

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.connectionStatusWaiting))
		context.run(model.carConnectionHint.value!!)
		verify(context, times(2)).getString(eq(R.string.txt_setup_enable_usbacc), eq("Test"))
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

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.txt_setup_bcl_waiting))
		assertEquals("", context.run(model.carConnectionHint.value!!))
	}

	@Test
	fun testBclDisconnected() = testCoroutineRule.runBlockingTest {
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

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.txt_setup_bcl_waiting))
		assertEquals("", context.run(model.carConnectionHint.value!!))
		// empty hint
		assertEquals("", context.run(model.hintBclDisconnected.value!!))

		delay(5500L)
		// flips to show a hint after a timeout
		context.run(model.hintBclDisconnected.value!!)
		verify(context).getString(eq(R.string.txt_setup_enable_bclspp))

		// main connection status shows the hint too
		context.run(model.carConnectionText.value!!)
		verify(context, times(2)).getString(eq(R.string.txt_setup_bcl_waiting))
		context.run(model.carConnectionHint.value!!)
		verify(context, times(2)).getString(eq(R.string.txt_setup_enable_bclspp))
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

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.txt_setup_bcl_connecting))
		assertEquals("", context.run(model.carConnectionHint.value!!))
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

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.txt_setup_bcl_connecting))
		context.run(model.carConnectionHint.value!!)
		verify(context, times(2)).getString(eq(R.string.txt_setup_enable_bcl_mode), eq("Test"))
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
		assertEquals("BT", model.bclTransport.value)

		context.run(model.bclModeText.value!!)
		verify(context).getString(eq(R.string.txt_setup_bcl_connected_transport), eq("BT"))

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.txt_setup_bcl_connecting))
	}

	@Test
	fun testConnectedNoSecurity() {
		val connection = mock<CarConnectionDebugging> {
			on {isConnectedSecurityConnecting} doReturn true
			on {isConnectedSecurityConnected} doReturn false
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.connectionStatusWaiting))
		context.run(model.carConnectionColor.value!!)
		verify(context).getColor(R.color.connectionWaiting)

		// within the time limit, don't show red if done connecting
		whenever(connection.isConnectedSecurityConnecting) doReturn false
		model.update()
		context.run(model.carConnectionText.value!!)
		verify(context, times(2)).getString(eq(R.string.connectionStatusWaiting))
		context.run(model.carConnectionColor.value!!)
		verify(context, times(2)).getColor(R.color.connectionWaiting)

		// still Connecting, don't show red
		Thread.sleep(2500)
		whenever(connection.isConnectedSecurityConnecting) doReturn true
		model.update()
		context.run(model.carConnectionText.value!!)
		verify(context, times(3)).getString(eq(R.string.connectionStatusWaiting))
		context.run(model.carConnectionColor.value!!)
		verify(context, times(3)).getColor(R.color.connectionWaiting)

		// done connecting
		whenever(connection.isConnectedSecurityConnecting) doReturn false
		model.update()
		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.connectionStatusMissingConnectedApp))
		context.run(model.carConnectionColor.value!!)
		verify(context).getColor(R.color.connectionError)
	}

	@Test
	fun testConnectedNoBrand() {
		val connection = mock<CarConnectionDebugging> {
			on {isConnectedSecurityConnected} doReturn true
			on {isBCLConnected} doReturn true
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.txt_setup_bcl_connecting))
		assertEquals("", context.run(model.carConnectionHint.value!!))
		context.run(model.carConnectionColor.value!!)
		verify(context).getColor(R.color.connectionWaiting)
		context.run(model.carLogo.value!!)
		verify(context, never()).getDrawable(R.drawable.logo_bmw)
		verify(context, never()).getDrawable(R.drawable.logo_mini)
	}

	@Test
	fun testConnectedBMWBrand() {
		val connection = mock<CarConnectionDebugging>{
			on {isConnectedSecurityConnected} doReturn true
			on {isBCLConnected} doReturn true
			on {carBrand} doReturn "BMW"
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.connectionStatusConnected), eq("BMW"))
		assertEquals("", context.run(model.carConnectionHint.value!!))
		context.run(model.carConnectionColor.value!!)
		verify(context).getColor(R.color.connectionConnected)
		context.run(model.carLogo.value!!)
		verify(context).getDrawable(R.drawable.logo_bmw)
	}

	@Test
	fun testConnectedMiniBrand() {
		val connection = mock<CarConnectionDebugging>{
			on {isConnectedSecurityConnected} doReturn true
			on {isBCLConnected} doReturn true
			on {carBrand} doReturn "Mini"
		}
		val carInfo = mock<CarInformation>()
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.connectionStatusConnected), eq("MINI"))
		assertEquals("", context.run(model.carConnectionHint.value!!))
		context.run(model.carConnectionColor.value!!)
		verify(context).getColor(R.color.connectionConnected)
		context.run(model.carLogo.value!!)
		verify(context).getDrawable(R.drawable.logo_mini)
	}

	@Test
	fun testChassisCode() {
		val carCapabilities = mapOf(
			"vehicle.type" to "F56"
		)
		val connection = mock<CarConnectionDebugging>{
			on {isConnectedSecurityConnected} doReturn true
			on {isBCLConnected} doReturn true
			on {carBrand} doReturn "Mini"
		}
		val carInfo = mock<CarInformation> {
			on {capabilities} doReturn carCapabilities
		}
		val model = ConnectionStatusModel(connection, carInfo).apply { update() }

		assertEquals(false, model.isBtConnected.value)
		assertEquals(false, model.isUsbConnected.value)
		assertEquals(true, model.isBclReady.value)
		assertEquals(ChassisCode.F56, model.carChassisCode.value)

		context.run(model.carConnectionText.value!!)
		verify(context).getString(eq(R.string.connectionStatusConnected), eq(ChassisCode.F56.toString()))
		assertEquals("", context.run(model.carConnectionHint.value!!))
		context.run(model.carConnectionColor.value!!)
		verify(context).getColor(R.color.connectionConnected)
	}
}