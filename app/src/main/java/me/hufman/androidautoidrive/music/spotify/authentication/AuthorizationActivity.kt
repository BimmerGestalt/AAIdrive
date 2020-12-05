package me.hufman.androidautoidrive.music.spotify.authentication

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import androidx.browser.customtabs.CustomTabsIntent
import android.util.Log
import com.adamratzman.spotify.SpotifyScope
import com.adamratzman.spotify.getSpotifyPkceCodeChallenge
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.music.spotify.SpotifyWebApi
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import net.openid.appauth.*
import net.openid.appauth.browser.BrowserWhitelist
import net.openid.appauth.browser.VersionedBrowserMatcher
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Authorization activity class that is used to perform the authorization and update the [AuthState]
 * with the authorization code. The activity will be closed once the process has either been
 * cancelled, refused, or succeeds.
 */
class AuthorizationActivity: Activity() {
	companion object {
		const val TAG = "AuthorizationActivity"

		const val REQUEST_CODE_SPOTIFY_LOGIN = 2356

		const val EXTRA_AUTHORIZATION_RESULT = "authorizationResult"
		const val AUTHORIZATION_CANCELED = 1
		const val AUTHORIZATION_SUCCESS = 0
		const val AUTHORIZATION_FAILED = -1
		const val AUTHORIZATION_REFUSED = -2

		val CODE_VERIFIER = generateCodeVerifierString()

		/**
		 * Generates a 45 character random alpha numeric string to be used for the code verifier in
		 * the PKCE authentication flow.
		 */
		private fun generateCodeVerifierString(): String {
			val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
			return (1..45).map { charPool[Random.nextInt(0, charPool.size)] }.joinToString("")
		}
	}

	private val authRequest = AtomicReference<AuthorizationRequest>()
	private val authIntent = AtomicReference<CustomTabsIntent>()
	private lateinit var authService: AuthorizationService
	private var authIntentLatch = CountDownLatch(1)
	private lateinit var authStateManager: SpotifyAuthStateManager

	/**
	 * Performs the authorization request
	 */
	private fun doAuth() {
		Log.d(TAG, "Authorization started")
		try {
			authIntentLatch.await()
		} catch (ex: InterruptedException) {
			Log.w(TAG, "Interrupted while waiting for auth intent")
		}

		val intent = authService.getAuthorizationRequestIntent(
				authRequest.get(),
				authIntent.get())

		startActivityForResult(intent, REQUEST_CODE_SPOTIFY_LOGIN)
	}

	private fun warmUpBrowser() {
		authIntentLatch = CountDownLatch(1)
		Log.d(TAG, "Warming up browser instance for auth request")
		val intentBuilder = authService.createCustomTabsIntentBuilder(authRequest.get().toUri())
		authIntent.set(intentBuilder.build())
		authIntentLatch.countDown()
	}

	private fun createAuthRequest() {
		Log.d(TAG, "Creating auth request")
		val codeChallenge = getSpotifyPkceCodeChallenge(CODE_VERIFIER)
		val codeChallengeMethod = "S256"
		val oAuthScopes = listOf(SpotifyScope.USER_MODIFY_PLAYBACK_STATE.uri)
		val clientId = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
				.metaData.getString("com.spotify.music.API_KEY", "unavailable")
		val authRequestBuilder = AuthorizationRequest.Builder(
				authStateManager.currentState.authorizationServiceConfiguration!!,
				clientId,
				ResponseTypeValues.CODE,
				Uri.parse(SpotifyAppController.REDIRECT_URI))
				.setScopes(oAuthScopes)
				.setCodeVerifier(CODE_VERIFIER, codeChallenge, codeChallengeMethod)
		authRequest.set(authRequestBuilder.build())
	}

	private fun initializeAppAuth() {
		Log.d(TAG, "Initializing AppAuth")
		recreateAuthorizationService()

		if (authStateManager.currentState.authorizationServiceConfiguration == null) {
			val authEndpointUri = Uri.parse("https://accounts.spotify.com/authorize")
			val tokenEndpointUri = Uri.parse("https://accounts.spotify.com/api/token")
			val config = AuthorizationServiceConfiguration(authEndpointUri, tokenEndpointUri)
			authStateManager.replaceState(AuthState(config))
		}

		runBlocking {
			createAuthRequest()
			warmUpBrowser()
		}
	}

