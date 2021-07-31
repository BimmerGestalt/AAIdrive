package me.hufman.androidautoidrive.music

import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicMetadataTest {
	val fakeBundle = mock<Bundle> {
		on {keySet()} doReturn emptySet()
	}

	/**
	 * SomaFM has both DISPLAY_TITLE and weird metadata fields
	 * So ignore the given artist/title and use DISPLAY_TITLE
	 */
	@Test
	fun testSomaFm() {
		val metadata = mock<MediaMetadataCompat> {
			on { bundle } doReturn fakeBundle
			on { getString(any()) } doReturn null
			on { getLong(any()) } doReturn 0
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)) } doReturn "Rusty Hodge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ARTIST)) } doReturn "Frank Chacksfield Orchestra - Holiday In Rhodes"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_COMPILATION)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_TITLE)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)) } doReturn "Classic bachelor pad, playful exotica and vintage music of tomorrow."
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)) } doReturn "Frank Chacksfield Orchestra - Holiday In Rhodes"
		}
		val parsed = MusicMetadata.fromMediaMetadata(metadata)
		assertEquals("artist", "Illinois Street Lounge", parsed.artist)
		assertEquals("album", "Illinois Street Lounge", parsed.album)
		assertEquals("title", "Frank Chacksfield Orchestra - Holiday In Rhodes", parsed.title)
	}

	/**
	 * A variant of SomaFM with correct artist/title fields
	 */
	@Test
	fun testSomaFmWithMetadata() {
		val metadata = mock<MediaMetadataCompat> {
			on { bundle } doReturn fakeBundle
			on { getString(any()) } doReturn null
			on { getLong(any()) } doReturn 0
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)) } doReturn "Rusty Hodge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ARTIST)) } doReturn "Frank Chacksfield Orchestra"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_COMPILATION)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_TITLE)) } doReturn "Holiday In Rhodes"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)) } doReturn "Classic bachelor pad, playful exotica and vintage music of tomorrow."
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)) } doReturn "Frank Chacksfield Orchestra - Holiday In Rhodes"
		}
		val parsed = MusicMetadata.fromMediaMetadata(metadata)
		assertEquals("artist", "Frank Chacksfield Orchestra", parsed.artist)
		assertEquals("album", "Illinois Street Lounge", parsed.album)
		assertEquals("title", "Holiday In Rhodes", parsed.title)
	}

	/**
	 * Energy Radio only has DISPLAY fields
	 */
	@Test
	fun testEnergyRadio() {
		val metadata = mock<MediaMetadataCompat> {
			on { bundle } doReturn fakeBundle
			on { getString(any()) } doReturn null
			on { getLong(any()) } doReturn 0
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM)) } doReturn "Tout l'univers (EP)"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_COMPILATION)) } doReturn "Energy Basel"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)) } doReturn "Energy Basel"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)) } doReturn "Tout l'univers - Gjon's Tears"        // title and artist?
		}
		val parsed = MusicMetadata.fromMediaMetadata(metadata)
		assertEquals("artist", "Energy Basel", parsed.artist)
		assertEquals("album", "Tout l'univers (EP)", parsed.album)
		assertEquals("title", "Tout l'univers - Gjon's Tears", parsed.title)
	}

	/**
	 * A variant of Energy Radio with valid artist and title fields
	 */
	@Test
	fun testEnergyRadioWithMetadata() {
		val metadata = mock<MediaMetadataCompat> {
			on { bundle } doReturn fakeBundle
			on { getString(any()) } doReturn null
			on { getLong(any()) } doReturn 0
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ARTIST)) } doReturn "Gjon's Tears"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM)) } doReturn "Tout l'univers (EP)"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_TITLE)) } doReturn "Tout l'univers"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_COMPILATION)) } doReturn "Energy Basel"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)) } doReturn "Energy Basel"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)) } doReturn "Tout l'univers - Gjon's Tears"        // title and artist?
		}
		val parsed = MusicMetadata.fromMediaMetadata(metadata)
		assertEquals("artist", "Gjon's Tears", parsed.artist)
		assertEquals("album", "Tout l'univers (EP)", parsed.album)
		assertEquals("title", "Tout l'univers", parsed.title)
	}

	/**
	 * Swapped DISPLAY fields
	 */
	@Test
	fun testAimp() {
		val metadata = mock<MediaMetadataCompat> {
			on { bundle } doReturn fakeBundle
			on { getString(any()) } doReturn null
			on { getLong(any()) } doReturn 0
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ARTIST)) } doReturn "dj TAKA"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)) } doReturn "dj TAKA"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM)) } doReturn "milestone"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_TITLE)) } doReturn "Votum stellarum"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)) } doReturn "Votum stellarum"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)) } doReturn "milestone"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)) } doReturn "dj TAKA"
		}
		val parsed = MusicMetadata.fromMediaMetadata(metadata)
		assertEquals("artist", "dj TAKA", parsed.artist)
		assertEquals("album", "milestone", parsed.album)
		assertEquals("title", "Votum stellarum", parsed.title)
	}
}