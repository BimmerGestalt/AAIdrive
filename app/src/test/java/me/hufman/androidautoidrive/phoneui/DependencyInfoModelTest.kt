package me.hufman.androidautoidrive.phoneui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.connections.CarConnectionDebugging
import me.hufman.androidautoidrive.phoneui.viewmodels.DependencyInfoModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DependencyInfoModelTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	@Test
	fun testStateBlank() {
		val carConnectionDebugging = mock<CarConnectionDebugging> {
			on { isBMWConnectedInstalled } doReturn false
			on { isBMWMineInstalled } doReturn false
			on { isMiniConnectedInstalled } doReturn false
			on { isMiniMineInstalled } doReturn false
			on { isConnectedSecurityInstalled } doReturn false
			on { isConnectedSecurityConnected } doReturn false
		}

		val model = DependencyInfoModel(carConnectionDebugging).apply { update() }

		assertEquals(false, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(false, model.isBmwReady.value)
		assertEquals(false, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(false, model.isMiniReady.value)
		assertEquals(false, model.isSecurityServiceDisconnected.value)  // no securityservice even installed
	}

	@Test
	fun testStateBMWMine() {
		val carConnectionDebugging = mock<CarConnectionDebugging> {
			on { isBMWConnectedInstalled } doReturn false
			on { isBMWMineInstalled } doReturn true
			on { isMiniConnectedInstalled } doReturn false
			on { isMiniMineInstalled } doReturn false
			on { isConnectedSecurityInstalled } doReturn true
			on { isConnectedSecurityConnected } doReturn true
		}

		val model = DependencyInfoModel(carConnectionDebugging).apply { update() }

		assertEquals(false, model.isBmwConnectedInstalled.value)
		assertEquals(true, model.isBmwMineInstalled.value)
		assertEquals(false, model.isBmwReady.value)
		assertEquals(false, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(false, model.isMiniReady.value)
		assertEquals(false, model.isSecurityServiceDisconnected.value)  // installed securityservice is connected
	}

	@Test
	fun testStateBMWConnectedNoSecurity() {
		val carConnectionDebugging = mock<CarConnectionDebugging> {
			on { isBMWConnectedInstalled } doReturn true
			on { isBMWMineInstalled } doReturn false
			on { isMiniConnectedInstalled } doReturn false
			on { isMiniMineInstalled } doReturn false
			on { isConnectedSecurityInstalled } doReturn true
			on { isConnectedSecurityConnected } doReturn false
		}

		val model = DependencyInfoModel(carConnectionDebugging).apply { update() }

		assertEquals(true, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(false, model.isBmwReady.value)
		assertEquals(false, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(false, model.isMiniReady.value)
		assertEquals(true, model.isSecurityServiceDisconnected.value)  // installed securityservice not connected
	}

	@Test
	fun testStateBMWConnectedYesSecurity() {
		val carConnectionDebugging = mock<CarConnectionDebugging> {
			on { isBMWConnectedInstalled } doReturn true
			on { isBMWMineInstalled } doReturn false
			on { isMiniConnectedInstalled } doReturn false
			on { isMiniMineInstalled } doReturn false
			on { isConnectedSecurityInstalled } doReturn true
			on { isConnectedSecurityConnected } doReturn true
		}

		val model = DependencyInfoModel(carConnectionDebugging).apply { update() }

		assertEquals(true, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(true, model.isBmwReady.value)
		assertEquals(false, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(false, model.isMiniReady.value)
		assertEquals(false, model.isSecurityServiceDisconnected.value)  // installed securityservice is connected
	}

	@Test
	fun testStateMiniMine() {
		val carConnectionDebugging = mock<CarConnectionDebugging> {
			on { isBMWConnectedInstalled } doReturn false
			on { isBMWMineInstalled } doReturn false
			on { isMiniConnectedInstalled } doReturn false
			on { isMiniMineInstalled } doReturn true
			on { isConnectedSecurityInstalled } doReturn true
			on { isConnectedSecurityConnected } doReturn true
		}

		val model = DependencyInfoModel(carConnectionDebugging).apply { update() }

		assertEquals(false, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(false, model.isBmwReady.value)
		assertEquals(false, model.isMiniConnectedInstalled.value)
		assertEquals(true, model.isMiniMineInstalled.value)
		assertEquals(false, model.isMiniReady.value)
		assertEquals(false, model.isSecurityServiceDisconnected.value)  // installed securityservice is connected
	}

	@Test
	fun testStateMiniConnectedNoSecurity() {
		val carConnectionDebugging = mock<CarConnectionDebugging> {
			on { isBMWConnectedInstalled } doReturn false
			on { isBMWMineInstalled } doReturn false
			on { isMiniConnectedInstalled } doReturn true
			on { isMiniMineInstalled } doReturn false
			on { isConnectedSecurityInstalled } doReturn true
			on { isConnectedSecurityConnected } doReturn false
		}

		val model = DependencyInfoModel(carConnectionDebugging).apply { update() }

		assertEquals(false, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(false, model.isBmwReady.value)
		assertEquals(true, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(false, model.isMiniReady.value)
		assertEquals(true, model.isSecurityServiceDisconnected.value)  // installed securityservice not connected
	}

	@Test
	fun testStateMiniConnectedYesSecurity() {
		val carConnectionDebugging = mock<CarConnectionDebugging> {
			on { isBMWConnectedInstalled } doReturn false
			on { isBMWMineInstalled } doReturn false
			on { isMiniConnectedInstalled } doReturn true
			on { isMiniMineInstalled } doReturn false
			on { isConnectedSecurityInstalled } doReturn true
			on { isConnectedSecurityConnected } doReturn true
		}

		val model = DependencyInfoModel(carConnectionDebugging).apply { update() }

		assertEquals(false, model.isBmwConnectedInstalled.value)
		assertEquals(false, model.isBmwMineInstalled.value)
		assertEquals(false, model.isBmwReady.value)
		assertEquals(true, model.isMiniConnectedInstalled.value)
		assertEquals(false, model.isMiniMineInstalled.value)
		assertEquals(true, model.isMiniReady.value)
		assertEquals(false, model.isSecurityServiceDisconnected.value)  // installed securityservice is connected
	}
}