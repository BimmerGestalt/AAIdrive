package me.hufman.androidautoidrive

import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import org.hamcrest.Matcher


object EspressoHelpers {
	fun clickChildViewWithId(id: Int): ViewAction {
		return object : ViewAction {
			override fun getConstraints(): Matcher<View>? {
				return null
			}

			override fun getDescription(): String {
				return "Click on a child view with specified id."
			}

			override fun perform(uiController: UiController?, view: View) {
				val v: View = view.findViewById(id)
				v.performClick()
			}
		}
	}

	fun withCustomConstraints(action: ViewAction, constraints: Matcher<View>): ViewAction {
		return object: ViewAction {
			override fun getConstraints(): Matcher<View> {
				return constraints
			}
			override fun getDescription(): String {
				return action.description
			}
			override fun perform(uiController: UiController?, view: View?) {
				action.perform(uiController, view)
			}
		}
	}
}