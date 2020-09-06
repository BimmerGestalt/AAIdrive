package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.TextInputController
import me.hufman.androidautoidrive.carapp.TextInputState
import me.hufman.idriveconnectionkit.rhmi.RHMIActionListCallback
import me.hufman.idriveconnectionkit.rhmi.RHMIActionSpellerCallback
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationConcrete
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TestTextInput {
	val app = RHMIApplicationConcrete().also {
		it.loadFromXML(this.javaClass.classLoader.getResourceAsStream("ui_description_onlineservices_v1.xml").readBytes())
	}
	val inputState = app.states.values.first {
		it.componentsList.size == 1 &&
				it.componentsList[0] is RHMIComponent.Input &&
				it.componentsList[0].asInput()?.suggestModel != 0
	}
	val inputComponent = inputState.componentsList[0] as RHMIComponent.Input

	val inputController = mock<TextInputController>()
	lateinit var textInputState: TextInputState

	@Before
	fun setUp() {
		// the convertRow method calls UnicodeCleaner, so make sure that's set up right
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDC08", listOf("cat2"), "cat")
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDE3B", listOf("heart_eyes_cat"), "heart_eyes_cat")

		reset(inputController)
		whenever(inputController.getSuggestions(any(), any())).doAnswer { emptyList() }
		whenever(inputController.getSuggestions("", "")).doAnswer { listOf("Yes", "No") }
		whenever(inputController.getSuggestions(any(), eq("a"))).doAnswer { listOf("any", "all") }
		app.modelData.clear()
		textInputState = TextInputState(0, inputState, inputController)
	}

	@Test
	fun startEmpty() {
		whenever(inputController.getSuggestions(any(), any())).doAnswer { emptyList() }
		inputState.focusCallback?.onFocus(true)
		verify(inputController).getSuggestions("", "")
		assertNotNull(app.modelData[inputComponent.suggestModel])
		assertEquals(0, (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).totalRows)
	}

	@Test
	fun startSuggestions() {
		inputState.focusCallback?.onFocus(true)
		verify(inputController).getSuggestions("", "")
		assertNotNull(app.modelData[inputComponent.suggestModel])
		assertEquals(listOf("Yes", "No"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		// click a suggestion with empty input
		(inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback as RHMIActionListCallback).onAction(1)
		verify(inputController).onSelect("No")
	}

	@Test
	fun singleLetter() {
		inputState.focusCallback?.onFocus(true)
		verify(inputController).getSuggestions("", "")
		assertNotNull(app.modelData[inputComponent.suggestModel])
		assertEquals(listOf("Yes", "No"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		whenever(inputController.getSuggestions(any(), any())).doAnswer { emptyList() }
		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("a")
		verify(inputController).getSuggestions("", "a")
		assertEquals(listOf("a"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		// click a suggestion with single letter input
		(inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback as RHMIActionListCallback).onAction(0)
		verify(inputController).onSelect("a")
	}

	@Test
	fun singleLetterSuggestions() {
		inputState.focusCallback?.onFocus(true)
		verify(inputController).getSuggestions("", "")
		assertNotNull(app.modelData[inputComponent.suggestModel])
		assertEquals(listOf("Yes", "No"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("a")
		verify(inputController).getSuggestions("", "a")
		assertEquals(listOf("a", "any", "all"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		// click a suggestion with single letter input
		try {
			(inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback as RHMIActionListCallback).onAction(1)
			fail()
		} catch (e: RHMIActionAbort) {}
		verify(inputController).getSuggestions("any", "")   // completes the word and waits for input
		assertEquals(listOf("any"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })
		verifyNoMoreInteractions(inputController)
	}

	@Test
	fun singleLetterDelete() {
		inputState.focusCallback?.onFocus(true)

		verify(inputController, times(1)).getSuggestions("", "")
		assertNotNull(app.modelData[inputComponent.suggestModel])
		assertEquals(listOf("Yes", "No"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("a")
		verify(inputController).getSuggestions("", "a")
		assertEquals(listOf("a", "any", "all"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		// backspace
		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("del")
		verify(inputController, times(2)).getSuggestions("", "")
		assertEquals(listOf("Yes", "No"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })
	}

	@Test
	fun singleSuggestionDelete() {
		inputState.focusCallback?.onFocus(true)

		verify(inputController, times(1)).getSuggestions("", "")
		assertNotNull(app.modelData[inputComponent.suggestModel])
		assertEquals(listOf("Yes", "No"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("a")
		verify(inputController).getSuggestions("", "a")
		assertEquals(listOf("a", "any", "all"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		// click a suggestion with single letter input
		try {
			(inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback as RHMIActionListCallback).onAction(1)
			fail()
		} catch (e: RHMIActionAbort) {}
		verify(inputController).getSuggestions("any", "")   // completes the word and waits for input
		assertEquals(listOf("any"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		// backspace, deletes the completed word
		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("del")
		verify(inputController, times(2)).getSuggestions("", "")
		assertNotNull(app.modelData[inputComponent.suggestModel])
		assertEquals(listOf("Yes", "No"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })
	}

	@Test
	fun suggestionLetterDelete() {
		inputState.focusCallback?.onFocus(true)

		verify(inputController, times(1)).getSuggestions("", "")
		assertEquals("", app.modelData[inputComponent.resultModel])
		assertEquals(listOf("Yes", "No"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("a")
		verify(inputController).getSuggestions("", "a")
		assertEquals("a", app.modelData[inputComponent.resultModel])
		assertEquals(listOf("a", "any", "all"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		// click a suggestion with single letter input
		try {
			(inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback as RHMIActionListCallback).onAction(1)
			fail()
		} catch (e: RHMIActionAbort) {}
		verify(inputController).getSuggestions("any", "")   // completes the word and waits for input
		assertEquals("any", app.modelData[inputComponent.resultModel])
		assertEquals(listOf("any"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		// enters another letter
		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("b")
		verify(inputController).getSuggestions("any", "b")
		assertEquals("any b", app.modelData[inputComponent.resultModel])
		assertEquals(listOf("any b"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		// backspace, deletes the single letter
		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("del")
		assertEquals("any", app.modelData[inputComponent.resultModel])
		assertEquals(listOf("any"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		// backspace, deletes the completed word
		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("del")
		assertEquals("", app.modelData[inputComponent.resultModel])
		assertEquals(listOf("Yes", "No"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })
	}

	@Test
	fun testVoiceDelete() {
		inputState.focusCallback?.onFocus(true)

		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("what a test")
		assertEquals("what a test", app.modelData[inputComponent.resultModel])
		assertEquals(listOf("what a test"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })

		// backspace, deletes the voice entered phrase
		(inputComponent.getAction()?.asRAAction()?.rhmiActionCallback as? RHMIActionSpellerCallback)?.onInput("del")
		assertEquals("", app.modelData[inputComponent.resultModel])
		assertEquals(listOf("Yes", "No"), (app.modelData[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })
	}
}