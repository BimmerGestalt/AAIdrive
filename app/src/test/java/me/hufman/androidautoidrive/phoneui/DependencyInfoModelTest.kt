package me.hufman.androidautoidrive.phoneui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.BackgroundInterruptionDetection
import me.hufman.androidautoidrive.connections.CarConnectionDebugging
import me.hufman.androidautoidrive.phoneui.viewmodels.DependencyInfoModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@Suppress("BooleanLiteralArgument")
class DependencyInfoModelTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	fun carConnection(bmwConnected: Boolean, bmwMine: Boolean,
	                  miniConnected: Boolean, miniMine: Boolean): CarConnectionDebugging {
		return mock {
			on { isBMWInstalled } doReturn (bmwConnected || bmwMine)
			on { isBMWConnectedInstalled } doReturn bmwConnected
			on { isBMWConnected65Installed} doReturn false
			on { isBMWMineInstalled } doReturn bmwMine
			on { isMiniInstalled } doReturn (miniConnected || miniMine)
			on { isMiniConnected65Installed} doReturn false
			on { isMiniConnectedInstalled } doReturn miniConnected
			on { isMiniMineInstalled } doReturn miniMine
			on { isConnectedSecurityInstalled } doReturn (bmwConnected || bmwMine || miniConnected || miniMine)
			on { isConnectedSecurityConnected } doReturn (bmwConnected || bmwMine || miniConnected || miniMine)
		}
	}
	fun interruptionDetector(detectedKilled: Int, detectedSuspended: Int): BackgroundInterruptionDetection {
		return mock {
			on {this.detectedKilled} doReturn detectedKilled
			on {this.detectedSuspended } doReturn detectedSuspended
		}
	}

	@Test
	fun testStateBlank() {
		val carConnectionDebugging = carConnection(false, false, false, false)
		val interruptionDetector = interruptionDetector(0, 0)
		val model = DependencyInfoModel(carConnectionDebugging, interruptionDetector).apply { update() }

		assertEquals(false, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(false, model.isBmwReady.value)
		assertEquals(false, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(false, model.isMiniReady.value)
		assertEquals(false, model.isSecurityServiceDisconnected.value)  // no securityservice even installed
		assertEquals(false, model.hasBackgroundKilled.value)
		assertEquals(false, model.hasBackgroundSuspended.value)
	}

	@Test
	fun testStateBMWMine() {
		val carConnectionDebugging = carConnection(false, true, false, false)

		val interruptionDetector = interruptionDetector(0, 0)
		val model = DependencyInfoModel(carConnectionDebugging, interruptionDetector).apply { update() }

		assertEquals(false, model.isBmwConnectedInstalled.value)
		assertEquals(true, model.isBmwMineInstalled.value)
		assertEquals(true, model.isBmwReady.value)
		assertEquals(false, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(false, model.isMiniReady.value)
		assertEquals(false, model.isSecurityServiceDisconnected.value)  // installed securityservice is connected
		assertEquals(false, model.hasBackgroundKilled.value)
		assertEquals(false, model.hasBackgroundSuspended.value)
	}

	@Test
	fun testStateBMWConnectedNoSecurity() {
		val carConnectionDebugging = carConnection(true, false, false, false)
		whenever(carConnectionDebugging.isConnectedSecurityConnected) doReturn false
		val interruptionDetector = interruptionDetector(0, 0)
		val model = DependencyInfoModel(carConnectionDebugging, interruptionDetector).apply { update() }

		assertEquals(true, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(false, model.isBmwReady.value)
		assertEquals(false, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(false, model.isMiniReady.value)
		assertEquals(true, model.isSecurityServiceDisconnected.value)  // installed securityservice not connected
		assertEquals(false, model.hasBackgroundKilled.value)
		assertEquals(false, model.hasBackgroundSuspended.value)
	}

	@Test
	fun testStateBMWConnectedYesSecurity() {
		val carConnectionDebugging = carConnection(true, false, false, false)
		val interruptionDetector = interruptionDetector(0, 0)
		val model = DependencyInfoModel(carConnectionDebugging, interruptionDetector).apply { update() }

		assertEquals(true, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(true, model.isBmwReady.value)
		assertEquals(false, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(false, model.isMiniReady.value)
		assertEquals(false, model.isSecurityServiceDisconnected.value)  // installed securityservice is connected
		assertEquals(false, model.hasBackgroundKilled.value)
		assertEquals(false, model.hasBackgroundSuspended.value)
	}

	@Test
	fun testStateMiniMine() {
		val carConnectionDebugging = carConnection(false, false, false, true)
		val interruptionDetector = interruptionDetector(0, 0)
		val model = DependencyInfoModel(carConnectionDebugging, interruptionDetector).apply { update() }

		assertEquals(false, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(false, model.isBmwReady.value)
		assertEquals(false, model.isMiniConnectedInstalled.value)
		assertEquals(true, model.isMiniMineInstalled.value)
		assertEquals(true, model.isMiniReady.value)
		assertEquals(false, model.isSecurityServiceDisconnected.value)  // installed securityservice is connected
		assertEquals(false, model.hasBackgroundKilled.value)
		assertEquals(false, model.hasBackgroundSuspended.value)
	}

	@Test
	fun testStateMiniConnectedNoSecurity() {
		val carConnectionDebugging = carConnection(false, false, true, false)
		whenever(carConnectionDebugging.isConnectedSecurityConnected) doReturn false
		val interruptionDetector = interruptionDetector(0, 0)
		val model = DependencyInfoModel(carConnectionDebugging, interruptionDetector).apply { update() }

		assertEquals(false, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(false, model.isBmwReady.value)
		assertEquals(true, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(false, model.isMiniReady.value)
		assertEquals(true, model.isSecurityServiceDisconnected.value)  // installed securityservice not connected
		assertEquals(false, model.hasBackgroundKilled.value)
		assertEquals(false, model.hasBackgroundSuspended.value)
	}

	@Test
	fun testStateMiniConnectedYesSecurity() {
		val carConnectionDebugging = carConnection(false, false, true, false)
		val interruptionDetector = interruptionDetector(0, 0)
		val model = DependencyInfoModel(carConnectionDebugging, interruptionDetector).apply { update() }

		assertEquals(false, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(false, model.isBmwReady.value)
		assertEquals(true, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(true, model.isMiniInstalled.value)
		assertEquals(true, model.isMiniReady.value)
		assertEquals(false, model.isSecurityServiceDisconnected.value)  // installed securityservice is connected
		assertEquals(false, model.hasBackgroundKilled.value)
		assertEquals(false, model.hasBackgroundSuspended.value)
	}

	@Test
	fun testBackgroundKilled() {
		val carConnectionDebugging = carConnection(false, false, true, false)
		val interruptionDetector = interruptionDetector(5, 0)
		val model = DependencyInfoModel(carConnectionDebugging, interruptionDetector).apply { update() }

		assertEquals(true, model.hasBackgroundKilled.value)
		assertEquals(false, model.hasBackgroundSuspended.value)
	}

	@Test
	fun testBackgroundSuspended() {
		val carConnectionDebugging = carConnection(false, false, true, false)
		val interruptionDetector = interruptionDetector(0, 4)
		val model = DependencyInfoModel(carConnectionDebugging, interruptionDetector).apply { update() }

		assertEquals(false, model.hasBackgroundKilled.value)
		assertEquals(true, model.hasBackgroundSuspended.value)
	}
}