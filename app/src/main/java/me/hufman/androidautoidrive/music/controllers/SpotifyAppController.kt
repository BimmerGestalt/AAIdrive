package me.hufman.androidautoidrive.music.controllers

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.google.gson.Gson
import com.soywiz.kds.iterators.fastForEachReverse
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.*
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.Observable
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.music.*
import me.hufman.androidautoidrive.music.PlaybackPosition
import me.hufman.androidautoidrive.music.spotify.SpotifyMusicMetadata
import me.hufman.androidautoidrive.music.spotify.SpotifyWebApi
import me.hufman.androidautoidrive.music.spotify.TemporaryPlaylistState
import me.hufman.androidautoidrive.utils.PackageManagerCompat.getApplicationInfoCompat
import me.hufman.androidautoidrive.utils.PackageManagerCompat.getPackageInfoCompat
import me.hufman.androidautoidrive.utils.Utils
import java.util.*

class SpotifyAppController(context: Context, val remote: SpotifyAppRemote, val webApi: SpotifyWebApi, val appSettings: MutableAppSettings, val isProbing: Boolean): MusicAppController {
	companion object {
		const val TAG = "SpotifyAppController"
		const val REDIRECT_URI = "me.hufman.androidautoidrive://spotify_callback"

		fun getClientId(context: Context): String {
			return context.packageManager.getApplicationInfoCompat(context.packageName, PackageManager.GET_META_DATA)
					?.metaData?.getString("com.spotify.music.API_KEY", "unset") ?: "unset"
		}

		fun hasSupport(context: Context): Boolean {
			val clientId = getClientId(context)
			return clientId != "unset" && clientId != "invalid" && clientId != ""
		}

		fun isSpotifyInstalled(context: Context): Boolean {
			return context.packageManager.getPackageInfoCompat("com.spotify.music", 0) != null
		}

		fun MusicMetadata.Companion.fromSpotify(track: Track, coverArt: Bitmap? = null): MusicMetadata {
			// general track metadata
			return MusicMetadata(mediaId = track.uri, queueId = track.uri.hashCode().toLong(),
					title = track.name, album = track.album.name, artist = track.artist.name, coverArt = coverArt)
		}

		fun MusicMetadata.toListItem(): ListItem {
			return ListItem(this.mediaId, this.mediaId, null, this.title, this.subtitle,
					this.playable, this.browseable)
		}

		fun CustomAction.Companion.fromSpotify(name: String, icon: Drawable? = null): CustomAction {
			return formatCustomActionDisplay(CustomAction("com.spotify.music", name, name, icon.hashCode(), icon, null, null))
		}
	}

	class Connector(val context: Context, val prompt: Boolean = true, val isProbing: Boolean = false): MusicAppController.Connector {
		companion object {
			var lastError: Throwable? = null
			fun previousControlSuccess(): Boolean {
				return AppSettings[AppSettings.KEYS.SPOTIFY_CONTROL_SUCCESS].toBoolean()
			}
		}

		var lastError: Throwable?
			get() = Connector.lastError
			set(value) {Connector.lastError = value}

		fun hasSupport(): Boolean {
			return SpotifyAppController.hasSupport(context)
		}
		fun isSpotifyInstalled(): Boolean {
			return SpotifyAppController.isSpotifyInstalled(context)
		}
		fun previousControlSuccess(): Boolean = Connector.previousControlSuccess()

		override fun connect(appInfo: MusicAppInfo): Observable<SpotifyAppController> {
			if (appInfo.packageName != "com.spotify.music") {
				val pendingController = MutableObservable<SpotifyAppController>()
				pendingController.value = null
				return pendingController
			}
			return connect()
		}

