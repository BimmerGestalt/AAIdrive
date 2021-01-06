package me.hufman.androidautoidrive.music

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import me.hufman.androidautoidrive.MutableObservable
import me.hufman.androidautoidrive.Observable
import me.hufman.androidautoidrive.music.controllers.GenericMusicAppController
import me.hufman.androidautoidrive.music.controllers.MusicAppController
import java.util.*

class MusicBrowser(val handler: Handler, val mediaBrowser: MediaBrowserCompat, val musicAppInfo: MusicAppInfo) {
	companion object {
		const val TAG = "MusicBrowser"
	}
	// all interactions with MediaBrowserCompat must be from the same thread

	var connected = true   // whether we are still connected
		private set

	init {
		if (musicAppInfo.className == null) {
			Log.i(TAG, "Skipping connection to ${musicAppInfo.name}, no className found")
		}
	}

	class Connector(val context: Context, val handler: Handler): MusicAppController.Connector {
		override fun connect(appInfo: MusicAppInfo): Observable<GenericMusicAppController> {
			val pendingController = MutableObservable<GenericMusicAppController>()
			if (appInfo.className == null) {
				pendingController.value = null
			} else {
				val component = ComponentName(appInfo.packageName, appInfo.className)
				Log.i(TAG, "Opening connection to ${appInfo.name}")
				// load the mediaBrowser on the UI thread
				handler.post {
					Log.i(TAG, "Connecting to the app ${appInfo.name}")
					var mediaBrowser: MediaBrowserCompat? = null
					val callback = object: MediaBrowserCompat.ConnectionCallback() {
						override fun onConnected() {
							val curMediaBrowser = mediaBrowser
							val sessionToken = curMediaBrowser?.sessionToken
							Log.i(TAG, "Successful MediaBrowser connection to ${appInfo.name}")
							if (curMediaBrowser != null && sessionToken != null) {
								pendingController.value = GenericMusicAppController(context, MediaControllerCompat(context, sessionToken), MusicBrowser(handler, curMediaBrowser, appInfo))
							} else {
								pendingController.value = null
							}
						}

						override fun onConnectionSuspended() {
							Log.i(TAG, "MediaBrowser suspended from ${appInfo.name}")
							mediaBrowser?.disconnect()
							pendingController.value?.disconnect()
							pendingController.value = null
						}
						override fun onConnectionFailed() {
							Log.i(TAG, "Failed MediaBrowser connection to ${appInfo.name}")
							mediaBrowser?.disconnect()
							pendingController.value?.disconnect()
							pendingController.value = null
						}
					}
					val mediaBrowserConnection = MediaBrowserCompat(context, component, callback, null)
					mediaBrowser = mediaBrowserConnection
					mediaBrowser.connect()
				}
			}
			return pendingController
		}

	}

	private fun getRoot(): String {
		if (!connected) return "disconnected root"
		return when (musicAppInfo.packageName) {
			"com.spotify.music" -> "com.google.android.projection.gearhead---spotify_media_browser_root_android_auto"   // the Android Auto root for Spotify
			"com.aspiro.tidal" -> "__ROOT_LOGGED_IN__"   // Tidal Music
			"com.apple.android.music" -> "__AUTO_ROOT__"     // Apple Music
			"com.radio.fmradio" -> "__ROOT__"   // Radio FM
			"app.sunshinelive.de.sunshinelive" -> "/"   // Sunshine Live
			"com.google.android.apps.books" -> "com.google.android.apps.play.books.orson"  // Google Play Books
			"fm.libro.librofm" -> "/"   // Libro FM
			"se.sr.android" -> "root"  // Sveriges Radio
			"com.bambuna.podcastaddict" -> "__ROOT__"  // Podcast Addict
			"com.neutroncode.mp" -> "root"    // Neutron
			"com.neutroncode.mpeval" -> "root"    // Neutron (Eval)
			"com.acast.nativeapp" -> "root"     // Acast Podcast Player
			"com.podcastsapp" -> "__ROOT__"     // Audecibel
			"com.audials" -> "root"         // Audials Radio
			"com.audials.paid" -> "root"    // Audials Radio Pro
			"grit.storytel.app" -> "/"      // Storytel
			"com.france24.androidapp" -> "france_media_monde"       // France 24 playlists?
			"com.rhapsody.napster" -> "ROOT"    // napster
			"net.faz.FAZ" -> "media_root_id"    // Faz, but it returns an empty list anyways
			"com.jio.media.jiobeats" -> "__ROOT__"      // JioSaavn
			"com.gaana" -> "_parent_"   // Gaana
			else -> return when(musicAppInfo.className) {   // some apps have a shared service library
				"com.itmwpb.vanilla.radioapp.player.MusicService" -> "/"    // OneCMS (HOT97 Official)
				"com.example.android.uamp.media.MusicService" -> "/"        // UAMP Example player (Radio Bob)
				else -> mediaBrowser.root
			}
		}
	}

	fun disconnect() {
		handler.post {
			connected = false
			mediaBrowser.disconnect()
		}
	}

	suspend fun browse(path: String?, timeout: Long = 10000): List<MediaBrowserCompat.MediaItem> {
		val deferred = CompletableDeferred<List<MediaBrowserCompat.MediaItem>>()
		withContext(handler.asCoroutineDispatcher()) {
			if (connected) {
				val browsePath = (path ?: getRoot()).let {
					// the browsePath (parentId) is not allowed to be blank
					if (it.isEmpty()) { "/" } else it
				}

				var callback: MediaBrowserCompat.SubscriptionCallback? = null
				callback = object : MediaBrowserCompat.SubscriptionCallback() {
					override fun onError(parentId: String) {
						mediaBrowser.unsubscribe(browsePath, callback!!)
						deferred.complete(emptyList())
					}

					override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem?>) {
						mediaBrowser.unsubscribe(browsePath, callback!!)
						deferred.complete(children.filterNotNull())
					}

					override fun onError(parentId: String, options: Bundle) {
						onError(parentId)
					}

					override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem?>, options: Bundle) {
						onChildrenLoaded(parentId, children)
					}
				}
				mediaBrowser.subscribe(browsePath, callback)

				// now we wait for the results
				try {
					withTimeout(timeout) {
						while (!deferred.isCompleted) {
							delay(100)
						}
					}
				} catch (e: CancellationException) {
					// timeout expired
					mediaBrowser.unsubscribe(browsePath, callback)
				}
				if (!deferred.isCompleted) {
					deferred.complete(LinkedList())
				}
				true    // requires a boolean for this branch?
			} else {
				deferred.complete(LinkedList())
			}
		}
		return deferred.await()
	}

	suspend fun search(query: String, timeout: Long = 5000): List<MediaBrowserCompat.MediaItem>? {
		val deferred = CompletableDeferred<List<MediaBrowserCompat.MediaItem>?>()
		withContext(handler.asCoroutineDispatcher()) {
			if (connected) {
				mediaBrowser.search(query, null, object : MediaBrowserCompat.SearchCallback() {
					override fun onError(query: String, extras: Bundle?) {
						deferred.complete(null)
					}

					override fun onSearchResult(query: String, extras: Bundle?, items: MutableList<MediaBrowserCompat.MediaItem?>) {
						deferred.complete(items.filterNotNull())
					}
				})
			} else {
				deferred.complete(null)
			}
			// now we wait for the results
			try {
				withTimeout(timeout) {
					while (!deferred.isCompleted) {
						delay(100)
					}
				}
			} catch (e: CancellationException) {
				// timeout expired
			}
			if (!deferred.isCompleted) {
				deferred.complete(null)
			}
		}
		return deferred.await()
	}
}