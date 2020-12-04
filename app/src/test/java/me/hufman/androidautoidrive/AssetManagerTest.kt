package me.hufman.androidautoidrive

import android.content.Context
import android.content.res.AssetManager
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.FileNotFoundException
import java.io.InputStream

class AssetManagerTest {
	val assets = mock<AssetManager> {
		on { open(any()) } doReturn mock<InputStream>()
	}
	val context = mock<Context> {
		on { assets } doReturn assets
	}



	/** This application falls through to the common resources */
	@Test
	fun testCommonAssets() {
		whenever(assets.open(argThat {startsWith("carapplications/appname/rhmi/bmw")})) doThrow FileNotFoundException()

		val manager = CarAppAssetManager(context, "appname")
		assertNotNull(manager.getAppCertificateRaw("bmw"))
		assertNotNull(manager.getUiDescription("BMW"))
		assertNotNull(manager.getImagesDB("bmw"))
		assertNotNull(manager.getTextsDB("bmw"))
		verify(assets, times(1)).open("carapplications/appname/rhmi/bmw/appname.p7b")
		verify(assets, times(1)).open("carapplications/appname/appname.p7b")
		verify(assets, times(1)).open("carapplications/appname/rhmi/bmw/ui_description.xml")
		verify(assets, times(1)).open("carapplications/appname/rhmi/ui_description.xml")
		verify(assets, times(1)).open("carapplications/appname/rhmi/bmw/images.zip")
		verify(assets, times(1)).open("carapplications/appname/rhmi/common/images.zip")
		verify(assets, times(1)).open("carapplications/appname/rhmi/bmw/texts.zip")
		verify(assets, times(1)).open("carapplications/appname/rhmi/common/texts.zip")
		verifyNoMoreInteractions(assets)
	}

	/** This application has all BMW resources */
	@Test
	fun testBMWAssets() {
		val manager = CarAppAssetManager(context, "appname")
		assertNotNull(manager.getAppCertificateRaw("bmw"))
		assertNotNull(manager.getUiDescription("BMW"))
		assertNotNull(manager.getImagesDB("bmw"))
		assertNotNull(manager.getTextsDB("bmw"))
		verify(assets, times(1)).open("carapplications/appname/rhmi/bmw/appname.p7b")
		verify(assets, times(1)).open("carapplications/appname/rhmi/bmw/ui_description.xml")
		verify(assets, times(1)).open("carapplications/appname/rhmi/bmw/images.zip")
		verify(assets, times(1)).open("carapplications/appname/rhmi/bmw/texts.zip")
		verifyNoMoreInteractions(assets)
	}

	/** This app has some Mini assets and falls through to common */
	@Test
	fun testMiniAssets() {
		whenever(assets.open("carapplications/appname/rhmi/mini/appname.p7b")) doThrow FileNotFoundException()
		whenever(assets.open("carapplications/appname/rhmi/mini/ui_description.xml")) doThrow FileNotFoundException()

		val manager = CarAppAssetManager(context, "appname")
		assertNotNull(manager.getAppCertificateRaw("minI"))
		assertNotNull(manager.getUiDescription("Mini"))
		assertNotNull(manager.getImagesDB("MINI"))
		assertNotNull(manager.getTextsDB("mInI"))
		verify(assets, times(1)).open("carapplications/appname/rhmi/mini/appname.p7b")
		verify(assets, times(1)).open("carapplications/appname/appname.p7b")
		verify(assets, times(1)).open("carapplications/appname/rhmi/mini/ui_description.xml")
		verify(assets, times(1)).open("carapplications/appname/rhmi/ui_description.xml")
		verify(assets, times(1)).open("carapplications/appname/rhmi/mini/images.zip")
		verify(assets, times(1)).open("carapplications/appname/rhmi/mini/texts.zip")
	}
}