		fun connect(): Observable<SpotifyAppController> {
			Log.i(TAG, "Attempting to connect to Spotify Remote")
			val params = ConnectionParams.Builder(getClientId(context))
					.setRedirectUri(REDIRECT_URI)
					.showAuthView(prompt)
					.build()

			val pendingController = MutableObservable<SpotifyAppController>()
			val remoteListener = object: com.spotify.android.appremote.api.Connector.ConnectionListener {
				override fun onFailure(e: Throwable?) {
					Log.e(TAG, "Failed to connect to Spotify Remote: $e")
					val appSettings = MutableAppSettingsReceiver(context)
					val expected = previousControlSuccess()
					if (hasSupport(context) && (expected || prompt)) {
						// show an error to the UI, unless we don't have an API key
						// but only if we previously had support or are manually prompting
						lastError = e
					}
					// remember that we failed to connect
					if (pendingController.value == null) {
						appSettings[AppSettings.KEYS.SPOTIFY_CONTROL_SUCCESS] = "false"
					}
					// disconnect an existing session, if any
					pendingController.value?.disconnect()
					pendingController.value = null
				}

				override fun onConnected(remote: SpotifyAppRemote?) {
					if (remote != null) {
						Log.i(TAG, "Successfully connected to Spotify Remote")
						lastError = null

						val appSettings = MutableAppSettingsReceiver(context)
						appSettings[AppSettings.KEYS.SPOTIFY_CONTROL_SUCCESS] = "true"

						val spotifyWebApi = SpotifyWebApi.getInstance(context, appSettings)
						spotifyWebApi.initializeWebApi(isProbing)
						spotifyWebApi.isUsingSpotify = true

						pendingController.value = SpotifyAppController(context, remote, spotifyWebApi, appSettings, isProbing)

						// if app discovery says we aren't able to connect, discover again
						if (!isProbing) {
							val musicAppDiscovery = MusicAppDiscovery(context, Handler(Looper.getMainLooper()))
							musicAppDiscovery.loadInstalledMusicApps()
							val spotifyAppInfo = musicAppDiscovery.allApps.firstOrNull { it.packageName == "com.spotify.music" }
							if (spotifyAppInfo?.connectable == false) {
								musicAppDiscovery.probeApp(spotifyAppInfo)
							}
						}
					} else {
						Log.e(TAG, "Connected to a null Spotify Remote?")
						pendingController.value = null
					}
				}
			}

			SpotifyAppRemote.connect(context, params, remoteListener)
			return pendingController
		}
	}

	// create the Custom Actions during client creation, so that the language is loaded
	val CUSTOM_ACTION_TURN_REPEAT_ALL_ON = CustomAction.fromSpotify("TURN_REPEAT_ALL_ON",
			context.getDrawable(R.drawable.spotify_repeat_off))
	val CUSTOM_ACTION_TURN_REPEAT_ONE_ON = CustomAction.fromSpotify("TURN_REPEAT_ONE_ON",
			context.getDrawable(R.drawable.spotify_repeat_on))
	val CUSTOM_ACTION_TURN_REPEAT_ONE_OFF = CustomAction.fromSpotify("TURN_REPEAT_ONE_OFF",
			context.getDrawable(R.drawable.spotify_repeat_one))
	val CUSTOM_ACTION_ADD_TO_COLLECTION = CustomAction.fromSpotify("ADD_TO_COLLECTION",
			context.getDrawable(R.drawable.spotify_add_library))
	val CUSTOM_ACTION_REMOVE_FROM_COLLECTION = CustomAction.fromSpotify("REMOVE_FROM_COLLECTION",
			context.getDrawable(R.drawable.spotify_added_library))
	val CUSTOM_ACTION_START_RADIO = CustomAction.fromSpotify("START_RADIO",
			context.getDrawable(R.drawable.spotify_start_radio))

	// always add these entries in the root, if they are missing
	class ReplacementListItem(id: String, val alternativeIds: List<String>, uri: String, imageUri: ImageUri,
	                          title: String, subtitle: String,
	                          playable: Boolean, hasChildren: Boolean):
			ListItem(id, uri, imageUri, title, subtitle, playable, hasChildren)
	val includedRootEntries = listOf(
			ReplacementListItem("com.spotify.your-library", listOf("com.spotify.your-music"), "com.spotify.your-library",
					ImageUri("android.resource://com.spotify.music/drawable/ic_eis_your_library"),
					L.MUSIC_SPOTIFY_BROWSEROOT_LIBRARY, "", false, true
			),
			ReplacementListItem("com.spotify.browse", listOf("spotify.browse"), "com.spotify.browse",
					ImageUri("android.resource://com.spotify.music/drawable/ic_eis_browse"),
					L.MUSIC_SPOTIFY_BROWSEROOT_BROWSE, "", false, true
			),
	)

	var connected = true

	// Spotify is very asynchronous, save any subscription state for the getters
	var callback: ((MusicAppController) -> Unit)? = null    // UI listener
	val spotifySubscription: Subscription<PlayerState> = remote.playerApi.subscribeToPlayerState()
	val playlistSubscription: Subscription<PlayerContext> = remote.playerApi.subscribeToPlayerContext()
	var playerActions: PlayerRestrictions? = null
	var playerOptions: PlayerOptions? = null
	var currentTrack: MusicMetadata? = null
	var currentSongCoverArtCache = LruCache<ImageUri, Bitmap>(4)
	var position: PlaybackPosition = PlaybackPosition(true, false, 0, 0, -1)
	var currentTrackLibrary: Boolean? = null
	var currentPlayerContext: PlayerContext = PlayerContext()
	var queueMetadata: QueueMetadata? = null
	val coverArtCache = LruCache<ImageUri, Bitmap>(50)
	var createQueueMetadataJob: Job? = null
	var defaultDispatcher = Dispatchers.Default
	var onQueueLoaded: (() -> Unit)? = null
	val gson: Gson = Gson()

