package me.hufman.androidautoidrive

import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

// https://stackoverflow.com/a/8530200
class MockSelfReturningAnswer: Answer<Any> {
	override fun answer(invocation: InvocationOnMock?): Any? {
		val mock = invocation?.mock ?: return RETURNS_DEFAULTS.answer(invocation)
		if (invocation.method.returnType.isInstance(mock)) {
			return mock
		}
		return RETURNS_DEFAULTS.answer(invocation)
	}
}