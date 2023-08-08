package me.hufman.androidautoidrive.addons

import android.content.Intent
import android.content.pm.*
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString

class AddonDiscoveryTest {
	val installedAddons = mutableListOf(
		AddonAppInfo("Basic CDS", mock(), "cds.basic").apply {
			cdsNormalRequested = true
			cdsNormalGranted = true
		},
		AddonAppInfo("Extra CDS Denied", mock(), "cds.extradenied").apply {
			cdsNormalRequested = true
			cdsNormalGranted = true
			cdsPersonalRequested = true
		},
		AddonAppInfo("Extra CDS Granted", mock(), "cds.extragranted").apply {
			cdsNormalRequested = true
			cdsNormalGranted = true
			cdsPersonalRequested = true
			cdsPersonalGranted = true
		},
		AddonAppInfo("Car Addon", mock(), "connection.yay").apply {
			cdsNormalRequested = true
			cdsNormalGranted = true
			cdsPersonalRequested = true
			cdsPersonalGranted = true
			carConnectionRequested = true
			carConnectionGranted = true
		}
	)
	val installedAddonsByName = installedAddons.associateBy { it.packageName }
	val applicationInfos = installedAddonsByName.mapValues { addonInfo ->
		// need to pre-create these mock objects
		// can't stub mocks inside a different mock doAnswer
		mock<ApplicationInfo> {
			on {loadIcon(any())} doReturn addonInfo.value.icon
			on {loadLabel(any())} doReturn addonInfo.value.name
		}.apply {
			packageName = addonInfo.value.packageName
		}
	}

	val packageManager = mock<PackageManager> {
		// discover apps
		on {queryIntentServices(any(), anyInt())} doAnswer {
			when ((it.arguments[0] as Intent).action) {
				AddonDiscovery.INTENT_DATA_SERVICE -> installedAddons.filter { addonInfo ->
					addonInfo.packageName.startsWith("cds")
				}
				AddonDiscovery.INTENT_CONNECTION_SERVICE -> installedAddons.filter { addonInfo ->
					addonInfo.packageName.startsWith("connection")
				}
				else -> emptyList()
			}.map { addonInfo ->
				val serviceInfo = mock<ServiceInfo>().also { serviceInfo ->
					serviceInfo.packageName = addonInfo.packageName
				}
				mock<ResolveInfo>().also { resolveInfo ->
					resolveInfo.serviceInfo = serviceInfo
				}
			}
		}
		// app info
		on {getApplicationInfo(anyString(), anyInt())} doAnswer {
			applicationInfos[it.arguments[0] as String]
		}
		// label
		on {getApplicationLabel(any())} doAnswer {
			val appInfo = installedAddonsByName[(it.arguments[0] as ApplicationInfo).packageName]!!
			appInfo.name
		}
		on {getText(any(), anyInt(), any())} doAnswer {
			val appInfo = installedAddonsByName[(it.arguments[0] as ApplicationInfo).packageName]!!
			appInfo.name
		}
		// ask what permissions the app requests in its Manifest
		on {getPackageInfo(anyString(), anyInt())} doAnswer {
			val addonInfo = installedAddonsByName[it.arguments[0] as String]!!
			val permissions = ArrayList<String>()
			if (addonInfo.cdsNormalRequested) permissions.add(AddonDiscovery.PERMISSION_NORMAL)
			if (addonInfo.cdsPersonalRequested) permissions.add(AddonDiscovery.PERMISSION_PERSONAL)

			mock<PackageInfo>().apply {
				requestedPermissions = permissions.toArray(Array(0) { "" })
				packageName = it.arguments[0] as String
				applicationInfo = applicationInfos[it.arguments[0] as String]
			}
		}
		// ask what permissions the app actually has
		on {checkPermission(any(), any())} doAnswer {
			val addonInfo = installedAddonsByName[it.arguments[1] as String]!!
			when (it.arguments[0] as String) {
				AddonDiscovery.PERMISSION_NORMAL -> if (addonInfo.cdsNormalGranted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
				AddonDiscovery.PERMISSION_PERSONAL -> if (addonInfo.cdsPersonalGranted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
				else -> PackageManager.PERMISSION_DENIED
			}
		}
	}

	@Test
	fun testDiscovery() {
		val discovery = AddonDiscovery(packageManager)
		val discoveredAddons = discovery.discoverApps()
		val discoveredByName = discoveredAddons.associateBy { it.packageName }
		installedAddons.forEach { expected ->
			val discovered = discoveredByName[expected.packageName]
			assertNotNull("Discovered ${expected.packageName}", discovered)
			discovered ?: return
			assertEquals("${expected.packageName } identifier info is the same", expected, discovered)
			assertEquals("${expected.packageName} cdsNormalRequested", expected.cdsNormalRequested, discovered.cdsNormalRequested)
			assertEquals("${expected.packageName} cdsNormalGranted", expected.cdsNormalGranted, discovered.cdsNormalGranted)
			assertEquals("${expected.packageName} cdsPersonalRequested", expected.cdsPersonalRequested, discovered.cdsPersonalRequested)
			assertEquals("${expected.packageName} cdsPersonalGranted", expected.cdsPersonalGranted, discovered.cdsPersonalGranted)
			assertEquals("${expected.packageName} carConnectionRequested", expected.carConnectionRequested, discovered.carConnectionRequested)
			assertEquals("${expected.packageName} carConnectionGranted", expected.carConnectionGranted, discovered.carConnectionGranted)
		}
	}
}