	init {
		spotifySubscription.setEventCallback { playerState ->
			if (isProbing) {
				Log.d(TAG, "Probe instance, not loading playerState")
				return@setEventCallback
			}

			Log.d(TAG, "Heard an update from Spotify")

			// update the available actions
			playerActions = playerState.playbackRestrictions
			playerOptions = playerState.playbackOptions

			// update the current track info
			val track = playerState.track
			if (track != null) {
				val cachedCoverArt = currentSongCoverArtCache[track.imageUri]
				currentTrack = MusicMetadata.fromSpotify(track, coverArt = cachedCoverArt)
				val loadingTrack = currentTrack
				if (cachedCoverArt == null) {
					// try to load the coverart
					val coverArtLoader = remote.imagesApi.getImage(track.imageUri)
					coverArtLoader.setResultCallback { coverArt ->
						currentSongCoverArtCache.put(track.imageUri, coverArt)
						if (loadingTrack?.mediaId == currentTrack?.mediaId) {   // still playing the same song
							currentTrack = MusicMetadata.fromSpotify(track, coverArt = coverArt)
							callback?.invoke(this)
						}
					}
				}
				currentTrackLibrary = null
				remote.userApi.getLibraryState(track.uri).setResultCallback {
					currentTrackLibrary = it.isAdded
				}
			} else {
				currentTrack = null
			}

			// update a progress bar
			position = PlaybackPosition(playerState.isPaused, false, lastPosition = playerState.playbackPosition, maximumPosition = playerState.track.duration)

			callback?.invoke(this)
		}

		playlistSubscription.setEventCallback { playerContext ->
			if (isProbing) {
				Log.d(TAG, "Probe instance, not loading playerContext")
				return@setEventCallback
			}

			Log.d(TAG, "Heard an update from Spotify queue: ${playerContext.uri}")
			// update the current queue
			val uri = playerContext.uri
			if (currentPlayerContext.uri != uri) {
				currentPlayerContext = playerContext

				// if there are any running QueueMetadata creation jobs then stop those
				if (createQueueMetadataJob?.isActive == true) {
					createQueueMetadataJob?.cancel()
				}
				createQueueMetadataJob = null

				webApi.clearPendingQueueMetadataCreate()

				val isLikedSongsPlaylist = playerContext.type == "your_library" || playerContext.type == "your_library_tracks"
				val isArtistPlaylist = playerContext.type == "artist" || playerContext.type == "your_library_artist"
				val isPodcastPlaylist = playerContext.type == "show"
				if (isLikedSongsPlaylist || playerContext.title == SpotifyWebApi.LIKED_SONGS_PLAYLIST_NAME) {
					createLikedSongsQueueMetadata()
				} else if (isArtistPlaylist || playerContext.title == SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME) {
					createArtistTopSongsQueueMetadata()
				} else if (isPodcastPlaylist) {
					createPodcastQueueMetadata(playerContext)
				} else {
					createPlaylistQueueMetadata(playerContext, true)
				}
			}

			callback?.invoke(this)
		}
	}

