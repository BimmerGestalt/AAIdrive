package me.hufman.androidautoidrive.carapp.notifications

import android.content.Context
import android.util.Log
import com.miguelfonseca.completely.AutocompleteEngine
import me.hufman.completelywordlist.utils.IteratorCollection
import me.hufman.completelywordlist.wordlist.WordListItem
import me.hufman.completelywordlist.wordlist.WordListLoader
import me.hufman.completelywordlist.wordlist.WordListReader
import java.io.*
import java.util.zip.GZIPInputStream

class WordListPersistence(val context: Context) {
	companion object {
		val TAG = "WordList"

		fun languageFilename(language: String): String {
			return "${language}_wordlist.gz"
		}
	}

	val loader = WordListLoader()

	fun isLanguageDownloaded(language: String): Boolean {
		return context.fileList().contains(languageFilename(language))
	}

	fun discover(): List<String> {
		return loader.discover()
	}

	fun download(language: String) {
		var reader: InputStream? = null
		var file: OutputStream? = null
		try {
			reader = loader.download(language)
			file = context.openFileOutput(languageFilename(language), Context.MODE_PRIVATE)
			reader.copyTo(file)
			file.close()
			reader.close()
		} catch (e: IOException) {
			Log.w(TAG, "Failed to download wordlist $language", e)
			reader?.close()
			file?.close()
		}
	}

	fun load(wordList: AutocompleteEngine<WordListItem>, language: String) {
		var reader: InputStream? = null
		try {
			reader = context.openFileInput(languageFilename(language))
			wordList.addAll(IteratorCollection(
				WordListReader(BufferedReader(InputStreamReader(GZIPInputStream(reader))))
			))
		} catch (e: IOException) {
			Log.w(TAG, "Failed to read wordlist $language", e)
			reader?.close()
			try {
				context.deleteFile(languageFilename(language))
			} catch (e: IOException) {}
		}
	}
}