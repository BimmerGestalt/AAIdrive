package me.hufman.androidautoidrive.phoneui

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.ChassisCode
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.TestCoroutineRule
import me.hufman.androidautoidrive.phoneui.viewmodels.CarSummaryModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt

class CarSummaryModelTest {
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
	fun testConnectedNoBrand() {
		val carInfo = mock<CarInformation>()
		val model = CarSummaryModel(carInfo, mock()).apply { update() }

		context.run(model.carLogo.value!!)
		verify(context, never()).getDrawable(R.drawable.logo_bmw)
		verify(context, never()).getDrawable(R.drawable.logo_mini)
	}

	@Test
	fun testConnectedBMWBrand() {
		val carCapabilities = mapOf(
				"hmi.type" to "BMW ID6L",
				"vehicle.type" to "F22"
		)
		val carInfo = mock<CarInformation> {
			on {capabilities} doReturn carCapabilities
		}
		val model = CarSummaryModel(carInfo, mock()).apply { update() }

		assertEquals("BMW", model.carBrand.value)
		context.run(model.carLogo.value!!)
		verify(context).getDrawable(R.drawable.logo_bmw)
	}

	@Test
	fun testConnectedMiniBrand() {
		val carCapabilities = mapOf(
			"hmi.type" to "MINI ID5",
			"vehicle.type" to "F56"
		)
		val carInfo = mock<CarInformation> {
			on {capabilities} doReturn carCapabilities
		}
		val model = CarSummaryModel(carInfo, mock()).apply { update() }

		assertEquals("MINI", model.carBrand.value)
		context.run(model.carLogo.value!!)
		verify(context).getDrawable(R.drawable.logo_mini)
	}

	@Test
	fun testChassisCode() {
		val carCapabilities = mapOf(
			"vehicle.type" to "F56"
		)
		val carInfo = mock<CarInformation> {
			on {capabilities} doReturn carCapabilities
		}
		val model = CarSummaryModel(carInfo, mock()).apply { update() }

		assertEquals(ChassisCode.F56, model.carChassisCode.value)
	}
}