	/**
	 * Creates the [QueueMetadata] for the artist playlist with the artist's top songs using the Web
	 * API. If the Web API is not authorized then the [QueueMetadata] is created from the app remote API.
	 */
	fun createArtistTopSongsQueueMetadata() {
		createQueueMetadataJob = GlobalScope.launch(defaultDispatcher) {
			val artistSongsTemporaryPlaylistStateKey = AppSettings.KEYS.SPOTIFY_ARTIST_SONGS_PLAYLIST_STATE
			val artistSongsStateJson = appSettings[artistSongsTemporaryPlaylistStateKey]
			var temporaryPlaylistState: TemporaryPlaylistState? = null

			val artistUri = if (currentPlayerContext.title == SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME && artistSongsStateJson.isNotBlank()) {
				temporaryPlaylistState = gson.fromJson(artistSongsStateJson, TemporaryPlaylistState::class.java)
				temporaryPlaylistState.originalPlaylistUri
			} else {
				currentPlayerContext.uri
			}

			val queueItems = webApi.getArtistTopSongs(this@SpotifyAppController, artistUri) ?: emptyList()
			if (queueItems.isNotEmpty()) {
				if (temporaryPlaylistState != null) {
					Log.d(TAG, "Previous artist state found and loaded for artist ${currentPlayerContext.title}")
					temporaryPlaylistState = loadAndUpdateTemporaryPlaylistState(artistSongsStateJson, queueItems)
				} else {
					// PlayerContext title of an artist playlist is sometimes blank
					if (currentPlayerContext.title.isBlank()) {
						currentPlayerContext = PlayerContext(currentPlayerContext.uri, getCurrentQueueListItem().title, currentPlayerContext.subtitle, currentPlayerContext.type)
					}

					temporaryPlaylistState = if (artistSongsStateJson.isBlank()) {
						Log.d(TAG, "No previous artist state found for artist ${currentPlayerContext.title}")
						createTemporaryPlaylistState(SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME, queueItems)
					} else {
						Log.d(TAG, "Found previous artist state for artist ${currentPlayerContext.title}")
						loadAndUpdateTemporaryPlaylistState(artistSongsStateJson, queueItems)
					}

					if (temporaryPlaylistState == null) {
						Log.e(TAG, "Error creating artist songs playlist for artist ${currentPlayerContext.title}, falling back to app remote API")
						createPlaylistQueueMetadata(currentPlayerContext, false)
						return@launch
					}
				}

				saveTemporaryPlaylistState(temporaryPlaylistState, artistSongsTemporaryPlaylistStateKey)

				callback?.invoke(this@SpotifyAppController)
			} else {
				createPlaylistQueueMetadata(currentPlayerContext, false)
			}
		}
	}

	/**
	 * Creates the [QueueMetadata] for the "Liked Songs" playlist using the Web API. If the web API is
	 * not authorized then the the [QueueMetadata] is created from the app remote API.
	 */
	fun createLikedSongsQueueMetadata() {
		createQueueMetadataJob = GlobalScope.launch(defaultDispatcher) {
			val queueItems = webApi.getLikedSongs(this@SpotifyAppController) ?: emptyList()
			if (queueItems.isNotEmpty()) {
				queueMetadata = QueueMetadata(currentPlayerContext.title, null, queueItems)
				onQueueLoaded?.invoke()

				val likedSongsTemporaryPlaylistStateKey = AppSettings.KEYS.SPOTIFY_LIKED_SONGS_PLAYLIST_STATE
				val likedSongsStateJson = appSettings[likedSongsTemporaryPlaylistStateKey]

				val temporaryPlaylistState = if (likedSongsStateJson.isBlank()) {
					Log.d(TAG, "No previous liked songs state found.")
					createTemporaryPlaylistState(SpotifyWebApi.LIKED_SONGS_PLAYLIST_NAME, queueItems)
				} else {
					Log.d(TAG, "Found previous liked songs state.")
					loadAndUpdateTemporaryPlaylistState(likedSongsStateJson, queueItems)
				}
				if (temporaryPlaylistState == null) {
					Log.e(TAG, "Error creating liked songs playlist, falling back to app remote API")
					createPlaylistQueueMetadata(currentPlayerContext, false)
					return@launch
				}

				saveTemporaryPlaylistState(temporaryPlaylistState, likedSongsTemporaryPlaylistStateKey)

				callback?.invoke(this@SpotifyAppController)
			} else {
				createPlaylistQueueMetadata(currentPlayerContext, false)
			}
		}
	}

	/**
	 * Creates a [TemporaryPlaylistState] for the supplied playlist name, creating the playlist if it
	 * is not present and adding the queueItems in context to it. If the playlist creation fails
	 * then null is returned.
	 */
	private suspend fun createTemporaryPlaylistState(playlistName: String, queueItems: List<MusicMetadata>): TemporaryPlaylistState? {
		val queueItemsHashCode = queueItems.hashCode().toString()
		val existingPlaylistUri = webApi.getPlaylistUri(playlistName)
		val playlistUri: String
		val playlistId: String
		if (existingPlaylistUri == null) {
			Log.d(TAG, "No user playlist for $playlistName found, creating a new one.")
			val uri = webApi.createPlaylist(playlistName, L.MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION) ?: return null
			webApi.addSongsToPlaylist(uri.id, queueItems)
			playlistUri = uri.uri
			playlistId = uri.id
		} else {
			Log.d(TAG, "User playlist for $playlistName found, using existing playlist.")
			playlistUri = existingPlaylistUri.uri
			playlistId = existingPlaylistUri.id
		}

		val temporaryPlaylistState = TemporaryPlaylistState(queueItemsHashCode, playlistUri, playlistId, currentPlayerContext.title, currentPlayerContext.uri)

		currentPlayerContext = PlayerContext(temporaryPlaylistState.playlistUri, currentPlayerContext.title, currentPlayerContext.subtitle, currentPlayerContext.type)

		val coverArt = getQueueCoverArt()
		queueMetadata = QueueMetadata(currentPlayerContext.title, null, queueItems, coverArt, currentPlayerContext.uri)
		onQueueLoaded?.invoke()

		val coverArtBase64 = Base64.encodeToString(Utils.compressBitmapJpg(coverArt, 85), Base64.NO_WRAP)
		webApi.setPlaylistImage(playlistId, coverArtBase64)

		return temporaryPlaylistState
	}

