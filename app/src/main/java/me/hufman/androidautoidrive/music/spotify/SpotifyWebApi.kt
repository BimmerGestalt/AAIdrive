package me.hufman.androidautoidrive.music.spotify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.adamratzman.spotify.*
import com.adamratzman.spotify.endpoints.pub.SearchApi
import com.adamratzman.spotify.models.*
import com.adamratzman.spotify.utils.Market
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicAppDiscovery
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.phoneui.SpotifyAuthorizationActivity
import net.openid.appauth.*
import java.util.*
import kotlin.collections.ArrayList

/**
 * Handles the logic around the Spotify Web API. This class is a singleton to prevent issues of having
 * multiple [SpotifyClientApi]s.
 */
class SpotifyWebApi private constructor(val context: Context, val appSettings: MutableAppSettings) {
	companion object {
		const val TAG = "SpotifyWebApi"
		const val NOTIFICATION_CHANNEL_ID = "SpotifyAuthorization"
		const val NOTIFICATION_REQ_ID = 56
		const val LIKED_SONGS_PLAYLIST_NAME = "AAIDRIVE_LIKED_SONGS"
		const val ARTIST_SONGS_PLAYLIST_NAME = "AAIDRIVE_ARTIST_SONGS"

		private var webApiInstance: SpotifyWebApi? = null
		private var instanceCount = 0

		/**
		 * Retrieves the current [SpotifyWebApi] instance creating one if it there are no instances
		 * currently running.
		 */
		fun getInstance(context: Context, appSettings: MutableAppSettings): SpotifyWebApi {
			if (webApiInstance == null) {
				webApiInstance = SpotifyWebApi(context, appSettings)
			}
			instanceCount++
			return webApiInstance as SpotifyWebApi
		}
	}

	// will be null if the user is not authenticated
	private var webApi: SpotifyClientApi? = null

	private val authStateManager: SpotifyAuthStateManager
	private val clientId: String
	private var pendingQueueMetadataCreate: (() -> Unit)? = null

	var isUsingSpotify: Boolean = false

	init {
		Log.d(TAG, "Initializing for the first time")
		authStateManager = SpotifyAuthStateManager.getInstance(appSettings)
		clientId = SpotifyAppController.getClientId(context)
	}

	/**
	 * Returns the songs from a specified playlist URI. This supports playlists that include both
	 * songs and podcasts. Local tracks are not supported.
	 */
	suspend fun getPlaylistSongs(spotifyAppController: SpotifyAppController, playlistUri: String): List<SpotifyMusicMetadata>? = executeApiCall("Failed to get songs from playlist $playlistUri") {
		if (webApi == null) {
			return@executeApiCall emptyList()
		}

		val songs: ArrayList<SpotifyMusicMetadata> = ArrayList()

		var pagedSongs = webApi?.playlists?.getPlaylistTracks(playlistUri, 50, 0, Market.FROM_TOKEN)
		while(pagedSongs != null) {
			pagedSongs.items.forEach { playlistTrack ->
				val track = playlistTrack.track?.asTrack
				if (track != null && track.isPlayable) {
					songs.add(createSpotifyMusicMetadataFromTrack(track, spotifyAppController))
				}

				val episodeTrack = playlistTrack.track?.asPodcastEpisodeTrack
				if (episodeTrack != null && episodeTrack.isPlayable) {
					songs.add(createSpotifyMusicMetadataFromPodcastEpisodeTrack(episodeTrack, spotifyAppController))
				}
			}
			pagedSongs = pagedSongs.getNext()
		}

		return@executeApiCall songs
	}

	/**
	 * Creates a private playlist with the provided name and optionally provided description. The
	 * newly created playlist's [PlaylistUri] is returned.
	 */
	suspend fun createPlaylist(playlistName: String, playlistDescription: String? = null): PlaylistUri? = executeApiCall("Failed to create playlist $playlistName") {
		webApi?.playlists?.createClientPlaylist(name = playlistName, description = playlistDescription, public = false)?.uri
	}

