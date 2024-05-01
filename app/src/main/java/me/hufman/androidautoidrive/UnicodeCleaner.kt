package me.hufman.androidautoidrive

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import io.wax911.emojify.EmojiManager
import io.wax911.emojify.model.Emoji
import io.wax911.emojify.parser.EmojiParser
import java.text.Normalizer

/** Cleans a text string to be suitable for showing in the car */
object UnicodeCleaner {
	/** Loads the list of emoji from a local asset file in the Context */
	fun init(context: Context) {
		try {
			EmojiManager.initEmojiData(context)
		} catch (e: Exception) {
			// can't find emoji data in context?
		}
	}

	/** Replaces any shortname tag to the matching emoji */
	fun encode(input: String): String {
		return EmojiParser.parseToUnicode(input)
	}

	/** Finds the first strong directionality class from this input */
	fun getStrongDirectionality(input: String): Byte? {
		return input.asSequence().map {
			Character.getDirectionality(it)
		}.map {
			when (it) {
				// ignore the embedding/override markers, but coalesce RTL Arabic to a strong RTL
				Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> Character.DIRECTIONALITY_RIGHT_TO_LEFT
				else -> it
			}
		}.firstOrNull {
			it == Character.DIRECTIONALITY_LEFT_TO_RIGHT ||
			it == Character.DIRECTIONALITY_RIGHT_TO_LEFT
		}
	}