	/**
	 * Loads a [TemporaryPlaylistState] from its serialized JSON string. If the state of the
	 * deserialized [TemporaryPlaylistState] is not valid then its contents and hash code will be
	 * updated.
	 */
	private suspend fun loadAndUpdateTemporaryPlaylistState(temporaryPlaylistStateJson: String, queueItems: List<MusicMetadata>): TemporaryPlaylistState {
		val queueItemsHashCode = queueItems.hashCode().toString()
		val temporaryPlaylistState = gson.fromJson(temporaryPlaylistStateJson, TemporaryPlaylistState::class.java)

		if (queueItemsHashCode != temporaryPlaylistState.hashCode) {
			Log.d(TAG, "Previous temporary playlist state is no longer valid, updating temporary playlist contents and writing new hash code.")
			webApi.replacePlaylistSongs(temporaryPlaylistState.playlistId, queueItems)
			temporaryPlaylistState.hashCode = queueItemsHashCode

			if (currentPlayerContext.uri != temporaryPlaylistState.playlistUri) {
				temporaryPlaylistState.playlistTitle = getCurrentQueueListItem().title
			}
		}

		currentPlayerContext = PlayerContext(temporaryPlaylistState.playlistUri, temporaryPlaylistState.playlistTitle, currentPlayerContext.subtitle, currentPlayerContext.type)

		val coverArt = getQueueCoverArt()
		queueMetadata = QueueMetadata(currentPlayerContext.title, null, queueItems, coverArt, currentPlayerContext.uri)
		onQueueLoaded?.invoke()

		return temporaryPlaylistState
	}

	/**
	 * Saves the provided [TemporaryPlaylistState] to the [AppSettings] for the supplied key.
	 */
	private fun saveTemporaryPlaylistState(temporaryPlaylistState: TemporaryPlaylistState, appSettingsTemporaryPlaylistStateKey: AppSettings.KEYS) {
		appSettings[appSettingsTemporaryPlaylistStateKey] = gson.toJson(temporaryPlaylistState)
	}

	/**
	 * Retrieves the cover art Bitmap for the provided ImageUri. If it is present in the coverArtCache
	 * then the Bitmap is returned otherwise the imagesApi is called to asynchronously add the cover
	 * art Bitmap to the cache and returns null in the meantime.
	 */
	fun getCoverArt(imageUri: ImageUri): Bitmap? {
		val coverArt = coverArtCache.get(imageUri)
		if (coverArt != null) {
			return coverArt
		} else {
			remote.imagesApi.getImage(imageUri, Image.Dimension.THUMBNAIL).setResultCallback { coverArtResponse ->
				coverArtCache.put(imageUri, coverArtResponse)
			}
		}
		return null
	}

	/**
	 * Creates the [QueueMetadata] for the playlist either using the Web API or App Remote API. If
	 * the Web API is marked to be used but is not authorized, the App Remote API is used as a fallback
	 * to create the [QueueMetadata].
	 */
	private fun createPlaylistQueueMetadata(playerContext: PlayerContext, useWebApi: Boolean) {
		createQueueMetadataJob = GlobalScope.launch(defaultDispatcher) {
			if (currentPlayerContext.uri != null) {
				currentPlayerContext = playerContext
				if (useWebApi) {
					val queueItems = webApi.getPlaylistSongs(this@SpotifyAppController, playerContext.uri) ?: emptyList()
					if (queueItems.isNotEmpty()) {
						createQueueMetadataFromPlayerContext(playerContext, queueItems)
					} else {
						Log.e(TAG, "Error getting songs from playlist ${playerContext.uri}, falling back to app remote API")
						createQueueMetadataWithAppRemote(playerContext)
					}
				} else {
					createQueueMetadataWithAppRemote(playerContext)
				}
			}
		}
	}

	/**
	 * Creates the [QueueMetadata] for the podcast playlist.
	 */
	private fun createPodcastQueueMetadata(playerContext: PlayerContext) {
		createQueueMetadataJob = GlobalScope.launch(defaultDispatcher) {
			if (currentPlayerContext.uri != null) {
				createQueueMetadataWithAppRemote(playerContext)
			}
		}
	}

