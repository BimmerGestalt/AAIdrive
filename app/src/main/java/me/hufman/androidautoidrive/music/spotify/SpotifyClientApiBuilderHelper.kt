package me.hufman.androidautoidrive.music.spotify

import com.adamratzman.spotify.SpotifyClientApiBuilder
import com.adamratzman.spotify.SpotifyUserAuthorization
import com.adamratzman.spotify.models.Token
import com.adamratzman.spotify.spotifyClientPkceApi
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.phoneui.SpotifyAuthorizationActivity

/**
 * Helper class for creating [SpotifyClientApiBuilder]s through the PKCE authentication flow.
 */
class SpotifyClientApiBuilderHelper {
	companion object {
		/**
		 * Creates a [SpotifyClientApiBuilder] from an authorization code.
		 */
		fun createApiBuilderWithAuthorizationCode(clientId: String, authorizationCode: String): SpotifyClientApiBuilder {
			val spotifyUserAuthorization = SpotifyUserAuthorization(authorizationCode = authorizationCode, pkceCodeVerifier = SpotifyAuthorizationActivity.CODE_VERIFIER)
			return spotifyClientPkceApi(
					clientId,
					SpotifyAppController.REDIRECT_URI,
					spotifyUserAuthorization)
		}

		/**
		 * Creates a [SpotifyClientApiBuilder] from an access token.
		 */
		fun createApiBuilderWithAccessToken(clientId: String, token: Token): SpotifyClientApiBuilder {
			val spotifyUserAuthorization = SpotifyUserAuthorization(token = token, pkceCodeVerifier = SpotifyAuthorizationActivity.CODE_VERIFIER)
			return spotifyClientPkceApi(
					clientId,
					SpotifyAppController.REDIRECT_URI,
					spotifyUserAuthorization)
		}
	}
}