	/**
	 * Adds the provided list of songs to the specified playlist.
	 */
	suspend fun addSongsToPlaylist(playlistId: String, songs: List<MusicMetadata>) = executeApiCall("Failed to add songs to playlist $playlistId") {
		// need to manually chunk the playlist into 100 song chunks and synchronously call the addPlayablesToClientPlaylist(..)
		// method for each as a workaround to the bug where >100 songs has the potential of adding the songs out of order
		songs.chunked(100).forEach { chunk ->
			val playables = chunk.map { PlayableUri(it.mediaId!!) }.toTypedArray()
			webApi?.playlists?.addPlayablesToClientPlaylist(playlistId, *playables)
		}
	}

	/**
	 * Replaces the songs of the specified playlist with the provided list of songs.
	 */
	suspend fun replacePlaylistSongs(playlistId: String, songs: List<MusicMetadata>) = executeApiCall("Failed to replace playlist $playlistId songs") {
		// can only add a maximum of 100 tracks per replace playlist playables request
		if (songs.size <= 100) {
			webApi?.playlists?.replaceClientPlaylistPlayables(playlistId, *songs.map { PlayableUri(it.mediaId!!) }.toTypedArray())
		} else {
			webApi?.playlists?.replaceClientPlaylistPlayables(playlistId)
			addSongsToPlaylist(playlistId, songs)
		}
	}

	/**
	 * Returns the [SpotifyUri] associated to the provided playlist name or null if it is not found.
	 */
	suspend fun getPlaylistUri(playlistName: String): SpotifyUri? = executeApiCall("Failed to find playlist with name $playlistName") {
		var playlists = webApi?.playlists?.getClientPlaylists(50,0)
		while (playlists != null) {
			val matchingPlaylist = playlists.items.find { it.name == playlistName }
			if (matchingPlaylist != null) {
				return@executeApiCall matchingPlaylist.uri
			}
			playlists = playlists.getNext()
		}

		return@executeApiCall null
	}

	suspend fun getLikedSongs(spotifyAppController: SpotifyAppController): List<SpotifyMusicMetadata>? = executeApiCall("Failed to get data from Liked Songs library") {
		if (webApi == null) {
			pendingQueueMetadataCreate = {
				Log.d(SpotifyAppController.TAG, "Retrying Liked Songs queue metadata creation")
				spotifyAppController.createLikedSongsQueueMetadata()
			}

			return@executeApiCall emptyList()
		}

		val likedSongs = webApi?.library?.getSavedTracks(50, market=Market.FROM_TOKEN)?.getAllItemsNotNull()
		return@executeApiCall likedSongs?.filter { it.track.isPlayable }?.map {
			createSpotifyMusicMetadataFromTrack(it.track, spotifyAppController)
		}
	}

	suspend fun getArtistTopSongs(spotifyAppController: SpotifyAppController, artistUri: String): List<SpotifyMusicMetadata>? = executeApiCall("Failed to get top tracks from ArtistUri $artistUri") {
		if (webApi == null) {
			pendingQueueMetadataCreate = {
				Log.d(SpotifyAppController.TAG, "Retrying Artist Songs queue metadata creation")
				spotifyAppController.createArtistTopSongsQueueMetadata()
			}

			return@executeApiCall emptyList()
		}

		val topSongs = webApi?.artists?.getArtistTopTracks(artistUri, market=Market.FROM_TOKEN)
		return@executeApiCall topSongs?.filter { it.isPlayable }?.map {
			createSpotifyMusicMetadataFromTrack(it, spotifyAppController)
		}
	}