	/**
	 * Creates the [QueueMetadata] for the provided [PlayerContext] using the app remote API.
	 */
	private fun createQueueMetadataWithAppRemote(playerContext: PlayerContext) {
		val listItem = ListItem(playerContext.uri, playerContext.uri, null, playerContext.title, playerContext.subtitle, false, true)
		loadPaginatedItems(listItem, { currentPlayerContext.uri == playerContext.uri }) {
			val queueItems = removeShufflePlayButtonMetadata(it, currentPlayerContext.uri)
			createQueueMetadataFromPlayerContext(playerContext, queueItems)
		}
	}

	/**
	 * Creates the [QueueMetadata] for the provided [PlayerContext].
	 */
	private fun createQueueMetadataFromPlayerContext(playerContext: PlayerContext, queueItems: List<MusicMetadata>) {
		// builds the QueueMetadata while waiting for cover art to load
		queueMetadata = QueueMetadata(playerContext.title, playerContext.subtitle, queueItems, mediaId = playerContext.uri)
		currentPlayerContext = PlayerContext(playerContext.uri, playerContext.title, playerContext.subtitle, playerContext.type)

		GlobalScope.launch(defaultDispatcher) {
			val coverArt = getQueueCoverArt()
			queueMetadata = QueueMetadata(playerContext.title, playerContext.subtitle, queueItems, coverArt, playerContext.uri)
		}

		onQueueLoaded?.invoke()
		callback?.invoke(this@SpotifyAppController)
	}

	/**
	 * Retrieves the [ListItem] of the current queue.
	 */
	private suspend fun getCurrentQueueListItem(): ListItem {
		val deferred = CompletableDeferred<ListItem>()
		val recentlyPlayedUri = "com.spotify.recently-played"
		val li = ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true)
		remote.contentApi.getChildrenOfItem(li, 1, 0).setResultCallback { recentlyPlayed ->
			val item = recentlyPlayed?.items?.get(0)
			if (item != null) {
				deferred.complete(item)
			}
		}

