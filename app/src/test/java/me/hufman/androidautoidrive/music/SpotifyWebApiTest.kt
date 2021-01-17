package me.hufman.androidautoidrive.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.adamratzman.spotify.*
import com.adamratzman.spotify.endpoints.client.ClientLibraryApi
import com.adamratzman.spotify.models.*
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MockAppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.music.spotify.SpotifyWebApi
import me.hufman.androidautoidrive.music.spotify.SpotifyAuthStateManager
import me.hufman.androidautoidrive.music.spotify.SpotifyMusicMetadata
import me.hufman.androidautoidrive.phoneui.SpotifyAuthorizationActivity
import net.openid.appauth.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.internal.util.reflection.FieldSetter
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.powermock.reflect.Whitebox

@RunWith(PowerMockRunner::class)
@PrepareForTest(SpotifyWebApi::class, SpotifyAppController::class, PendingIntent::class, SpotifyAuthStateManager::class)
class SpotifyWebApiTest {

	lateinit var context: Context
	lateinit var spotifyAuthStateManager: SpotifyAuthStateManager
	lateinit var spotifyWebApi: SpotifyWebApi
	lateinit var appSettings: MutableAppSettings

	@Before
	fun setup()
	{
		context = mock()
		spotifyAuthStateManager = mock()
		appSettings = MockAppSettings()

		PowerMockito.mockStatic(SpotifyAuthStateManager::class.java)
		val companion = PowerMockito.mock(SpotifyAuthStateManager.Companion::class.java)
		Whitebox.setInternalState(SpotifyAuthStateManager::class.java, "Companion", companion)
		PowerMockito.`when`(SpotifyAuthStateManager.getInstance(appSettings)).thenReturn(spotifyAuthStateManager)

		spotifyWebApi = Whitebox.invokeConstructor(SpotifyWebApi::class.java, context, appSettings)
	}