	suspend fun searchForQuery(spotifyAppController: SpotifyAppController, query: String): List<SpotifyMusicMetadata>? = executeApiCall("Failed to get search results for query $query") {
		if (webApi == null) {
			return@executeApiCall emptyList()
		}

		val searchResults = webApi?.search?.search(query, *SearchApi.SearchType.values(), limit=8, market=Market.FROM_TOKEN)

		// run through each of the search result categories and compile full list
		val searchResultMusicMetadata: ArrayList<SpotifyMusicMetadata> = ArrayList()

		val albumResults = searchResults?.albums
		if (albumResults != null && albumResults.isNotEmpty()) {
			searchResultMusicMetadata.addAll(albumResults.items.map { result ->
				val mediaId = result.uri.uri
				val coverArtUri = getCoverArtUri(result.images)
				val artists = result.artists.joinToString(", ") { it.name }
				val type = result.type.replaceFirstChar(Char::uppercase)

				SpotifyMusicMetadata(spotifyAppController, mediaId, mediaId.hashCode().toLong(), coverArtUri, artists, result.name, result.name, type, playable=true, browseable=false)
			})
		}

		val songResults = searchResults?.tracks
		if (songResults != null && songResults.isNotEmpty()) {
			searchResultMusicMetadata.addAll(songResults.items.map { result ->
				val mediaId = result.uri.uri
				val coverArtUri = getCoverArtUri(result.album.images)
				val artists = result.artists.joinToString(", ") { it.name }
				val albumMediaId = result.album.uri.uri
				val type = result.type.replaceFirstChar(Char::uppercase)

				SpotifyMusicMetadata(spotifyAppController, mediaId, mediaId.hashCode().toLong(), coverArtUri, artists, albumMediaId, result.name, type, playable=true, browseable=false)
			})
		}

		val artistResults = searchResults?.artists
		if (artistResults != null && artistResults.isNotEmpty()) {
			searchResultMusicMetadata.addAll(artistResults.items.map { result ->
				val mediaId = result.uri.uri
				val coverArtUri = getCoverArtUri(result.images)
				val type = result.type.replaceFirstChar(Char::uppercase)

				SpotifyMusicMetadata(spotifyAppController, mediaId, mediaId.hashCode().toLong(), coverArtUri, result.name, null, result.name, type, playable=true, browseable=false)
			})
		}

		val playlistResults = searchResults?.playlists
		if (playlistResults != null && playlistResults.isNotEmpty()) {
			searchResultMusicMetadata.addAll(playlistResults.items.map { result ->
				val mediaId = result.uri.uri
				val coverArtUri = getCoverArtUri(result.images)
				val type = result.type.replaceFirstChar(Char::uppercase)

				SpotifyMusicMetadata(spotifyAppController, mediaId, mediaId.hashCode().toLong(), coverArtUri, result.name, null, result.name, type, playable=true, browseable=false)
			})
		}

		val showResults = searchResults?.shows
		if (showResults != null && showResults.isNotEmpty()) {
			searchResultMusicMetadata.addAll(showResults.items.filterNotNull().map { result ->
				val mediaId = result.uri.uri
				val coverArtUri = getCoverArtUri(result.images)
				val subtitle = result.type.replaceFirstChar(Char::uppercase)

				SpotifyMusicMetadata(spotifyAppController, mediaId, mediaId.hashCode().toLong(), coverArtUri, result.publisher, null, result.name, subtitle, playable=true, browseable=false)
			})
		}

		val episodeResults = searchResults?.episodes
		if (episodeResults != null && episodeResults.isNotEmpty()) {
			searchResultMusicMetadata.addAll(episodeResults.items.filterNotNull().map { result ->
				val mediaId = result.uri.uri
				val coverArtUri = getCoverArtUri(result.images)
				val subtitle = result.type.replaceFirstChar(Char::uppercase)

				SpotifyMusicMetadata(spotifyAppController, mediaId, mediaId.hashCode().toLong(), coverArtUri, null, null, result.name, subtitle, playable=true, browseable=false)
			})
		}
		return@executeApiCall searchResultMusicMetadata
	}

	/**
	 * Sets the specified playlist's cover art image to the specified image. The supplied image data
	 * must be a JPG Base64 string format.
	 */
	suspend fun setPlaylistImage(playlistId: String, coverArtImageData: String) = executeApiCall("Failed to upload the cover art image to playlist $playlistId") {
		webApi?.playlists?.uploadClientPlaylistCover(playlistId, imageData = coverArtImageData)
	}

	/**
	 * Initializes the [SpotifyClientApi] instance and updates the [AuthState] with the token used.
	 */
	fun initializeWebApi(isProbing: Boolean = false) {
		if(webApi != null) {
			return
		}
		webApi = createWebApiClient()
		if (webApi != null) {
			authStateManager.updateTokenResponseWithToken(webApi!!.token, clientId)

			pendingQueueMetadataCreate?.invoke()
			clearPendingQueueMetadataCreate()

			if (!isProbing) {
				updateSpotifyAppInfoAsSearchable()
			}
		}
	}

