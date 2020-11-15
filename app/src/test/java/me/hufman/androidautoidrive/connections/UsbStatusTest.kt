package me.hufman.androidautoidrive.connections

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.*
import org.junit.Test

class UsbStatusTest {

	val context = mock<Context>()

	@Test
	fun testUsbListener() {
		val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"  // private action about connected state

		val callback = mock<() -> Unit>()
		val usbManager = mock<UsbManager>()
		whenever(context.getSystemService(eq(UsbManager::class.java))) doReturn usbManager
		val usbStatus = UsbStatus(context, callback)

		// test that it hooks to events
		usbStatus.register()
		verify(context).registerReceiver(any(), argThat {hasAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)})
		verify(context).registerReceiver(any(), argThat {hasAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)})
		verify(context).registerReceiver(any(), argThat {hasAction(ACTION_USB_STATE)})
		usbStatus.unregister()
		verify(context).unregisterReceiver(any())

		// try the callback
		val usbCallback = argumentCaptor<BroadcastReceiver>()
		verify(context, atLeastOnce()).registerReceiver(usbCallback.capture(), any())
		usbCallback.lastValue.onReceive(context, Intent(UsbManager.ACTION_USB_ACCESSORY_ATTACHED))
		verify(callback).invoke()
		reset(callback)
		usbCallback.lastValue.onReceive(context, Intent(UsbManager.ACTION_USB_ACCESSORY_DETACHED))
		verify(callback).invoke()
		reset(callback)
		usbCallback.lastValue.onReceive(context, Intent(ACTION_USB_STATE).apply {
			putExtra("mtp", false)
		})
		verify(callback).invoke()
		reset(callback)

		// test connection state
		assertFalse(usbStatus.isUsbConnected)
		assertFalse(usbStatus.isUsbTransferConnected)
		assertFalse(usbStatus.isUsbAccessoryConnected)

		// set connection
		usbCallback.lastValue.onReceive(context, Intent(ACTION_USB_STATE).apply {
			putExtra("connected", true)
			putExtra("mtp", false)
			putExtra("accessory", false)
		})
		assertTrue(usbStatus.isUsbConnected)
		assertFalse(usbStatus.isUsbTransferConnected)
		assertFalse(usbStatus.isUsbAccessoryConnected)

		// set MTP connection
		usbCallback.lastValue.onReceive(context, Intent(ACTION_USB_STATE).apply {
			putExtra("connected", true)
			putExtra("mtp", true)
			putExtra("accessory", false)
		})
		assertTrue(usbStatus.isUsbConnected)
		assertTrue(usbStatus.isUsbTransferConnected)
		assertFalse(usbStatus.isUsbAccessoryConnected)

		// set Accessory connection
		usbCallback.lastValue.onReceive(context, Intent(ACTION_USB_STATE).apply {
			putExtra("connected", true)
			putExtra("mtp", true)
			putExtra("accessory", true)     // not enough!
		})
		assertTrue(usbStatus.isUsbConnected)
		assertTrue(usbStatus.isUsbTransferConnected)
		assertFalse(usbStatus.isUsbAccessoryConnected)

		// set the USB Accessory name
		val accessory = mock<UsbAccessory> {
			on { manufacturer } doReturn "BMWi"
		}
		whenever(usbManager.accessoryList) doAnswer { arrayOf(accessory) }
		assertTrue(usbStatus.isUsbAccessoryConnected)
	}
}