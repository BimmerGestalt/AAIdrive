package me.hufman.androidautoidrive.carapp.notifications

import com.miguelfonseca.completely.AutocompleteEngine
import me.hufman.androidautoidrive.carapp.TextInputController
import me.hufman.completelywordlist.wordlist.WordListItem

interface SuggestionStrategy {
	fun completeWord(draft: String, prefix: String): List<CharSequence>

	fun nextWord(draft: String): List<CharSequence>
}

class CompletelySuggestionStrategy(val autocomplete: AutocompleteEngine<WordListItem>): SuggestionStrategy {
	override fun completeWord(draft: String, prefix: String): List<CharSequence> {
		return autocomplete.search(prefix.toLowerCase(), 10).map {
			it.word
		}
	}

	override fun nextWord(draft: String): List<CharSequence> {
		return emptyList()
	}
}

abstract class SuggestionInputController(val strategy: SuggestionStrategy): TextInputController {
	override fun getSuggestions(draft: String, currentWord: String): List<CharSequence> {
		return if (currentWord.isNotEmpty()) {
			strategy.completeWord(draft, currentWord)
		} else {
			strategy.nextWord(draft)
		}
	}
}