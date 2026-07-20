package com.westboytech.camera.filters

enum class FilterType(val label: String) {
    NONE("Original"),
    CLASSIC_BW("Classic B&W"),
    CYBERPUNK("Cyberpunk"),
    VINTAGE_90S("Vintage 90s"),
    CINEMATIC("Cinematic");

    companion object {
        fun next(current: FilterType): FilterType {
            val values = entries
            val idx = values.indexOf(current)
            return values[(idx + 1).coerceAtMost(values.size - 1)]
        }
        fun previous(current: FilterType): FilterType {
            val values = entries
            val idx = values.indexOf(current)
            return values[(idx - 1).coerceAtLeast(0)]
        }
    }
}