	/**
	 * Returns if the current [AuthState] is authorized.
	 */
	fun isAuthorized(): Boolean {
		return authStateManager.isAuthorized()
	}

	fun clearPendingQueueMetadataCreate() {
		pendingQueueMetadataCreate = null
	}

	/**
	 * Executes the [SpotifyClientApi] call with exception handling for failures. In the case of a
	 * failure the supplied error message will be the prefix of the appropriate log message.
	 *
	 * Note: This should wrap **EVERY** [SpotifyClientApi] call.
	 */
	private suspend fun <R> executeApiCall(errorMessage: String, block: suspend () -> R): R? {
		try {
			return block()
		} catch (e: SpotifyException.AuthenticationException) {
			Log.e(TAG, errorMessage + " due to authentication error with the message: ${e.message}")
			deauthorizeWebApiSession(e)
		} catch (e: Exception) {
			if (e.message?.contains("Status Code 403") == true) {
				Log.e(TAG, errorMessage + " due to unauthorized error with the message: ${e.message}")
				deauthorizeWebApiSession(e)
			} else {
				Log.e(TAG, errorMessage + " due to exception with the message: ${e.message}")
			}
		}
		return null
	}

	/**
	 * De-authorizes the current running Web Api session, performing the following actions:
	 *  - Adding authorization exception to the [SpotifyAuthStateManager]
	 *  - Creating an unauthorized notification
	 *  - setting the [SpotifyClientApi] instance to be null
	 */
	private fun deauthorizeWebApiSession(e: Exception) {
		authStateManager.addAccessTokenAuthorizationException(e)
		createNotAuthorizedNotification()
		webApi = null
	}

	/**
	 * Updates the Spotify [MusicAppInfo] searchable flag to true if it is set to false.
	 */
	private fun updateSpotifyAppInfoAsSearchable() {
		val musicAppDiscovery = MusicAppDiscovery(context, Handler(Looper.getMainLooper()))
		musicAppDiscovery.loadInstalledMusicApps()
		val spotifyAppInfo = musicAppDiscovery.allApps.firstOrNull { it.packageName == "com.spotify.music" }

		// if app discovery says we aren't able to search, discover again
		if (spotifyAppInfo?.searchable == false) {
			musicAppDiscovery.probeApp(spotifyAppInfo)
		}
	}

	/**
	 * Creates the Web API client using the PKCE authentication flow. If the [SpotifyClientApi] creation
	 * and validation process encounters a authentication failure, a null instance is returned and the
	 * [AuthState] updated with the exception.
	 */
	private fun createWebApiClient(): SpotifyClientApi? {
		val accessToken = authStateManager.getAccessToken()
		if (accessToken != null) {
			return try {
				val expirationIn = authStateManager.getAccessTokenExpirationIn()
				val refreshToken = authStateManager.getRefreshToken()
				val scopes = authStateManager.getScopeString()
				val token = Token(accessToken, "Bearer", expirationIn.toInt(), refreshToken, scopes)
				val apiBuilder = SpotifyClientApiBuilderHelper.createApiBuilderWithAccessToken(clientId, token)
				apiBuilder.options.onTokenRefresh = {
					Log.d(TAG, "Updating AuthState with refreshed token")
					authStateManager.updateTokenResponseWithToken(it.token, clientId)
				}
				runBlocking {
					apiBuilder.build()
				}
			} catch (e: SpotifyException.AuthenticationException) {
				Log.e(SpotifyAppController.TAG, "Failed to create the web API with an access token. Access token is invalid")
				authStateManager.addAccessTokenAuthorizationException(e)
				createNotAuthorizedNotification()
				null
			} catch (e: Exception) {
				Log.e(SpotifyAppController.TAG, "Failed to create the web API due to the error: ${e.message}")
				null
			}
		} else {
			val authorizationCode = authStateManager.getAuthorizationCode()
			if (authorizationCode == null) {
				Log.e(SpotifyAppController.TAG, "Failed to create the Web API with an authorization code. Authorization code is invalid or not found")
				createNotAuthorizedNotification()
				return null
			}

			return try {
				val apiBuilder = SpotifyClientApiBuilderHelper.createApiBuilderWithAuthorizationCode(clientId, authorizationCode)
				apiBuilder.options.onTokenRefresh = {
					Log.d(TAG, "Updating AuthState with refreshed token")
					authStateManager.updateTokenResponseWithToken(it.token, clientId)
				}
				runBlocking {
					apiBuilder.build()
				}
			} catch (e: SpotifyException.AuthenticationException) {
				authStateManager.addAuthorizationCodeAuthorizationException(e)
				Log.e(SpotifyAppController.TAG, "Failed to create the web API with an authorization code. Authorization failed with the error: ${e.message}")
				createNotAuthorizedNotification()
				null
			} catch (e: Exception) {
				Log.e(SpotifyAppController.TAG, "Failed to create the web API due to the error: ${e.message}")
				null
			}
		}
	}

