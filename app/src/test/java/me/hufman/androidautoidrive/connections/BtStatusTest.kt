package me.hufman.androidautoidrive.connections

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.os.Parcelable
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.mockBroadcastReceiverFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BtStatusTest {
	val context = mock<Context>()

	@Before
	fun setUp() {
		mockBroadcastReceiverFactory = { body ->
			mock {
				on { onReceive(any(), any()) } doAnswer { body(it.arguments[0] as Context, it.arguments[1] as Intent) }
			}
		}
	}

	@Test
	fun testBtListener() {
		val callback = mock<() -> Unit>()
		val btManager = mock<BluetoothManager>()
		whenever(context.getSystemService(eq(BluetoothManager::class.java))) doReturn btManager
		val btAdapter = mock<BluetoothAdapter>()
		whenever(btManager.adapter) doReturn btAdapter
		val btStatus = BtStatus(context, callback)

		// verify registration
		val a2dpListenerCaptor = argumentCaptor<BluetoothProfile.ServiceListener>()
		val hfListenerCaptor = argumentCaptor<BluetoothProfile.ServiceListener>()
		btStatus.register()
		verify(btAdapter).getProfileProxy(eq(context), a2dpListenerCaptor.capture(), eq(BluetoothProfile.A2DP))
		verify(btAdapter).getProfileProxy(eq(context), hfListenerCaptor.capture(), eq(BluetoothProfile.HEADSET))

		val bluetoothListenerCaptor = argumentCaptor<BroadcastReceiver>()
		val uuidListenerCaptor = argumentCaptor<BroadcastReceiver>()
		verify(context).registerReceiver(bluetoothListenerCaptor.capture(), argThat { hasAction(BluetoothDevice.ACTION_ACL_CONNECTED) })
		verify(context).registerReceiver(bluetoothListenerCaptor.capture(), argThat { hasAction(BluetoothDevice.ACTION_ACL_DISCONNECTED) })
		verify(context).registerReceiver(bluetoothListenerCaptor.capture(), argThat { hasAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) })
		verify(context).registerReceiver(uuidListenerCaptor.capture(), argThat { hasAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED) })
		verify(context).registerReceiver(uuidListenerCaptor.capture(), argThat { hasAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) })
		verify(context).registerReceiver(uuidListenerCaptor.capture(), argThat { hasAction(BluetoothDevice.ACTION_UUID) })

		// profiles connect
		val hfProfile = mock<BluetoothProfile>()
		val a2dpProfile = mock<BluetoothProfile>()
		hfListenerCaptor.lastValue.onServiceConnected(BluetoothProfile.HEADSET, hfProfile)
		a2dpListenerCaptor.lastValue.onServiceConnected(BluetoothProfile.A2DP, a2dpProfile)
		verify(callback, times(2)).invoke()
		reset(callback)

		// trigger a UUID discovery with a connected car
		val car = mock<BluetoothDevice> {
			on { name } doReturn "BMW12345"
		}
		val headphones = mock<BluetoothDevice> {
			on { name } doReturn "Bose"
		}
		whenever(a2dpProfile.connectedDevices) doAnswer { listOf(car, headphones)}
		a2dpListenerCaptor.lastValue.onServiceConnected(BluetoothProfile.A2DP, a2dpProfile)
		verify(car).fetchUuidsWithSdp()
		verify(headphones, never()).fetchUuidsWithSdp()

		// UUID discover finishes
		reset(callback)
		uuidListenerCaptor.lastValue.onReceive(context, Intent(BluetoothDevice.ACTION_UUID).apply {
			putExtra(BluetoothDevice.EXTRA_DEVICE, car)
			putExtra(BluetoothDevice.EXTRA_UUID, emptyArray<Parcelable>())
		})
		verify(callback).invoke()

		// status
		whenever(hfProfile.connectedDevices) doAnswer { listOf(headphones) }
		whenever(a2dpProfile.connectedDevices) doAnswer { listOf(headphones) }

		assertFalse(btStatus.isBTConnected)
		assertFalse(btStatus.isHfConnected)
		whenever(hfProfile.connectedDevices) doAnswer { listOf(car) }
		assertTrue(btStatus.isHfConnected)

		assertFalse(btStatus.isA2dpConnected)
		whenever(a2dpProfile.connectedDevices) doAnswer { listOf(car) }
		assertTrue(btStatus.isA2dpConnected)
		assertTrue(btStatus.isBTConnected)

		val uuidSSP = mock<ParcelUuid> {
			on { uuid } doReturn BtStatus.UUID_SPP
		}
		whenever(car.uuids) doAnswer { emptyArray<ParcelUuid>() }
		assertFalse(btStatus.isSPPAvailable)
		whenever(car.uuids) doAnswer { arrayOf(uuidSSP) }
		assertTrue(btStatus.isSPPAvailable)

		// unregister
		btStatus.unregister()
		verify(context).unregisterReceiver(bluetoothListenerCaptor.lastValue)
		verify(context).unregisterReceiver(uuidListenerCaptor.lastValue)
		verify(btAdapter).closeProfileProxy(BluetoothProfile.HEADSET, hfProfile)
		verify(btAdapter).closeProfileProxy(BluetoothProfile.A2DP, a2dpProfile)
	}
}