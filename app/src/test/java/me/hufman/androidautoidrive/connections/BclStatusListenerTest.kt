package me.hufman.androidautoidrive.connections

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.mockBroadcastReceiverFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BclStatusListenerTest {
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
	fun testBclListener() {
		val callback = mock<() -> Unit>()
		val bclStatus = BclStatusListener(context, callback)

		// test subscription
		bclStatus.subscribe()
		verify(context).registerReceiver(any(), argThat {hasAction(BclStatusListener.BCL_REPORT)})
		verify(context).registerReceiver(any(), argThat {hasAction(BclStatusListener.BCL_REPORT)})
		bclStatus.unsubscribe()
		verify(context).unregisterReceiver(any())

		// try the callback
		val bclCallback = argumentCaptor<BroadcastReceiver>()
		verify(context, atLeastOnce()).registerReceiver(bclCallback.capture(), any())
		bclCallback.lastValue.onReceive(context, Intent(BclStatusListener.BCL_REPORT).apply {
			putExtra("EXTRA_START_TIMESTAMP", SystemClock.uptimeMillis())
			putExtra("EXTRA_NUM_BYTES_READ", 100L)
			putExtra("EXTRA_NUM_BYTES_WRITTEN", 1000L)
			putExtra("EXTRA_NUM_CONNECTIONS", 14)
			putExtra("EXTRA_INSTANCE_ID", 13.toShort())
			putExtra("EXTRA_WATCHDOG_RTT", 30L)
			putExtra("EXTRA_HU_BUFFER_SIZE", 8192)
			putExtra("EXTRA_REMAINING_ACK_BYTES", 65000L)
			putExtra("EXTRA_STATE", "RUNNING")
			putExtra("EXTRA_BRAND", "BMW")
		})

		assertEquals(100L, bclStatus.bytesRead)
		assertEquals(1000L, bclStatus.bytesWritten)
		assertEquals(14, bclStatus.numConnections)
		assertEquals(13.toShort(), bclStatus.instanceId)
		assertEquals(30, bclStatus.watchdogRtt)
		assertEquals(8192, bclStatus.huBufsize)
		assertEquals(65000L, bclStatus.remainingAckBytes)
		assertEquals("RUNNING", bclStatus.state)
		assertEquals("BMW", bclStatus.brand)
		verify(callback).invoke()
		reset(callback)

		bclCallback.lastValue.onReceive(context, Intent(BclStatusListener.BCL_TRANSPORT).apply {
			putExtra("EXTRA_TRANSPORT", "USB")
		})
		assertEquals("USB", bclStatus.transport)
		verify(callback).invoke()
		reset(callback)

		assertTrue(bclStatus.toString().isNotBlank())
	}
}