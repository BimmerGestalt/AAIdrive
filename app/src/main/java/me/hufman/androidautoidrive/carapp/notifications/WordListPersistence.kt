package me.hufman.androidautoidrive.carapp.notifications

import android.content.Context
import com.miguelfonseca.completely.AutocompleteEngine
import me.hufman.completelywordlist.wordlist.WordListItem
import me.hufman.completelywordlist.wordlist.WordListLoader

class WordListPersistence(val context: Context) {
	val loader = WordListLoader()

	fun discover(): List<String> {
		return loader.discover()
	}

	fun download(language: String) {

	}

	fun import(wordList: AutocompleteEngine<WordListItem>) {

	}
}