	private fun recreateAuthorizationService() {
		if (this::authService.isInitialized) {
			Log.d(TAG, "Discarding existing AuthService instance")
			authService.dispose()
		}
		authService = createAuthorizationService()
		authRequest.set(null)
		authIntent.set(null)
	}

	private fun createAuthorizationService(): AuthorizationService {
		Log.d(TAG, "Creating authorization service")
		val builder = AppAuthConfiguration.Builder()
		builder.setBrowserMatcher(BrowserWhitelist(
				VersionedBrowserMatcher.CHROME_CUSTOM_TAB,
				VersionedBrowserMatcher.SAMSUNG_CUSTOM_TAB))
		builder.setConnectionBuilder(DefaultConnectionBuilder.INSTANCE)
		return AuthorizationService(this, builder.build())
	}

	private fun processAuthorizationCode(requestCode: Int, resultCode: Int, data: Intent?) {
		if (requestCode == REQUEST_CODE_SPOTIFY_LOGIN) {
			if (resultCode == RESULT_CANCELED) {
				onAuthorizationCancelled()
				return
			}

			data?.let { intent ->
				val response = AuthorizationResponse.fromIntent(intent)
				val ex = AuthorizationException.fromIntent(intent)

				if (response != null || ex != null) {
					authStateManager.updateAfterAuthorization(response, ex)
				}

				when {
					response?.authorizationCode != null -> {
						authStateManager.updateAfterAuthorization(response, ex)
						onAuthorizationSucceed()
					}
					ex != null -> {
						onAuthorizationRefused("Authorization flow failed: " + ex.message)
					}
					else -> {
						onAuthorizationFailed()
					}
				}
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		authStateManager = SpotifyAuthStateManager.getInstance(this)
		initializeAppAuth()
		doAuth()
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		processAuthorizationCode(requestCode, resultCode, data)
	}

	override fun onDestroy() {
		super.onDestroy()
		if (this::authService.isInitialized) {
			authService.dispose()
		}
	}

	/**
	 * Authorized cancelled workflow. This is called if the authorization process activity was cancelled.
	 */
	private fun onAuthorizationCancelled() {
		Log.d(TAG, "Authorization cancelled")
		finishActivityWithResult(AUTHORIZATION_CANCELED)
	}

	/**
	 * Authorized failed workflow. This is called if the authorization process failed.
	 */
	private fun onAuthorizationFailed() {
		Log.d(TAG, "Authorization failed. No authorization state retained - reauthorization required")
		finishActivityWithResult(AUTHORIZATION_FAILED)
	}

	/**
	 * Authorized refused workflow. This is called if the authorization request was refused by the Spotify
	 * authentication server.
	 */
	private fun onAuthorizationRefused(error: String?) {
		Log.d(TAG, "Authorization refused with the error: $error")
		finishActivityWithResult(AUTHORIZATION_REFUSED)
	}

	/**
	 * Authorization success workflow. The [SpotifyWebApi] web API client instance will be initialized
	 * with the authorized code obtained.
	 */
	private fun onAuthorizationSucceed() {
		Log.d(TAG, "Authorization process completed successfully. AuthState updated")
		clearNotAuthorizedNotification()
		SpotifyWebApi.getInstance(this).initializeWebApi()

		finishActivityWithResult(AUTHORIZATION_SUCCESS)
	}

	/**
	 * Finishes the activity with the specified result constant.
	 */
	private fun finishActivityWithResult(authorizationResult: Int) {
		val returnIntent = Intent()
		returnIntent.putExtra(EXTRA_AUTHORIZATION_RESULT, authorizationResult)
		setResult(RESULT_OK, returnIntent)
		finish()
	}

	/**
	 * Clears the notification that is displayed when the user is not authenticated.
	 */
	private fun clearNotAuthorizedNotification() {
		val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.cancel(SpotifyAppController.NOTIFICATION_REQ_ID)
	}
}