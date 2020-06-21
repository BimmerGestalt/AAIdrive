import android.content.Context
import android.content.res.Configuration
import java.lang.AssertionError
import java.util.*
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

object L {
	// all of the strings used in the car app
	// these default string values are used in tests, Android resources are used for real
	var NOTIFICATIONS_TITLE = "Notifications"
	var NOTIFICATIONS_EMPTY_LIST = "No Notifications"
	var NOTIFICATION_CLEAR_ACTION = "Clear"
	var NOTIFICATION_OPTIONS = "Options"
	var NOTIFICATION_POPUPS = "Notification Popups"
	var NOTIFICATION_POPUPS_PASSENGER = "Popups with passenger"

	var MAP_ACTION_VIEWMAP = "View Full Map"
	var MAP_ACTION_SEARCH = "Search for Place"
	var MAP_ACTION_CLEARNAV = "Clear Navigation"

	var MUSIC_APPLIST_TITLE = "Apps"
	var MUSIC_APPLIST_EMPTY = "<No Apps>"
	var MUSIC_CUSTOMACTIONS_TITLE = "Actions"
	var MUSIC_DISCONNECTED = "<Not Connected>"
	var MUSIC_BROWSE_TITLE = "Browse"
	var MUSIC_BROWSE_EMPTY = "<Empty>"
	var MUSIC_BROWSE_LOADING = "<Loading>"
	var MUSIC_BROWSE_SEARCHING = "<Searching>"
	var MUSIC_BROWSE_ACTION_JUMPBACK = "Jump Back"
	var MUSIC_BROWSE_ACTION_FILTER = "Filter"
	var MUSIC_BROWSE_ACTION_SEARCH = "Search"
	var MUSIC_BROWSE_PLAY_FROM_SEARCH = "Play From Search"
	var MUSIC_QUEUE_TITLE = "Now Playing"
	var MUSIC_QUEUE_EMPTY = "<Empty Queue>"
	var MUSIC_SKIP_PREVIOUS = "Back"
	var MUSIC_SKIP_NEXT = "Next"

	var MUSIC_SPOTIFY_TURN_SHUFFLE_ON = "Turn Shuffle On"
	var MUSIC_SPOTIFY_REMOVE_FROM_COLLECTION = "Dislike"
	var MUSIC_SPOTIFY_START_RADIO = "Make Radio Station"
	var MUSIC_SPOTIFY_TURN_REPEAT_ALL_ON = "Turn Repeat All On"
	var MUSIC_SPOTIFY_TURN_SHUFFLE_OFF = "Turn Shuffle Off"
	var MUSIC_SPOTIFY_TURN_REPEAT_ONE_ON = "Turn Repeat One On"
	var MUSIC_SPOTIFY_TURN_REPEAT_ONE_OFF = "Turn Repeat Off"
	var MUSIC_SPOTIFY_ADD_TO_COLLECTION = "Like"
	var MUSIC_SPOTIFY_THUMB_UP = "Thumb Up"
	var MUSIC_SPOTIFY_THUMBS_UP_SELECTED = "Thumbed Up"
	var MUSIC_SPOTIFY_THUMB_DOWN = "Thumb Down"
	var MUSIC_SPOTIFY_THUMBS_DOWN_SELECTED = "Thumbed Down"    // not sure if this exists, but just to be complete

	fun loadResources(context: Context, locale: Locale? = null) {
		val thisContext = if (locale == null) { context } else {
			val origConf = context.resources.configuration
			val localeConf = Configuration(origConf)
			localeConf.setLocale(locale)
			context.createConfigurationContext(localeConf)
		}
		val stringProperties = L::class.memberProperties.filterIsInstance<KMutableProperty1<L, String>>()
		for (member in stringProperties) {
			if (member.name.matches(Regex("[A-Z_]+"))) {
				// this should crash the app if a new L string is created without a matching resource
				val id = thisContext.resources.getIdentifier(member.name, "string", context.packageName)
				if (id == 0) {
					throw AssertionError("Could not find Resource value for string ${member.name}")
				}
				val value =	thisContext.resources.getString(id)
				member.set(L, value)
			}
		}

	}
}