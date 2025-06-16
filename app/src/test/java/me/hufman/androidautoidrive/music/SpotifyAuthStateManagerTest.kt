package me.hufman.androidautoidrive.music

import com.adamratzman.spotify.SpotifyException
import com.adamratzman.spotify.SpotifyScope
import com.adamratzman.spotify.models.Token
import org.mockito.kotlin.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MockAppSettings
import me.hufman.androidautoidrive.MockSelfReturningAnswer
import me.hufman.androidautoidrive.music.spotify.SpotifyAuthStateManager
import net.openid.appauth.*
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.powermock.reflect.Whitebox

@RunWith(PowerMockRunner::class)
@PrepareForTest(AuthState::class, System::class, SpotifyAuthStateManager::class)
class SpotifyAuthStateManagerTest {

	lateinit var spotifyAuthStateManager: SpotifyAuthStateManager
	lateinit var appSettings: MockAppSettings

	@Before
	fun setup() {
		appSettings = MockAppSettings()
		spotifyAuthStateManager = Whitebox.invokeConstructor(SpotifyAuthStateManager::class.java, appSettings)
	}

	@Test
	fun testNewInstance_EmptyAuthStateString() {
		assertTrue(isEmptyAuthState(spotifyAuthStateManager.currentState))
	}

	@Test
	fun testNewInstance_ExistingAuthStateString() {
		val state = "authStateJson"
		appSettings[AppSettings.KEYS.SPOTIFY_AUTH_STATE_JSON] = state

		val authState = AuthState()
		Mockito.mockStatic(AuthState::class.java).use {
			it.`when`<AuthState> {AuthState.jsonDeserialize(state)}.thenReturn(authState)

			spotifyAuthStateManager = Whitebox.invokeConstructor(SpotifyAuthStateManager::class.java, appSettings)
		}

		assertEquals(authState, spotifyAuthStateManager.currentState)
	}

	@Test
	fun testNewInstance_DeserializeJsonException() {
		val state = "authStateJson"
		appSettings[AppSettings.KEYS.SPOTIFY_AUTH_STATE_JSON] = state

		Mockito.mockStatic(AuthState::class.java).use {
			it.`when`<AuthState> {AuthState.jsonDeserialize(state)}.thenThrow(JSONException("message"))

			spotifyAuthStateManager = Whitebox.invokeConstructor(SpotifyAuthStateManager::class.java, appSettings)
		}

		assertTrue(isEmptyAuthState(spotifyAuthStateManager.currentState))
	}

	@Test
	fun testGetAuthorizationCode_NullLastAuthorizationResponse() {
		val currentState: AuthState = mock()
		whenever(currentState.lastAuthorizationResponse).thenReturn(null)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		assertNull(spotifyAuthStateManager.getAuthorizationCode())
	}

	@Test
	fun testGetAuthorizationCode_ExistingLastAuthorizationResponse() {
		val authorizationCode = "authorizationCode"
		val authorizationResponse: AuthorizationResponse = mock()
		Whitebox.setInternalState(authorizationResponse, "authorizationCode", authorizationCode)

		val currentState: AuthState = mock()
		whenever(currentState.lastAuthorizationResponse).thenReturn(authorizationResponse)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		assertEquals(authorizationCode, spotifyAuthStateManager.getAuthorizationCode())
	}

	@Test
	fun testGetAccessToken() {
		val accessToken = "accessToken"
		val currentState: AuthState = mock()
		whenever(currentState.accessToken).thenReturn(accessToken)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		assertEquals(accessToken, spotifyAuthStateManager.getAccessToken())
	}

	@Test
	fun testGetAccessTokenExpirationIn_NullAccessTokenExpirationTime() {
		val currentState: AuthState = mock()
		whenever(currentState.accessTokenExpirationTime).thenReturn(null)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		assertEquals(0, spotifyAuthStateManager.getAccessTokenExpirationIn())
	}

