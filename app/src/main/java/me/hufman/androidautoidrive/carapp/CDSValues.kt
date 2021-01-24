package me.hufman.androidautoidrive.carapp

import com.google.gson.JsonObject
import java.util.*

enum class CDSVehicleLanguage(val value: Int, val locale: Locale) {
	NONE(0, Locale.ROOT),
	DE(1, Locale("DE", "")),
	EN_UK(2, Locale("EN", "UK")),
	EN_US(3, Locale("EN", "US")),
	ES_ES(4, Locale("ES", "ES")),
	IT(5, Locale("IT", "")),
	FR_FR(6, Locale("FR", "FR")),
	NL_BE(7, Locale("NL", "BE")),
	NL_NL(8, Locale("NL", "NL")),
	AR(9, Locale("AR", "")),
	ZH_TW(10, Locale("ZH", "TW")),
	ZH_CN(11, Locale("ZH", "CN")),
	KO(12, Locale("KO", "")),
	JA(13, Locale("JA", "")),
	RU(14, Locale("RU", "")),
	FR_CA(15, Locale("FR", "CA")),
	ES_MX(16, Locale("ES", "MX")),
	PT(17, Locale("PT", "")),
	PL(18, Locale("PL", "")),
	EL(19, Locale("EL", "")),
	TR(20, Locale("TR", "")),
	HU(21, Locale("HU", "")),
	RO(22, Locale("RO", "")),
	SV(23, Locale("SV", "")),
	PT_BR(24, Locale("PT", "BR")),
	SK(27, Locale("SK", "")),
	CS(28, Locale("CS", "")),
	SL(29, Locale("SL", "")),
	DA(30, Locale("DA", "")),
	NO(31, Locale("NO", "")),
	FI(32, Locale("FI", "")),
	ID(33, Locale("ID", "")),
	TH(34, Locale("TH", "")),
	INVALID(255, Locale.ROOT);

	companion object {
		fun fromValue(value: Int?): CDSVehicleLanguage {
			return values().firstOrNull {
				it.value == value
			} ?: INVALID
		}

		fun fromCdsProperty(value: JsonObject?): CDSVehicleLanguage {
			return fromValue(value?.get("language")?.asInt)
		}
	}
}