	@Test
	fun testInitializeWebApi_ValidAccessToken() {
		PowerMockito.mockStatic(SpotifyAppController::class.java)
		val spotifyAppControllerCompanion = PowerMockito.mock(SpotifyAppController.Companion::class.java)
		Whitebox.setInternalState(SpotifyAppController::class.java, "Companion", spotifyAppControllerCompanion)

		val clientId = "clientId"
		PowerMockito.`when`(spotifyAppControllerCompanion.getClientId(context)).thenReturn(clientId)

		val spotifyOptions: SpotifyApiOptions = mock()
		val spotifyApiOptionsBuilder: SpotifyApiOptionsBuilder = mock()
		whenever(spotifyApiOptionsBuilder.build()).thenReturn(spotifyOptions)
		PowerMockito.whenNew(SpotifyApiOptionsBuilder::class.java).withAnyArguments().thenReturn(spotifyApiOptionsBuilder)

		val accessToken = "validAccessToken"
		val expirationIn: Long = 5
		val refreshToken = "refreshToken"
		val scopeString = "scopeString"
		whenever(spotifyAuthStateManager.getAccessToken()).thenReturn(accessToken)
		whenever(spotifyAuthStateManager.getAccessTokenExpirationIn()).thenReturn(expirationIn)
		whenever(spotifyAuthStateManager.getRefreshToken()).thenReturn(refreshToken)
		whenever(spotifyAuthStateManager.getScopeString()).thenReturn(scopeString)

		val expiresAt: Long = 2
		val token: Token = mock()
		whenever(token.refreshToken).thenReturn(refreshToken)
		whenever(token.accessToken).thenReturn(accessToken)
		whenever(token.expiresAt).thenReturn(expiresAt)
		whenever(token.scopes).thenReturn(listOf(SpotifyScope.USER_LIBRARY_READ))
		PowerMockito.whenNew(Token::class.java).withArguments(accessToken, "Bearer", expirationIn.toInt(), refreshToken, scopeString).thenReturn(token)

		val webApi: SpotifyClientAPI = mock()
		whenever(webApi.token).thenReturn(token)

		val apiBuilder: SpotifyClientApiBuilder = mock()
		whenever(apiBuilder.build()).thenReturn(webApi)

		PowerMockito.mockStatic(SpotifyWebApi.SpotifyClientApiBuilderHelper::class.java)
		val companion = PowerMockito.mock(SpotifyWebApi.SpotifyClientApiBuilderHelper.Companion::class.java)
		Whitebox.setInternalState(SpotifyWebApi.SpotifyClientApiBuilderHelper::class.java, "Companion", companion)
		PowerMockito.`when`(companion.createApiBuilderWithAccessToken(clientId, token, spotifyApiOptionsBuilder)).thenReturn(apiBuilder)

		val authorizationServiceConfig: AuthorizationServiceConfiguration = mock()
		whenever(spotifyAuthStateManager.getAuthorizationServiceConfiguration()).thenReturn(authorizationServiceConfig)

		val tokenRequest: TokenRequest = mock()
		val tokenRequestBuilder: TokenRequest.Builder = mock()
		whenever(tokenRequestBuilder.setRefreshToken(refreshToken)).thenReturn(tokenRequestBuilder)
		whenever(tokenRequestBuilder.setGrantType(GrantTypeValues.REFRESH_TOKEN)).thenReturn(tokenRequestBuilder)
		whenever(tokenRequestBuilder.build()).thenReturn(tokenRequest)
		PowerMockito.whenNew(TokenRequest.Builder::class.java).withArguments(authorizationServiceConfig, clientId).thenReturn(tokenRequestBuilder)

		val tokenResponse: TokenResponse = mock()
		val tokenResponseBuilder: TokenResponse.Builder = mock()
		whenever(tokenResponseBuilder.setAccessToken(accessToken)).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.setRefreshToken(refreshToken)).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.setAccessTokenExpirationTime(expiresAt)).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.setScopes(listOf(SpotifyScope.USER_LIBRARY_READ.uri))).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.build()).thenReturn(tokenResponse)
		PowerMockito.whenNew(TokenResponse.Builder::class.java).withArguments(tokenRequest).thenReturn(tokenResponseBuilder)

		doNothing().whenever(spotifyAuthStateManager).updateTokenResponse(tokenResponse, null)

		spotifyWebApi.initializeWebApi()

		verify(spotifyAuthStateManager).updateTokenResponse(tokenResponse, null)
	}

	@Test
	fun testInitializeWebApi_InvalidAccessToken()
	{
		PowerMockito.mockStatic(SpotifyAppController::class.java)
		val spotifyAppControllerCompanion = PowerMockito.mock(SpotifyAppController.Companion::class.java)
		Whitebox.setInternalState(SpotifyAppController::class.java, "Companion", spotifyAppControllerCompanion)

		val clientId = "clientId"
		PowerMockito.`when`(spotifyAppControllerCompanion.getClientId(context)).thenReturn(clientId)

		val spotifyOptions: SpotifyApiOptions = mock()
		val spotifyApiOptionsBuilder: SpotifyApiOptionsBuilder = mock()
		whenever(spotifyApiOptionsBuilder.build()).thenReturn(spotifyOptions)
		PowerMockito.whenNew(SpotifyApiOptionsBuilder::class.java).withAnyArguments().thenReturn(spotifyApiOptionsBuilder)

		val accessToken = "inValidAccessToken"
		val expirationIn: Long = 5
		val refreshToken = "refreshToken"
		val scopeString = "scopeString"
		whenever(spotifyAuthStateManager.getAccessToken()).thenReturn(accessToken)
		whenever(spotifyAuthStateManager.getAccessTokenExpirationIn()).thenReturn(expirationIn)
		whenever(spotifyAuthStateManager.getRefreshToken()).thenReturn(refreshToken)
		whenever(spotifyAuthStateManager.getScopeString()).thenReturn(scopeString)

		val expiresAt: Long = 2
		val token: Token = mock()
		whenever(token.refreshToken).thenReturn(refreshToken)
		whenever(token.accessToken).thenReturn(accessToken)
		whenever(token.expiresAt).thenReturn(expiresAt)
		whenever(token.scopes).thenReturn(listOf(SpotifyScope.USER_LIBRARY_READ))
		PowerMockito.whenNew(Token::class.java).withArguments(accessToken, "Bearer", expirationIn.toInt(), refreshToken, scopeString).thenReturn(token)

		val exception = SpotifyException.AuthenticationException("message")
		val apiBuilder: SpotifyClientApiBuilder = mock()
		whenever(apiBuilder.build()).doAnswer { throw exception }

		PowerMockito.mockStatic(SpotifyWebApi.SpotifyClientApiBuilderHelper::class.java)
		val companion = PowerMockito.mock(SpotifyWebApi.SpotifyClientApiBuilderHelper.Companion::class.java)
		Whitebox.setInternalState(SpotifyWebApi.SpotifyClientApiBuilderHelper::class.java, "Companion", companion)
		PowerMockito.`when`(companion.createApiBuilderWithAccessToken(clientId, token, spotifyApiOptionsBuilder)).thenReturn(apiBuilder)

		doNothing().whenever(spotifyAuthStateManager).addAccessTokenAuthorizationException(exception)

		val notificationManager: NotificationManager = mock()
		whenever(context.getSystemService(NotificationManager::class.java)).thenReturn(notificationManager)

		spotifyWebApi.initializeWebApi()

		verify(spotifyAuthStateManager).addAccessTokenAuthorizationException(exception)
		verify(notificationManager, never()).notify(any(), any())
	}

	@Test
	fun testInitializeWebApi_NullAuthorizationCode()
	{
		PowerMockito.mockStatic(SpotifyAppController::class.java)
		val spotifyAppControllerCompanion = PowerMockito.mock(SpotifyAppController.Companion::class.java)
		Whitebox.setInternalState(SpotifyAppController::class.java, "Companion", spotifyAppControllerCompanion)

		val clientId = "clientId"
		PowerMockito.`when`(spotifyAppControllerCompanion.getClientId(context)).thenReturn(clientId)

		val spotifyOptions: SpotifyApiOptions = mock()
		val spotifyApiOptionsBuilder: SpotifyApiOptionsBuilder = mock()
		whenever(spotifyApiOptionsBuilder.build()).thenReturn(spotifyOptions)
		PowerMockito.whenNew(SpotifyApiOptionsBuilder::class.java).withAnyArguments().thenReturn(spotifyApiOptionsBuilder)

		whenever(spotifyAuthStateManager.getAccessToken()).thenReturn(null)
		whenever(spotifyAuthStateManager.getAuthorizationCode()).thenReturn(null)

		val notificationManager: NotificationManager = mock()
		whenever(context.getSystemService(NotificationManager::class.java)).thenReturn(notificationManager)

		spotifyWebApi.initializeWebApi()

		verify(notificationManager, never()).notify(any(), any())
	}

	@Test
	fun testInitializeWebApi_ValidAuthorizationCode()
	{
		PowerMockito.mockStatic(SpotifyAppController::class.java)
		val spotifyAppControllerCompanion = PowerMockito.mock(SpotifyAppController.Companion::class.java)
		Whitebox.setInternalState(SpotifyAppController::class.java, "Companion", spotifyAppControllerCompanion)

		val clientId = "clientId"
		PowerMockito.`when`(spotifyAppControllerCompanion.getClientId(context)).thenReturn(clientId)

		val spotifyOptions: SpotifyApiOptions = mock()
		val spotifyApiOptionsBuilder: SpotifyApiOptionsBuilder = mock()
		whenever(spotifyApiOptionsBuilder.build()).thenReturn(spotifyOptions)
		PowerMockito.whenNew(SpotifyApiOptionsBuilder::class.java).withAnyArguments().thenReturn(spotifyApiOptionsBuilder)

		val authorizationCode = "authorizationCode"
		whenever(spotifyAuthStateManager.getAccessToken()).thenReturn(null)
		whenever(spotifyAuthStateManager.getAuthorizationCode()).thenReturn(authorizationCode)

		val refreshToken = "refreshToken"
		val accessToken = "accessToken"
		val expiresAt: Long = 2
		val token: Token = mock()
		whenever(token.refreshToken).thenReturn(refreshToken)
		whenever(token.accessToken).thenReturn(accessToken)
		whenever(token.expiresAt).thenReturn(expiresAt)
		whenever(token.scopes).thenReturn(listOf(SpotifyScope.USER_LIBRARY_READ))

		val webApi: SpotifyClientAPI = mock()
		PowerMockito.whenNew(SpotifyClientApi::class.java).withAnyArguments().thenReturn(webApi)
		whenever(webApi.token).thenReturn(token)

		val apiBuilder: SpotifyClientApiBuilder = mock()
		whenever(apiBuilder.build()).thenReturn(webApi)

		PowerMockito.mockStatic(SpotifyWebApi.SpotifyClientApiBuilderHelper::class.java)
		val companion = PowerMockito.mock(SpotifyWebApi.SpotifyClientApiBuilderHelper.Companion::class.java)
		Whitebox.setInternalState(SpotifyWebApi.SpotifyClientApiBuilderHelper::class.java, "Companion", companion)
		PowerMockito.`when`(companion.createApiBuilderWithAuthorizationCode(clientId, authorizationCode, spotifyApiOptionsBuilder)).thenReturn(apiBuilder)

		val authorizationServiceConfig: AuthorizationServiceConfiguration = mock()
		whenever(spotifyAuthStateManager.getAuthorizationServiceConfiguration()).thenReturn(authorizationServiceConfig)

		val tokenRequest: TokenRequest = mock()
		val tokenRequestBuilder: TokenRequest.Builder = mock()
		whenever(tokenRequestBuilder.setRefreshToken(refreshToken)).thenReturn(tokenRequestBuilder)
		whenever(tokenRequestBuilder.setGrantType(GrantTypeValues.REFRESH_TOKEN)).thenReturn(tokenRequestBuilder)
		whenever(tokenRequestBuilder.build()).thenReturn(tokenRequest)
		PowerMockito.whenNew(TokenRequest.Builder::class.java).withArguments(authorizationServiceConfig, clientId).thenReturn(tokenRequestBuilder)

		val tokenResponse: TokenResponse = mock()
		val tokenResponseBuilder: TokenResponse.Builder = mock()
		whenever(tokenResponseBuilder.setAccessToken(accessToken)).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.setRefreshToken(refreshToken)).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.setAccessTokenExpirationTime(expiresAt)).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.setScopes(listOf(SpotifyScope.USER_LIBRARY_READ.uri))).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.build()).thenReturn(tokenResponse)
		PowerMockito.whenNew(TokenResponse.Builder::class.java).withArguments(tokenRequest).thenReturn(tokenResponseBuilder)

		doNothing().whenever(spotifyAuthStateManager).updateTokenResponse(tokenResponse, null)

		spotifyWebApi.initializeWebApi()

		verify(spotifyAuthStateManager).updateTokenResponse(tokenResponse, null)
	}

	@Test
	fun testInitializeWebApi_InvalidAuthorizationCode()
	{
		PowerMockito.mockStatic(SpotifyAppController::class.java)
		val spotifyAppControllerCompanion = PowerMockito.mock(SpotifyAppController.Companion::class.java)
		Whitebox.setInternalState(SpotifyAppController::class.java, "Companion", spotifyAppControllerCompanion)

		val clientId = "clientId"
		PowerMockito.`when`(spotifyAppControllerCompanion.getClientId(context)).thenReturn(clientId)

		val spotifyOptions: SpotifyApiOptions = mock()
		val spotifyApiOptionsBuilder: SpotifyApiOptionsBuilder = mock()
		whenever(spotifyApiOptionsBuilder.build()).thenReturn(spotifyOptions)
		PowerMockito.whenNew(SpotifyApiOptionsBuilder::class.java).withAnyArguments().thenReturn(spotifyApiOptionsBuilder)

		val authorizationCode = "authorizationCode"
		whenever(spotifyAuthStateManager.getAccessToken()).thenReturn(null)
		whenever(spotifyAuthStateManager.getAuthorizationCode()).thenReturn(authorizationCode)

		val exception = SpotifyException.AuthenticationException("message")
		val apiBuilder: SpotifyClientApiBuilder = mock()
		whenever(apiBuilder.build()).doAnswer { throw exception }

		PowerMockito.mockStatic(SpotifyWebApi.SpotifyClientApiBuilderHelper::class.java)
		val companion = PowerMockito.mock(SpotifyWebApi.SpotifyClientApiBuilderHelper.Companion::class.java)
		Whitebox.setInternalState(SpotifyWebApi.SpotifyClientApiBuilderHelper::class.java, "Companion", companion)
		PowerMockito.`when`(companion.createApiBuilderWithAuthorizationCode(clientId, authorizationCode, spotifyApiOptionsBuilder)).thenReturn(apiBuilder)

		doNothing().whenever(spotifyAuthStateManager).addAuthorizationCodeAuthorizationException(exception)

		val notificationManager: NotificationManager = mock()
		whenever(context.getSystemService(NotificationManager::class.java)).thenReturn(notificationManager)

		spotifyWebApi.initializeWebApi()

		verify(spotifyAuthStateManager).addAuthorizationCodeAuthorizationException(exception)
		verify(notificationManager, never()).notify(any(), any())
	}

	@Test
	fun testInitializeWebApi_GetLikedSongsAttemptedTrue()
	{
		PowerMockito.mockStatic(SpotifyAppController::class.java)
		val spotifyAppControllerCompanion = PowerMockito.mock(SpotifyAppController.Companion::class.java)
		Whitebox.setInternalState(SpotifyAppController::class.java, "Companion", spotifyAppControllerCompanion)

		val clientId = "clientId"
		PowerMockito.`when`(spotifyAppControllerCompanion.getClientId(context)).thenReturn(clientId)

		val spotifyOptions: SpotifyApiOptions = mock()
		val spotifyApiOptionsBuilder: SpotifyApiOptionsBuilder = mock()
		whenever(spotifyApiOptionsBuilder.build()).thenReturn(spotifyOptions)
		PowerMockito.whenNew(SpotifyApiOptionsBuilder::class.java).withAnyArguments().thenReturn(spotifyApiOptionsBuilder)

		val authorizationCode = "authorizationCode"
		whenever(spotifyAuthStateManager.getAccessToken()).thenReturn(null)
		whenever(spotifyAuthStateManager.getAuthorizationCode()).thenReturn(authorizationCode)

		val refreshToken = "refreshToken"
		val accessToken = "accessToken"
		val expiresAt: Long = 2
		val token: Token = mock()
		whenever(token.refreshToken).thenReturn(refreshToken)
		whenever(token.accessToken).thenReturn(accessToken)
		whenever(token.expiresAt).thenReturn(expiresAt)
		whenever(token.scopes).thenReturn(listOf(SpotifyScope.USER_LIBRARY_READ))

		val webApi: SpotifyClientAPI = mock()
		PowerMockito.whenNew(SpotifyClientApi::class.java).withAnyArguments().thenReturn(webApi)
		whenever(webApi.token).thenReturn(token)

		val apiBuilder: SpotifyClientApiBuilder = mock()
		whenever(apiBuilder.build()).thenReturn(webApi)

		PowerMockito.mockStatic(SpotifyWebApi.SpotifyClientApiBuilderHelper::class.java)
		val companion = PowerMockito.mock(SpotifyWebApi.SpotifyClientApiBuilderHelper.Companion::class.java)
		Whitebox.setInternalState(SpotifyWebApi.SpotifyClientApiBuilderHelper::class.java, "Companion", companion)
		PowerMockito.`when`(companion.createApiBuilderWithAuthorizationCode(clientId, authorizationCode, spotifyApiOptionsBuilder)).thenReturn(apiBuilder)

		val authorizationServiceConfig: AuthorizationServiceConfiguration = mock()
		whenever(spotifyAuthStateManager.getAuthorizationServiceConfiguration()).thenReturn(authorizationServiceConfig)

		val tokenRequest: TokenRequest = mock()
		val tokenRequestBuilder: TokenRequest.Builder = mock()
		whenever(tokenRequestBuilder.setRefreshToken(refreshToken)).thenReturn(tokenRequestBuilder)
		whenever(tokenRequestBuilder.setGrantType(GrantTypeValues.REFRESH_TOKEN)).thenReturn(tokenRequestBuilder)
		whenever(tokenRequestBuilder.build()).thenReturn(tokenRequest)
		PowerMockito.whenNew(TokenRequest.Builder::class.java).withArguments(authorizationServiceConfig, clientId).thenReturn(tokenRequestBuilder)

		val tokenResponse: TokenResponse = mock()
		val tokenResponseBuilder: TokenResponse.Builder = mock()
		whenever(tokenResponseBuilder.setAccessToken(accessToken)).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.setRefreshToken(refreshToken)).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.setAccessTokenExpirationTime(expiresAt)).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.setScopes(listOf(SpotifyScope.USER_LIBRARY_READ.uri))).thenReturn(tokenResponseBuilder)
		whenever(tokenResponseBuilder.build()).thenReturn(tokenResponse)
		PowerMockito.whenNew(TokenResponse.Builder::class.java).withArguments(tokenRequest).thenReturn(tokenResponseBuilder)

		doNothing().whenever(spotifyAuthStateManager).updateTokenResponse(tokenResponse, null)

		FieldSetter.setField(spotifyWebApi, spotifyWebApi::class.java.getDeclaredField("getLikedSongsAttempted"), true)

		val spotifyAppController: SpotifyAppController = mock()
		doNothing().whenever(spotifyAppController).createLikedSongsQueueMetadata()
		FieldSetter.setField(spotifyWebApi, spotifyWebApi::class.java.getDeclaredField("spotifyAppControllerCaller"), spotifyAppController)

		spotifyWebApi.initializeWebApi()

		verify(spotifyAuthStateManager).updateTokenResponse(tokenResponse, null)
		verify(spotifyAppController).createLikedSongsQueueMetadata()
	}

	@Test
	fun testInitializeWebApi_NullAuthenticationCode_ShowUnauthenticatedNotificationSettingTrue() {
		PowerMockito.mockStatic(SpotifyAppController::class.java)
		val spotifyAppControllerCompanion = PowerMockito.mock(SpotifyAppController.Companion::class.java)
		Whitebox.setInternalState(SpotifyAppController::class.java, "Companion", spotifyAppControllerCompanion)

		val clientId = "clientId"
		PowerMockito.`when`(spotifyAppControllerCompanion.getClientId(context)).thenReturn(clientId)

		val spotifyOptions: SpotifyApiOptions = mock()
		val spotifyApiOptionsBuilder: SpotifyApiOptionsBuilder = mock()
		whenever(spotifyApiOptionsBuilder.build()).thenReturn(spotifyOptions)
		PowerMockito.whenNew(SpotifyApiOptionsBuilder::class.java).withAnyArguments().thenReturn(spotifyApiOptionsBuilder)

		whenever(spotifyAuthStateManager.getAccessToken()).thenReturn(null)
		whenever(spotifyAuthStateManager.getAuthorizationCode()).thenReturn(null)

		appSettings[AppSettings.KEYS.SPOTIFY_SHOW_UNAUTHENTICATED_NOTIFICATION] = "true"

		val notifyIntent: Intent = mock()
		PowerMockito.whenNew(Intent::class.java).withArguments(context, SpotifyAuthorizationActivity::class.java).thenReturn(notifyIntent)

		val getStringText = "string"
		whenever(context.getString(any())).thenReturn(getStringText)

		val contentIntent: PendingIntent = mock()
		PowerMockito.mockStatic(PendingIntent::class.java)
		PowerMockito.`when`(PendingIntent.getActivity(context, SpotifyWebApi.NOTIFICATION_REQ_ID, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT)).thenAnswer { contentIntent }

		val notification: Notification = mock()
		val notificationBuilder: NotificationCompat.Builder = mock()
		whenever(notificationBuilder.setContentTitle(getStringText)).thenReturn(notificationBuilder)
		whenever(notificationBuilder.setContentText(getStringText)).thenReturn(notificationBuilder)
		whenever(notificationBuilder.setSmallIcon(any())).thenReturn(notificationBuilder)
		whenever(notificationBuilder.setContentIntent(contentIntent)).thenReturn(notificationBuilder)
		whenever(notificationBuilder.build()).thenReturn(notification)
		PowerMockito.whenNew(NotificationCompat.Builder::class.java).withArguments(context, SpotifyWebApi.NOTIFICATION_CHANNEL_ID).thenReturn(notificationBuilder)

		val notificationChannel: NotificationChannel = mock()
		PowerMockito.whenNew(NotificationChannel::class.java).withArguments(SpotifyWebApi.NOTIFICATION_CHANNEL_ID, "Spotify Authorization", NotificationManager.IMPORTANCE_HIGH).thenReturn(notificationChannel)

		val notificationManager: NotificationManager = mock()
		doNothing().whenever(notificationManager).createNotificationChannel(notificationChannel)
		whenever(context.getString(R.string.notification_channel_spotify)).thenReturn("Spotify Authorization")
		whenever(context.getSystemService(NotificationManager::class.java)).thenReturn(notificationManager)

		doNothing().whenever(notificationManager).notify(SpotifyWebApi.NOTIFICATION_REQ_ID, notification)

		spotifyWebApi.initializeWebApi()

		verify(notificationManager).createNotificationChannel(notificationChannel)
		verify(notificationManager).notify(SpotifyWebApi.NOTIFICATION_REQ_ID, notification)
	}

	@Test
	fun testDisconnect()
	{
		val webApi: SpotifyClientApi = mock()
		doNothing().whenever(webApi).shutdown()
		FieldSetter.setField(spotifyWebApi, spotifyWebApi::class.java.getDeclaredField("webApi"), webApi)

		spotifyWebApi.isUsingSpotify = true

		spotifyWebApi.disconnect()

		verify(webApi).shutdown()
		assertEquals(false, spotifyWebApi.isUsingSpotify)
	}

	@Test
	fun testGetLikedSongs_NullWebApi() {
		FieldSetter.setField(spotifyWebApi, spotifyWebApi::class.java.getDeclaredField("webApi"), null)

		val spotifyAppController: SpotifyAppController = mock()
		val likedSongs = spotifyWebApi.getLikedSongs(spotifyAppController)

		assertEquals(emptyList<SpotifyMusicMetadata>(), likedSongs)
	}

	@Test
	fun testGetLikedSongs_Success() {
		val uriId1 = "uriId1"
		val trackName1 = "Track 1"
		val artistName1 = "Artist 1"
		val albumName1 = "Album 1"
		val coverArtCode1 = "/coverArtCode1"

		val uriId2 = "uriId2"
		val trackName2 = "Track 2"
		val artistName2 = "Artist 2"
		val albumName2 = "Album 2"
		val coverArtCode2 = "/coverArtCode2"

		val savedTracks = listOf(
				createSavedTrack(uriId1, trackName1, artistName1, albumName1, coverArtCode1),
				createSavedTrack(uriId2, trackName2, artistName2, albumName2, coverArtCode2)
		)
		val spotifyRestAction: SpotifyRestAction<List<SavedTrack>> = mock()
		whenever(spotifyRestAction.complete()).thenReturn(savedTracks)

		val spotifyRestActionPaging: SpotifyRestActionPaging<SavedTrack, PagingObject<SavedTrack>> = mock()
		whenever(spotifyRestActionPaging.getAllItemsNotNull()).thenReturn(spotifyRestAction)

		val clientLibraryApi: ClientLibraryApi = mock()
		whenever(clientLibraryApi.getSavedTracks(50)).doAnswer { spotifyRestActionPaging }

		val webApi: SpotifyClientApi = mock()
		whenever(webApi.library).thenReturn(clientLibraryApi)
		FieldSetter.setField(spotifyWebApi, spotifyWebApi::class.java.getDeclaredField("webApi"), webApi)

		val spotifyAppController: SpotifyAppController = mock()
		val likedSongs = spotifyWebApi.getLikedSongs(spotifyAppController)

		assertNotNull(likedSongs)
		assertEquals(2, likedSongs!!.size)

		val metadata1 = createSpotifyMusicMetadata(spotifyAppController, "spotify:track:${uriId1}", coverArtCode1, artistName1, albumName1, trackName1)
		assertEquals(metadata1, likedSongs[0])

		val metadata2 = createSpotifyMusicMetadata(spotifyAppController, "spotify:track:${uriId2}", coverArtCode2, artistName2, albumName2, trackName2)
		assertEquals(metadata2, likedSongs[1])
	}

	@Test
	fun testGetLikedSongs_AuthenticationException() {
		val exception = SpotifyException.AuthenticationException("message")
		val clientLibraryApi: ClientLibraryApi = mock()
		whenever(clientLibraryApi.getSavedTracks(50)).doAnswer { throw exception }

		val webApi: SpotifyClientApi = mock()
		whenever(webApi.library).thenReturn(clientLibraryApi)
		FieldSetter.setField(spotifyWebApi, spotifyWebApi::class.java.getDeclaredField("webApi"), webApi)

		doNothing().whenever(spotifyAuthStateManager).addAccessTokenAuthorizationException(exception)

		val notificationManager: NotificationManager = mock()
		whenever(context.getSystemService(NotificationManager::class.java)).thenReturn(notificationManager)

		val spotifyAppController: SpotifyAppController = mock()
		val likedSongs = spotifyWebApi.getLikedSongs(spotifyAppController)

		verify(notificationManager, never()).notify(any(), any())

		assertEquals(null, likedSongs)
	}

	@Test
	fun testGetLikedSongs_SpotifyException() {
		val exception = SpotifyException.BadRequestException("message")
		val clientLibraryApi: ClientLibraryApi = mock()
		whenever(clientLibraryApi.getSavedTracks(50)).doAnswer { throw exception }

		val webApi: SpotifyClientApi = mock()
		whenever(webApi.library).thenReturn(clientLibraryApi)
		FieldSetter.setField(spotifyWebApi, spotifyWebApi::class.java.getDeclaredField("webApi"), webApi)

		val spotifyAppController: SpotifyAppController = mock()
		val likedSongs = spotifyWebApi.getLikedSongs(spotifyAppController)

		assertEquals(null, likedSongs)
	}

	@Test
	fun testIsAuthorized() {
		whenever(spotifyAuthStateManager.isAuthorized()).thenReturn(true)

		assertEquals(true, spotifyWebApi.isAuthorized())
	}

	private fun createSpotifyMusicMetadata(spotifyAppController: SpotifyAppController, uriId: String, coverArtCode: String, artistName: String, albumName: String, trackName: String): SpotifyMusicMetadata {
		return SpotifyMusicMetadata(spotifyAppController, uriId, uriId.hashCode().toLong(), "spotify:image:${coverArtCode.drop(1)}", artistName, albumName, trackName)
	}

	private fun createSavedTrack(uriId: String, trackName: String, artistName: String, albumName: String, coverArtCode: String): SavedTrack {
		val images = listOf(SpotifyImage(300, coverArtCode, 300))
		val artists = listOf(SimpleArtist(emptyMap(), "href", "id", ArtistUri("artistUri"), artistName, "type"))
		val album = SimpleAlbum("album", emptyList(), emptyMap(), "href", "id", AlbumUri("albumUri"), artists, images, albumName, "type", null, "1950", "year")
		return 	SavedTrack("value", Track(emptyMap(), emptyMap(), emptyList(), "", "", PlayableUri(uriId), album, artists, true, 1, 5, false, null, trackName, 1, null, 1, ""))
	}
}