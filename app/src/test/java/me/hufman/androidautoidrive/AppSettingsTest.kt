package me.hufman.androidautoidrive

import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppSettingsTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	@Test
	fun testBooleanSettingRead() {
		val context = mock<Context>()
		val setting = BooleanLiveSetting(context, AppSettings.KEYS.ENABLED_NOTIFICATIONS)

		// it can read the AppSettings
		AppSettings.tempSetSetting(setting.key, "true")
		assertEquals("$setting is true", true, setting.value)
		AppSettings.tempSetSetting(setting.key, "false")
		assertEquals("$setting is false" , false, setting.value)
	}
	@Test
	fun testBooleanSettingWrite() {
		val preferences = mock<SharedPreferences> {
			on { edit() } doReturn mock<SharedPreferences.Editor>()
		}
		val context = mock<Context> {
			on {getSharedPreferences(any(), any())} doReturn preferences
		}
		val setting = BooleanLiveSetting(context, AppSettings.KEYS.ENABLED_NOTIFICATIONS)

		// it can write the AppSettings
		AppSettings.tempSetSetting(setting.key, "false")
		setting.setValue(true)
		assertEquals("$setting is true", "true", AppSettings[setting.key])
		setting.setValue(false)
		assertEquals("$setting is true", "false", AppSettings[setting.key])
	}

	@Test
	fun testStringSettingRead() {
		val context = mock<Context>()
		val setting = StringLiveSetting(context, AppSettings.KEYS.GMAPS_STYLE)

		// it can read the AppSettings
		AppSettings.tempSetSetting(setting.key, "night")
		assertEquals("night", setting.value)
		AppSettings.tempSetSetting(setting.key, "auto")
		assertEquals("auto", setting.value)
	}
	@Test
	fun testStringSettingWrite() {
		val preferences = mock<SharedPreferences> {
			on { edit() } doReturn mock<SharedPreferences.Editor>()
		}
		val context = mock<Context> {
			on {getSharedPreferences(any(), any())} doReturn preferences
		}
		val setting = StringLiveSetting(context, AppSettings.KEYS.GMAPS_STYLE)

		// it can write the AppSettings
		AppSettings.tempSetSetting(setting.key, "auto")
		setting.setValue("night")
		assertEquals("night", AppSettings[setting.key])
		setting.setValue("auto")
		assertEquals("auto", AppSettings[setting.key])
	}

	@Test
	fun testListSettings() {
		val settings = MockAppSettings(AppSettings.KEYS.HIDDEN_MUSIC_APPS to "a,b")
		val listSettings = ListSetting(settings, AppSettings.KEYS.HIDDEN_MUSIC_APPS)

		// tests the mass set functionality
		val expected = setOf("a", "b")
		assertEquals(expected, listSettings.getAll())

		listSettings.setAll(setOf("1", "2"))
		assertEquals("1,2", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		listSettings.setAll(setOf("a", "b"))
		val foundItems = mutableSetOf<String>()
		listSettings.withSet {
			foundItems.addAll(this)
		}
		assertEquals(expected, foundItems)

		// set functionality
		assertEquals("a,b", listSettings.iterator().asSequence().joinToString(","))
		assertTrue(listSettings.contains("a"))
		assertFalse(listSettings.contains("c"))
		assertTrue(listSettings.containsAll(setOf("a")))
		assertTrue(listSettings.containsAll(setOf("a", "b")))
		assertFalse(listSettings.containsAll(setOf("a", "b", "c")))
		assertEquals(2, listSettings.size)
		assertFalse(listSettings.isEmpty())

		// mutable set functionality
		listSettings.add("c")
		assertEquals("a,b,c", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		listSettings.addAll(setOf("c", "d"))
		assertEquals("a,b,c,d", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		listSettings.clear()
		assertEquals("", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS] = "a,b"
		listSettings.remove("b")
		assertEquals("a", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		listSettings.removeAll(setOf("a", "b"))
		assertEquals("", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS] = "a,b"
		listSettings.retainAll(setOf("a", "c"))
		assertEquals("a", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])
	}
}