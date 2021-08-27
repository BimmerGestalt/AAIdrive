package me.hufman.androidautoidrive

import androidx.lifecycle.ViewModel
import me.hufman.androidautoidrive.phoneui.viewmodels.mockedViewModels
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement

class MockViewModels: TestWatcher() {
	init {
		// clear out the global mockedViewModels when a new test suite is instantiated
		mockedViewModels.clear()
	}

	// the viewModels that we are told about
	val viewModels = HashMap<Class<*>, ViewModel>()

	// a helper setter to add new mocked viewmodels
	operator fun set(key: Class<*>, model: ViewModel) {
		viewModels[key] = model
		// we need to set the global mockedViewModels asap
		// so that they are ready when activityScenarioRule is created
		mockedViewModels[key] = model
	}

	override fun starting(description: Description) {
		viewModels.forEach { (key, model) ->
			mockedViewModels[key] = model
		}
		super.starting(description)
	}

	override fun finished(description: Description) {
		viewModels.forEach { (key, _) ->
			mockedViewModels.remove(key)
		}
		super.finished(description)
	}
}