	@Test
	fun testGetAccessTokenExpirationIn_ExistingAccessTokenExpirationTime() {
		val currentState: AuthState = mock()
		whenever(currentState.accessTokenExpirationTime).thenReturn(System.currentTimeMillis() + 2999)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		assertEquals(2, spotifyAuthStateManager.getAccessTokenExpirationIn())
	}

	@Test
	fun testGetRefreshToken() {
		val refreshToken = "refreshToken"
		val currentState: AuthState = mock()
		whenever(currentState.refreshToken).thenReturn(refreshToken)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		assertEquals(refreshToken, spotifyAuthStateManager.getRefreshToken())
	}

	@Test
	fun testGetScopeString() {
		val scopeString = "scopeString"
		val currentState: AuthState = mock()
		whenever(currentState.scope).thenReturn(scopeString)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		assertEquals(scopeString, spotifyAuthStateManager.getScopeString())
	}

	@Test
	fun testGetAuthorizationServiceConfiguration() {
		val authorizationServiceConfiguration: AuthorizationServiceConfiguration = mock()
		val currentState: AuthState = mock()
		whenever(currentState.authorizationServiceConfiguration).thenReturn(authorizationServiceConfiguration)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		assertEquals(authorizationServiceConfiguration, spotifyAuthStateManager.getAuthorizationServiceConfiguration())
	}

	@Test
	fun testIsAuthorized() {
		val currentState: AuthState = mock()
		whenever(currentState.isAuthorized).thenReturn(true)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		assertTrue(spotifyAuthStateManager.isAuthorized())
	}

	@Test
	fun testUpdateAuthorizationResponse() {
		val authorizationResponse: AuthorizationResponse = mock()
		val authStateSerializedString = "auth state serialized"
		val currentState: AuthState = mock()
		doNothing().whenever(currentState).update(authorizationResponse, null)
		whenever(currentState.jsonSerializeString()).thenReturn(authStateSerializedString)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		spotifyAuthStateManager.updateAuthorizationResponse(authorizationResponse, null)

		verify(currentState).update(authorizationResponse, null)
		assertEquals(authStateSerializedString, appSettings[AppSettings.KEYS.SPOTIFY_AUTH_STATE_JSON])
	}

	@Test
	fun testUpdateTokenResponseWithToken() {
		val refreshToken = "refreshToken"
		val accessToken = "accessToken"
		val expiresAt = 5L
		val spotifyScopes = listOf(SpotifyScope.USER_LIBRARY_READ)
		val token: Token = mock()
		whenever(token.refreshToken).thenReturn(refreshToken)
		whenever(token.accessToken).thenReturn(accessToken)
		whenever(token.expiresAt).thenReturn(expiresAt)
		whenever(token.scopes).thenReturn(spotifyScopes)

		val authorizationServiceConfiguration: AuthorizationServiceConfiguration = mock()
		val currentState: AuthState = mock()
		whenever(currentState.authorizationServiceConfiguration).thenReturn(authorizationServiceConfiguration)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		val clientId = "clientId"
		val tokenRequest: TokenRequest = mock()
		val tokenRequestBuilder: TokenRequest.Builder = mock(defaultAnswer = MockSelfReturningAnswer())
		whenever(tokenRequestBuilder.build()).thenReturn(tokenRequest)
		PowerMockito.whenNew(TokenRequest.Builder::class.java).withArguments(authorizationServiceConfiguration, clientId).thenReturn(tokenRequestBuilder)

		val tokenResponse: TokenResponse = mock()
		val tokenResponseBuilder: TokenResponse.Builder = mock(defaultAnswer = MockSelfReturningAnswer())
		whenever(tokenResponseBuilder.build()).thenReturn(tokenResponse)
		PowerMockito.whenNew(TokenResponse.Builder::class.java).withArguments(tokenRequest).thenReturn(tokenResponseBuilder)

		val authStateSerializedString = "auth state serialized"
		doNothing().whenever(currentState).update(tokenResponse, null)
		whenever(currentState.jsonSerializeString()).thenReturn(authStateSerializedString)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		spotifyAuthStateManager.updateTokenResponseWithToken(token, clientId)

		verify(currentState).update(tokenResponse, null)
		assertEquals(authStateSerializedString, appSettings[AppSettings.KEYS.SPOTIFY_AUTH_STATE_JSON])
	}

