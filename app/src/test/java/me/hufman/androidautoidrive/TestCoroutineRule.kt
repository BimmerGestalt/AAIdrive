package me.hufman.androidautoidrive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

// From https://proandroiddev.com/how-to-unit-test-code-with-coroutines-50c1640f6bef
@Suppress("EXPERIMENTAL_API_USAGE")
class TestCoroutineRule: TestRule {
	private val testCoroutineDispatcher = TestCoroutineDispatcher()
	private val testCoroutineScope = TestCoroutineScope(testCoroutineDispatcher)
	override fun apply(base: Statement, description: Description) = object: Statement() {
		override fun evaluate() {
			Dispatchers.setMain(testCoroutineDispatcher)

			base.evaluate()

			Dispatchers.resetMain()
			testCoroutineDispatcher.cancel()
		}
	}

	fun runBlockingTest(block: suspend TestCoroutineScope.() -> Unit) {
		testCoroutineScope.runBlockingTest { block() }
	}
}