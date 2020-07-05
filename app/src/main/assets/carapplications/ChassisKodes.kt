enum class ChassisCode(val brand: String, val chassis: String) {
    // Mini codes
    R50("MINI", "Cooper"),
    R53("MINI", "Cooper"),
    R56("MINI", "Cooper"),
    F55("MINI", "Cooper"),
    F56("MINI", "Cooper"),
    R52("MINI", "Convertible"),
    R57("MINI", "Convertible"),
    F57("MINI", "Convertible"),
    R55("MINI", "Clubman"),
    F54("MINI", "Clubman"),
    R58("MINI", "Coupe"),
    R59("MINI", "Roadster"),
    R61("MINI", "Paceman"),
    R60("MINI", "Countryman"),
    F60("MINI", "Countryman"),

    // BMW Codes
    F48("BMW", "X1"),
    F25("BMW", "X3"),
    E84("BMW", "X1"),
    E83("BMW", "X3"),
    E72("BMW", "X6"),
    E71("BMW", "X6"),
    E70("BMW", "X5"),
    E53("BMW", "X5"),
    E26("BMW", "M1"),
    E86("BMW", "Z4 Coupe"),
    E89("BMW", "Z4 Roadster"),
    E85("BMW", "Z4 Roadster"),
    E25("BMW", "Concept Turbo"),

    E9("BMW",	"2 Series"),
    E6("BMW",	"2 Series"),
    E3("BMW",	"2 Series"),

    F36("BMW", "4 Series"),
    F33("BMW", "4 Series"),
    F32("BMW", "4 Series"),
    F35("BMW", "3 Series"),
    F34("BMW", "3 Series"),
    F31("BMW", "3 Series"),
    F30("BMW", "3 Series"),
    F23("BMW", "1 Series"),
    F22("BMW", "1 Series"),
    F21("BMW", "1 Series"),
    F20("BMW", "1 Series"),
    F18("BMW", "5 Series"),
    F13("BMW", "6 Series"),
    F12("BMW", "6 Series"),
    F11("BMW", "5 Series"),
    F10("BMW", "5 Series"),
    F07("BMW", "5 Series"),
    F04("BMW", "7 Series"),
    F03("BMW", "7 Series"),
    F02("BMW", "7 Series"),
    F01("BMW", "7 Series"),
    E93("BMW", "3 Series"),
    E92("BMW", "3 Series"),
    E91("BMW", "3 Series"),
    E90("BMW", "3 Series"),
    E88("BMW", "1 Series"),
    E87("BMW", "1 Series"),
    E82("BMW", "1 Series"),
    E81("BMW", "1 Series"),
    E68("BMW", "7 Series"),
    E67("BMW", "7 Series"),
    E66("BMW", "7 Series"),
    E65("BMW", "7 Series"),
    E64("BMW", "6 Series"),
    E63("BMW", "6 Series"),
    E61("BMW", "5 Series"),
    E60("BMW", "5 Series"),
    E52("BMW", "8 Series"),
    E46("BMW", "3 Series"),
    E39("BMW", "5 Series"),
    E38("BMW", "7 Series"),
    E36("BMW", "3 Series"),
    E34("BMW", "5 Series"),
    E32("BMW", "7 Series"),
    E31("BMW", "8 Series"),
    E30("BMW", "3 Series"),
    E28("BMW", "5 Series"),
    E24("BMW", "6 Series"),
    E23("BMW", "7 Series"),
    E21("BMW", "3 Series"),
    E12("BMW", "5 Series"),
    E82E("BMW", "1 Series");

    companion object {
        fun fromCode(chassisCode: String): ChassisCode? {
            return ChassisCode.values().firstOrNull {
                it.name == chassisCode.toUpperCase()
            }
        }
    }

    override fun toString(): String {
        return "$brand $chassis"
    }
}