	@Test
	fun testAddAccessTokenAuthorizationException() {
		val exception = SpotifyException.AuthenticationException("message")
		val authorizationException: AuthorizationException = mock()
		PowerMockito.whenNew(AuthorizationException::class.java).withArguments(AuthorizationException.TYPE_OAUTH_TOKEN_ERROR, -1, "Access Token Authentication Error", "Authentication failed with the message: ${exception.message}", null, exception).thenReturn(authorizationException)

		val tokenResponse: TokenResponse? = null
		val authStateSerializedString = "auth state serialized"
		val currentState: AuthState = mock()
		doNothing().whenever(currentState).update(tokenResponse, authorizationException)
		whenever(currentState.jsonSerializeString()).thenReturn(authStateSerializedString)
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		spotifyAuthStateManager.addAccessTokenAuthorizationException(exception)

		verify(currentState).update(tokenResponse, authorizationException)
		assertEquals(authStateSerializedString, appSettings[AppSettings.KEYS.SPOTIFY_AUTH_STATE_JSON])
	}

	@Test
	fun testAddAuthorizationCodeAuthorizationException() {
		val exception = SpotifyException.AuthenticationException("message")
		val authorizationException: AuthorizationException = mock()
		PowerMockito.whenNew(AuthorizationException::class.java).withArguments(AuthorizationException.TYPE_OAUTH_AUTHORIZATION_ERROR, -1, "Authorization Code Authentication Error", "Authentication failed with the message: ${exception.message}", null, exception).thenReturn(authorizationException)

		val authorizationResponse: AuthorizationResponse? = null
		val authStateSerializedString = "auth state serialized"
		val currentState: AuthState = mock()
		doNothing().whenever(currentState).update(authorizationResponse, authorizationException)
		whenever(currentState.jsonSerializeString()).thenReturn(authStateSerializedString)

		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		spotifyAuthStateManager.addAuthorizationCodeAuthorizationException(exception)

		verify(currentState).update(authorizationResponse, authorizationException)
		assertEquals(authStateSerializedString, appSettings[AppSettings.KEYS.SPOTIFY_AUTH_STATE_JSON])
	}

	@Test
	fun testReplaceAuthState() {
		val currentState: AuthState = mock()
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		val authStateSerializedString = "auth state serialized"
		val authState: AuthState = mock()
		whenever(authState.jsonSerializeString()).thenReturn(authStateSerializedString)

		spotifyAuthStateManager.replaceAuthState(authState)

		assertEquals(authState, spotifyAuthStateManager.currentState)
		assertEquals(authStateSerializedString, appSettings[AppSettings.KEYS.SPOTIFY_AUTH_STATE_JSON])
	}

	@Test
	fun testClearAuthState() {
		val currentState: AuthState = mock()
		Whitebox.setInternalState(spotifyAuthStateManager, "currentState", currentState)

		val authStateSerializedString = "auth state serialized"
		val authState: AuthState = mock()
		whenever(authState.jsonSerializeString()).thenReturn(authStateSerializedString)

		spotifyAuthStateManager.replaceAuthState(authState)

		assertEquals(authState, spotifyAuthStateManager.currentState)
		assertEquals(authStateSerializedString, appSettings[AppSettings.KEYS.SPOTIFY_AUTH_STATE_JSON])

		PowerMockito.whenNew(AuthState::class.java).withAnyArguments().thenReturn(authState)
		spotifyAuthStateManager.clear()
		assertTrue(isEmptyAuthState(spotifyAuthStateManager.currentState))
	}

	private fun isEmptyAuthState(authState: AuthState): Boolean {
		return !authState.isAuthorized
				&& authState.accessToken == null
				&& authState.refreshToken == null
				&& authState.lastAuthorizationResponse == null
				&& authState.lastTokenResponse == null
	}
}