	fun cleanBidiIsolates(input: String): String {
		val LRE = '\u202A'      // LTR embed
		val RLE = '\u202B'      // RTL embed
		val PDF = '\u202C'      // end embed
		val LRI = '\u2066'      // LTR isolate
		val RLI = '\u2067'      // RTL isolate
		val FSI = '\u2068'      // auto-ordered isolate
		val PDI = '\u2069'      // end isolate
		val ISOLATES = charArrayOf(LRI, RLI, FSI)
		var cleaned = input

		// clean out isolate symbols
		var lastIsolatePos = cleaned.lastIndexOfAny(ISOLATES)
		while (lastIsolatePos >= 0) {
			val isolatePop = cleaned.indexOf(PDI, lastIsolatePos)
			if (lastIsolatePos < cleaned.length && isolatePop > lastIsolatePos) {
				val isolate = cleaned[lastIsolatePos]
				val before = cleaned.substring(0, lastIsolatePos)
				val subsection = cleaned.substring(lastIsolatePos + 1, isolatePop)
				val after = cleaned.substring(isolatePop + 1)
				val directionality by lazy { getStrongDirectionality(subsection) }
				val newEmbed = if (isolate == LRI) {
					"$LRE$subsection$PDF"
				} else if (isolate == RLI) {
					"$RLE$subsection$PDF"
				} else if (directionality == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
					"$LRE$subsection$PDF"
				} else if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT) {
					"$RLE$subsection$PDF"
				} else {
					subsection      // just a bare string
				}
				cleaned = before + newEmbed + after
			}
			lastIsolatePos = cleaned.lastIndexOfAny(ISOLATES)
		}
		return cleaned
	}

	var _cleanFontForcedOn = false
	val FONT_VARIANTS = mapOf(
		0x2102 to 'C',  // Double-Struck Capital C
		0x210A to 'g',  // Script Small G
		0x210B to 'H',  // Script Capital H
		0x210C to 'H',  // Black-Letter Capital H
		0x210D to 'H',  // Double-Struck Capital H
		0x210E to 'h',  // Planck Constant
		0x210F to 'h',  // Planck Constant Over Two Pi
		0x2110 to 'I',  // Script Capital I
		0x2111 to 'I',  // Black-Letter Capital I
		0x2112 to 'L',  // Script Capital L
		0x2113 to 'l',  // Script Small L
		0x2115 to 'N',  // Double-Struck Capital N
		0x2119 to 'P',  // Double-Struck Capital P
		0x211A to 'Q',  // Double-Struck Capital Q
		0x211B to 'R',  // Script Capital R
		0x211C to 'R',  // Black-Letter Capital R
		0x211D to 'R',  // Double-Struck Capital R
		0x2124 to 'Z',  // Double-Struck Capital Z
		0x2128 to 'Z',  // Black-Letter Capital Z
		0x212C to 'B',  // Script Capital B
		0x212D to 'C',  // Black-Letter Capital C
		0x212F to 'e',  // Script Small E
		0x2130 to 'E',  // Script Capital E
		0x2131 to 'F',  // Script Capital F
		0x2133 to 'M',  // Script Capital M
		0x2134 to 'o',  // Script Small O
		0x2139 to 'i',  // Information Source
		0x213C to 'π',  // Double-Struck Small Pi
		0x213D to 'γ',  // Double-Struck Small Gamma
		0x213E to 'Γ',  // Double-Struck Capital Gamma
		0x213F to 'Π',  // Double-Struck Capital Pi
		0x2140 to 'Σ',  // Double-Struck N-Ary Summation
		0x2145 to 'D',  // Double-Struck Italic Capital D
		0x2146 to 'd',  // Double-Struck Italic Small D
		0x2147 to 'e',  // Double-Struck Italic Small E
		0x2148 to 'i',  // Double-Struck Italic Small I
		0x2149 to 'j',  // Double-Struck Italic Small J
		// greek letters
		0x1D6B9 to 'Θ', // Mathematical Bold Capital Theta Symbol
		0x1D6C1 to '∇', // Mathematical Bold Nabla
		0x1D6DB to '∂', // Mathematical Bold Partial Differential
		0x1D6DC to 'ε', // Mathematical Bold Epsilon Symbol
		0x1D6DD to 'ϑ', // Mathematical Bold Theta Symbol
		0x1D6DE to 'κ', // Mathematical Bold Kappa Symbol
		0x1D6DF to 'ϕ', // Mathematical Bold Phi Symbol
		0x1D6E0 to 'ϱ', // Mathematical Bold Rho Symbol
		0x1D6E1 to 'ϖ', // Mathematical Bold Pi Symbol
		0x1D6F3 to 'Θ', // Mathematical Italic Capital Theta Symbol
		0x1D6FB to '∇', // Mathematical Italic Nabla
		0x1D715 to '∂', // Mathematical Italic Partial Differential
		0x1D716 to 'ε', // Mathematical Italic Epsilon Symbol
		0x1D717 to 'ϑ', // Mathematical Italic Theta Symbol
		0x1D718 to 'κ', // Mathematical Italic Kappa Symbol
		0x1D719 to 'ϕ', // Mathematical Italic Phi Symbol
		0x1D71A to 'ϱ', // Mathematical Italic Rho Symbol
		0x1D71B to 'ϖ', // Mathematical Italic Pi Symbol
		0x1D72D to 'Θ', // Mathematical Bold Italic Capital Theta Symbol
		0x1D735 to '∇', // Mathematical Bold Italic Nabla
		0x1D74F to '∂', // Mathematical Bold Italic Partial Differential
		0x1D750 to 'ε', // Mathematical Bold Italic Epsilon Symbol
		0x1D751 to 'ϑ', // Mathematical Bold Italic Theta Symbol
		0x1D752 to 'κ', // Mathematical Bold Italic Kappa Symbol
		0x1D753 to 'ϕ', // Mathematical Bold Italic Phi Symbol
		0x1D754 to 'ϱ', // Mathematical Bold Italic Rho Symbol
		0x1D755 to 'ϖ', // Mathematical Bold Italic Pi Symbol
		0x1D767 to 'Θ', // Mathematical Sans-Serif Bold Capital Theta Symbol
		0x1D76F to '∇', // Mathematical Sans-Serif Bold Nabla
		0x1D789 to '∂', // Mathematical Sans-Serif Bold Partial Differential
		0x1D78A to 'ε', // Mathematical Sans-Serif Bold Epsilon Symbol
		0x1D78B to 'ϑ', // Mathematical Sans-Serif Bold Theta Symbol
		0x1D78C to 'κ', // Mathematical Sans-Serif Bold Kappa Symbol
		0x1D78D to 'ϕ', // Mathematical Sans-Serif Bold Phi Symbol
		0x1D78E to 'ϱ', // Mathematical Sans-Serif Bold Rho Symbol
		0x1D78F to 'ϖ', // Mathematical Sans-Serif Bold Pi Symbol
		0x1D7A1 to 'Θ', // Mathematical Sans-Serif Bold Italic Capital Theta Symbol
		0x1D7A9 to '∇', // Mathematical Sans-Serif Bold Italic Nabla
		0x1D7C3 to '∂', // Mathematical Sans-Serif Bold Italic Partial Differential
		0x1D7C4 to 'ε', // Mathematical Sans-Serif Bold Italic Epsilon Symbol
		0x1D7C5 to 'ϑ', // Mathematical Sans-Serif Bold Italic Theta Symbol
		0x1D7C6 to 'κ', // Mathematical Sans-Serif Bold Italic Kappa Symbol
		0x1D7C7 to 'ϕ', // Mathematical Sans-Serif Bold Italic Phi Symbol
		0x1D7C8 to 'ϱ', // Mathematical Sans-Serif Bold Italic Rho Symbol
		0x1D7C9 to 'ϖ', // Mathematical Sans-Serif Bold Italic Pi Symbol
		0x1D7CA to 'Ϝ', // Mathematical Bold Capital Digamma
		0x1D7CB to 'ϝ', // Mathematical Bold Small Digamma
	)
	@SuppressLint("NewApi")
	fun cleanFontVariations(input: String): String {
		/* Manually decomposes some characters from https://unicodeplus.com/decomposition/Font */
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || _cleanFontForcedOn) {
			input.codePoints().map {
				when (it) {
					in 0x1D401 .. 0x1D419 -> it - 0x1D401 + 'A'.code    // 𝐀 Mathematical Bold Capital
					in 0x1D41A .. 0x1D433 -> it - 0x1D41A + 'a'.code    // 𝐚 Mathematical Bold Small
					in 0x1D434 .. 0x1D44D -> it - 0x1D434 + 'A'.code    // 𝐴 Mathematical Italic Capital
					in 0x1D44E .. 0x1D467 -> it - 0x1D44E + 'a'.code    // 𝑎 Mathematical Italic Small
					in 0x1D468 .. 0x1D481 -> it - 0x1D468 + 'A'.code    // 𝑨 Mathematical Bold Italic Capital
					in 0x1D482 .. 0x1D49B -> it - 0x1D482 + 'a'.code    // 𝒂 Mathematical Bold Italic Small
					in 0x1D49C .. 0x1D4B5 -> it - 0x1D49C + 'A'.code    // 𝒜 Mathematical Script Capital
					in 0x1D4B6 .. 0x1D4CF -> it - 0x1D4B6 + 'a'.code    // 𝒶 Mathematical Script Small
					in 0x1D4D0 .. 0x1D4E9 -> it - 0x1D4D0 + 'A'.code    // 𝓐 Mathematical Bold Script Capital
					in 0x1D4EA .. 0x1D503 -> it - 0x1D4EA + 'a'.code    // 𝓪 Mathematical Bold Script Small
					in 0x1D504 .. 0x1D51D -> it - 0x1D504 + 'A'.code    // 𝔄 Mathematical Fraktur Capital
					in 0x1D51E .. 0x1D537 -> it - 0x1D51E + 'a'.code    // 𝔞 Mathematical Fraktur Small
					in 0x1D538 .. 0x1D551 -> it - 0x1D538 + 'A'.code    // 𝔸 Mathematical Double-Struck Capital
					in 0x1D552 .. 0x1D56B -> it - 0x1D51E + 'a'.code    // 𝕒 Mathematical Double-Struck Small
					in 0x1D56C .. 0x1D585 -> it - 0x1D56C + 'A'.code    // 𝕬 Mathematical Bold Fraktur Capital
					in 0x1D586 .. 0x1D59F -> it - 0x1D586 + 'a'.code    // 𝖆 Mathematical Bold Fraktur Small
					in 0x1D5A0 .. 0x1D5B9 -> it - 0x1D5A0 + 'A'.code    // 𝖠 Mathematical Sans-Serif Capital
					in 0x1D5BA .. 0x1D5D3 -> it - 0x1D5BA + 'a'.code    // 𝖺 Mathematical Sans-Serif Small
					in 0x1D5D4 .. 0x1D5ED -> it - 0x1D5D4 + 'A'.code    // 𝗔 Mathematical Sans-Serif Bold Capital
					in 0x1D5EE .. 0x1D607 -> it - 0x1D5EE + 'a'.code    // 𝗮 Mathematical Sans-Serif Bold Small
					in 0x1D608 .. 0x1D621 -> it - 0x1D608 + 'A'.code    // 𝘈 Mathematical Sans-Serif Italic Capital
					in 0x1D622 .. 0x1D63B -> it - 0x1D622 + 'a'.code    // 𝘢 Mathematical Sans-Serif Italic Small
					in 0x1D63C .. 0x1D655 -> it - 0x1D63C + 'A'.code    // 𝘼 Mathematical Sans-Serif Bold Italic Capital
					in 0x1D656 .. 0x1D66F -> it - 0x1D656 + 'a'.code    // 𝙖 Mathematical Sans-Serif Bold Italic Small
					in 0x1D670 .. 0x1D689 -> it - 0x1D670 + 'A'.code    // 𝙰 Mathematical Monospace Capital
					in 0x1D68A .. 0x1D6A3 -> it - 0x1D68A + 'a'.code    // 𝚊 Mathematical Monospace Small
					in 0x1D6A8 .. 0x1D6B8 -> it - 0x1D6A8 + 'Α'.code    // 𝚨 Mathematical Bold Capital Alpha to Rho
					in 0x1D6BA .. 0x1D6C0 -> it - 0x1D6BA + 'Σ'.code    // 𝚺 Mathematical Bold Capital Sigma to Omega
					in 0x1D6C2 .. 0x1D6DA -> it - 0x1D6C2 + 'α'.code    // 𝛂 Mathematical Bold Small
					in 0x1D6E2 .. 0x1D6F2 -> it - 0x1D6E2 + 'Α'.code    // 𝛢 Mathematical Italic Capital Alpha to Rho
					in 0x1D6F4 .. 0x1D6FA -> it - 0x1D6F4 + 'Σ'.code    // 𝛴 Mathematical Italic Capital Sigma to Omega
					in 0x1D6FC .. 0x1D714 -> it - 0x1D6FC + 'α'.code    // 𝛼 Mathematical Italic Small
					in 0x1D71C .. 0x1D72C -> it - 0x1D71C + 'Α'.code    // 𝜜 Mathematical Bold Italic Capital Alpha to Rho
					in 0x1D72E .. 0x1D734 -> it - 0x1D72E + 'Σ'.code    // 𝜮 Mathematical Bold Italic Capital Sigma to Omega
					in 0x1D736 .. 0x1D74E -> it - 0x1D736 + 'α'.code    // 𝜶 Mathematical Bold Italic Small
					in 0x1D756 .. 0x1D766 -> it - 0x1D756 + 'Α'.code    // 𝝖 Mathematical Sans-Serif Bold Capital Alpha to Rho
					in 0x1D768 .. 0x1D76E -> it - 0x1D768 + 'Σ'.code    // 𝝨 Mathematical Sans-Serif Bold Capital Sigma to Omega
					in 0x1D770 .. 0x1D788 -> it - 0x1D770 + 'α'.code    // 𝝰 Mathematical Sans-Serif Bold Small
					in 0x1D790 .. 0x1D7A0 -> it - 0x1D790 + 'Α'.code    // 𝞐 Mathematical Sans-Serif Bold Italic Capital Alpha to Rho
					in 0x1D7A2 .. 0x1D7A8 -> it - 0x1D7A2 + 'Σ'.code    // 𝞢 Mathematical Sans-Serif Bold Italic Capital Sigma to Omega
					in 0x1D7AA .. 0x1D7C2 -> it - 0x1D7AA + 'α'.code    // 𝞪 Mathematical Sans-Serif Bold Small
					in 0x1D7CE .. 0x1D7D7 -> it - 0x1D7CE + '0'.code    // 𝟎 Mathematical Bold Digit
					in 0x1D7D8 .. 0x1D7E1 -> it - 0x1D7D8 + '0'.code    // 𝟘 Mathematical Double-Struck Digit
					in 0x1D7E2 .. 0x1D7EB -> it - 0x1D7E2 + '0'.code    // 𝟢 Mathematical Sans-Serif Digit
					in 0x1D7EC .. 0x1D7F5 -> it - 0x1D7EC + '0'.code    // 𝟬 Mathematical Sans-Serif Bold Digit
					in 0x1D7F6 .. 0x1D7FF -> it - 0x1D7F6 + '0'.code    // 𝟶 Mathematical Monospace Digit
					in 0x1FBF0 .. 0x1FBF9 -> it - 0x1FBF0 + '0'.code    // 🯰 Segmented Digit
					else -> FONT_VARIANTS[it]?.code ?: it
				}
			}.toArray().let {
				String(it, 0, it.size)
			}
		} else {
			input
		}
	}

	// referenced from https://demos.joypixels.com/latest/ascii-smileys.html
	// with supplement from https://en.wikipedia.org/wiki/List_of_emoticons
	val EMOTICONS = mapOf(
			"joy" to "XD",
			"smiley" to ":D",
			"slight_smile" to ":)",
			"sweat_smile" to "':D",
			"laughing" to "XD",
			"wink" to ";)",
			"sweat" to "':(",
			"kissing_heart" to ":*",
			"stuck_out_tongue_winking_eye" to "XP",
			"disappointed" to ":(",
			"angry" to ">:(",
			"cry" to ":'(",
			"fearful" to "D:",
			"flushed" to ":$",
			"dizzy_face" to "%)",
			"innocent" to "O:)",
			"sunglasses" to "8)",
			"expressionless" to "-__-",
			"confused" to ":\\",
			"stuck_out_tongue" to ":P",
			"open_mouth" to ":O",
			"no_mouth" to ":X",
			// and some others from Emojify's data
			"grinning" to ":D",
			"smile" to ":D",
			"blush" to ":$",
			"relaxed" to ":)",
			"kissing_closed_eyes" to ":*",
			"kissing" to ":*",
			"kissing_smiling_eyes" to ":*",
			"stuck_out_tongue_closed_eyes" to "XP",
			"grin" to ":D",
			"unamused" to "-__-",
			"disappointed" to "v_v",
			"yum" to "XP",
			"astonished" to ":O",
			"frowning" to "D:",
			"anguished" to "D:",
			"smiling_imp" to ">:)",
			"grimacing" to ":E",
			"neutral_face" to ":|",
			"no_mouth" to ":X",
			// other symbols
			"yellow_heart" to "♥",
			"blue_heart" to "♥",
			"purple_heart" to "♥",
			"green_heart" to "♥",
			"heart" to "♥",
			"broken_heart" to "</3",
			"heartpulse" to "♥",
			"heartbeat" to "♥",
			"sparkling_heart" to "♥",
			"cupid" to "♥",
			"hearts" to "♥",
			"black_heart" to "♥",
			"orange_heart" to "♥",
			"diamonds" to "♦",
			"large_orange_diamond" to "♦",
			"large_blue_diamond" to "♦",
			"small_orange_diamond" to "♦",
			"small_blue_diamond" to "♦",
			"clubs" to "♣",
			"spades" to "♠",
	)

	fun convertEmoticon(alias: String): String {
		return EMOTICONS[alias] ?: alias
	}

	/** Replaces any supported unicode from this string to the shortname tag */
	fun clean(input: String, convertEmoticons: Boolean = true): String {
		val bidiCleaned = cleanBidiIsolates(input)
		val fontCleaned = cleanFontVariations(bidiCleaned)
		val normalizedCleaned = Normalizer.normalize(fontCleaned, Normalizer.Form.NFC)

		val emojiTransformer = object : EmojiParser.EmojiTransformer {
			override fun transform(unicodeCandidate: EmojiParser.UnicodeCandidate): String {
				val emoji = unicodeCandidate.emoji?.emoji ?: ""
				if (emoji[0].code < 255) {
					// base ascii code, like :copyright: and :registered:
					return emoji
				} else if (convertEmoticons && EMOTICONS.containsKey(unicodeCandidate.emoji?.aliases?.get(0) ?: "")) {
					return " " + convertEmoticon(unicodeCandidate.emoji?.aliases?.get(0) ?: "") + " "
				} else {
					return ":" + unicodeCandidate.emoji?.aliases?.get(0) + ":"
				}
			}
		}
		return EmojiParser.parseFromUnicode(normalizedCleaned, emojiTransformer)
	}

	/** Builds a simple Emoji object */
	@VisibleForTesting(otherwise = VisibleForTesting.NONE)
	fun _buildPlaceholderEmoji(emoji: String, aliases: List<String>, description: String): Emoji {
		return Emoji(emojiChar=emoji, emoji=emoji, unicode=emoji, aliases=aliases, description = description,
				htmlDec="&#${emoji[0].code};", htmlHex="&#${emoji[0].code.toString(16)};")
	}

	/** Builds just enough of an Emoji object to pass unit tests, and adds it to the data */
	@VisibleForTesting(otherwise = VisibleForTesting.NONE)
	fun _addPlaceholderEmoji(emoji: String, aliases: List<String>, description: String) {
		val emojiData = EmojiManager.getAll() as ArrayList<Emoji>
		emojiData.add(_buildPlaceholderEmoji(emoji, aliases, description))
	}

	/** Using a specific emoji alias search, return matching emoji */
	fun searchEmoji(emojiList: Collection<Emoji>, search: String, limit: Int = 10): List<Emoji> {
		if (search.isBlank()) return emptyList()
		val startMatches = emojiList.asSequence()
				.filter {  emoji ->
					emoji.aliases?.any { alias ->
						alias.startsWith(search, true)
					} ?: false
				}
		val subMatches = emojiList.asSequence()
				.filter { emoji ->
					emoji.aliases?.any { alias ->
						alias.contains("-$search", true) ||
						alias.contains("_$search", true)
					} ?: false
				}
		return (startMatches + subMatches)
				.distinct()
				.take(limit)
				.toList()
	}
}