	/**
	 * Creates and displays a notification stating that the web API needs to be authorized only if the
	 * user has previously successfully authorized.
	 */
	private fun createNotAuthorizedNotification() {
		if (appSettings[AppSettings.KEYS.SPOTIFY_SHOW_UNAUTHENTICATED_NOTIFICATION] == "false") {
			return
		}
		val notifyIntent = Intent(context, SpotifyAuthorizationActivity::class.java)
		notifyIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
		val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
				.setContentTitle(context.getString(R.string.notification_title))
				.setContentText(context.getString(R.string.txt_spotify_auth_notification))
				.setSmallIcon(R.drawable.ic_notify)
				.setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_REQ_ID, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
				.build()
		val notificationManager = context.getSystemService(NotificationManager::class.java)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
					context.getString(R.string.notification_channel_spotify),
					NotificationManager.IMPORTANCE_HIGH)
			notificationManager.createNotificationChannel(channel)
		}
		notificationManager.notify(NOTIFICATION_REQ_ID, notification)
	}

	/**
	 * Constructs the cover art uri from the list of [SpotifyImage]s that works with the Spotify App
	 * Remote ImagesApi. If there are no images that are found that match the dimensions then null is
	 * returned.
	 */
	private fun getCoverArtUri(images: List<SpotifyImage>, heightToMatch: Int = 300): String? {
		val sortedImages = images.sortedBy { it.height }
		var coverArtIndex = sortedImages.indexOfFirst { it.height ?: 0 >= heightToMatch }
		if (coverArtIndex == -1 && images.size == 1) {
			coverArtIndex = 0
		}
		return if (coverArtIndex != -1) {
			val imageUrl = sortedImages[coverArtIndex].url
			val coverArtCode = imageUrl.substring(imageUrl.lastIndexOf("/")+1)
			"spotify:image:$coverArtCode"
		} else {
			null
		}
	}

	/**
	 * Creates a [SpotifyMusicMetadata] from a provided [Track].
	 */
	private fun createSpotifyMusicMetadataFromTrack(track: Track, spotifyAppController: SpotifyAppController): SpotifyMusicMetadata {
		val mediaId = track.uri.uri
		val artists = track.artists.joinToString(", ") { it.name }
		val album = track.album
		val coverArtUri = getCoverArtUri(album.images)
		return SpotifyMusicMetadata(spotifyAppController, mediaId, mediaId.hashCode().toLong(), coverArtUri, artists, album.name, track.name)
	}

	private fun createSpotifyMusicMetadataFromPodcastEpisodeTrack(episode: PodcastEpisodeTrack, spotifyAppController: SpotifyAppController): SpotifyMusicMetadata {
		val mediaId = episode.uri.uri
		val artists = episode.artists.joinToString(", ") { it.name }
		val album = episode.album
		val coverArtUri = getCoverArtUri(album.images)
		return SpotifyMusicMetadata(spotifyAppController, mediaId, mediaId.hashCode().toLong(), coverArtUri, artists, album.name, episode.name)
	}

	/**
	 * Disconnect process for shutting down the [SpotifyClientApi].
	 */
	fun disconnect() {
		instanceCount--
		Log.d(TAG, "Disconnecting SpotifyWebApi")
		if (instanceCount == 0) {
			Log.d(TAG, "All instances of SpotifyWebApi disconnected. Shutting down Web API")
			webApi?.shutdown()
			isUsingSpotify = false
			clearPendingQueueMetadataCreate()
		}
	}
}