		return deferred.await()
	}

	/**
	 * Retrieves the cover art of the current queue.
	 */
	private suspend fun getQueueCoverArt(): Bitmap {
		val deferred = CompletableDeferred<Bitmap>()
		val item = getCurrentQueueListItem()
		remote.imagesApi.getImage(item.imageUri, Image.Dimension.THUMBNAIL).setResultCallback { coverArt ->
			deferred.complete(coverArt)
		}

		return deferred.await()
	}

	/**
	 * Loads the complete children contents of the given parentItem
	 * checking if stillValid at each step
	 * and then passing it to onComplete at the end
	 */
	private fun loadPaginatedItems(parentItem: ListItem,
	                               stillValid: () -> Boolean, onComplete: (List<MusicMetadata>) -> Unit) {
		loadPaginatedItems(LinkedList(), parentItem, stillValid, onComplete)
	}

	/**
	 * Loads the complete children contents of the given parentItem
	 * into the destination list
	 * checking if stillValid at each step
	 * and passing the destination list to onComplete at the end
	 */
	private fun loadPaginatedItems(destination: MutableList<MusicMetadata>, parentItem: ListItem,
	                               stillValid: () -> Boolean, onComplete: (List<MusicMetadata>) -> Unit) {
		remote.contentApi.getChildrenOfItem(parentItem, 200, destination.size).setResultCallback { items ->
			if (items != null && stillValid()) {    // this is still a valid request
				destination.addAll(items.items.filterNotNull().map { listItem: ListItem ->
					SpotifyMusicMetadata.fromSpotifyQueueListItem(this, listItem)
				})
				if (destination.size < items.total && items.items.isNotEmpty()) {   // more tracks to load
					loadPaginatedItems(destination, parentItem, stillValid, onComplete)
				} else {
					onComplete(destination)
				}
			}
		}.setErrorCallback {
			Log.w(TAG, "Unable to fetch Spotify content", it)
			onComplete(destination)
		}
	}

	override fun play() {
		remote.connectApi.connectSwitchToLocalDevice()
		remote.playerApi.resume()
	}

	override fun pause() {
		remote.playerApi.pause()
	}

	override fun skipToPrevious() {
		remote.playerApi.skipPrevious()
	}

	override fun skipToNext() {
		remote.playerApi.skipNext()
	}

	override fun seekTo(newPos: Long) {
		remote.playerApi.seekTo(newPos)
	}

	override fun playSong(song: MusicMetadata) {
		// if the item is a track then load the album context for it so the rest of the album is queued up
		if (song.subtitle == "Track") {
			// album queue loaded is not the correct context for the song, will need to load the correct album into the queue
			if (queueMetadata?.mediaId != song.album) {
				remote.playerApi.play(song.album)

				// queue is loaded async but is needed before playing the song from the queue
				onQueueLoaded = {
					playQueue(song)
				}
			} else {
				playQueue(song)
			}
		} else {
			remote.playerApi.play(song.mediaId)
		}
	}

	override fun playQueue(song: MusicMetadata) {
		// queue item equality is based on queueId, which we are storing as the uri hashCode
		// we don't know the index to jump to
		// so, we have to iterate through the queue to find the user's selected queueId
		val queueUri = this.currentPlayerContext.uri
		if (song.queueId != null) {
			queueMetadata?.songs?.forEachIndexed { index, it ->
				if (it.queueId == song.queueId) {
					remote.playerApi.skipToIndex(queueUri, index)
					return
				}
			}
		}
	}

	override fun playFromSearch(search: String) {
	}

	override fun customAction(action: CustomAction) {
		when (action) {
			CUSTOM_ACTION_TURN_REPEAT_ALL_ON -> remote.playerApi.setRepeat(Repeat.ALL)
			CUSTOM_ACTION_TURN_REPEAT_ONE_ON -> remote.playerApi.setRepeat(Repeat.ONE)
			CUSTOM_ACTION_TURN_REPEAT_ONE_OFF -> remote.playerApi.setRepeat(Repeat.OFF)
			CUSTOM_ACTION_ADD_TO_COLLECTION -> currentTrack?.mediaId?.let { remote.userApi.addToLibrary(it) }
			CUSTOM_ACTION_REMOVE_FROM_COLLECTION -> currentTrack?.mediaId?.let { remote.userApi.removeFromLibrary(it) }
		}
	}

	override fun getQueue(): QueueMetadata? {
		// unreliable per https://github.com/spotify/android-sdk/issues/10
		return queueMetadata
	}

	override fun getMetadata(): MusicMetadata? {
		return currentTrack
	}

	override fun getPlaybackPosition(): PlaybackPosition {
		return position
	}

	override fun isSupportedAction(action: MusicAction): Boolean {
		val playerActions = playerActions
		return when (action) {
			MusicAction.SKIP_TO_PREVIOUS -> playerActions?.canSkipPrev == true
			MusicAction.SKIP_TO_NEXT -> playerActions?.canSkipNext == true
			MusicAction.PLAY -> true
			MusicAction.PAUSE -> true
			MusicAction.SEEK_TO -> playerActions?.canSeek == true
			MusicAction.SKIP_TO_QUEUE_ITEM -> false
			MusicAction.SET_SHUFFLE_MODE -> playerActions?.canToggleShuffle == true
			MusicAction.SET_REPEAT_MODE -> playerActions?.canRepeatContext == true || playerActions?.canRepeatTrack == true
			else -> false
		}
	}

	override fun getCustomActions(): List<CustomAction> {
		val actions = LinkedList<CustomAction>()

		// Needed for when the repeat button isn't supported by the hmiState
		if (getRepeatMode() == RepeatMode.ALL) {
			if (playerActions?.canRepeatTrack == true) {
				actions.add(CUSTOM_ACTION_TURN_REPEAT_ONE_ON)
			}
			else {
				actions.add(CUSTOM_ACTION_TURN_REPEAT_ONE_OFF)
			}
		} else if (getRepeatMode() == RepeatMode.ONE) {
			actions.add(CUSTOM_ACTION_TURN_REPEAT_ONE_OFF)
		}
		else {
			if (playerActions?.canRepeatContext == true) {
				actions.add(CUSTOM_ACTION_TURN_REPEAT_ALL_ON)
			}
			else if (playerActions?.canRepeatTrack == true) {
				actions.add(CUSTOM_ACTION_TURN_REPEAT_ONE_ON)
			}
		}

		if (currentTrackLibrary == false) actions.add(CUSTOM_ACTION_ADD_TO_COLLECTION)
		if (currentTrackLibrary == true) actions.add(CUSTOM_ACTION_REMOVE_FROM_COLLECTION)

		// START_RADIO is blocked on https://github.com/spotify/android-sdk/issues/66

		return actions
	}

	override fun toggleShuffle() {
		val shuffling = isShuffling()
		remote.playerApi.setShuffle(!shuffling)
	}

	override fun toggleRepeat() {
		if (playerOptions?.repeatMode == Repeat.OFF) {
			if (playerActions?.canRepeatContext == true) {
				remote.playerApi.setRepeat(Repeat.ALL)
			} else if (playerActions?.canRepeatTrack == true) {
				remote.playerApi.setRepeat(Repeat.ONE)
			}
		} else if (playerOptions?.repeatMode == Repeat.ALL) {
			if (playerActions?.canRepeatTrack == true) {
				remote.playerApi.setRepeat(Repeat.ONE)
			} else {
				remote.playerApi.setRepeat(Repeat.OFF)
			}
		} else {
			remote.playerApi.setRepeat(Repeat.OFF)
		}
	}

	override fun isShuffling(): Boolean {
		return playerOptions?.isShuffling == true
	}

	override fun getRepeatMode(): RepeatMode {
		return when(playerOptions?.repeatMode) {
			Repeat.ALL -> RepeatMode.ALL
			Repeat.ONE -> RepeatMode.ONE
			else -> RepeatMode.OFF
		}
	}

	override suspend fun browse(directory: MusicMetadata?): List<MusicMetadata> {
		val isArtistDirectory: (MusicMetadata?) -> Boolean = {it?.mediaId?.contains(":artists:") == true}
		val deferred = CompletableDeferred<List<MusicMetadata>>()
		if (directory?.mediaId == null) {
			remote.contentApi.getRecommendedContentItems("default").setResultCallback { results ->
				val items = (results?.items ?: emptyArray()).toMutableList()
				includedRootEntries.fastForEachReverse { item ->
					if (items.size > 0 && !items.any { it.id == item.id || item.alternativeIds.contains(it.id) }) {
						items.add(1, item)  // add at #1 because #0 is recently-played
					}
				}

				deferred.complete(items.map {
					SpotifyMusicMetadata.fromBrowseItem(this, it)
				})
			}.setErrorCallback {
				deferred.complete(LinkedList())
			}
		} else if (isArtistDirectory(directory)) {
			GlobalScope.launch(defaultDispatcher) {
				val artistMediaId = directory.mediaId
				val artistTopSongs = webApi.getArtistTopSongs(this@SpotifyAppController, artistMediaId) ?: emptyList()
				if (artistTopSongs.isNotEmpty()) {
					deferred.complete(artistTopSongs)
				} else {
					loadPaginatedItems(directory.toListItem(), { !deferred.isCancelled }) { results ->
						deferred.complete(removeShufflePlayButtonMetadata(results, artistMediaId))
					}
				}
			}
		} else {
			loadPaginatedItems(directory.toListItem(), { !deferred.isCancelled }) { results ->
				deferred.complete(results.filterNot { it.title == SpotifyWebApi.LIKED_SONGS_PLAYLIST_NAME || it.title == SpotifyWebApi.ARTIST_SONGS_PLAYLIST_NAME })
			}
		}
		return deferred.await()
	}

	/**
	 * Removes the shuffle play button [MusicMetadata] if it is present in the supplied list.
	 *
	 * When loading a list of tracks such as an album, a shuffle play button [MusicMetadata] object is
	 * sometimes present. Call this method to get the list of [MusicMetadata]s that omit the shuffle
	 * play button.
	 */
	private fun removeShufflePlayButtonMetadata(items: List<MusicMetadata>, mediaIdContext: String): List<MusicMetadata> {
		return if (items.isNotEmpty() && items[0].mediaId == mediaIdContext) {
			items.drop(1)
		} else {
			items
		}
	}

	override suspend fun search(query: String): List<MusicMetadata> {
		val deferred = CompletableDeferred<List<MusicMetadata>>()
		GlobalScope.launch(defaultDispatcher) {
			deferred.complete(webApi.searchForQuery(this@SpotifyAppController, query) ?: emptyList())
		}
		return deferred.await()
	}

	override fun subscribe(callback: (MusicAppController) -> Unit) {
		this.callback = callback
	}

	override fun isConnected(): Boolean {
		return this.connected
	}

	override fun disconnect() {
		Log.d(TAG, "Disconnecting from Spotify")
		this.connected = false
		this.callback = null
		try {
			spotifySubscription.cancel()
		} catch (e: Exception) {
			Log.w(TAG, "Exception while disconnecting from Spotify: $e")
		}
		try {
			playlistSubscription.cancel()
		} catch (e: Exception) {
			Log.w(TAG, "Exception while disconnecting from Spotify: $e")
		}
		try {
			SpotifyAppRemote.disconnect(remote)
		} catch (e: Exception) {
			Log.w(TAG, "Exception while disconnecting from Spotify: $e")
		}

		webApi.disconnect()
	}

	override fun toString(): String {
		return "SpotifyAppController"
	}
}